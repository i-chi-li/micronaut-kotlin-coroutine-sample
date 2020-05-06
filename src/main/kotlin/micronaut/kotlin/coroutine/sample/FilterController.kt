package micronaut.kotlin.coroutine.sample

import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.authentication.DefaultAuthentication
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.rules.SecurityRuleResult
import io.micronaut.security.token.reader.TokenReader
import io.micronaut.security.token.validator.TokenValidator
import io.micronaut.web.router.RouteMatch
import io.reactivex.Flowable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.reactive.awaitSingle
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import javax.inject.Singleton

@Controller("/filter")
class FilterController {
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * プロキシ先
     *
     * curl -iv http://localhost:8080/filter/proxy
     * curl -iv http://localhost:8080/filter/proxy -H "X-Proxy: true"
     */
    @Get("/proxy")
    @Produces(CustomMediaType.APPLICATION_XML_UTF8, MediaType.APPLICATION_JSON)
    fun getOther(request: HttpRequest<*>): HttpResponse<*> {
        log.info("getOther: $request")
        logRequest(log, request)
        return HttpResponse.ok(mapOf("getOther" to "日本語"))
            .header("X-FOO", "BAR")
            .cookie(Cookie.of("FOO1", "BAR1"))
            .contentType(MediaType.APPLICATION_XML_TYPE)
            .characterEncoding(Charsets.UTF_8)
            .locale(Locale.JAPANESE)
            .status(HttpStatus.ACCEPTED, "FOO BAR ACCEPTED")
    }

    /**
     * プロキシ先
     *
     * curl -iv http://localhost:8080/filter/proxy -d "foo=bar"
     * curl -iv http://localhost:8080/filter/proxy -d "foo=bar" -H "X-Proxy: true"
     */
    @Post("/proxy")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON)
    @Produces(CustomMediaType.APPLICATION_XML_UTF8, MediaType.APPLICATION_JSON)
    fun postOther(request: HttpRequest<*>): HttpResponse<*> {
        log.info("postOther: $request")
        logRequest(log, request)
        return HttpResponse.ok(mapOf("postOther" to "日本語"))
            .header("X-FOO", "BAR")
            .cookie(Cookie.of("FOO1", "BAR1"))
            .contentType(MediaType.APPLICATION_XML_TYPE)
            .characterEncoding(Charsets.UTF_8)
            .locale(Locale.JAPANESE)
            .status(HttpStatus.ACCEPTED, "FOO BAR ACCEPTED")
    }
}

/**
 * I/O 処理などを、フィルダーで実行する場合に非ブロッキング処理として実装する方法
 * Coroutine を利用したサンプル
 */
@Singleton
class TraceService {
    private val log = LoggerFactory.getLogger(this.javaClass)

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun trace(request: HttpRequest<*>): Flow<Boolean> = flow {
        log.info("Tracing request: ${request.uri}")
        // ここで何かしらの重い処理を行う想定
        emit(true)
    }
        // I/O スレッドで実行
        .flowOn(Dispatchers.IO)
}

