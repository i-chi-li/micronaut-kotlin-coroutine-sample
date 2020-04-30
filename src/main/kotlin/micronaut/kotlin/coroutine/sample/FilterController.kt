package micronaut.kotlin.coroutine.sample

import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Body
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
import io.micronaut.security.annotation.Secured
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.reactive.awaitSingle
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import java.util.*
import javax.inject.Singleton

@Controller("/filter")
class FilterController {
    private val log = LoggerFactory.getLogger(this.javaClass)

    @Get
    fun index(): HttpResponse<*> {
        log.info("index")
        return HttpResponse.ok("""{"result": "OK"}""")
    }

    /**
     * プロキシ
     * curl -iv http://localhost:8080/filter/proxy
     * curl -iv http://localhost:8080/filter/proxy
     */
    @Get("/proxy")
    @Produces(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    fun getProxy(request: HttpRequest<*>): HttpResponse<*> {
        log.info("proxy: $request")
        logRequest(request)
        return HttpResponse.ok("proxy")
    }

    /**
     * プロキシ
     * curl -iv http://localhost:8080/filter/proxy -d ""
     * curl -iv http://localhost:8080/filter/proxy
     */
    @Post("/proxy")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    fun postProxy(request: HttpRequest<*>, @Body body: Any?): HttpResponse<*> {
        log.info("proxy: $request")
        logRequest(request)
        return HttpResponse.ok("proxy")
    }

    /**
     * プロキシ先
     *
     * curl -iv http://localhost:8080/filter/proxy/other
     */
    @Get("/proxy/other")
    @Produces(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON)
    fun getOther(request: HttpRequest<*>): HttpResponse<*> {
        log.info("getOther: $request")
        logRequest(request)
        return HttpResponse.ok("getOther 日本語")
            .header("X-FOO", "BAR")
            .cookie(Cookie.of("FOO1", "BAR1"))
            .contentType(MediaType.APPLICATION_XML_TYPE)
            .characterEncoding(Charsets.UTF_16)
            .locale(Locale.JAPANESE)
            .status(HttpStatus.ACCEPTED, "FOO BAR ACCEPTED")
    }

    /**
     * プロキシ先
     *
     * curl -iv http://localhost:8080/filter/proxy/other
     */
    @Post("/proxy/other")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON)
    fun postOther(request: HttpRequest<*>): HttpResponse<*> {
        log.info("postOther: $request")
        logRequest(request)
        return HttpResponse.ok("postOther 日本語")
            .header("X-FOO", "BAR")
            .cookie(Cookie.of("FOO1", "BAR1"))
            .contentType(MediaType.APPLICATION_XML_TYPE)
            .characterEncoding(Charsets.UTF_16)
            .locale(Locale.JAPANESE)
            .status(HttpStatus.ACCEPTED, "FOO BAR ACCEPTED")
    }

