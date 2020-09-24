package micronaut.kotlin.coroutine.sample

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.reactivex.Single
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("HttpClientController")

/**
 * HTTP Client 利用サンプル
 */
@Controller("/httpClient")
open class HttpClientController(
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
 * HTTP Client 定義
 *
 * インターフェース定義のみだが、
 * コンパイル時に Micronaut が実装を生成する。
 *
 * 接続先 URL は、以下の方法で上書きできる
 * - Java オプションに指定する。例： -Dsample.http.client.url=https://foo.com
 * - 環境変数に指定する。例： SAMPLE_HTTP_CLIENT_URL=https://foo.com
 */
@Client("\${sample.http.client.url:`https://httpbin.org`}")
interface HttpClientSample {
    /**
     * GET メソッド
     * パスでクエリパラメータを指定する方法
     */
    @Get("/get{?foo,bar}")
    fun get(foo: String, bar: Int): ResponseData

    /**
     * GET メソッド
     * アノテーションで、クエリストリングを指定する方法
     */
    @Get("/get")
    fun get2(@QueryValue foo: String, @QueryValue("custom") bar: Int): ResponseData

    /**
     * POST メソッド
     * BODY データを指定する方法
     */
    @Post("/post")
    fun post(postData: PostData): ResponseData

    /**
     * POST メソッド
     * BODY データおよび、ヘッダを指定する方法
     */
    @Post("/post")
    fun post2(postData: PostData, @Header("X-Foo-Bar") foo: String?): ResponseData

    /**
     * POST メソッド
     * 非同期呼び出しする方法
     * 戻り値型を非同期型にする
     */
    @Post("/post")
    fun post3(postData: PostData): Single<ResponseData>
}

/**
 * ポスト用データ
 */
data class PostData(
    val foo1: String,
    val foo2: Int,
    val bar1: List<String>
)

/**
 * レスポンス
 */
data class ResponseData(
    val args: Any,
    val headers: Any,
    val origin: String? = null,
    val url: String,
    val data: String? = null,
    val files: Any? = null,
    val form: Any? = null
)