@Filter("/filter/**")
class TraceFilter(
    private val traceService: TraceService
) : HttpServerFilter {
    // フィルタの優先度（小さい方（マイナスも含む）が優先度が高い）
    override fun getOrder(): Int {
        return Ordered.HIGHEST_PRECEDENCE + 9_900
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun doFilter(
        request: HttpRequest<*>,
        chain: ServerFilterChain
    ): Publisher<MutableHttpResponse<*>> {
        return traceService.trace(request)
            .mapLatest {
                // HTTP レスポンスを取得する
                chain.proceed(request).awaitSingle()
            }
            .onEach { response ->
                // HTTP レスポンスにヘッダを追加
                response.headers["X-Trace-Enabled"] = "true"
            }
            // Publisher を返す必要があるので変換
            .asPublisher()
    }
}

/**
 * リクエストを別のサーバへプロキシするフィルタ
 */
@Filter("\${filter.proxy.target.path:`/filter/**`}")
class ProxyFilter(
    @Client("\${filter.proxy.url:`http://localhost:8080`}")
    val httpClient: RxHttpClient
) : HttpServerFilter {
    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun getOrder(): Int {
        return Ordered.HIGHEST_PRECEDENCE + 10_000
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun doFilter(
        request: HttpRequest<*>,
        chain: ServerFilterChain
    ): Publisher<MutableHttpResponse<*>> {
        // プロキシ要否を判定
        val isProxy = request.headers["X-Proxy"] == "true"

        return if (isProxy) {
            // プロキシが必要な場合
            log.info("isProxy: $isProxy")
            log.info("======== request ========")
            logRequest(log, request)
            // プロキシ用のリクエストを生成
            val proxyRequest: MutableHttpRequest<*> = getProxyHttpRequest(request)
            log.info("======== proxyRequest ========")
            logRequest(log, proxyRequest)

            flow {
                // プロキシ先からレスポンスを取得
                val response: MutableHttpResponse<*> = getProxyHttpResponse(proxyRequest)
                emit(response)
            }
                .catch { cause ->
                    // 例外発生時
                    log.error("Proxy Error: ${cause.message}", cause)
                    val response: MutableHttpResponse<Any?> = HttpResponse.serverError(cause.message)
                    emit(response)
                }
                .flowOn(Dispatchers.IO)
                .asPublisher()
        } else {
            // プロキシが不要な場合
            chain.proceed(request)
        }
    }

    /**
     * プロキシ用リクエストを生成
     *
     * @param request 元の HTTP リクエスト
     * @return プロキシ用 HTTP リクエストを返す。
     */
    private fun getProxyHttpRequest(
        request: HttpRequest<*>
    ): MutableHttpRequest<*> {
        // body は、Controller に定義している、このリクエストを処理するメソッドの引数定義に、
        // @Body アノテーションが定義されている場合のみ、格納される。
        // HttpRequest 引数のみでは、body が格納されないため注意。
        // つまり、フィルターで body を参照する場合、必ず、Controller に対応する処理メソッドを作成して、
        // 引数に @Body を定義する必要がある。
        val body = request.getBody(ByteArray::class.java).orElse(null)
        val headers = request.headers.asMap(CharSequence::class.java, CharSequence::class.java)
            // AWS で付与するヘッダを除去
            .filter { (key: CharSequence, _: CharSequence) ->
                key.toString().toLowerCase().let { lowerCaseKey ->
                    // 大文字小文字区別無く判定する必要がある。
                    // ELB がヘッダを小文字に変換してしまうようだ。
                    // プロキシ指示ヘッダを除去
                    lowerCaseKey != "x-proxy"
                        && !lowerCaseKey.startsWith("x-forwarded-")
                        && !lowerCaseKey.startsWith("x-amzn-")
                }
            }

        return HttpRequest.create<ByteArray?>(
            request.method,
            "${request.uri}"
        )
            .headers(headers)
            .body(body)
    }

    /**
     * プロキシ先から Http レスポンスを取得
     *
     * @param proxyRequest
     * @return
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getProxyHttpResponse(
        proxyRequest: MutableHttpRequest<*>
    ): MutableHttpResponse<*> {
        val requestFlow: Publisher<HttpResponse<Any?>> = httpClient.exchange(
            proxyRequest,
            Any::class.java
        )

        return requestFlow
            .asFlow()
            .onEach { response ->
                // 受信時に実行する処理
                logResponse(log, response)
            }
            .map { response ->
                // プロキシ先からのレスポンスを、本来のレスポンス用に変換
                // プロキシからのレスポンスは、変更不可の型（HttpResponse）となる、
                // 本来のレスポンスは、変更可能な型（MutableHttpResponse）なので、変換する必要がある。
                HttpResponse.status<Any?>(response.status, response.reason()).apply {
                    headers(response.headers.asMap(CharSequence::class.java, CharSequence::class.java))
                    // 上記のヘッダ設定で、以下の2つは設定される。
//                    contentType(response.contentType.orElse(MediaType.ALL_TYPE))
//                    characterEncoding(response.characterEncoding)
                    body(response.body())
                }
                    .header("X-Proxy-Response", "true")
            }
            .catch { cause ->
                // 例外発生時
                log.info("Proxy Error: ${cause.message}", cause)
                val response: MutableHttpResponse<Any?> = HttpResponse.serverError(cause.message)
                emit(response)
            }
            .flowOn(Dispatchers.IO)
            .single()
    }
}

private fun logRequest(log: Logger, request: HttpRequest<*>) {
    log.info("-------- logRequest Start --------")
    log.info("uri: ${request.uri}")
    log.info("method: ${request.method}")
    log.info("path: ${request.path}")
    log.info("remoteAddress: ${request.remoteAddress}")
    log.info("serverAddress: ${request.serverAddress}")
    log.info("parameters: ${request.parameters.asMap()}")
    log.info("headers: ${request.headers.asMap()}")
    // NettyClientHttpRequest クラスの getCookies() は、未実装のため呼び出し禁止
    //log.info("cookies: ${request.cookies.asMap()}")
    log.info("attributes: ${request.attributes.asMap()}")
    log.info("body: ${request.body.orElse(null)}")
    log.info("-------- logRequest Finish --------")
}

private fun logResponse(log: Logger, response: HttpResponse<*>) {
    log.info("-------- logResponse Start --------")
    log.info("status: ${response.status}")
    log.info("code: ${response.code()}")
    log.info("reason: ${response.reason()}")
    log.info("headers: ${response.headers.asMap()}")
    log.info("attributes: ${response.attributes.asMap()}")
    log.info("body: ${response.body.orElse(null)}")
    log.info("-------- logResponse Finish --------")
}

@Singleton
class FilterSecurityTokenReader : TokenReader {
    private val log = LoggerFactory.getLogger(this.javaClass)
//    override fun getOrder(): Int {
//        return Ordered.HIGHEST_PRECEDENCE
//    }

    override fun findToken(request: HttpRequest<*>): Optional<String> {
        log.info("Start findToken")
        return if (request.body.isPresent) {
            val bodyOptional = request.getBody(Map::class.java)
            if (bodyOptional.isPresent) {
                val body = bodyOptional.get()
                Optional.ofNullable(body["orgId"]?.toString())
            } else {
                Optional.empty()
            }
        } else {
            Optional.empty()
        }
    }
}

@Singleton
class FilterTokenValidator : TokenValidator {
    private val log = LoggerFactory.getLogger(this.javaClass)
//    override fun getOrder(): Int {
//        return Ordered.HIGHEST_PRECEDENCE
//    }

    override fun validateToken(token: String?): Publisher<Authentication> {
        log.info("validateToken")
        return if (token != null && token == "org1") {
            Flowable.just(
                DefaultAuthentication(
                    "FilterTokenValidator",
                    mutableMapOf<String, Any>(
                        "token" to token
                    )
                )
            )
        } else {
            Flowable.empty()
        }
    }

}

@Singleton
class FilterSecurityRule : SecurityRule {
    private val log = LoggerFactory.getLogger(this.javaClass)
//    override fun getOrder(): Int {
//        return Ordered.HIGHEST_PRECEDENCE
//    }

    override fun check(
        request: HttpRequest<*>,
        routeMatch: RouteMatch<*>?,
        claims: MutableMap<String, Any>?
    ): SecurityRuleResult {
        log.info("check")

        return SecurityRuleResult.UNKNOWN
    }
}

