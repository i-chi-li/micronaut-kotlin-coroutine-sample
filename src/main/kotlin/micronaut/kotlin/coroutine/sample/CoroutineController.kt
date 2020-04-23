package micronaut.kotlin.coroutine.sample

import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import kotlin.coroutines.CoroutineContext

@Controller("/coroutine")
class CoroutineController {
    @Get("/")
    @Produces(CustomMediaType.TEXT_PLAIN_UTF8)
    // メソッドを Coroutine 対応にするため、suspend 関数にできる
    suspend fun index(): String {
        coroutineScope {
            println("index")
        }
        return "Hello"
    }

    @Get("/delayed")
    @Produces(CustomMediaType.TEXT_PLAIN_UTF8)
    suspend fun delayed(): String {
        delay(1)
        return "Delayed"
    }

    // ステータスを返すだけの関数でも、Suspend 関数として機能する。
    @Status(HttpStatus.CREATED)
    @Get("/status")
    suspend fun status() {
    }

    @Status(HttpStatus.CREATED)
    @Get("/statusDelayed")
    suspend fun statusDelayed(): Unit {
        delay(1)
    }

    // Coroutine の Flow を利用することにより、サーバー側でストリーミング処理を実装できる。
    @Get("/headlinesWithFlow")
    @Produces(MediaType.APPLICATION_JSON_STREAM)
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
    @Produces(CustomMediaType.TEXT_PLAIN_UTF8)
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


// ------------------------------------------------------------
// ThreadContextElement インターフェース利用例
// ------------------------------------------------------------

/**
 * Coroutine 切替時のスレッド名を管理する Coroutine コンテキストの実装例
 *
 * Coroutine コンテキストは、「Coroutine コンテキスト要素」毎に管理する。
 * Coroutine コンテキスト要素は、一意のキーで識別する。
 *
 * @property name スレッド名
 */
class CoroutineName(val name: String) : ThreadContextElement<String> {
    // CoroutineName を特定する Coroutine コンテキスト要素キーを定義する。
    // companion object で定義するので、シングルトンとなる。
    companion object Key : CoroutineContext.Key<CoroutineName>

    /**
     * このインスタンスの Coroutine コンテキスト要素キーを返す。
     */
    override val key: CoroutineContext.Key<*>
        get() = Key

    /**
     * 新たに Coroutine を開始する場合に呼び出される。
     * スレッド名を更新する。
     *
     * @param context Coroutine コンテキスト
     * @return 呼び出し元のスレッド名を返す。
     */
    override fun updateThreadContext(context: CoroutineContext): String {
        // 現在のスレッド名を取得
        val previousName = Thread.currentThread().name
        // スレッド名を更新
        Thread.currentThread().name = "$previousName # $name"
        // 以前のスレッド名を返す。
        return previousName
    }

    /**
     * 呼び出し元の Coroutine へ復帰する場合に呼び出される。
     * スレッド名を復元する。
     *
     * @param context Coroutine コンテキスト
     * @param oldState この Coroutine へ遷移する前のスレッド名
     */
    override fun restoreThreadContext(context: CoroutineContext, oldState: String) {
        Thread.currentThread().name = oldState
    }
}

fun main() = runBlocking<Unit> {
    println("Thread name: ${Thread.currentThread().name}")
    launch(Dispatchers.Default + CoroutineName("Progress bar coroutine")) {
        println("Thread name: ${Thread.currentThread().name}")
        withContext(CoroutineName("Nested context")) {
            println("Thread name: ${Thread.currentThread().name}")
        }
        println("Thread name: ${Thread.currentThread().name}")
    }.join()
    println("Thread name: ${Thread.currentThread().name}")
}

