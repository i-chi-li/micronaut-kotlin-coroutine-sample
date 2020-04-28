<!-- toc -->
- [Coroutines Support](https://docs.micronaut.io/latest/guide/index.html#coroutines)
  - 制限事項
    - @RequestScope での制約
    - 関数の戻り値での制約

# [Coroutines Support](https://docs.micronaut.io/latest/guide/index.html#coroutines)
Micronaut では、Kotlin Coroutine をサポートする。
サポートする機能は、以下となる。

- Controller の関数を Suspend で宣言できる
- Controller の関数の戻り値を、Flow 型にし、ストリーミング処理ができる

```kotlin
@Get("/delayed")
suspend fun delayed(): String {
    delay(1)
    return "Delayed"
}

@Get(value = "/headlinesWithFlow", processes = [MediaType.APPLICATION_JSON_STREAM])
internal fun streamHeadlinesWithFlow(): Flow<Headline> =
    flow {
        repeat(100) {
            with (Headline()) {
                text = "Latest Headline at " + ZonedDateTime.now()
                emit(this)
                delay(1_000)
            }
        }
    }
```

## 制限事項
現時点では、以下の問題がある。

- GraalVM で制約がある（回避策有り）
- @ThreadLocal および、@RequestScope で制約がある
- 関数の戻り値に、制約がある。後述

### @RequestScope での制約
@ThreadLocal および、@RequestScope は、ThreadLocal を利用してインスタンスの管理を行っている。
対して、Coroutine は、スレッドに束縛されないため、Suspend 前後で、
スレッドが異なる場合がある。
よって、ThreadLocal を利用している、@ThreadLocal および、@RequestScope は、
利用することができない。
この制約は、Coroutine に限らず、別スレッドで動作させた場合も同様となる。

この問題が発生した場合のログは以下のようになる。

>io.micronaut.context.exceptions.NoSuchBeanException: No bean of type [micronaut.kotlin.coroutine.sample.FooBean] exists for the given qualifier: @RequestScope. Make sure the bean is not disabled by bean requirements (enable trace logging for 'io.micronaut.context.condition' to check) and if the bean is enabled then ensure the class is declared a bean and annotation processing is enabled (for Java and Kotlin the 'micronaut-inject-java' dependency should be configured as an annotation processor).

### 関数の戻り値での制約
現時点では、以下のような制約がある。

- Deferred（async の戻り値）は利用できない。
- 関数の戻り値に、直接 coroutineScope や、withContext などを指定できない。

したがって、Controller に定義するリクエスト処理関数は、
原則として、runBlocking などを除き、通常の処理ブロック（{}）で宣言することとする。

```suspend fun asyncSyncJob(async: Boolean?): String = coroutineScope { ... }```
などのように、関数の定義に coroutineScope や、withContext などを直接返すような定義をすると、
例外発生などで、Coroutine がキャンセルされた場合に、
Micronaut 側の処理に伝播してしまい、以下のようなエラーとなり、
レスポンスを返さないまま、接続が切れない状態となる。

```
Caused by: java.lang.ClassCastException: io.micronaut.http.bind.binders.CustomContinuation cannot be cast to kotlin.coroutines.jvm.internal.CoroutineStackFrame
```

この事象は、バグではなく、仕様と考える。
Coroutine のアンチパターンとして、
[Kotlin Coroutinesパターン＆アンチパターン](https://qiita.com/ikemura23/items/fb8caeba4c35fcd85644)
 などのサイトにも記載があるが、キャンセルが、アプリケーション全体に、
 意図しない伝播をしないように実装することが必要となる。
coroutineScope を直接返す場合でも、意図しない伝播を防ぐ手段がある。
親を持たない設定にした CoroutineScope や、SupervisorJob を Coroutine コンテキストに設定することで、
キャンセルが親に伝播することを防止できる。
try-cache でのキャンセル伝播の制御も考えるかもしれないが、実際には無理となる。
例外が発生する箇所での try-cache は、意図したとおりに機能するが、
例外が、Coroutine 階層を超えた時点で、
例外が発生した Coroutine は、キャンセル状態となり、
例外を処理しても、Coroutine がキャンセル状態になっているので、
親や、子にキャンセルが伝播してしまう。
