<!-- toc -->
- [kotlin-coroutines-test モジュール](#kotlin-coroutines-test-モジュール)
  - [Dispatchers.Main の委譲](#dispatchersmain-の委譲)
  - [runBlockingTest ビルダについて](#runblockingtest-ビルダについて)
    - [通常 Suspend 関数のテスト](#通常-suspend-関数のテスト)
    - [launch および、async 処理のテスト](#launch-およびasync-処理のテスト)
    - [delay 関数を使う launch または、async 処理のテスト](#delay-関数を使う-launch-またはasync-処理のテスト)
    - [runBlockingTest ビルダを利用した withTimeout 処理のテスト](#runblockingtest-ビルダを利用した-withtimeout-処理のテスト)
    - [pauseDispatcher ビルダおよび、resumeDispatcher ビルダ](#pausedispatcher-ビルダおよびresumedispatcher-ビルダ)
  - [構造化された並行性とテストの統合](#構造化された並行性とテストの統合)
    - [runBlockingTest が提供する TestCoroutineScope](#runblockingtest-が提供する-testcoroutinescope)
    - [明示的な TestCoroutineScope の提供](#明示的な-testcoroutinescope-の提供)
    - [明示的な TestCoroutineDispatcher の提供](#明示的な-testcoroutinedispatcher-の提供)
    - [runBlockingTest 不使用時の、TestCoroutineScope および TestCoroutineDispatcher でのテスト](#runblockingtest-不使用時のtestcoroutinescope-および-testcoroutinedispatcher-でのテスト)
  - [withContext で時間制御](#withcontext-で時間制御)

# kotlin-coroutines-test モジュール
Coroutine のテストユーティリティ。
Coroutine を効率よくテストするための各種機能を持つ。

このモジュールは、テストのみに利用すること。
メインソースは、絶対に依存させないこと。

[サンプルソースコード](../../src/test/kotlin/micronaut/kotlin/coroutine/sample/coroutine/CoroutineTestUtilTest.kt)

## Dispatchers.Main の委譲
 ```Dispatchers.setMain``` 拡張関数は、```Dispatchers.Main``` をテスト中に上書きする。
この機能は、プラットフォームの Main ディスパッチャが利用できない状況や、
テスト用のディスパッチャに置き換える場合に有用となる。

このモジュールを依存関係に含めると、ServiceLoader によって、
```Dispatchers.Main``` をテスト用の実装で上書きする。

以下のように、```Dispatchers.setMain``` 拡張関数で、
```Dispatchers.Main``` を上書きする。

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class SomeTest {
    private val log: Logger = LoggerFactory.getLogger("SomeTest")
    private lateinit var mainThreadSurrogate: ExecutorCoroutineDispatcher

    @OptIn(ObsoleteCoroutinesApi::class)
    @BeforeEach
    fun beforeEach() {
        mainThreadSurrogate = newSingleThreadContext("UI thread")
        Dispatchers.setMain(mainThreadSurrogate)
    }

    @AfterEach
    fun afterEach() {
        // Main ディスパッチャを元に戻す
        Dispatchers.resetMain()
        mainThreadSurrogate.close()
    }

    @Test
    fun testSomeUI() = runBlocking<Unit> {
        launch(Dispatchers.Main) {
            log.info("start someUI")
            // ...
        }
    }
}
```

実行結果  
スレッドが、"UI thread" に上書きされていることに注目。

```
23:23:19.318 [UI thread @coroutine#2] INFO  Test - start someUI
```

## runBlockingTest ビルダについて
通常の Suspend 関数や、launch または、async ビルダで生成した Coroutine をテストする場合、
runBlockingTest ビルダを利用することで、以下のようなテスト用の追加機能を利用できる。

- 通常の Suspend 関数での、自動の時間進行
- 複数の Coroutine テストでの、明示的な時間制御
- launch や、async ビルダコードブロックの詳細な追跡
- テストの 一時停止、手動での進行および、Coroutine 処理の実行再開
- 発生した未処理の例外をテスト失敗として報告

### 通常 Suspend 関数のテスト
runBlockingTest ビルダは、delay 関数などを呼び出す、
通常の Suspend 関数をテストする場合に利用できる。
テストを開始すると、仮想時間が、delay 関数で指定した時間だけ、自動的に進む。
runBlockingTest の戻り値型は、Unit のみとなる。

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class RunBlockingTest {
    private lateinit var log: Logger

    @Test
    fun testSuspendFunctionNormal() = runBlocking {
        log = LoggerFactory.getLogger("RunBlockingTest-Normal")
        val actual = suspendFunction()
    }

    @Test
    fun testSuspendFunctionFast() = runBlockingTest {
        log = LoggerFactory.getLogger("RunBlockingTest-Fast")
        val actual = suspendFunction()
    }

    suspend fun suspendFunction() {
        log.info("Start suspendFunction")
        delay(1_000)
        log.info("Finish suspendFunction")
    }
}
```

実行結果  
runBlockingTest で実行した方は、delay での中断時間が無いことに注目。

```
00:02:09.005 [Test worker @coroutine#1] INFO  Test-Normal - Start suspendFunction
00:02:10.017 [Test worker @coroutine#1] INFO  Test-Normal - Finish suspendFunction
00:02:10.047 [Test worker @coroutine#2] INFO  Test-Fast - Start suspendFunction
00:02:10.049 [Test worker @coroutine#2] INFO  Test-Fast - Finish suspendFunction
```

### launch および、async 処理のテスト
デフォルトの ```runBlockingTest``` の挙動は、テストを容易にするため、
Coroutine 処理では、最初の ```delay``` 関数または、```yield``` 関数まで、確実に実行する。
さらに、すべての Coroutine が完了するまで、内部の仮想時間を自動進行させる。
その際に、完了できない Coroutine 処理があると、```UncompletedCoroutinesError``` をスローする。
runBlockingTest のデフォルト挙動は、CoroutineStart パラメータを無視する。

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchAsyncTest {
    private lateinit var log: Logger

    @Test
    fun testLaunch() = runBlockingTest {
        log = LoggerFactory.getLogger("LaunchAsyncTest")
        log.info("Start testLaunch")
        foo()
        log.info("Finish testLaunch")
        // この Coroutine 処理は、foo の処理が完了するまえに終了するはずだが・・・
    }

    private fun CoroutineScope.foo() {
        launch {
            log.info("Start foo")
            bar()
            log.info("Finish foo")
            // runBlockingTest で実行すると、本来途中で終了する処理も、最後まできちんと実行される
        }
    }

    suspend fun bar() {}
}
```

実行結果  

```
00:41:00.930 [Test worker @coroutine#1] INFO  Test - Start testLaunch
00:41:00.940 [Test worker @coroutine#2] INFO  Test - Start foo
00:41:00.940 [Test worker @coroutine#2] INFO  Test - Finish foo
00:41:00.942 [Test worker @coroutine#1] INFO  Test - Finish testLaunch
```

### delay 関数を使う launch または、async 処理のテスト
launch または、async 処理から delay を呼び出した場合、
runBlockingTest は、即座の時間自動進行はしない。
それによって、テストで異なる遅延時間を持つ、
複数の Coroutine 処理間の相互作用を確認できる。
テストの時間制御には、DelayController インターフェースを利用できる。
runBlockingTest 処理ブロックでは、DelayController の任意の関数を呼び出せる。

runBlockingTest は、テストが完了する前に、すべての Coroutine が完了するまで、
継続的に時間の自動進行を試みる。
それにより、多くの一般的なテストコードの最後で、advanceUntilIdle を呼び出す必要がなくなる。
いずれかの Coroutine が、時間までに処理を完了できない場合、UncompletedCoroutinesError をスローする。

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchAsyncWithDelayTest {
    private lateinit var log: Logger

    @Test
    fun testLaunchDelay() = runBlockingTest {
        log = LoggerFactory.getLogger("LaunchAsyncWithDelayTest")
        log.info("Start testLaunchDelay")
        foo()
        log.info("currentTime $currentTime ms")
        log.info("advanceTimeBy")
        // ディスパッチャの仮想時間を指定 ms 進める。
        advanceTimeBy(500)
        log.info("currentTime $currentTime ms")
        // 戻り値は、この呼び出しで進めた時間（ms）を返す。これまでに進んだ合計時間ではない。
        val forwarded = advanceTimeBy(1_500)
        log.info("Forwarded $forwarded ms")
        log.info("currentTime $currentTime ms")
        log.info("Finish testLaunchDelay")
    }

    suspend fun CoroutineScope.foo() {
        launch {
            log.info("Start foo")
            delay(1_000)
            log.info("Finish foo")
        }
    }
}
```

実行結果

```
08:08:21.623 [Test worker @coroutine#1] INFO  Test - Start testLaunchDelay
08:08:21.634 [Test worker @coroutine#2] INFO  Test - Start foo
08:08:21.641 [Test worker @coroutine#1] INFO  Test - currentTime 0 ms
08:08:21.642 [Test worker @coroutine#1] INFO  Test - advanceTimeBy
08:08:21.642 [Test worker @coroutine#1] INFO  Test - currentTime 500 ms
08:08:21.642 [Test worker @coroutine#2] INFO  Test - Finish foo
08:08:21.643 [Test worker @coroutine#1] INFO  Test - Forwarded 1500 ms
08:08:21.643 [Test worker @coroutine#1] INFO  Test - currentTime 2000 ms
08:08:21.644 [Test worker @coroutine#1] INFO  Test - Finish testLaunchDelay
```

### runBlockingTest ビルダを利用した withTimeout 処理のテスト
時間制御を利用して、タイムアウトコードをテストできる。
テストは、対象の関数が withTimeout ブロック内で一時停止したことを確認し、
タイムアウトが発生するまで、時間を進めることで行う。

コードによっては、コードを一時停止させるため、他のさまざまなモックや、
偽装の手段を利用する必要がある。
以下のコードの場合、Deferred をテストコードで生成し、テスト対象に渡している箇所となる。

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class UsingRunBlockingTestWithTimeoutTest {
    private lateinit var log: Logger

    @Test
    fun testWithTimeout() = runBlockingTest {
        log = LoggerFactory.getLogger("UsingRunBlockingTestWithTimeoutTest")
        log.info("Start testWithTimeout")
        val uncompleted = CompletableDeferred<String>()
        val job = foo(uncompleted)
        job.invokeOnCompletion {
            // タイムアウトが発生しても、例外が発生するわけでない。
            if (it == null) {
                log.info("Failed: $it")
            } else {
                log.info("Successfully")
            }
        }
        log.info("advanceTimeBy")
        // ディスパッチャの仮想時間を、タイムアウトが発生するまで進める
        advanceTimeBy(1_000)
        // タイムアウトした処理（foo の処理）が、キャンセルされる。
        // 呼び出し元（このブロック）は、キャンセルされない。
        // キャンセルされたかどうかを判断できる。
        log.info("job.isCancelled: ${job.isCancelled}")
        log.info("Finish testWithTimeout")
    }

    fun CoroutineScope.foo(resultDeferred: Deferred<String>) = launch {
        log.info("Start foo")
        val result: String = withTimeout(1_000) {
            log.info("Start withTimeout")
            // この待機は、終わらない
            resultDeferred.await()
            log.info("Finish withTimeout")
            "SUCCESS"
        }
        // withTimeout ブロックでタイムアウトが発生すると、以後の処理はキャンセルされる。
        // つまり、この行まで到達しない。
        log.info("result: result")
        log.info("Finish foo")
    }
}
```

実行結果

```
09:11:45.473 [Test worker @coroutine#1] INFO  Test - Start testWithTimeout
09:11:45.484 [Test worker @coroutine#2] INFO  Test - Start foo
09:11:45.493 [Test worker @coroutine#2] INFO  Test - Start withTimeout
09:11:45.502 [Test worker @coroutine#1] INFO  Test - advanceTimeBy
09:11:45.513 [Test worker @coroutine#2] INFO  Test - Successfully
09:11:45.513 [Test worker @coroutine#1] INFO  Test - job.isCancelled: true
09:11:45.513 [Test worker @coroutine#1] INFO  Test - Finish testWithTimeout
```

### pauseDispatcher ビルダおよび、resumeDispatcher ビルダ
幾つかのテストでは、より繊細な Coroutine 処理の制御が必要となる。
その場合、pauseDispatcher および resumeDispatcher を利用することで、
runBlockingTest が利用する TestCoroutineDispatcher を
一時停止および、再開することができる。

ディスパッチャを一時停止した場合、生成した Coroutine は、実行せず、キューに追加される。
さらに、一時停止したディスパッチャの仮想時間は、自動的に進行しなくなる。

推奨するテスト方法は、ここで説明した、一時停止行うテストと、
起動元との依存関係や、副作用を確認する、通常のテストの両方を行うことである。

重要：pauseDispatcher にラムダブロックを指定すると、
その次のブロックを即座に実行してしまう。
その場合、pauseDispatcher 処理中に、未完了の delay 呼び出しがあると、
自動進行に時間が掛かるようになる。
解決策として、pauseDispatcher をラムダブロック無しで呼び出し、
resumeDispatcher で明示的に再開を行うようにする。（testResumeDispatcher テストを参照）

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class PauseDispatcherTest {
    private lateinit var log: Logger

    @Test
    fun testPauseDispatcher() = runBlockingTest {
        log = LoggerFactory.getLogger("testPauseDispatcher")
        log.info("Start testPauseDispatcher")
        pauseDispatcher {
            log.info("Call foo")
            foo()
            // この時点では、foo の Coroutine 処理は開始していない
            log.info("runCurrent")
            // Coroutine 処理を開始
            runCurrent()
            log.info("advanceTimeBy")
            // 仮想時間を進める
            advanceTimeBy(1_000)
        }
        log.info("Finish testPauseDispatcher")
    }

    @Test
    fun testResumeDispatcher() = runBlockingTest {
        log = LoggerFactory.getLogger("testResumeDispatcher")
        log.info("Start testResumeDispatcher")
        // ディスパッチャを一時停止する
        pauseDispatcher()
        log.info("Call foo")
        // foo の Coroutine 処理は、まだ開始しない
        foo()
        log.info("Call launch")
        // launch の Coroutine 処理は、まだ開始しない
        launch {
            log.info("Start launch")
            delay(100)
            log.info("Finish launch")
        }
        log.info("resumeDispatcher")
        // ディスパッチャを再開する
        // この時点で、foo と launch の Coroutine 処理が開始される
        resumeDispatcher()
        log.info("Finish testResumeDispatcher")
    }

    fun CoroutineScope.foo() = launch {
        log.info("Start foo")
        delay(1_000)
        log.info("Finish foo")
    }
}
```

実行結果  
Coroutine 処理の開始タイミングを制御していることに注目。

```
10:38:14.866 [Test worker @coroutine#1] INFO  testPauseDispatcher - Start testPauseDispatcher
10:38:14.872 [Test worker @coroutine#1] INFO  testPauseDispatcher - Call foo
10:38:14.878 [Test worker @coroutine#1] INFO  testPauseDispatcher - runCurrent
10:38:14.878 [Test worker @coroutine#2] INFO  testPauseDispatcher - Start foo
10:38:14.884 [Test worker @coroutine#1] INFO  testPauseDispatcher - advanceTimeBy
10:38:14.885 [Test worker @coroutine#2] INFO  testPauseDispatcher - Finish foo
10:38:14.886 [Test worker @coroutine#1] INFO  testPauseDispatcher - Finish testPauseDispatcher

10:38:14.895 [Test worker @coroutine#3] INFO  testResumeDispatcher - Start testResumeDispatcher
10:38:14.895 [Test worker @coroutine#3] INFO  testResumeDispatcher - Call foo
10:38:14.895 [Test worker @coroutine#3] INFO  testResumeDispatcher - Call launch
10:38:14.896 [Test worker @coroutine#3] INFO  testResumeDispatcher - resumeDispatcher
10:38:14.896 [Test worker @coroutine#4] INFO  testResumeDispatcher - Start foo
10:38:14.897 [Test worker @coroutine#5] INFO  testResumeDispatcher - Start launch
10:38:14.897 [Test worker @coroutine#5] INFO  testResumeDispatcher - Finish launch
10:38:14.897 [Test worker @coroutine#4] INFO  testResumeDispatcher - Finish foo
10:38:14.898 [Test worker @coroutine#3] INFO  testResumeDispatcher - Finish testResumeDispatcher
```

## 構造化された並行性とテストの統合
構造化された並行性を利用するコードは、Coroutine を起動するために、
CoroutineScope を必要とする。
以下のクラスは、runBlockingTest を一般的な構造化された並行性パターンを利用した、
テスト対象コードに渡すことができる。

| 名称 | 説明 |
| :--- | :--- |
| TestCoroutineScope | runBlockingTest でテスト対象 Coroutine を詳細に制御するためのスコープ |
| TestCoroutineDispatcher | runBlockingTest で利用できる ディスパッチャ |

両クラスは、さまざまなテスト要求に対応できるように提供している。
テストによっては、TestCoroutineDispatcher の利用で簡単になる場合がある。
たとえば、Dispatchers.setMain は、TestCoroutineDispatcher は設定できるが、
TestCoroutineScope は、設定できない。

TestCoroutineScope は、常に TestCoroutineDispatcher を使用して、
Coroutine 処理を実行する。
また、TestCoroutineExceptionHandler を利用して、未処理の例外をテスト失敗と報告する。

TestCoroutineScope をテスト対象に渡すことで、テストケースで Coroutine の実行制御し、
テスト対象の Coroutine 処理で発生した、未処理例外を検知して、テスト失敗の報告を行える。

### runBlockingTest が提供する TestCoroutineScope
単純なテストケースでは、runBlockingTest が生成した、TestCoroutineScope を直接利用できる。
このような実装は、テスト対象が、CoroutineScope の拡張関数であるような場合で推奨する。

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class FooTest {
    @Test
    fun testFoo() = runBlockingTest {
        // foo は、runBlockingTest の TestCoroutineScope を利用する
        foo()
    }

    fun CoroutineScope.foo() {
        // launch の処理は、TestCoroutineScope で実行される
        launch {
            // ...
        }
    }
}
```

### 明示的な TestCoroutineScope の提供
多くの場合、依存関係の注入（DI）や、サービスロケータなどの、
別の手段で、CoroutineScope を提供する必要があるため、
runBlockingTest が生成する TestCoroutineScope を直接利用できない。

テストコードでは、TestCoroutineScope を明示的に生成して、利用できる。
TestCoroutineScope は、実行中の Coroutine と、未処理例外を監視するため、
ステートフルとなっている。
そのため、テストケース実行後に、必ず cleanupTestCoroutines を呼び出す必要がある。

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class ProvidingScopeTest {
    private val testScope = TestCoroutineScope()
    private lateinit var subject: Subject

    @BeforeEach
    fun beforeEach() {
        // コンストラクタでスコープを明示的にしてする
        subject = Subject(testScope)
    }

    @AfterEach
    fun afterEach() {
        // TestCoroutineScope を初期化
        testScope.cleanupTestCoroutines()
    }

    @Test
    fun testFoo() = runBlockingTest {
        // foo は、runBlockingTest の TestCoroutineScope を利用する
        subject.foo()
    }
}

class Subject(private val scope: CoroutineScope) {
    fun foo() {
        scope.launch {
            // 指定された スコープで実行
        }
    }
}
```

### 明示的な TestCoroutineDispatcher の提供
未処理例外を補足するために、TestCoroutineScope を利用することを推奨するが、
場合によっては、TestCoroutineDispatcher を利用する方が、簡単である。
たとえば、Dispatchers.setMain は、TestCoroutineScope を設定できないが、
テストでの Coroutine の制御は、TestCoroutineDispatcher を利用する。

TestCoroutineScope と TestCoroutineDispatcher の主な違いは、
未処理例外の処理方法となる。
TestCoroutineDispatcher を利用すると、未処理例外は、通常の Coroutine 例外処理を行う。
TestCoroutineScope の場合は、常に TestCoroutineDispatcher を利用する。

テストでは、明示的に TestCoroutineScope を生成せずに、TestCoroutineDispatcher を使用できる。
この方法は、テスト対象クラスが、CoroutineDispatcher を指定できるが、
CoroutineScope を指定できないような場合に適している。

TestCoroutineDispatcher は、実行中の Coroutine を追跡するため、ステートフルである。
したがって、すべてのテストケース後に、cleanupTestCoroutines を呼び出す必要がある。

未処理例外をテスト失敗として検知する場合で、テスト対象のコードが複雑にならない場合は、
TestCoroutineScope を利用することを推奨する。
ただし、CoroutineScope を外部から受け取るようにすると、コードが複雑になる傾向がある。
そのような場合には、TestCoroutineDispatcher を提供するパターンを推奨する。

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class ProvidingDispatcherTest {
    private val testDispatcher = TestCoroutineDispatcher()

    @BeforeEach
    fun beforeEach() {
        // Main ディスパッチャ を TestCoroutineDispatcher に差し替える
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun afterEach() {
        // TestCoroutineDispatcher を初期化
        testDispatcher.cleanupTestCoroutines()
        // Main ディスパッチャを元に戻す
        Dispatchers.resetMain()
    }

    @Test
    fun testFoo() = runBlockingTest {
        // foo は、TestCoroutineDispatcher を利用する
        foo()
    }
}

fun foo() {
    MainScope().launch {
        // Dispatchers.setMain で設定したディスパッチャで実行
    }
}
```

### runBlockingTest 不使用時の、TestCoroutineScope および TestCoroutineDispatcher でのテスト
runBlockingTest を使わずに、TestCoroutineScope と TestCoroutineDispatcher を利用することができる。
複数のディスパッチャを導入するようなテストでは、ライブラリ制作者は、
runBlockingTest 利用の代替手段として提供することがある。

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class WithoutRunBlockingTest {
    @Test
    fun testFooWithAutoProgress() {
        val scope = TestCoroutineScope()
        scope.foo()
        // 保留中のすべてのタスクをすぐに実行し、仮想時間を最後の遅延まで進める。
        // 仮想時間の進行で、新しいタスクが登録された場合、このメソッドが戻る前に実行する。
        scope.advanceUntilIdle()
    }

    fun CoroutineScope.foo() {
        launch {
            println("Start foo")
            delay(1_000)
            println("Finish foo")
        }
    }
}
```

## withContext で時間制御
withContext(Dispatchers.IO) や、 withContext(Dispatchers.Default) のような処理は、
Coroutine 形式での処理では共通しているが、TestCoroutineDispatcher を利用することができない。

テスト対象で withContext の処理が遅延する場合、ディスパッチャを置き換えるため、
TestCoroutineDispatcher を設定する必要がある。

以下のようなテスト対象を呼び出す場合に、依存関係の注入、サービスロケータまたは、パラメータなどで、
TestCoroutineDispatcher を設定できる必要がある。

```kotlin
suspend fun veryExpensiveOne() = withContext(Dispatchers.Default) {
    delay(1_000)
    // 高価なテスト結果
    1
}
```

withContext 内のコードが、非常に単純な場合では、TestCoroutineDispatcher の提供は、
それほど重要ではないかもしれない。
veryExpensiveTwo 関数は、Dispatchers.Default のスレッド切替後、
TestCoroutineDispatcher と Dispatchers.Default で同様に動作する。
この関数の場合、withContext は、常に直接値を返すため、TestCoroutineDispatcher を挿入する必要がない。

```kotlin
suspend fun veryExpensiveTwo() = withContext(Dispatchers.Default) {
    // 高価なテスト結果
    2
}
```

テストコードは、withContext 処理で、遅延を制御したり、
複雑な処理の実行制御を行う必要がある場合、TestCoroutineDispatcher を提供する必要がある。
