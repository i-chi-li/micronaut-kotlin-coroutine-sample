<!-- toc -->
- [コンテキストとディスパッチャ](#コンテキストとディスパッチャ)
    - [ディスパッチャが未指定の場合](#ディスパッチャが未指定の場合)
    - [Dispatchers.Unconfined を指定した場合](#dispatchersunconfined-を指定した場合)
    - [Dispatchers.Default を指定した場合](#dispatchersdefault-を指定した場合)
    - [newSingleThreadContext を指定した場合](#newsinglethreadcontext-を指定した場合)
  - [Coroutine とスレッドのデバッグ](#coroutine-とスレッドのデバッグ)
  - [スレッド間の往来](#スレッド間の往来)
  - [コンテキストに格納されているジョブ](#コンテキストに格納されているジョブ)
  - [Coroutine の子階層](#coroutine-の子階層)
  - [親 Coroutine の責務](#親-coroutine-の責務)
  - [デバッグ用に Coroutine を命名](#デバッグ用に-coroutine-を命名)
  - [コンテキスト要素の結合](#コンテキスト要素の結合)
  - [Coroutine スコープ](#coroutine-スコープ)
  - [スレッドローカルデータ](#スレッドローカルデータ)

# コンテキストとディスパッチャ
Coroutine は、さまざまな値を格納した、```Coroutine コンテキスト```を保持する。
```Coroutine コンテキスト```に含まれる主な要素は、ジョブとディスパッチャとなる。

## ディスパッチャとスレッド
ディスパッチャは、Coroutine を実行するスレッドを決定する。
ディスパッチャは、Coroutine の実行を「特定のスレッドに限定」したり、
「スレッドプールを利用」したり、「制限せず実行」など制御する。
```Coroutine ビルダ```には、```Coroutine コンテキスト```をパラメータとして指定できる。

ディスパッチャーによる実行スレッドの違いを以下の観点で確認する
- ディスパッチャ未指定
- ```Dispatchers.Unconfined``` を指定
- ```Dispatchers.Default``` を指定
- ```newSingleThreadContext``` を指定
```kotlin
fun main() = runBlocking<Unit> {
    launch {
        println("main runBlocking      : I'm working in thread ${Thread.currentThread().name}")
    }
    launch(Dispatchers.Unconfined) {
        println("Unconfined            : I'm working in thread ${Thread.currentThread().name}")
    }
    launch(Dispatchers.Default) {
        println("Default               : I'm working in thread ${Thread.currentThread().name}")
    }
    launch(newSingleThreadContext("MyOwnThread")) {
        println("newSingleThreadContext: I'm working in thread ${Thread.currentThread().name}")
    }
}
```
実行結果  
環境によりスレッド名の番号は異なる。
```
Unconfined            : I'm working in thread main
Default               : I'm working in thread DefaultDispatcher-worker-2
newSingleThreadContext: I'm working in thread MyOwnThread
main runBlocking      : I'm working in thread main
```

### ディスパッチャが未指定の場合
ディスパッチャを指定しない場合は、親からコンテキストを継承し、
コンテキストに格納されているディスパッチャでスレッドが決定される。  
この例の場合、親は runBlocking であり、実行スレッドは main スレッドとなる。

### Dispatchers.Unconfined を指定した場合
制限無し（Unconfined）ディスパッチャは、main スレッドで実行されているように見えるが、
スレッドの利用方法が特殊なディスパッチャとなる。  
制限無し（Unconfined）ディスパッチャは、最初の Suspend 位置まで、
呼び出し元スレッドでコルーチンを開始する。
Suspend 後、呼び出された Suspend 機能により決定するスレッド内で Coroutine を再開する。
制限無し（Unconfined）ディスパッチャは、CPU 時間を消費せず、
特定スレッド限定の共有データ（UIなど）を、更新しない Coroutine に適している。

一方、ディスパッチャは、デフォルトで親 ```Coroutine スコープ``` から継承する。
特に、runBlocking デフォルトのディスパッチャは、呼び出しスレッドに限定されているため、
ディスパッチャを継承すると、予測可能な FIFO スケジューリングで、このスレッドに実行を限定する効果がある。

実際の挙動を確認する。
```kotlin
fun main() = runBlocking<Unit> {
    launch(Dispatchers.Unconfined) {
        println("Unconfined      : I'm working in thread ${Thread.currentThread().name}")
        delay(500L)
        println("Unconfined      : After delay in thread ${Thread.currentThread().name}")
    }
    launch {
        println("main runBlocking: I'm working in thread ${Thread.currentThread().name}")
        delay(1000L)
        println("main runBlocking: After delay in thread ${Thread.currentThread().name}")
    }
}
```
実行結果  
制限無し（Unconfined）ディスパッチャの場合は、Suspend 後のスレッドが変わっている。
```
Unconfined      : I'm working in thread main
main runBlocking: I'm working in thread main
Unconfined      : After delay in thread kotlinx.coroutines.DefaultExecutor
main runBlocking: After delay in thread main
```

### Dispatchers.Default を指定した場合
共有バックグラウンドプールのスレッドを利用して起動する。  
GlobalScope デフォルトのディスパッチャは、Dispatchers.Default。

### newSingleThreadContext を指定した場合
専用のスレッドを新たに生成する。
生成したスレッドが不要になった場合は、close 関数で開放をするか、
アプリケーション全体で再利用するなど、自前で管理する必要がある。

## Coroutine とスレッドのデバッグ
Coroutine は、あるスレッドで Suspend し、別のスレッドで再開する。
したがって、スレッドの一般的な確認方法では、デバッグが難しい。
一般的なスレッドを確認する方法は、ログにスレッド名を出力することで、
多くのロギングフレームワークでサポートされている。  
Coroutine の場合は、スレッド名だけでは、得られる情報が不十分なため、
デバッグに有用な機能がライブラリに含まれている。  
デバッグ機能を有効にするには、JVM オプションに「```-Dkotlinx.coroutines.debug```」を指定する。

以下のコードを実行する。
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")
fun main() = runBlocking<Unit> {
    val a = async {
        log.info("I'm computing a piece of the answer")
        6
    }
    val b = async {
        log.info("I'm computing another piece of the answer")
        7
    }
    log.info("The answer is ${a.await() * b.await()}")
}
```

実行結果
```
10:52:09.120 [main] INFO  sample - I'm computing a piece of the answer
10:52:09.124 [main] INFO  sample - I'm computing another piece of the answer
10:52:09.125 [main] INFO  sample - The answer is 42
```

上記コードに JVM オプション「-Dkotlinx.coroutines.debug」を指定して実行する。  
このようにログ設定を変更せず、スレッド名に Coroutine の情報も出力できる。
```
10:47:47.892 [main @coroutine#2] INFO  sample - I'm computing a piece of the answer
10:47:47.895 [main @coroutine#3] INFO  sample - I'm computing another piece of the answer
10:47:47.896 [main @coroutine#1] INFO  sample - The answer is 42
```
上記デバッグモードは、JVM オプションに「-ea」（-enableassertions 指定した粒度でアサーションを有効にする）を
指定することでも有効化される。

## スレッド間の往来
JVM オプションに「-Dkotlinx.coroutines.debug」を指定して以下のコードを実行する。
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")
@ObsoleteCoroutinesApi
fun main() {
    // スレッド Ctx1 を作成
    newSingleThreadContext("Ctx1").use { ctx1 ->
        // スレッド Ctx2 を作成
        newSingleThreadContext("Ctx2").use { ctx2 ->
            // スレッド Ctx1 で Coroutine を起動
            runBlocking(ctx1) {
                log.info("Start in Ctx1")
                // スレッド Ctx2 で Coroutine を起動
                withContext(ctx2) {
                    log.info("Working in Ctx2")
                }
                log.info("Back to Ctx1")
            }
        }
    }
}
```
実行結果
```
12:45:46.575 [Ctx1 @coroutine#1] INFO  sample - Start in Ctx1
12:45:46.583 [Ctx2 @coroutine#1] INFO  sample - Working in Ctx2
12:45:46.584 [Ctx1 @coroutine#1] INFO  sample - Back to Ctx1
```
上記コードでは、runBlocking で明示的に Coroutine を生成する。
しかし、withContext では、同一 Coroutine に留まりながら、コンテキストを変更することにより、
スレッドのみ切り替えている。

>newSingleThreadContext は、将来的に置き換わる機能となっている。
>この関数は、スレッドを生成し、生成したスレッドを開放する処理を自ら行う必要があるため、
>問題となっている。
>[ここ](https://github.com/Kotlin/kotlinx.coroutines/issues/261)で議論となっているようだ。

## コンテキストに格納されているジョブ
コンテキストから、ジョブの情報を取得できる。
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")
@ObsoleteCoroutinesApi
fun main() {
    // スレッド Ctx1 を作成
    newSingleThreadContext("Ctx1").use { ctx1 ->
        // スレッド Ctx2 を作成
        newSingleThreadContext("Ctx2").use { ctx2 ->
            // スレッド Ctx1 で Coroutine を起動
            runBlocking(ctx1) {
                log.info("Start in Ctx1.   Job: ${coroutineContext[Job]}")
                // スレッド Ctx2 で Coroutine を起動
                withContext(ctx2) {
                    log.info("Working in Ctx2. Job: ${coroutineContext[Job]}")
                }
                log.info("Back to Ctx1.    Job: ${coroutineContext[Job]}")
            }
        }
    }
}
```
実行結果  
Coroutine の種類と、Job の情報を確認できる。
行末にハッシュ値があるが、Job インスタンスのハッシュ値となる。スレッドや、Coroutine を識別するものではないので注意。
```
14:04:29.512 [Ctx1 @coroutine#1] INFO  sample - Start in Ctx1.   Job: "coroutine#1":BlockingCoroutine{Active}@2139d221
14:04:29.521 [Ctx2 @coroutine#1] INFO  sample - Working in Ctx2. Job: "coroutine#1":DispatchedCoroutine{Active}@61ecbc20
14:04:29.522 [Ctx1 @coroutine#1] INFO  sample - Back to Ctx1.    Job: "coroutine#1":BlockingCoroutine{Active}@2139d221
```

## Coroutine の子階層
別の Coroutine 処理から、新たな Coroutine を生成する場合、親 Coroutine のコンテキストを継承する。
親子構造を持った Coroutine の場合、親 Coroutine がキャンセルされると、子 Coroutine も再帰的にすべてキャンセルとなる。
ただし、GlobalScope で新たに Coroutine を生成した場合は、親は設定されず、起動元スコープとは関係なく、独立して動作する。
```kotlin
fun main() = runBlocking {
    val request = launch {
        // 2 つの Coroutine を生成する。
        // job1 は、親を持たず、独立して動作する Coroutine を生成
        GlobalScope.launch {
            println("job1: I run in GlobalScope and execute independently!")
            delay(1000L)
            println("job1: I am not affected by cancellation of the request")
        }
        // job2 は、現在の Coroutine を親として動作する Coroutine を生成
        launch {
            delay(100L)
            println("job2: I am a child of the request coroutine")
            delay(1000L)
            println("job2: I will not execute this line if my parent request is cancelled")
        }
    }
    delay(500L)
    request.cancel()
    delay(1000L)
    println("main: Who has survived request cancellation?")
}
```
実行結果  
GlobalScope で生成した Coroutine （job1）は、
起動元の Coroutine がキャンセルされても、最後まで処理を完了している。
対して、呼び出し元を親として生成した Coroutine （job2）は、
親をキャンセルした時点で、子供もキャンセルされている。
```
job1: I run n GlobalScope and execute independently!
job2: I am a child of the request coroutine
job1: I am not affected by cancellation of the request
main: Who has survived request cancellation?
```

## 親 Coroutine の責務
親 Coroutine は、常に子 Coroutine の完了を待機する。
親 Coroutine は、Job.join などで明示的に、子 Coroutine の完了を待つ必要は無い。
```kotlin
fun main() = runBlocking {
    val request = launch {
        repeat(3) { i ->
            launch {
                delay((i + 1) * 200L)
                println("Coroutine $i is done")
            }
        }
        println("request: I'm done and I don't explicitly join my children that are still active")
    }
    request.join()
    println("Now processing of the request is complete")
}
```
実行結果  
request ジョブ（親）は、子 Coroutine を起動して即座に完了している。
しかし、request.join() だけで、子 Coroutine の完了まで待機することができる。
```
request: I'm done and I don't explicitly join my children that are still active
Coroutine 0 is done
Coroutine 1 is done
Coroutine 2 is done
Now processing of the request is complete
```

## デバッグ用に Coroutine を命名
通常、Coroutine 名は、自動的に ID が割当たる。
これは、頻繁に Coroutine が生成され、ログから同一の Coroutine 処理を判別するような場合に適している。  
しかし、特定の要求や、特定のバックグラウンド処理などの場合は、
明示的に Coroutine 名を設定することを推奨する。
Coroutine ビルダに CoroutineName を指定することができる。

JVM オプションに「-Dkotlinx.coroutines.debug」を指定して実行。
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")
fun main() = runBlocking {
    log.info("Started main coroutine")
    val v1 = async(CoroutineName("v1coroutine")) {
        delay(500L)
        log.info("Computing v1")
        252
    }
    val v2 = async(CoroutineName("v2coroutine")) {
        delay(1000L)
        log.info("Computing v2")
        6
    }
    log.info("The answer for v1 / v2 = ${v1.await() / v2.await()}")
}
```
実行結果  
Coroutine 名が設定されている。
```
15:12:34.425 [main @coroutine#1] INFO  sample - Started main coroutine
15:12:34.945 [main @v1coroutine#2] INFO  sample - Computing v1
15:12:35.446 [main @v2coroutine#3] INFO  sample - Computing v2
15:12:35.446 [main @coroutine#1] INFO  sample - The answer for v1 / v2 = 42
```

## コンテキスト要素の結合
Coroutine コンテキストは、```+```演算子で結合できる。
これは、Coroutine コンテキストで複数の要素を扱う場合に有用となる。

以下のように、デフォルトのディスパッチャと Coroutine の命名を同時に行いたい場合などに利用できる。  
JVM オプションに「-Dkotlinx.coroutines.debug」を指定して実行。
```kotlin
fun main() = runBlocking<Unit> {
    launch(Dispatchers.Default + CoroutineName("test")) {
        println("I'm working in thread ${Thread.currentThread().name}")
    }
}
```
実行結果  
```
I'm working in thread DefaultDispatcher-worker-1 @test#2
```

## Coroutine スコープ
Coroutine スコープは、異なるライフサイクルを持つリソースや、非同期処理が混在するアプリケーションで、
これらをカプセル化する抽象化手段となる。
メモリリークなどを防止する手段として、リソース管理に Coroutine スコープを活用すれば、幸せになれます。（超意訳）
> ここは、Android アプリの話がメインのようなので、割愛

## スレッドローカルデータ
スレッドローカルデータを Coroutine に渡したり、Coroutine 間で受け渡す方法として、
ThreadLocal.asContextElement 拡張機能が利用できる。  
この拡張機能を利用すると、Coroutine が内部で自動的に ThreadLocal の値を復元する。

ThreadLocal を Coroutine で利用するためのサンプル。
```kotlin
val threadLocal = ThreadLocal<String?>()
fun main() = runBlocking<Unit> {
    threadLocal.set("main")
    println("Pre-main, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
    val job = launch(Dispatchers.Default + threadLocal.asContextElement("launch")) {
        // Suspend 前後で異なるスレッドで実行されるが、ThreadLocal の値は同じ
        println("Launch start, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
        delay(200L)
        println("After yield, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
    }
    launch(Dispatchers.Default) {
        // 上記のスレッド切替をするための細工
        delay(100L)
        println("Other, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
    }
    job.join()
    println("Post-main, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
}
```
実行結果  
同一 Coroutine の場合、異なるスレッドで動作した場合でも、ThreadLocal の値が同値になる。
```
Pre-main, current thread: Thread[main @coroutine#1,5,main], thread local value: 'main'
Launch start, current thread: Thread[DefaultDispatcher-worker-2 @coroutine#2,5,main], thread local value: 'launch'
Other, current thread: Thread[DefaultDispatcher-worker-1 @coroutine#3,5,main], thread local value: 'null'
After yield, current thread: Thread[DefaultDispatcher-worker-1 @coroutine#2,5,main], thread local value: 'launch'
Post-main, current thread: Thread[main @coroutine#1,5,main], thread local value: 'main'
```
この拡張機能を利用する場合の注意点として、コンテキストの設定を忘れた場合、
スレッドが切り替わると、予期しない値が設定されている状態になる可能性がある。
その場合は、ThreadLocal<*>.ensurePresent() 拡張関数を利用する。
この拡張関数は、対象の ThreadLocal インスタンスが、Coroutine コンテキストに、
含まれていない場合に、IllegalStateException をスローする。
get() の前に呼び出しておくと、設定漏れが検知出来る。

注意点としては、ThreadLocal の値の変更は、Coroutine の呼び出し元まで伝播しない。
次の Suspend 時に変更が失われる。その場合は、withContext を利用して、Coroutine 内の
ThreadLocal の値を変更する。
または、値を格納するクラスなどを作成して、ThreadLocal に格納する。
ただし、該当クラスは、スレッドセーフである必要がある。

ロギング MDC、トランザクションコンテキストまたは、スレッドローカルを内部で利用する
その他のライブラリなどの高度な利用法については、ThreadContextElement インターフェースを参照。

## ThreadContextElement インターフェース
ThreadContextElement インターフェースは、
Coroutine の切替時に行う処理を Coroutine コンテキストとして実装するためのインターフェースとなる。
Coroutine 毎に保持する値は、総称型で指定する。
Coroutine 毎に保持する値には、対応するキーを定義する。
Coroutine 毎に保持する値は、ThreadContextElement の実装コード内で保持せず、
Coroutine 毎に保持する値の更新時には、更新前の値を戻り値で返し、
Coroutine 毎に保持する値の復元時には、更新前の値を引数で受け取る。
つまり、更新前の値は、キーに対応させて Coroutine ライブラリ側で管理される。

```kotlin
/**
 * Coroutine 切替時のスレッド名を管理する Coroutine コンテキストの実装例
 *
 * Coroutine コンテキストは、「Coroutine コンテキスト要素」毎に管理する。
 * Coroutine コンテキスト要素は、一意のキーで識別する。
 *
 * @property name スレッド名
 */
class CoroutineName(val name: String) : ThreadContextElement<String> {
    // CoroutineName を特定する Coroutine コンテキスト要素キーを定義する。
    // companion object で定義するので、シングルトンとなる。
    companion object Key : CoroutineContext.Key<CoroutineName>

    /**
     * このインスタンスの Coroutine コンテキスト要素キーを返す。
     */
    override val key: CoroutineContext.Key<*>
        get() = Key

    /**
     * 新たに Coroutine を開始する場合に呼び出される。
     * スレッド名を更新する。
     *
     * @param context Coroutine コンテキスト
     * @return 呼び出し元のスレッド名を返す。
     */
    override fun updateThreadContext(context: CoroutineContext): String {
        // 現在のスレッド名を取得
        val previousName = Thread.currentThread().name
        // スレッド名を更新
        Thread.currentThread().name = "$previousName # $name"
        // 以前のスレッド名を返す。
        return previousName
    }

    /**
     * 呼び出し元の Coroutine へ復帰する場合に呼び出される。
     * スレッド名を復元する。
     *
     * @param context Coroutine コンテキスト
     * @param oldState この Coroutine へ遷移する前のスレッド名
     */
    override fun restoreThreadContext(context: CoroutineContext, oldState: String) {
        Thread.currentThread().name = oldState
    }
}

fun main() = runBlocking<Unit> {
    println("Thread name: ${Thread.currentThread().name}")
    launch(Dispatchers.Default + CoroutineName("Progress bar coroutine")) {
        println("Thread name: ${Thread.currentThread().name}")
        withContext(CoroutineName("Nested context")) {
            println("Thread name: ${Thread.currentThread().name}")
        }
        println("Thread name: ${Thread.currentThread().name}")
    }.join()
    println("Thread name: ${Thread.currentThread().name}")
}
```

実行結果

```
Thread name: main @coroutine#1
Thread name: DefaultDispatcher-worker-2 # Progress bar coroutine @coroutine#2
Thread name: DefaultDispatcher-worker-2 # Progress bar coroutine @coroutine#2 # Nested context
Thread name: DefaultDispatcher-worker-2 # Progress bar coroutine @coroutine#2
Thread name: main @coroutine#1
```
