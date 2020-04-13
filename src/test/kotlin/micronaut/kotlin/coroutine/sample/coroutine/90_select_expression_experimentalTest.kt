package micronaut.kotlin.coroutine.sample.coroutine

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.onReceiveOrNull
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.selectUnbiased
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class select_expression_experimentalTest {
    var log: Logger = LoggerFactory.getLogger("sample")

    @OptIn(ExperimentalCoroutinesApi::class)
    fun CoroutineScope.fizz() = produce<String> {
        while (true) {
            delay(300)
            send("Fizz")
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun CoroutineScope.buzz() = produce<String> {
        while (true) {
            delay(500)
            send("Buzz!")
        }
    }

    suspend fun selectFizzBuzz(fizz: ReceiveChannel<String>, buzz: ReceiveChannel<String>) {
        select<Unit> {
            fizz.onReceive {
                log.info("fizz -> '$it'")
            }
            buzz.onReceive {
                log.info("buzz -> '$it'")
            }
        }
    }

    @Test
    fun test1() = runBlocking<Unit> {
        log = LoggerFactory.getLogger("test1")
        val fizz = fizz()
        val buzz = buzz()
        repeat(7) {
            selectFizzBuzz(fizz, buzz)
        }
        coroutineContext.cancelChildren()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun selectAorB(a: ReceiveChannel<String>, b: ReceiveChannel<String>): String =
        select {
            a.onReceiveOrNull().invoke {
                log.info("Receive 'a'")
                if (it == null) {
                    "Channel 'a' is closed"
                } else {
                    "a -> '$it'"
                }
            }
            b.onReceiveOrNull().invoke {
                log.info("Receive 'b'")
                if (it == null) {
                    "Channel 'b' is closed"
                } else {
                    "b -> '$it'"
                }
            }
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun test2() = runBlocking<Unit> {
        log = LoggerFactory.getLogger("test2")
        val a = produce<String> {
            repeat(4) {
                log.info("Send 'a'")
                send("Hello $it")
            }
        }
        val b = produce<String> {
            repeat(4) {
                log.info("Send 'b'")
                send("World $it")
            }
        }
        repeat(8) {
            log.info("main: ${selectAorB(a, b)}")
        }
        log.info("Cancel")
        coroutineContext.cancelChildren()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun selectUnbiasedAorB(a: ReceiveChannel<String>, b: ReceiveChannel<String>): String =
        selectUnbiased {
            a.onReceiveOrNull().invoke {
                log.info("Receive 'a'")
                if (it == null) {
                    "Channel 'a' is closed"
                } else {
                    "a -> '$it'"
                }
            }
            b.onReceiveOrNull().invoke {
                log.info("Receive 'b'")
                if (it == null) {
                    "Channel 'b' is closed"
                } else {
                    "b -> '$it'"
                }
            }
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun test3() = runBlocking<Unit> {
        log = LoggerFactory.getLogger("test3")
        val a = produce<String> {
            repeat(4) {
                log.info("Send 'a'")
                send("Hello $it")
            }
        }
        val b = produce<String> {
            repeat(4) {
                log.info("Send 'b'")
                send("World $it")
            }
        }
        repeat(8) {
            log.info("main: ${selectUnbiasedAorB(a, b)}")
        }
        log.info("Cancel")
        coroutineContext.cancelChildren()
    }
}
