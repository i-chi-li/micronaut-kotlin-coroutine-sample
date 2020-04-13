package micronaut.kotlin.coroutine.sample.coroutine

import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/*
runBlockingTest ビルダは、テスト用のビルダ。
以下のような機能を持つ。
- 通常の Suspend 機能を時間を自動的に進める
- 複数の Coroutine をテストするために、明示的に時間を制御する。
- launch または、async コードブロックの熱心な実行
- Coroutine の一時停止、手動で進行や、再開をする
- キャッチされなかった例外をテスト失敗として報告する
 */

class WithTimeoutTest {
    val log: Logger = LoggerFactory.getLogger("WithTimeoutTest")

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testTimeout() = runBlockingTest {

        withTimeout(500) {
            repeat(10) {
                log.info("I'm sleeping $it")
                delay(5)
            }
        }
    }
}
