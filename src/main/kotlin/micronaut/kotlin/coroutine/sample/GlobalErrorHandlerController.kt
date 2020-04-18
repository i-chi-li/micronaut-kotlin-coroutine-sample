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
import org.slf4j.LoggerFactory
import java.sql.SQLException

@Controller("/handler")
class GlobalErrorHandlerController {
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * グローバル例外ハンドラ
     * 未処理例外が発生した場合に処理をする。
     * 任意の例外のみを処理することもできる。
     *
     * ハンドラは、グローバルであっても、必ず Controller クラス内に定義する必要がある。
     *
     * @param request HTTP リクエスト
     * @param e 例外
     * @return HTTP レスポンスを返す。
     */
    @Error(global = true)
    fun globalErrorHandler(request: HttpRequest<*>, e: Throwable): HttpResponse<JsonError> {
        log.info("Start globalErrorHandler()", e)
        val error = JsonError("Exception Error Handler: ${e.message}")
            .link(Link.SELF, Link.of(request.uri))
        return HttpResponse.serverError<JsonError>()
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
