# Retry Advice

[サンプルコード](../../src/test/kotlin/micronaut/kotlin/coroutine/sample/micronaut/RetryTest.kt)

Retry Advice には、以下のアノテーションがある。

- @Retryable
- @CircuitBreaker

以下のような、非同期処理結果を返すメソッドにも適用可能。

| ライブラリ名 | 型 |
| ---- | ---- |
| RxJava | Flowable |
| Java 標準 | CompletableFuture |
| Coroutine | Flow |

## 制限事項
以下の制限事項がある。

- DI をした Bean でのみ利用可能（インスタンスを直接生成しても利用できない。）

## @Retryable
@Retryable は、リトライ処理を定義できる。
アノテーションの属性には、以下を設定できる。

| 属性名 | デフォルト値 | 説明 |
| ---- | ---- | ---- |
| attempts | 3 | 最大リトライ回数。 |
| delay | 1s | リトライ毎の遅延時間の算出に利用する。 |
| multiplier | 1.0 | リトライ毎の遅延時間の算出に利用する。 |
| maxDelay | - | 遅延時間の累積がこの時間を超えた場合、最大リトライ回数未満でもリトライしない。 |
| includes | - | 指定した例外クラス以外の場合、リトライしない。exclude より先に判定される。 |
| excludes | - | 指定した例外クラスだった場合、リトライしない。 |

リトライ毎の遅延時間の算出式は以下となる。

リトライ毎の遅延時間 ＝ 現在のリトライ回数 × multiplier × delay

multiplier が、 0.5 の場合  
0.5s -> 1s -> 1.5s -> 2s のように増える。

multiplier が、 1.0 の場合  
1s -> 2s -> 3s -> 4s のように増える。

multiplier が、4.0 の場合  
4s -> 8s -> 12s -> 16s のように増える。

遅延時間の増分は、等間隔でないので注意。

## @CircuitBreaker
@CircuitBreaker は、過度なリトライを抑止する機能となる。
@CircuitBreaker は、@Retryable の拡張機能となる。
reset 属性に、リトライオーバー後にメソッドの再利用が可能になる期間を指定する。
再利用可能になる前にメソッドを呼び出すと、前回にスローした例外を、
リトライ無しで、即座に再スローする。
この機能は、I/O や リクエスト処理などが、溢れた場合、
リトライが大量に発生して、システムに問題が発生することを軽減するために利用する。

## Bean Creation Retry
@Retryable は、@Factory クラスの Bean 取得メソッドにも付与できる。
その場合、外部サービスの起動遅延によって、アクセスできないような状態で、
リトライを行うことができる。

```kotlin
@Factory
class Neo4jDriverFactory {
    @Retryable(includes = [ServiceUnavailableException::class])
    @Bean(preDestroy = "close")
    fun buildDriver(): Driver {
        // ...
    }
}
```

## Retry Events
RetryEventListener インターフェースを実装した Bean クラスを登録することにより、
リトライ発生時のイベント処理を一括して行える。

```kotlin
@Singleton
class CustomRetryEventListener : RetryEventListener {
    val log = LoggerFactory.getLogger(this.javaClass)
    override fun onApplicationEvent(event: RetryEvent) {
        log.info("event.source: ${event.source}" +
            ", overallDelay: ${event.retryState.overallDelay}" +
            ", currentAttempt: ${event.retryState.currentAttempt()}",
            ", ${event.throwable.javaClass.simpleName}")
    }
}
```

@CircuitBreaker の場合は、追加で、

```kotlin
/**
 * @CircuitBreaker でリトライオーバーになった場合のイベントを処理する
 */
@Singleton
class CircuitOpenEventListener : ApplicationEventListener<CircuitOpenEvent> {
    private val log = LoggerFactory.getLogger(this.javaClass)
    override fun onApplicationEvent(event: CircuitOpenEvent) {
        log.info("event.source: ${event.source}" +
            ", currentAttempt: ${event.retryState.currentAttempt()}" +
            ", overallDelay: ${event.retryState.overallDelay}")
    }
}

// @CircuitBreaker でのリトライオーバー状態から回復した場合に発生するイベントを処理する。
// ・・・と思ったが、発生しなかった。現時点では、不明。
@Singleton
class CircuitClosedEventListener : ApplicationEventListener<CircuitClosedEvent> {
    val log = LoggerFactory.getLogger(this.javaClass)
    override fun onApplicationEvent(event: CircuitClosedEvent) {
        log.info("event.source: ${event.source}")
    }
}
```
