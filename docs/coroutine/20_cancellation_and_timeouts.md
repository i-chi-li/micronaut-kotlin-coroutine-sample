<!-- toc -->
- [キャンセルとタイムアウト](#キャンセルとタイムアウト)
  - [キャンセル](#キャンセル)
    - [リソースクローズ処理](#リソースクローズ処理)
    - [キャンセル無効処理](#キャンセル無効処理)
  - [タイムアウト](#タイムアウト)

# キャンセルとタイムアウト

## キャンセル
キャンセル実装例。
```kotlin
fun main() = runBlocking {
    val job = launch {
        repeat(1000) { i ->
            println("job: I'm sleeping $i ...")
            delay(500L)
        }
    }
    delay(1300L)
    println("main: I'm tired of waiting!")
    // 以下を同時に行う job.cancelAndJoin() もある
    // 処理をキャンセル
    job.cancel()
    // 即座に終了しないため、待つ必要がある
    job.join()
    println("main: Now I can quit.")
}
```
ただし、Coroutine のキャンセルは、既存の Suspend 関数を呼び出すか、処理中にキャンセルチェックをする必要がある。
次のようなコードの場合は、キャンセルできない。
```kotlin
fun main() = runBlocking {
    val startTime = System.currentTimeMillis()
    // 明示的に Dispatchers.Default を指定しないと、main スレッドで実行される。（要調査）
    val job = launch(Dispatchers.Default) {
        var nextPrintTime = startTime
        var i = 0
        while(i < 5) {
            if(System.currentTimeMillis() >= nextPrintTime) {
                println("job: I'm sleeping ${i++} ...")
                nextPrintTime += 500L
            }
        }
    }
    delay(1300L)
    println("main: I'm tired of waiting!")
    job.cancelAndJoin()
    println("main: Now I can quit.")
}
```
キャンセルをできるようにするためには、2 つ方法があり、1 つは、isActive 変数で判定する、
2 つ目は、yield() 関数のような既存の Suspend 関数を呼び出す。
isActive 変数は、Coroutine スコープ内であれば利用可能。
キャンセルできるように変更した例
```kotlin
fun main() = runBlocking {
    val startTime = System.currentTimeMillis()
    val job = launch(Dispatchers.Default) {
        var nextPrintTime = startTime
        var i = 0
        // 方法 1：isActive で判定する
        while(isActive && i < 5) {
            // 方法 2：yield() を呼び出す
            // 既存の Suspend 関数であれば、どれでもよい。
            // yield()
            if(System.currentTimeMillis() >= nextPrintTime) {
                println("job: I'm sleeping ${i++} ...")
                nextPrintTime += 500L
            }
        }
    }
    delay(1300L)
    println("main: I'm tired of waiting!")
    job.cancelAndJoin()
    println("main: Now I can quit.")
}
```

### リソースクローズ処理
Coroutine がキャンセルされると、Suspend 関数呼び出し時に CancellationException がスローされる。
したがって、try-cache-finally または、AutoCloseable.use などでキャンセル時のリソースクローズ処理を記述できる。
```kotlin
fun main() = runBlocking {
    val startTime = System.currentTimeMillis()
    val job = launch(Dispatchers.Default) {
        var nextPrintTime = startTime
        var i = 0
        try {
            // AutoCloseable.use { } の場合もクローズされる
            FooResource().use {
                while (i < 5) {
                    yield()
                    if (System.currentTimeMillis() >= nextPrintTime) {
                        println("job: I'm sleeping ${i++} ...")
                        nextPrintTime += 500L
                    }
                }
            }
        } catch (ex: CancellationException) {
            println("job: I'm running catch: ${ex.message}")
        } finally {
            println("job: I'm running finally")
        }
    }
    delay(1300L)
    println("main: I'm tired of waiting!")
    job.cancelAndJoin()
    println("main: Now I can quit.")
}

class FooResource : AutoCloseable {
    init {
        println("FooResource: created")
    }
    override fun close() {
        println("FooResource: closed")
    }
}
```

### キャンセル無効処理
Coroutine キャンセル後の処理などで、キャンセルチェックをしている処理や、
Suspend 関数を呼び出す必要がある場合などを呼び出す必要がある場合、
そのまま呼び出すと、CancellationException がスローされてしまう。
そのような場合は、キャンセルが無効となるブロックで処理を行う。
```kotlin
fun main() = runBlocking {
    val job = launch {
        try {
            repeat(1000) { i ->
                println("job: I'm sleeping $i ...")
                delay(500L)
            }
        } finally {
            // キャンセル無効ブロック
            withContext(NonCancellable) {
                println("job: I'm running finally")
                // キャンセル後でも Suspend 関数を呼び出せる
                delay(1000L)
                // キャンセルチェック用フラグも true となっている
                println("isActive: $isActive")
                println("job: And I've just delayed for 1 sec because I'm non-cancellable")
            }
        }
    }
    delay(1300L)
    println("main: I'm tired of waiting!")
    job.cancelAndJoin()
    println("main: Now I can quit.")
}
```

## タイムアウト
withTimeout ブロックを利用すると、Coroutine の実行タイムアウトを設定可能。
withTimeout ブロックの場合、タイムアウト発生時に TimeoutCancellationException がスローされる。
TimeoutCancellationException は、CoroutineScope（runBlocking を除く）中では、
通常のキャンセルと同じく、正常終了と見なされ、外側にスローされることはない。 
TimeoutCancellationException は、CancellationException のサブクラスとなる。
```kotlin
fun main() = runBlocking {
    withTimeout(1300L) {
        repeat(1000) { i ->
            println("I'm sleeping $i ...")
            delay(500L)
        }
    }
}
```

もし、TimeoutCancellationException のスローの代わりに、
null を返す方が適切な場合は、以下のようにする。
```kotlin
fun main() = runBlocking {
    // タイムアウト発生時には、null を返す
    val result = withTimeoutOrNull(1300L) {
        repeat(1000) { i ->
            println("I'm sleeping $i ...")
            delay(500L)
        }
        // 正常完了時の戻り値
        "Done"
    }
    println("result: $result")
}
```