    private fun logRequest(request: HttpRequest<*>) {
        log.info("uri: ${request.uri}")
        log.info("method: ${request.method}")
        log.info("path: ${request.path}")
        log.info("remoteAddress: ${request.remoteAddress}")
        log.info("serverAddress: ${request.serverAddress}")
        log.info("parameters: ${request.parameters.asMap()}")
        log.info("headers: ${request.headers.asMap()}")
        log.info("cookies: ${request.cookies.asMap()}")
        log.info("attributes: ${request.attributes.asMap()}")
        log.info("body: ${request.body.orElse(null)}")
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
    @Client("\${filter.proxy.url:`http://localhost:8090`}")
    val httpClient: RxHttpClient
) : HttpServerFilter {
    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun getOrder(): Int {
        return Ordered.HIGHEST_PRECEDENCE + 10_000
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun doFilter(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
        // プロキシ要否を判定
        val isProxy = if (request.body.isPresent) {
            val body: Map<*, *> = request.getBody(Map::class.java).get()
            body["proxy"] == "true"
        } else {
            false
        }
        request.getUserPrincipal()

        return if (request.path == "/filter/proxy" && isProxy) {
            // プロキシ用のリクエストを生成
            val proxyRequest: MutableHttpRequest<ByteArray?> = getProxyHttpRequest(request)

            // 非ブロッキング処理を生成
            flow {
                // プロキシ先からレスポンスを取得
                val response: HttpResponse<ByteArray?> = getProxyHttpResponse(proxyRequest)

                // プロキシ先からのレスポンスを、本来のレスポンス用に変換
                // プロキシからのレスポンスは、変更不可の型（HttpResponse）となる、
                // 本来のレスポンスは、変更可能な型（MutableHttpResponse）なので、変換する必要がある。
                val mutableResponse: MutableHttpResponse<ByteArray?> =
                    HttpResponse.status<ByteArray?>(response.status, response.reason()).apply {
                        headers(response.headers.asMap(CharSequence::class.java, CharSequence::class.java))
                        contentType(response.contentType.orElse(MediaType.ALL_TYPE))
                        characterEncoding(response.characterEncoding)
                        body(response.body())
                    }
                emit(mutableResponse)
            }
                .flowOn(Dispatchers.IO)
                .asPublisher()
        } else {
            chain.proceed(request)
        }
    }

    /**
     * プロキシ用リクエストを生成
     *
     * @param request 元の HTTP リクエスト
     * @return プロキシ用 HTTP リクエストを返す。
     */
    private fun getProxyHttpRequest(request: HttpRequest<*>): MutableHttpRequest<ByteArray?> {
        // body は、Controller に定義している、このリクエストを処理するメソッドの引数定義に、
        // @Body アノテーションが定義されている場合のみ、格納される。
        // HttpRequest 引数のみでは、body が格納されないため注意。
        // つまり、フィルターで body を参照する場合、必ず、Controller に対応する処理メソッドを作成して、
        // 引数に @Body を定義する必要がある。
        val body = request.getBody(ByteArray::class.java).orElse(null)
        return HttpRequest.create<ByteArray?>(
            request.method,
            "${request.path}/other"
        )
            .headers(request.headers.asMap(CharSequence::class.java, CharSequence::class.java))
            .contentType(request.contentType.orElse(MediaType.ALL_TYPE))
            .cookies(request.cookies.all)
            .body(body)
    }

    /**
     * プロキシ先から Http レスポンスを取得
     *
     * @param proxyRequest
     * @return
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getProxyHttpResponse(proxyRequest: MutableHttpRequest<ByteArray?>): HttpResponse<ByteArray?> {
        val requestFlow: Publisher<HttpResponse<ByteArray?>> = httpClient.exchange(
            proxyRequest,
            ByteArray::class.java
        )

        return requestFlow
            .asFlow()
            .onEach { response ->
                log.info("response: $response")
            }
            .catch { collector ->
                // 例外発生時
                log.info("Proxy Error: $collector")
                val response: HttpResponse<ByteArray?> = HttpResponse.serverError(collector.message?.toByteArray())
                emit(response)
            }
            .flowOn(Dispatchers.IO)
            .single()
    }
}

@Singleton
class FilterSecurityTokenReader: TokenReader {
    private val log = LoggerFactory.getLogger(this.javaClass)
//    override fun getOrder(): Int {
//        return Ordered.HIGHEST_PRECEDENCE
//    }

    override fun findToken(request: HttpRequest<*>): Optional<String> {
        log.info("Start findToken")
        return if(request.body.isPresent) {
            val bodyOptional = request.getBody(Map::class.java)
            if(bodyOptional.isPresent) {
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
class FilterTokenValidator: TokenValidator {
    private val log = LoggerFactory.getLogger(this.javaClass)
//    override fun getOrder(): Int {
//        return Ordered.HIGHEST_PRECEDENCE
//    }

    override fun validateToken(token: String?): Publisher<Authentication> {
        log.info("validateToken")
        return if(token != null && token == "org1") {
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

