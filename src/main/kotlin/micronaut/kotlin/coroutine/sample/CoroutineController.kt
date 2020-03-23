package micronaut.kotlin.coroutine.sample

import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.Status
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

@Controller("/coroutine")
class CoroutineController {
    @Get("/")
    @Produces("${MediaType.TEXT_PLAIN}; ${MediaType.CHARSET_PARAMETER}=utf-8")
    // メソッドを Coroutine 対応にするため、suspend 関数にできる
    suspend fun index(): String {
        coroutineScope {
            println("index")
        }
        return "Hello"
    }

    @Get("/delayed")
    @Produces("${MediaType.TEXT_PLAIN}; ${MediaType.CHARSET_PARAMETER}=utf-8")
    suspend fun delayed(): String {
        delay(1)
        return "Delayed"
    }

    // ステータスを返すだけの関数でも、Suspend 関数として機能する。
    @Status(HttpStatus.CREATED)
    @Get("/status")
    suspend fun status(): Unit {
    }

    @Status(HttpStatus.CREATED)
    @Get("/statusDelayed")
    suspend fun statusDelayed(): Unit {
        delay(1)
    }

    // Coroutine の Flow を利用することにより、サーバー側でストリーミング処理を実装できる。
    @Get("/headlinesWithFlow", processes = [MediaType.APPLICATION_JSON_STREAM])
    internal fun streamHeadlinesWithFlow(): Flow<Headline> =
        flow {
            println("Start streamHeadlinesWithFlow")
            repeat(10) {
                println("send ${it + 1} times")
                val headline = Headline("Latest Headline at ${ZonedDateTime.now()}")
                emit(headline)
                delay(100)
            }
            println("Finish streamHeadlinesWithFlow")
        }

    @Get("/background")
    @Produces("${MediaType.TEXT_PLAIN};${MediaType.CHARSET_PARAMETER}=utf-8")
    suspend fun background(): String {
        GlobalScope.launch {
            delay(1000L)
            println("World")
        }
        println("Hello,")
        delay(2000L)
        return "OK"
    }
}

// ストリーミング処理用のデータクラス
data class Headline(var title: String, var description: String? = null)
