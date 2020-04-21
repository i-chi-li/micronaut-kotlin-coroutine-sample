<!-- toc -->
- [例外処理と監視](#例外処理と監視)
  - [例外処理](#例外処理)
    - [例外の伝播](#例外の伝播)
    - [Coroutine 例外ハンドラ](#coroutine-例外ハンドラ)
    - [キャンセル処理と例外](#キャンセル処理と例外)
    - [例外の集約](#例外の集約)
  - [監視](#監視)
    - [SupervisorJob](#supervisorjob)
    - [supervisorScope](#supervisorscope)
    - [監視 Coroutine での例外](#監視-coroutine-での例外)

# 例外処理と監視

## 例外処理
この章では、例外処理と例外のキャンセルについて説明する。
キャンセルされた Coroutine が Suspend 位置で、CancellationException をスローすること、
そして、Coroutine の仕組みによって無視されることは既に記載した。
しかし、キャンセル中に例外がスローされた場合、または、同じ Coroutine の複数の子が、
例外をスローした場合は、どうなるだろうか？

### 例外の伝播
Coroutine ビルダには、 例外を自動的に伝播するもの（launch や actor）および、
ユーザに公開するもの（async や produce）の 2 つの種類がある。
例外を自動的に伝播するものは、例外を Java と同様に未処理として扱う。
ユーザに公開するものは、最終的に await や、receive の呼び出し時に、
ユーザが例外を処理する必要がある。
（produce や、receive は、Channel の章で説明する）

実際の挙動を、GlobalScope の Coroutine で確認する。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

fun main() = runBlocking<Unit> {
    val job = GlobalScope.launch {
        log.info("Throwing exception from launch")
        throw IndexOutOfBoundsException()
    }
    job.join()
    log.info("Joined failed job")
    val deferred = GlobalScope.async {
        log.info("Throwing exception from async")
        throw ArithmeticException()
    }
    try {
        deferred.await()
        log.info("Unreached")
    } catch (e: ArithmeticException) {
        log.info("Caught ArithmeticException")
    }
}
```
実行結果
```
17:53:05.186 [DefaultDispatcher-worker-1 @coroutine#2] INFO  sample - Throwing exception from launch
Exception in thread "DefaultDispatcher-worker-1 @coroutine#2" java.lang.IndexOutOfBoundsException
	at micronaut.kotlin.coroutine.sample.CoroutineControllerKt$main$1$job$1.invokeSuspend(CoroutineController.kt:84)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:56)
	at kotlinx.coroutines.scheduling.CoroutineScheduler.runSafely(CoroutineScheduler.kt:571)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.executeTask(CoroutineScheduler.kt:738)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.runWorker(CoroutineScheduler.kt:678)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run(CoroutineScheduler.kt:665)
17:53:05.213 [main @coroutine#1] INFO  sample - Joined failed job
17:53:05.215 [DefaultDispatcher-worker-1 @coroutine#3] INFO  sample - Throwing exception from async
17:53:05.313 [main @coroutine#1] INFO  sample - Caught ArithmeticException
```

### Coroutine 例外ハンドラ
CoroutineExceptionHandler コンテキスト属性は、
カスタムロギングや、例外処理を行う可能性のある、
Coroutine の汎用ブロックとして利用できる。
この機能は、Thread.uncaughtExceptionHandler に類似している。

JVM では、ServiceLoader によって、すべての Coroutine に登録された
 CoroutineExceptionHandler グローバル例外ハンドラを再定義できる。
グローバル例外ハンドラは、特定のハンドラが登録されていない場合に使用される
 Thread.uncaughtExceptionHandler と類似している。

Android では、uncaughtExceptionPreHandler が、
グローバル Coroutine 例外ハンドラとしてインストールされる。

CoroutineExceptionHandler は、ユーザに処理を任せている async ビルダなどの場合、
呼び出されないため、設定しても効果が無い。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

fun main() = runBlocking<Unit> {
    val handler = CoroutineExceptionHandler { _, throwable ->
        log.info("Caught $throwable")
    }
    val job = GlobalScope.launch(handler) {
        throw AssertionError()
    }
    val deferred = GlobalScope.async(handler) {
        throw ArithmeticException()
    }
    joinAll(job, deferred)
}
```
実行結果  
launch でのみ例外ハンドラが呼び出されており、async では呼び出されていないことに注目。
```
18:20:36.812 [DefaultDispatcher-worker-2 @coroutine#2] INFO  sample - Caught java.lang.AssertionError
```

### キャンセル処理と例外
キャンセルは、例外を除き、厳格に管理されている。
Coroutine のキャンセルは、内部で CancellationException をスローする。
CancellationException は、すべてのハンドラで無視されるため、
デバッグ情報として、追加の情報が、必要な場合のソースとしてのみ、利用価値がある。
つまり、処理内で、CancellationException を生成して throw しても、
無視されるので、意味が無いということ。

Job.cancel() 関数は、親をキャンセルすることなく、
特定の Job のみキャンセルする事ができる。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

fun main() = runBlocking<Unit> {
    val job = launch {
        val child = launch {
            try {
                delay(Long.MAX_VALUE)
            } finally {
                log.info("Child is cancelled")
            }
        }
        yield()
        log.info("Cancelling child")
        child.cancel()
        child.join()
        yield()
        log.info("Parent is not cancelled")
    }
    job.join()
}
```
実行結果
```
18:38:40.843 [main @coroutine#2] INFO  sample - Cancelling child
18:38:40.936 [main @coroutine#3] INFO  sample - Child is cancelled
18:38:40.938 [main @coroutine#2] INFO  sample - Parent is not cancelled
```

Coroutine が、CancellationException 以外の例外を検知した場合、その親もキャンセルする。
この挙動は、変更できず、構造化された並行性を担保する前提となっている。

CancellationException 以外の例外は、全ての子が終了した後に、親によって処理される。

>これは、これらの例では、GlobalScope で作成された Coroutine に常に
>CoroutineExceptionHandler がインストールされる理由でもある。
>main スレッドの Coroutine は、設定された例外ハンドラに関わらず、
>子が例外で完了した場合は、常にキャンセルするため、
>メインスレッドの runBlocking スコープで起動する Coroutine には、
>例外ハンドラを設定しても意味が無い。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

fun main() = runBlocking<Unit> {
    val handler = CoroutineExceptionHandler { _, throwable ->
        log.info("Caught $throwable")
    }
    val job = GlobalScope.launch(handler) {
        launch {
            try {
                delay(Long.MAX_VALUE)
            } finally {
                withContext(NonCancellable) {
                    log.info("Children are cancelled, but exception is not handled until all children terminate")
                    delay(100)
                    log.info("The first child finished its non cancellable block")
                }
            }
        }
        launch {
            delay(10)
            log.info("Second child throws an exception")
            throw ArithmeticException()
        }
    }
    job.join()
}
```
実行結果
```
19:08:11.385 [DefaultDispatcher-worker-1 @coroutine#4] INFO  sample - Second child throws an exception
19:08:11.482 [DefaultDispatcher-worker-1 @coroutine#3] INFO  sample - Children are cancelled, but exception is not handled until all children terminate
19:08:11.584 [DefaultDispatcher-worker-1 @coroutine#3] INFO  sample - The first child finished its non cancellable block
19:08:11.585 [DefaultDispatcher-worker-1 @coroutine#3] INFO  sample - Caught java.lang.ArithmeticException
```

### 例外の集約
複数の子 Coroutine が例外をスローした場合、通常では、最初の例外のみ処理される。
ただし、Coroutine が finally ブロックで別の例外をスローした場合などで、
最初の例外が失われる可能性がある。
それを除けば、追加で例外が発生した場合は、無視される。

>解決策の 1 つは、各例外を個別に報告することだが、
>Deferred.await は動作の不整合を回避するために、
>同じ仕組みを備えている必要があり、
>これにより Coroutine の実装の詳細が発生します。
>（その作業の一部を子に委任したか、not） 例外ハンドラにリークしません。

```kotlin
er = LoggerFactory.getLogger("sample")

fun main() = runBlocking<Unit> {
    val handler = CoroutineExceptionHandler { _, throwable ->
        log.info("Caught $throwable with suppressed ${throwable.suppressed.contentToString()}")
    }
    val job = GlobalScope.launch(handler) {
        launch {
            try {
                delay(Long.MAX_VALUE)
            } finally {
                throw ArithmeticException()
            }
        }
        launch {
            delay(100)
            throw IOException()
        }
        delay(Long.MAX_VALUE)
    }
    job.join()
}
```
実行結果
```
19:29:54.244 [DefaultDispatcher-worker-1 @coroutine#2] INFO  sample - Caught java.io.IOException with suppressed [java.lang.ArithmeticException]
```

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

fun main() = runBlocking<Unit> {
    val handler = CoroutineExceptionHandler { _, throwable ->
        log.info("Caught original $throwable")
    }
    val job = GlobalScope.launch(handler) {
        val inner = launch {
            launch {
                throw IOException()
            }
        }
        try {
            inner.join()
        } catch (e: CancellationException) {
            log.info("Rethrowing CancellationException with original cause")
            throw e
        }
    }
    job.join()
}
```
実行結果
```
19:34:15.712 [DefaultDispatcher-worker-3 @coroutine#2] INFO  sample - Rethrowing CancellationException with original cause
19:34:15.718 [DefaultDispatcher-worker-3 @coroutine#2] INFO  sample - Caught original java.io.IOException
```

## 監視
以前にも言及したように、キャンセルは、Coroutine 階層全体に伝播する、双方向の関係となる。
しかし、一方向のキャンセルを実現するにはどうすればよいか？

このような用件を持った良い例は、スコープ内にジョブが定義された UI コンポーネントとなる。
UI の子タスクのいずれかが失敗した場合、必ずしも UI コンポーネント全体をキャンセルする
（強制終了する）必要はないが、UI コンポーネントを破棄（ジョブをキャンセル）した場合は、
すべての子ジョブの不要となった要求を失敗させる必要がある。
別の例で言うと、複数の子ジョブを生成・監視して、失敗を検知し
失敗した子ジョブのみを、再起動する必要があるサーバープロセスなどがある。

### SupervisorJob
SupervisorJob は、監視などの目的のために利用することが可能。
通常の Job に似ているが、キャンセルが下方のみに伝播するという点で異なる。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

fun main() = runBlocking<Unit> {
    val supervisor = SupervisorJob()
    with(CoroutineScope(coroutineContext + supervisor)) {
        val firstChild = launch(CoroutineExceptionHandler { _, _ -> }) {
            log.info("First child is failing")
            throw AssertionError("First child is cancelled")
        }
        val secondChild = launch {
            firstChild.join()
            log.info("First child is cancelled: ${firstChild.isCancelled}, but second one is still active")
            try {
                delay(Long.MAX_VALUE)
            } finally {
                log.info("Second child is cancelled because supervisor is cancelled")
            }
        }
        firstChild.join()
        log.info("Cancelling supervisor")
        supervisor.cancel()
        secondChild.join()
    }
}
```
実行結果  
1 行目、最初のジョブで例外が発生  
2 行目、2 番目のジョブで、最初のジョブを待ち合わせていたが、
最初のジョブが例外で完了したため、次の処理へ遷移  
3 行目、親をキャンセル  
4 行目、親のキャンセルが、2 番目のジョブに伝播して終了となる
```
22:21:32.141 [main @coroutine#2] INFO  sample - First child is failing
22:21:32.148 [main @coroutine#3] INFO  sample - First child is cancelled: true, but second one is still active
22:21:32.149 [main @coroutine#1] INFO  sample - Cancelling supervisor
22:21:32.225 [main @coroutine#3] INFO  sample - Second child is cancelled because supervisor is cancelled
```

### supervisorScope
スコープ付き同時実行の場合、同じ目的で CoroutineScope の代わりに、supervisorScope を使用できる。
キャンセルは、一方向のみに伝播し、失敗した場合にのみ、すべての子をキャンセルする。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

fun main() = runBlocking<Unit> {
    try {
        supervisorScope {
            val child = launch {
                try {
                    log.info("Child is sleeping")
                    delay(Long.MAX_VALUE)
                } finally {
                    log.info("Child is cancelled")
                }
            }
            yield()
            log.info("Throwing exception from scope")
            throw AssertionError()
        }
    } catch (e: AssertionError) {
        log.info("Caught assertion error")
    }
}
```
実行結果
```
22:37:57.516 [main @coroutine#2] INFO  sample - Child is sleeping
22:37:57.528 [main @coroutine#1] INFO  sample - Throwing exception from scope
22:37:57.630 [main @coroutine#2] INFO  sample - Child is cancelled
22:37:57.687 [main @coroutine#1] INFO  sample - Caught assertion error
```

### 監視 Coroutine での例外
通常ジョブと監視ジョブのもう一つの重要な違いは、例外処理で、
すべての子は、例外処理の仕組みで、自ら例外を処理する必要がある。
この違いは、子の例外が、親に伝播しないという仕組みのため。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

fun main() = runBlocking<Unit> {
    val handler = CoroutineExceptionHandler { _, throwable ->
        log.info("Caught $throwable")
    }
    supervisorScope {
        val child = launch(handler) {
            log.info("Child throws an exception")
            throw AssertionError()
        }
        log.info("Scope is completing")
    }
    log.info("Scope is completed")
}
```
実行結果
```
22:49:22.813 [main @coroutine#1] INFO  sample - Scope is completing
22:49:22.832 [main @coroutine#2] INFO  sample - Child throws an exception
22:49:22.835 [main @coroutine#2] INFO  sample - Caught java.lang.AssertionError
22:49:22.837 [main @coroutine#1] INFO  sample - Scope is completed
```
