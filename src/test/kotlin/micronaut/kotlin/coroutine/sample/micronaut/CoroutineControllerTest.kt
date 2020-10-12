package micronaut.kotlin.coroutine.sample.micronaut

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest.annotation.MicronautTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import micronaut.kotlin.coroutine.sample.Headline

// Micronaut の DI などの機能を利用して、テストを記述するためのアノテーション
// テスト対象も自動的に起動される。
@MicronautTest
@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineControllerTest(
    // コンストラクタの引数が、DI される。
    @Client("/")
    private val client: RxHttpClient,
    private val headlineClient: HeadlineClient
) : StringSpec({
    "suspend function" {
        val result = client.toBlocking().retrieve("/coroutine")
//        println("[${description().name}] result: $result")
        result shouldNotBe null
    }

    "delayed" {
        val result = client.toBlocking().retrieve("/coroutine/delayed")
//        println("[${description().name}] result: $result")
        result shouldNotBe null
    }

    "status" {
        val result = client.toBlocking().exchange<String>("/coroutine/status")
//        println("[${description().name}]: ${result.status}")
        result shouldNotBe null
    }

    "statusDelayed" {
        val result = client.toBlocking().exchange<String>("/coroutine/status")
//        println("[${description().name}]: ${result.status}")
        result shouldNotBe null
    }

    // Flow 処理でストリーミングを受信
    "headlinesWithFlow" {
        val stream = headlineClient.streamHeadlines()
//        println("[${description().name}]: Start")
        val result = mutableListOf<Headline>()
        // Suspend 関数を利用可能
        stream.collect {
            // サーバ側の処理は、一度に全部行われるのではなく、クライアントでデータを要求する度にデータを返す。
//            println("[${description().name}]: $it")
            result.add(it)
        }
        result.size shouldBe 10
//        println("[${description().name}]: Finish")
    }

    "background" {
        val result = client.toBlocking().retrieve("/coroutine/background")
//        println("[${description().name}]: $result")
        result shouldNotBe null
    }
})

// 任意の型で、HTTP レスポンスを受け取る場合、
// 以下のようにインターフェースを定義する必要がある。
// Coroutine の Flow 処理で、ストリーミングを受信する例
@Client("/coroutine")
interface HeadlineClient {
    @Get(value = "/headlinesWithFlow", processes = [MediaType.APPLICATION_JSON_STREAM])
    fun streamHeadlines(): Flow<Headline>
}

/*
実行結果
[] が先頭に付いている場合、テストケースで出力しているログ。それ以外は、テスト対象が出力しているログ。

index
[suspend function] result: Hello
[delayed] result: Delayed
[status]: CREATED
[statusDelayed]: CREATED
[headlinesWithFlow]: Start
Start streamHeadlinesWithFlow
send 1 times
[headlinesWithFlow]: Headline(title=Latest Headline at 2020-03-31T09:58:46.726+09:00[Asia/Tokyo], description=null)
send 2 times
[headlinesWithFlow]: Headline(title=Latest Headline at 2020-03-31T09:58:47.221+09:00[Asia/Tokyo], description=null)
send 3 times
[headlinesWithFlow]: Headline(title=Latest Headline at 2020-03-31T09:58:47.325+09:00[Asia/Tokyo], description=null)
send 4 times
[headlinesWithFlow]: Headline(title=Latest Headline at 2020-03-31T09:58:47.427+09:00[Asia/Tokyo], description=null)
send 5 times
[headlinesWithFlow]: Headline(title=Latest Headline at 2020-03-31T09:58:47.530+09:00[Asia/Tokyo], description=null)
send 6 times
[headlinesWithFlow]: Headline(title=Latest Headline at 2020-03-31T09:58:47.632+09:00[Asia/Tokyo], description=null)
send 7 times
[headlinesWithFlow]: Headline(title=Latest Headline at 2020-03-31T09:58:47.734+09:00[Asia/Tokyo], description=null)
send 8 times
[headlinesWithFlow]: Headline(title=Latest Headline at 2020-03-31T09:58:47.834+09:00[Asia/Tokyo], description=null)
send 9 times
[headlinesWithFlow]: Headline(title=Latest Headline at 2020-03-31T09:58:47.936+09:00[Asia/Tokyo], description=null)
send 10 times
[headlinesWithFlow]: Headline(title=Latest Headline at 2020-03-31T09:58:48.037+09:00[Asia/Tokyo], description=null)
Finish streamHeadlinesWithFlow
[headlinesWithFlow]: Finish
Hello,
World
[background]: OK
 */
