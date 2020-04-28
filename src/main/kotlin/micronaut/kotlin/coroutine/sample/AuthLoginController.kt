@file:Suppress("MaxLineLength")

package micronaut.kotlin.coroutine.sample

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.rules.SecurityRuleResult
import io.micronaut.web.router.RouteMatch
import org.slf4j.LoggerFactory
import java.util.*
import javax.inject.Singleton

/**
 * ログイン・コントローラ
 *
 * @param authTokenHolder 認証トークン保持用
 */
@Controller("/auth/login")
@Secured(SecurityRule.IS_ANONYMOUS)
class AuthLoginController(
    private val authTokenHolder: AuthTokenHolder
) {
    /** ロガー */
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * ログイン
     *
     * ここで生成する Authorization: Bearer ヘッダー用のトークンは、
     * AuthTOTPController.kt の CustomAuthTokenReader クラスで抽出し、
     * CustomAuthTokenValidator クラスで判定するための形式になっている。
     * 「ユーザID:トークン」形式文字列を Base64 でエンコードしている。
     *
     * curl -iv http://localhost:8080/auth/login -H "Content-Type: application/json; charset=utf-8" -d "{\"orgId\": \"org1\", \"userId\": \"user1\", \"password\": \"password\"}"
     *
     * @param loginData ログインデータ
     * @return HTTP レスポンスを返す。
     */
    @Post
    fun login(@Body loginData: LoginData, request: HttpRequest<*>): HttpResponse<*> {
        return if (loginData.orgId == "org1" && loginData.userId == "user1" && loginData.password == "password") {
            val token = (('a'..'z') + ('A'..'Z') + ('0'..'9'))
                .shuffled()
                .subList(0, 16)
                .joinToString("")
            log.info("token: $token")
            val authToken = "${loginData.orgId}\t${loginData.userId}:$token"
            log.info("authToken: $authToken")
            authTokenHolder.clear()
            // 生成したトークンを保持
            authTokenHolder.add(authToken)
            // トークンを Base64 形式に変換
            val tokenBase64 = Base64.getEncoder().encodeToString(authToken.toByteArray())
            log.info("tokenBase64: $tokenBase64")
            HttpResponse.ok("""{"token": "$tokenBase64"}""")
        } else {
            HttpResponse.unauthorized<String>()
        }
    }

    /**
     * ログイン情報
     *
     * @property orgId 組織 ID
     * @property userId ユーザ ID
     * @property password パスワード
     */
    data class LoginData(val orgId: String, val userId: String, val password: String)
}

/**
 * 認証が必要なコントローラ
 */
@Controller("/auth/data")
class AuthDataController {
    /**
     * 認証ありページ
     *
     * curl -iv http://localhost:8080/auth/data -H "Content-Type: application/json; charset=utf-8"
     * curl -iv http://localhost:8080/auth/data -H "Content-Type: application/json; charset=utf-8" -H "Authorization: Bearer ＜ここにトークンを指定＞"
     * curl -iv http://localhost:8080/auth/data -H "Content-Type: application/json; charset=utf-8" -H "Authorization: Bearer b3JnMQl1c2VyMTo1cGFpbko3TkhXUTg5RTBT"
     */
    @Get
    fun index()= HttpStatus.OK.toString()
}

/**
 * 認証トークン保持用
 */
@Singleton
class AuthTokenHolder {
    private val holder: MutableSet<String> = mutableSetOf()
    fun add(token: String): Boolean = holder.add(token)
    fun contains(token: String): Boolean = holder.contains(token)
    fun clear() = holder.clear()
}
