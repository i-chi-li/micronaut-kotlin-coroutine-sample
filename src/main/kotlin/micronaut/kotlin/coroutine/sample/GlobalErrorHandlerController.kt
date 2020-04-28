package micronaut.kotlin.coroutine.sample

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.hateoas.Link
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.security.authentication.AuthorizationException
import org.slf4j.LoggerFactory
import java.sql.SQLException
import javax.inject.Singleton

@Controller("/handler")
class GlobalErrorHandlerController {
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * グローバル例外ハンドラ
     * 未処理例外が発生した場合に処理をする。
     * 任意の例外のみを処理することもできる。
     *
     * Throwable で、すべての例外を処理してしまうと、
     * AuthorizationException も処理対象となってしまい、
     * デフォルトの認証関連処理が行われなくなる。
     *
     * この関数内で、例外をスローすると、再びこの関数が呼び出され、ループするので注意。
     *
     * ハンドラは、グローバルであっても、必ず Controller クラス内に定義する必要がある。
     *
     * @param request HTTP リクエスト
     * @param e 例外
     * @return HTTP レスポンスを返す。
     */
//    @Error(global = true)
    fun globalErrorHandler(request: HttpRequest<*>, e: Throwable): HttpResponse<JsonError> {
        log.info("Start globalErrorHandler(): ${e.javaClass.simpleName}")
        val error = JsonError("Global Exception Error Handler: [${e.javaClass.simpleName}] message: ${e.message ?: ""}")
            .link(Link.SELF, Link.of(request.uri))
        return HttpResponse.serverError<JsonError>()
            .body(error)
    }

    /**
     * グローバル未認証例外ハンドラ
     *
     * 未認証アクセス時の、統一したレスポンスを定義できる。
     *
     * @param request HTTP リクエスト
     * @param e 例外
     * @return HTTP レスポンスを返す。
     */
//    @Error(global = true, exception = AuthorizationException::class)
    fun globalAuthorizationExceptionHandler(
        request: HttpRequest<*>,
        e: AuthorizationException
    ): HttpResponse<JsonError>? {
        log.info("Start globalAuthorizationExceptionHandler(): ${e.javaClass.simpleName}")

        // 未認証アクセス時に、ログイン画面へ遷移させる。
//        return HttpResponse.seeOther(URI("/login"))

        // 未認証アクセス時に、エラーレスポンスを返す。
        val error = JsonError(
            "Global AuthorizationException Handler: [${e.javaClass.simpleName}] message: ${e.message ?: ""}"
        )
            .link(Link.SELF, Link.of(request.uri))
        return HttpResponse.unauthorized<JsonError>()
            .body(error)
    }

    /**
     * グローバルステータスハンドラ
     * 処理で、任意のステータスを返した場合に処理をする。
     * 未処理例外時の INTERNAL_SERVER_ERROR は、このハンドラでは処理できない。
     * 処理から、明示的に INTERNAL_SERVER_ERROR を返した場合のみ、このハンドラで処理できる。
     *
     * @param request
     * @return
     */
    @Error(global = true, status = HttpStatus.INTERNAL_SERVER_ERROR)
    fun globalStatusHandler(request: HttpRequest<*>): HttpResponse<JsonError> {
        log.info("Start globalStatusHandler()")
        val error = JsonError("Status Error Handler.")
            .link(Link.SELF, Link.of(request.uri))
        return HttpResponse.notFound<JsonError>()
            .body(error)
    }

    /**
     * SQLException をスローする。
     * curl -i http://localhost:8080/handler/exception
     */
    @Get("/exception")
    @Produces(MediaType.APPLICATION_JSON)
    fun exception(): HttpResponse<String> {
        throw SQLException("throw Something Throwable")
    }

    /**
     * 明示的に INTERNAL_SERVER_ERROR レスポンスコードを返す。
     * curl -i http://localhost:8080/handler/status
     */
    @Get("/status")
    @Produces(MediaType.APPLICATION_JSON)
    fun status(): HttpResponse<String> {
        return HttpResponse.serverError("Response Server Error")
    }
}

@Singleton
class CustomThrowableErrorHandler : ExceptionHandler<Throwable, HttpResponse<*>> {
    private val log = LoggerFactory.getLogger(this.javaClass)
    override fun handle(request: HttpRequest<*>, exception: Throwable): HttpResponse<*> {
        log.error("Throwable handler [${exception.message}]", exception)
        return HttpResponse.badRequest("Error: ${exception.message}")
    }
}
