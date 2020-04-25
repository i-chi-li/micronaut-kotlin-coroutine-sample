<!-- toc -->
- HTTP セッション
  - セッションの有効化
    - Redis セッション
  - セッションの特定方法を設定
  - セッションの利用方法
  - クライアントでのセッションの利用
  - @SessionValue の利用
  - セッションイベント

# HTTP セッション
デフォルトでは、Micronaut は、ステートレス HTTP サーバである。
しかし、アプリケーションの要件によっては、HTTP セッションの概念が必要となる。

Micronaut には、Spring Session に触発されたセッションモジュールが付属している。
現時点では、二つの実装があり、以下の特徴がある。

- インメモリ・セッション  
  複数のインスタンスを実行する場合は、
  スティッキー・セッション・プロキシと組み合わせる必要がある。
- Redis セッション  
  セッションは、Redis に格納する。
  Redis へのセッションの読み取り、書き込みは、非ブロッキング I/O を使用する。

## セッションの有効化
インメモリ・セッションの有効化は、以下の依存ライブラリを追加するだけで可能となる。

```
implementation "io.micronaut:micronaut-session"
```

### Redis セッション
セッションインスタンスを Redis に保存する場合は、
Micronaut Redis モジュールを詳細に設定できる。
Redis セッションをすぐに利用するには、
依存ライブラリに micronaut-redis-lettuce を追加する必要がある。

```
implementation "io.micronaut:micronaut-session"
runtimeOnly "io.micronaut.configuration:micronaut-redis-lettuce"
```

Redis セッションを有効にするには、application.yml に設定する。

```
redis:
    uri: redis://localhost:6379
micronaut:
    session:
        http:
            redis:
                enabled: true
```

## セッションの特定方法を設定
[Session](https://docs.micronaut.io/latest/api/io/micronaut/session/Session.html)
の特定方法は、
[HttpSessionConfiguration](https://docs.micronaut.io/latest/api/io/micronaut/session/http/HttpSessionConfiguration.html)
で設定できる。
デフォルトでは、セッションの特定処理は、
[HttpSessionFilter](https://docs.micronaut.io/latest/api/io/micronaut/session/http/HttpSessionFilter.html)
で行い、
セッション識別子は、HTTP ヘッダー（Authorization-Info 、X-Auth-Token）
または、Cookie （SESSION）のいずれかを参照する。

ヘッダーまたは、Cookie での特定方法を無効にする場合は、application.yml に設定を行う。
また、ヘッダー名や、Cookie 名も変更できる。

```
micronaut:
    session:
        http:
            cookie: false
            header: true
```

## セッションの利用方法
Session オブジェクトを取得するには、
Controller のリクエスト処理関数へ、Session 型の引数を宣言する。
以下のコードの場合、Session は、必須の引数なため、
リクエスト処理時に、事前に Session を生成して、
[SessionStore](https://docs.micronaut.io/latest/api/io/micronaut/session/SessionStore.html)
に保存される。

```kotlin
import io.micronaut.session.Session

@Controller("/shopping")
class ShoppingController {

    @Post("/cart/{name}")
    internal fun addItem(session: Session, name: String): Cart {
        // 属性名と値の型を指定して、セッションから値を取得する
        val cart = session.get(ATTR_CART, Cart::class.java).orElseGet({
            val newCart = Cart()
            // 属性名を指定して、セッションに値を格納する
            session.put(ATTR_CART, newCart)
            newCart
        })
        cart.items.add(name)
    }
}
```

Session が必須でない場合、
Java であれば、@Nullable 、Kotlin であれば、Null 許可型として宣言することで、
Session が特定できない場合、自動的に Session の生成および、引数へのインジェクトをしない。
また、Session を特定でき、SessionStore に存在しない場合、
自動的に、Session を生成して、SessionStore に格納し、引数へインジェクトする。

```kotlin
    @Post("/cart/clear")
    internal fun clearCart(session: Session?) {
        session?.remove(ATTR_CART)
    }
```

## クライアントでのセッションの利用
クライアントが、Web ブラウザの場合は、Cookie を有効にすれば、Session が機能する。
しかしながら、プログラムによる HTTP クライアントの場合は、
HTTP 呼び出し時に、セッション識別子を確実に送信する必要がある。

前述の Session が必須のリクエスト処理関数を呼び出した場合、
HTTP クライアントは、セッション識別子を格納したヘッダーまたは、
Cookie を必ず受け取れる。

```kotlin
    var response = client.exchange(HttpRequest.GET<Cart>("/shopping/cart"), Cart::class.java)
        .blockingFirst()
    var cart = response.body()
        // セッション識別子を取得する
        assertNotNull(response.header(HttpHeaders.AUTHORIZATION_INFO))
        assertNotNull(cart)
        cart.items.isEmpty()
```

HTTP クライアントは、リクエスト送信時に、
レスポンスで受け取ったセッション識別子を渡すことで、既存のセッションを継続できる。

```kotlin
    val sessionId = response.header(HttpHeaders.AUTHORIZATION_INFO)
        response = client.exchange(
            HttpRequest.POST("/shopping/cart/Apple", "")
                // セッション識別子を設定する
                .header(HttpHeaders.AUTHORIZATION_INFO, sessionId), Cart::class.java)
                .blockingFirst()
            cart = response.body()
```

## @SessionValue の利用
@SessionValue は、Session 内の任意の属性を、直接引数に定義できる。
@SessionValue に属性名を未指定の場合、Session 内の属性は、関数の引数名で検索する。

```kotlin
    @Get("/cart")
    // セッション識別子名を指定
    @SessionValue(ATTR_CART)
    // セッション内の cart 属性をインジェクション
    internal fun viewCart(@SessionValue cart: Cart?): Cart {
        var cart = cart
        if (cart == null) {
            cart = Cart()
        }
        return cart
    }
```

## セッションイベント
セッションに関連するイベントは、ApplicationEventListener Bean を登録することで取得できる。
セッションに関するイベントは、io.micronaut.session.event パッケージに含まれる以下となる。

| 種別 | 呼び出し契機 |
| ---- | ---- |
| SessionCreatedEvent | セッション生成時 |
| SessionDeletedEvent | セッション削除時 |
| SessionExpiredEvent | セッション期限切れ時 |
| SessionDestroyedEvent | SessionDeletedEvent および、SessionExpiredEvent 発生時 |

```kotlin
@Singleton
class SessionEventListener: ApplicationEventListener<SessionCreatedEvent> {
    override fun onApplicationEvent(event: SessionCreatedEvent) {
        val session = event.source
        val data = session.get("data1", String::class.java)
    }
}
```
