@file:Suppress("MaxLineLength")

package micronaut.kotlin.coroutine.sample

import com.warrenstrange.googleauth.GoogleAuthenticator
import com.warrenstrange.googleauth.ICredentialRepository
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.authentication.DefaultAuthentication
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.token.reader.HttpHeaderTokenReader
import io.micronaut.security.token.reader.TokenReader
import io.micronaut.security.token.validator.TokenValidator
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import javax.inject.Singleton

/**
 * TOTP 認証処理
 */
@Controller("/totp")
class AuthTOTPController {
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * 秘密鍵を生成して取得する
     *
     * curl -iv "http://localhost:8080/totp/generateKey?userId=user1"
     *
     * @param userId 秘密鍵の識別名
     * @return
     */
    @Get("/generateKey")
    @Secured(SecurityRule.IS_ANONYMOUS)
    fun generateKey(userId: String): String {
        val auth = GoogleAuthenticator()
        val credential = auth.createCredentials(userId)
        log.info("secretKey: ${credential.key}")
        return """{"userId": "$userId", "secretKey": "${credential.key}"}"""
    }

    /**
     * ユーザ ID から、検証コード（6 桁の数値）を生成して取得する
     *
     * 本来、検証コードは、クライアント側で生成する。
     *
     * curl -iv "http://localhost:8080/totp/getCode?userId=user1"
     */
    @Get("/getCode")
    @Secured(SecurityRule.IS_ANONYMOUS)
    fun getCode(userId: String): String {
        val auth = GoogleAuthenticator()
        val verificationCode = auth.getTotpPasswordOfUser(userId)
        // "userId:verificationCode" を Base64 エンコード
        val authToken = Base64.getEncoder().encodeToString("$userId:$verificationCode".toByteArray())
        return """{"result": "Authorization: Bearer $authToken"}"""
    }

    /**
     * 認証が必要なリクエスト
     *
     * curl -iv http://localhost:8080/totp/list -H "Content-Type: application/json; charset=utf-8" -H "Authorization: Bearer ＜ここにトークンを指定＞"
     * curl -iv http://localhost:8080/totp/list -H "Content-Type: application/json; charset=utf-8" -H "Authorization: Bearer dXNlcjE6MjcxODk4"
     */
    @Get("/list")
    fun list() = HttpStatus.OK.toString()
}

/**
 * TOTP ライブラリで利用する
 *
 * META-INF/services/com.warrenstrange.googleauth.ICredentialRepository ファイルにクラスを定義する。
 *
 */
class CredentialRepositoryTOTP : ICredentialRepository {
    private val log = LoggerFactory.getLogger(this.javaClass)

    companion object {
        val secretHolder: ConcurrentMap<String, String> = ConcurrentHashMap()
    }

    override fun saveUserCredentials(
        userName: String,
        secretKey: String,
        validationCode: Int,
        scratchCodes: MutableList<Int>
    ) {
        log.info("Start saveUserCredentials" +
            " [userName: $userName, secretKey: $secretKey," +
            " validationCode: $validationCode, scratchCodes: $scratchCodes]")
        secretHolder[userName] = secretKey
        log.info("secretHolder: $secretHolder")
        log.info("Finish saveUserCredentials")
    }

    override fun getSecretKey(userName: String): String? {
        log.info("Start getSecretKey")
        val key = secretHolder[userName]
        log.info("key: $key")
        log.info("Finish getSecretKey")
        return key
    }
}

/**
 * 認証用のトークンを取得する
 * HTTP ヘッダー用のベースクラスを継承している。
 * 認証用の値を、ヘッダ以外から取得する場合は、
 * TokenReader のみを実装する。
 */
@Singleton
class CustomAuthTokenReader : TokenReader, HttpHeaderTokenReader() {
    /**
     * 認証に使用するヘッダ名を返す
     * ベースクラスで、リクエストヘッダから取得してくれる。
     */
    override fun getHeaderName(): String {
        return "Authorization"
    }

    /**
     * 認証に使用するヘッダの値に付いているプレフィックスを返す。
     * ベースクラスで、プレフィックスを取り除いてくれる。
     * この場合は、"Bearer "が取り除かれる。
     */
    override fun getPrefix(): String {
        return "Bearer"
    }
}

/**
 * 認証用のトークンを検証する
 */
@Singleton
class CustomAuthTokenValidator(
    private val authTokenHolder: AuthTokenHolder
) : TokenValidator {
    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun getOrder(): Int {
        return Ordered.HIGHEST_PRECEDENCE + 10_000
    }
    /**
     * トークンを検証する
     * 戻り値に、Flowable.empty() を返すと、
     * 認証できなかったこととなる。
     *
     * DefaultAuthentication に格納している Map は、
     * SecurityRule の check メソッドの claims 引数に渡される。
     * claims の値が null の場合、認証ありアクセスができなくなる。
     * したがって、少なくとも空の Map を格納する必要がある。
     */
    override fun validateToken(token: String): Publisher<Authentication> {
        log.info("Start validateToken")
        // トークンを Base64 デコードする
        val decoded = runCatching {
            Base64.getDecoder().decode(token.toByteArray()).toString(Charsets.UTF_8)
        }
            .onFailure {
                log.error("Base64 decoded failed: ${it.message}")
            }
            .getOrNull()
        val paramList = decoded?.split(":")
        if(paramList == null || paramList.size != 2) {
            return Flowable.empty()
        }
        val (userId, secret) = paramList
        val code = secret.toIntOrNull()

        // 認証キーを判定
        return if (authTokenHolder.contains(decoded)) {
            // AuthLoginController からのリクエストでの認証成功の場合
            Flowable.just(DefaultAuthentication(
                "CustomAuth",
                mutableMapOf<String, Any>(
                    "AuthType" to "Custom",
                    "userId" to userId,
                    "secret" to secret,
                    // 認証したユーザが保持するロールを指定する。
                    // ロールは、@Secured や、intercept-url-map の access に指定した文字列と比較される
                    "roles" to listOf("CUSTOM_ROLE_1")
                )
            ))
        } else if (userId == "user1" && code != null) {
            // AuthTOTPController からのリクエストでの認証の場合
            val auth = GoogleAuthenticator()
            val valid = auth.authorizeUser(userId, code)
            if (valid) {
                // 認証成功
                Flowable.just<Authentication>(DefaultAuthentication(
                    "CustomAuth",
                    mutableMapOf<String, Any>(
                        "AuthType" to "Custom",
                        "userId" to userId,
                        "code" to code
                    )
                ))
            } else {
                // 認証失敗
                Flowable.empty()
            }
        } else {
            // 認証失敗
            Flowable.empty()
        }
    }
}
