package micronaut.kotlin.coroutine.sample

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Retryable
import io.micronaut.retry.event.RetryEvent
import io.micronaut.retry.event.RetryEventListener
import io.reactivex.Single
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.inject.Singleton

private val log: Logger = LoggerFactory.getLogger("HttpClientController")

/**
 * リトライアノテーション利用サンプル
 * TODO HTTP Client, Factory, Method etc...
 */
@Controller("/retry")
open class RetryAdviceController(
    // HTTP Client をインジェクションする
    private val httpClientSample: HttpClientSample
) {
    /**
     * GET メソッド呼び出しサンプル
     */
    @Get("/get")
    fun get(): ResponseData {
        val result = this.httpClientSample.get("values", 10);
        log.info("result: $result")
        return result
    }

    /**
     * GET メソッド呼び出しサンプル
     */
    @Get("/get2")
    fun get2(): ResponseData {
        val result = this.httpClientSample.get2("values", 10);
        log.info("result: $result")
        return result
    }

    /**
     * POST メソッド呼び出しサンプル
     */
    @Get("/post")
    fun post(): ResponseData {
        val postData = PostData("hoge", 123, listOf("aaa", "bbb"))
        val result = this.httpClientSample.post(postData)
        log.info("result: $result")
        return result
    }

    /**
     * POST メソッド呼び出しサンプル
     */
    @Get("/post2")
    fun post2(): ResponseData {
        val postData = PostData("hoge", 123, listOf("aaa", "bbb"))
        val result = this.httpClientSample.post2(postData, "bar")
        log.info("result: $result")
        return result
    }

    /**
     * POST メソッド呼び出しサンプル
     * 非同期呼び出し
     */
    @Get("/post3")
    fun post3(): Single<ResponseData> {
        val postData = PostData("hoge", 123, listOf("aaa", "bbb"))
        return httpClientSample.post3(postData).doOnSuccess { res ->
            log.info("res: $res")
        }
    }
}

/**
 * リトライ付き HTTP Client 定義
 *
 * インターフェース定義のみだが、
 * コンパイル時に Micronaut が実装を生成する。
 *
 * 接続先 URL は、以下の方法で上書きできる
 * - Java オプションに指定する。例： -Dsample.http.client.url=https://foo.com
 * - 環境変数に指定する。例： SAMPLE_HTTP_CLIENT_URL=https://foo.com
 */
@Client("\${sample.http.client.url:`https://httpbin.org`}")
interface RetryHttpClientSample {
    /**
     * GET メソッド
     */
    @Retryable
    @Get("/get{?foo,bar}")
    fun get(foo: String, bar: Int): ResponseData
}

@Singleton
class SampleRetryEventListener : RetryEventListener {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun onApplicationEvent(event: RetryEvent) {
        log.info("event: $event")
    }
}
