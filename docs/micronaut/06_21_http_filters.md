# HTTP Filters
Micronaut HTTPサーバでは、従来の Java アプリケーションでの
サーブレットフィルターと同様の機能をサポートする。
リクエストおよび、レスポンス処理でレスポンシブにフィルター処理を行う。

フィルターは、以下の用途を補助する機能を提供する。

- 受信した HTTP リクエストの変更
- 送信する HTTP レスポンスの変更
- セキュリティや、トレースなどの横断的関心事の実装

サーバの場合は、HttpServerFilter インターフェースの doFilter メソッドを実装する。
doFilter メソッドは、HttpRequest インターフェースおよび、
ServerFilterChain インターフェースの実装インスタンスを受け取る。

> 【重要】  
> HttpRequest 中の body は、リクエストに合致する Controller のリクエスト処理で、
> 引数に @Body アノテーションを定義している場合だけ格納される。
> 必ず、@Body アノテーションが必要で、
> HttpRequest のみを引数で定義しても、body は空となる。  
> したがって、Controller に定義していないリクエストを、
> フィルターで、他のサイトなどへのプロキシ処理をする場合は、
> body が取得できないことになる。  
> 以下でこの仕様について Issues となっている。  
> どうやら、Ver 2 で、解消されるようだ。  
> [Provide a way to read the request body in a filter when the route does not read it #1113](https://github.com/micronaut-projects/micronaut-core/issues/1113)

ServerFilterChain インターフェースには、フィルタの連鎖が格納されている。
フィルタの連鎖の最後尾は、リクエストに一致した振り分け先の処理となる。
ServerFilterChain.proceed メソッドを呼び出すことで、リクエスト処理を継続できる。

proceed() メソッドは、クライアントへのレスポンスを発行する、
Reactive Streams の Publisher を返す。

フィルターは、イベントループで実行されるため、
ブロッキング操作は、別のスレッドプールに処理を委譲する必要がある。

## フィルター処理の記述方法
いくつかの外部システムを使用して、
Micronautの「Hello World」のサンプルへの、
各リクエストを追跡したいという、架空のユースケースを考える。
外部システムは、データベース、分散トレースサービスであり、
I/O 操作が必要な場合がある。

避けるべきは、フィルター内の基礎となる Netty イベントループをブロックすること。
代わりに、I/O が完了したらフィルターに実行を続行させます。

例として、RxJava を使用して I/O 操作を行う、次のような TraceService を考える。

```kotlin
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
```

次に、この実装をフィルター処理にインジェクトして利用する。

```kotlin
@Filter("/filter/**")
class TraceFilter(
    private val traceService: TraceService
) : HttpServerFilter {
    // フィルタの優先度（小さい方（マイナスも含む）が優先度が高い）
    override fun getOrder(): Int {
        return Ordered.HIGHEST_PRECEDENCE + 1_000
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
```

実装した処理では、リクエスト処理を継続する前に、
非ブロッキング処理にラップすることや、
HTTP レスポンスを変更するなどの重要な概念を示している。

