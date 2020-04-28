<!-- toc -->
- [Method Adapter Advice](https://docs.micronaut.io/latest/guide/index.html#adapterAdvice)

# [Method Adapter Advice](https://docs.micronaut.io/latest/guide/index.html#adapterAdvice)
Method Adapter Advice とは、付与することにより、
任意の機能を持ったクラスを自動生成するための機能となる。

Micronaut 標準で利用できる、Method Adapter Advice の一つとして、
@EventListener がある。
@EventListener を付与すると、ApplicationEventListener を実装したクラスと、
@EventListener を付与したメソッドを呼び出すコードを自動生成する。

@EventListener の利用例

```kotlin
import io.micronaut.context.event.StartupEvent
import io.micronaut.runtime.event.annotation.EventListener

@EventListener
fun onStartup(event: StartupEvent) {
    // ApplicationContext 起動時に実行するコードをここに記述する
}
```

@EventListener の定義（Java のコード）
@Adapter に 単一の抽象メソッド（SAM: single abstract method）を指定している。

@Indexed は、付与すると、Bean のインデックスを生成し、
実行中の Bean 特定が高速化する。
特定のインターフェースに複数の実装が存在するような場合、有用となる。

```java
import io.micronaut.aop.Adapter;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.annotation.Indexed;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Adapter(ApplicationEventListener.class) // <1>
@Indexed(ApplicationEventListener.class)
public @interface EventListener {
}
```
