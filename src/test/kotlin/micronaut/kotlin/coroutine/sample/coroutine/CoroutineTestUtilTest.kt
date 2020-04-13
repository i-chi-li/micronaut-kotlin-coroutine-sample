package micronaut.kotlin.coroutine.sample.coroutine

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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

@OptIn(ExperimentalCoroutinesApi::class)
class RunBlockingTest {
    private lateinit var log: Logger

    @Test
    fun testSuspendFunctionNormal() = runBlocking {
        log = LoggerFactory.getLogger("RunBlockingTest-Normal")
        suspendFunction()
    }

    @Test
    fun testSuspendFunctionFast() = runBlockingTest {
        log = LoggerFactory.getLogger("RunBlockingTest-Fast")
        suspendFunction()
    }

    suspend fun suspendFunction() {
        log.info("Start suspendFunction")
        delay(1_000)
        log.info("Finish suspendFunction")
    }
}

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
        log.info("result: $result")
        log.info("Finish foo")
    }
}

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

suspend fun veryExpensiveOne() = withContext(Dispatchers.Default) {
    delay(1_000)
    // 高価なテスト結果
    1
}

suspend fun veryExpensiveTwo() = withContext(Dispatchers.Default) {
    // 高価なテスト結果
    2
}
