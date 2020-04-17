# Coroutines Support
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

### @RequestScope での制約
@ThreadLocal および、@RequestScope は、ThreadLocal を利用してインスタンスの管理を行っている。
対して、Coroutine は、スレッドに束縛されないため、Suspend 前後で、
スレッドが異なる場合がある。
よって、ThreadLocal を利用している、@ThreadLocal および、@RequestScope は、
利用することができない。
この制約は、Coroutine に限らず、別スレッドで動作させた場合も同様となる。

この問題が発生した場合のログは以下のようになる。

>io.micronaut.context.exceptions.NoSuchBeanException: No bean of type [micronaut.kotlin.coroutine.sample.FooBean] exists for the given qualifier: @RequestScope. Make sure the bean is not disabled by bean requirements (enable trace logging for 'io.micronaut.context.condition' to check) and if the bean is enabled then ensure the class is declared a bean and annotation processing is enabled (for Java and Kotlin the 'micronaut-inject-java' dependency should be configured as an annotation processor).

