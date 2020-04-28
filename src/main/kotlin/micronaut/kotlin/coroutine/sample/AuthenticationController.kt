@file:Suppress("FunctionOnlyReturningConstant")

package micronaut.kotlin.coroutine.sample

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationFailureReason
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.handlers.LoginHandler
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.rules.SecurityRuleResult
import io.micronaut.web.router.RouteMatch
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import javax.inject.Singleton

@Controller("/login")
// デフォルトでは、認証済みでアクセスをするように設定
@Secured(SecurityRule.IS_ANONYMOUS)
class AuthenticationController {
    /** ロガー */
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * ログイン画面表示
     * http://localhost:8080/auth/login
     */
    @Get("/")
    @Produces(MediaType.TEXT_HTML)
    fun index(): String {
        log.info("Start index")
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"><title>Login</title></head>
            <body><form method="post" action="/login">
              <p><label>ユーザ ID: <input type="text" name="username"></label></p>
              <p><label>パスワード: <input type="password" name="password"></label></p>
              <p><input type="submit" value="ログイン"></p>
            </form></body></html>
        """.trimIndent()
    }

    @Get("/auth")
    @Produces(MediaType.TEXT_HTML)
    fun auth(): String {
        log.info("Start auth")
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"><title>Login failed</title></head>
            <body>
            <h1>ログイン成功</h1
            </body></html>
        """.trimIndent()
    }

    /**
     * ログイン失敗画面表示
     * http://localhost:8080/auth/failed
     */
    @Get("/authFailed")
    @Produces(MediaType.TEXT_HTML)
    fun failed(): String {
        log.info("Start failed")
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"><title>Login failed</title></head>
            <body>
            <h1>ログイン失敗</h1
            </body></html>
        """.trimIndent()
    }

    @Get("/admin")
    @Produces(MediaType.TEXT_HTML)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    fun admin(): String {
        log.info("Start admin")
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"><title>admin</title></head>
            <body>
            <h1>認証済み</h1
            </body></html>
        """.trimIndent()
    }
}

/**
 * 適当なページ
 */
@Controller("/foo")
class Foo {
    /** ロガー */
    private val log = LoggerFactory.getLogger(this.javaClass)

    @Get("/")
    @Produces(MediaType.TEXT_HTML)
    fun index(): String {
        log.info("Start admin")
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"><title>admin</title></head>
            <body>
            <h1>認証済み</h1
            </body></html>
        """.trimIndent()
    }
}

/**
 * 認証処理実装
 */
@Singleton
class AuthenticationProviderUserPassword : AuthenticationProvider {
    /** ロガー */
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * 認証処理を行う
     *
     * この関数は、デフォルト実装が定義されているため、必須の実装ではないが、
     * 実装が必須の引数の異なる authenticate 関数は、非推奨となっているため、
     * 今後は、こちらの関数を利用する。
     * この関数のデフォルト実装では、非推奨関数の方を内部から呼び出しているため、
     * この関数を実装しない場合は、非推奨関数をきちんと実装する必要がある。
     *
     * @param request リクエスト
     * @param authenticationRequest 認証情報
     * @return 認証結果を返す。
     */
    override fun authenticate(
        request: HttpRequest<*>,
        authenticationRequest: AuthenticationRequest<*, *>
    ): Publisher<AuthenticationResponse> {
        log.info("Start authenticate()")
        return if (
            authenticationRequest.identity == "users"
            && authenticationRequest.secret == "password"
        ) {
            log.info("Finish authenticate() Successful")
            Flowable.just(UserDetails(authenticationRequest.identity.toString(), listOf()))
        } else {
            log.info("Finish authenticate() Failed")
            val reason = AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH
            Flowable.just(AuthenticationFailed(reason))
        }
    }

    /**
     * 非推奨メソッドであり、利用しないが、実装が必須
     */
    override fun authenticate(
        authenticationRequest: AuthenticationRequest<*, *>
    ): Publisher<AuthenticationResponse> {
        TODO("Unsupported Function")
    }
}

/**
 * ログイン認証後のレスポンス処理
 */
@Singleton
class CustomLoginHandler : LoginHandler {
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * ログイン成功時
     *
     * @param userDetails ユーザ詳細
     * @param request リクエスト
     * @return ログイン成功時のレスポンスを返す。
     */
    override fun loginSuccess(userDetails: UserDetails, request: HttpRequest<*>): HttpResponse<*> {
        log.info("Start loginSuccess")
        return HttpResponse.ok("Authentication Successful [userDetails: $userDetails]")
    }

    /**
     * ログイン失敗時
     *
     * この関数は、デフォルト実装が定義されているため、必須の実装ではないが、
     * 実装が必須の引数の異なる loginFailed 関数は、非推奨となっているため、
     * 今後は、こちらの関数を利用する。
     * この関数のデフォルト実装では、非推奨関数の方を内部から呼び出しているため、
     * この関数を実装しない場合は、非推奨関数をきちんと実装する必要がある。
     *
     * @param authenticationResponse 認証結果
     * @return ログイン失敗時のレスポンスを返す。
     */
    override fun loginFailed(authenticationResponse: AuthenticationResponse): MutableHttpResponse<*> {
        log.info("Start loginSuccess")
        val message = authenticationResponse.message.orElse("None")
        return HttpResponse.unauthorized<String>().body("Login failed [$message]")
    }

    /**
     * 非推奨メソッドで、利用しないが、実装が必須
     */
    override fun loginFailed(authenticationFailed: AuthenticationFailed): HttpResponse<*> {
        TODO("Unsupported function")
    }
}

/**
 * カスタム・セキュリティ・ルール
 */
@Singleton
class CustomSecurityRule : SecurityRule {
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * セキュリティチェック
     *
     * @param request リクエスト
     * @param routeMatch 一致したルーティング情報（メソッドや、パス、コントローラクラスなど）
     * @param claims クレーム
     * @return チェック結果を返す。
     */
    override fun check(
        request: HttpRequest<*>,
        routeMatch: RouteMatch<*>?,
        claims: MutableMap<String, Any>?
    ): SecurityRuleResult {
        if (request.path !in listOf("/login", "/foo")) return SecurityRuleResult.UNKNOWN
        log.info("Start check")
        val auth = request.headers.authorization
        return if (auth.isPresent) {
            log.info("Authorization header: ${auth.get()}")
            log.info("Finish check successful")
            SecurityRuleResult.ALLOWED
        } else {
            log.info("Finish check failed")
            SecurityRuleResult.REJECTED
        }
    }
}

//@Singleton
//@Replaces(HttpStatusCodeRejectionHandler::class)
//class CustomRejectionHandler : HttpStatusCodeRejectionHandler() {
//    private val log = LoggerFactory.getLogger(this.javaClass)
//    override fun reject(request: HttpRequest<*>, forbidden: Boolean): Publisher<MutableHttpResponse<*>> {
//        log.info("Start reject")
//        return Flowable.fromPublisher(super.reject(request, forbidden))
//            .map { response ->
//                response.header("X-Reason", "Example Header")
//            }
//    }
//}
