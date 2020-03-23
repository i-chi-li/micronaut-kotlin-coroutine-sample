package micronaut.kotlin.coroutine.sample.coroutine

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LaunchTest {
    val log: Logger = LoggerFactory.getLogger("LaunchTest")

    // ログ出力用の Channel
    // 並行処理のため、log を直接使用すると、ログ出力順が前後する
    val logChannel = Channel<String>(100)

    // ログ出力用 Channel のキューをログに出力するデーモンスレッド処理
    val logJob = GlobalScope.launch {
        for (msg in logChannel) {
            log.info(msg)
        }
    }

    // Coroutine スコープ外で使用するデバッグログ出力の関数
    fun debug(msg: String, threadName: String) = runBlocking {
        logChannel.send("[$threadName] $msg")
    }

    // Coroutine スコープ内で使用するデバッグログ出力の拡張関数
    suspend fun CoroutineScope.debug(msg: String, threadName: String) {
        logChannel.send("[$threadName] $msg")
    }

    // Job のデバッグ出力を行う拡張関数
    fun Job.debug() {
        debug("実行中？ %5s, 完了？ %5s, キャンセル？ %5s".format(isActive, isCompleted, isCancelled), Thread.currentThread().name)
    }

    @Test
    fun testLaunch() {
        log.warn("Start doLaunch")
        // 任意のタイミングで処理を開始できるように設定
        val job = GlobalScope.launch(start = CoroutineStart.LAZY) {
            debug("Start launch", Thread.currentThread().name)
            try {
                delay(1000)
            } catch (e: CancellationException) {
                debug("Cancelled", Thread.currentThread().name)
                throw e
            }
            debug("Finish launch", Thread.currentThread().name)
        }

        // 完了(エラー時含む)時処理
        job.invokeOnCompletion {
            debug("invokeOnCompletion", Thread.currentThread().name)
            if (it == null) {
                // 正常完了時
                debug("正常終了", Thread.currentThread().name)
            } else {
                debug("異常終了[$it]", Thread.currentThread().name)
            }
        }
        job.debug()
        debug("処理開始を要求", Thread.currentThread().name)
        // 処理を開始
        job.start()
        // 処理が、開始前、完了および、異常終了していた場合、例外をスローする
        // 処理中であることを保証するための機能
        job.ensureActive()
        job.debug()
        debug("Cancelling ...", Thread.currentThread().name)
        // 処理をキャンセル
        job.cancel()
        job.debug()
        runBlocking {
            // この Coroutine ブロックは、Suspend 関数呼び出しのため
            // 待ち合わせ
            job.join()
            // 処理のキャンセルと待ち合わせ
            job.cancelAndJoin()
        }
        log.warn("Finish doLaunch")
    }
}
