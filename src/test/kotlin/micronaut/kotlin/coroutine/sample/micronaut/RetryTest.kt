package micronaut.kotlin.coroutine.sample.micronaut

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.retry.annotation.CircuitBreaker
import io.micronaut.retry.annotation.Retryable
import io.micronaut.retry.event.CircuitClosedEvent
import io.micronaut.retry.event.CircuitOpenEvent
import io.micronaut.retry.event.RetryEvent
import io.micronaut.retry.event.RetryEventListener
import io.micronaut.test.extensions.kotest.annotation.MicronautTest
import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import javax.inject.Inject
import javax.inject.Singleton

private val log = LoggerFactory.getLogger("RetryTest")

@MicronautTest
class RetryTest(
    applicationContext: ApplicationContext
) : StringSpec({
    "3 retry 1 s delay (Default) Setting" {
        log.info("Start 3 retry 1 s delay (Default) Setting")
        // 2 回失敗して、3 回目で成功する
        // 1 秒間隔で再試行する
        val retryBean = applicationContext.getBean(RetryBean::class.java)
        retryBean.defaultSetting(3)
        log.info("Finish 3 retry 1 s delay (Default) Setting")
    }
    "multiplier Setting" {
        log.info("Start multiplier Setting")
        // multiplier を 2 に設定
        val retryBean = applicationContext.getBean(RetryBean::class.java)
        retryBean.retryMultiplier(3)
        log.info("Finish multiplier Setting")
    }
    "5 retry 2 s delay Setting" {
        log.info("Start 5 retry 2 s delay Setting")
        // 4 回失敗して、5 回目で成功する
        // 2 秒間隔で再試行する
        val retryBean = applicationContext.getBean(RetryBean::class.java)
        retryBean.retry5delay2(5)
        log.info("Finish 5 retry 2 s delay Setting")
    }
    "Configuration Setting" {
        log.info("Start Configuration Setting")
        // application.yml の設定値を参照する
        val retryBean = applicationContext.getBean(RetryBean::class.java)
        retryBean.retryFromConfig(4)
        log.info("Finish Configuration Setting")
    }
    "Reactive Retry (RxJava)" {
        log.info("Start Reactive Retry (RxJava)")
        val retryBean = applicationContext.getBean(RetryBean::class.java)
        val flowable = retryBean.retryFlowable(3)
//        flowable.blockingForEach { log.info("number: $it") }
        for (number in flowable.blockingIterable()) {
            log.info("number: $number")
        }
        log.info("Finish Reactive Retry (RxJava)")
    }
    "Reactive Retry (CompletableFuture)" {
        log.info("Start Reactive Retry (CompletableFuture)")
        val retryBean = applicationContext.getBean(RetryBean::class.java)
        val future = retryBean.retryFuture(3)
        log.info("result: ${future.join()}")
        log.info("Finish Reactive Retry (CompletableFuture)")
    }
    "Reactive Retry (Coroutine)" {
        log.info("Start Reactive Retry (Coroutine)")
        val retryBean = applicationContext.getBean(RetryBean::class.java)
        val flow = retryBean.retryCoroutine(3)
        flow.collect { log.info("result: $it") }
        log.info("Finish Reactive Retry (Coroutine)")
    }
    "Reactive Retry (Circuit Breaker)" {
        log.info("Start Reactive Retry (Circuit Breaker)")
        val retryBean = applicationContext.getBean(RetryBean::class.java)
        // リトライオーバーとなる回数を指定し、失敗後、即座に 2 回のアクセスを行うようにする。
        repeat(3) {
            val exception = shouldThrowExactly<IllegalArgumentException> {
                // 初回アクセス時のリトライオーバー以後、
                // reset に設定した時間を経過しないと、
                // 再アクセス時に、リトライ無しで即座に 初回と同じ例外を再スローする。
                retryBean.circuitBreaker(3)
            }
            log.info("exception: ${exception.message}")
            delay(200)
        }
        // reset に設定した時間が経過するのを待機
        delay(1000)
        // 再びアクセスするとリトライ有りで成功する
        val result = retryBean.circuitBreaker(2)
        log.info("result: $result")

        log.info("Finish Reactive Retry (Circuit Breaker)")
    }
})

// リトライテスト対象 Bean
@Prototype
open class RetryBean {
    // 処理毎に加算するカウンタ
    private var count = 1

    // デフォルト設定
    @Retryable
    open fun defaultSetting(successNumber: Int): Int {
        return getNumber(successNumber)
    }

    // multiplier 2 設定
    @Retryable(multiplier = "2", attempts = "4", delay = "1s")
    open fun retryMultiplier(successNumber: Int): Int {
        return getNumber(successNumber)
    }

    // 5 回 2 秒間隔
    @Retryable(attempts = "5", delay = "2s")
    open fun retry5delay2(successNumber: Int): Int {
        return getNumber(successNumber)
    }

    // 設定ファイルの値を利用
    // application.yml ファイルに値を定義する。
    // デフォルト値は、設定キーの後にコロン（:）を付けて設定できる。
    // デフォルト値にコロン（:）を含む場合は、バッククォートで囲む「"\${foo.url:`http://foo.net:8080`}"」
    // Kotlin の場合は、文字列テンプレート無効化のために、"$" を "\$"のようにエスケープする必要がある。
    @Retryable(attempts = "\${retry.test.attempts:0}", delay = "\${retry.test.delay:2s}")
    open fun retryFromConfig(successNumber: Int): Int {
        return getNumber(successNumber)
    }

    // リアクティブ（RxJava）の場合
    @Retryable(attempts = "2", delay = "100ms")
    open fun retryFlowable(successNumber: Int): Flowable<Int> = Flowable
        .generate<Int> { emitter ->
            // 値を取得
            emitter.onNext(getNumber(successNumber))
            // 値が一つ取得できたら終了
            emitter.onComplete()
        }
        .subscribeOn(Schedulers.io())

    // リアクティブ（Java 標準 CompletableFuture）の場合
    @Retryable(attempts = "2", delay = "100ms")
    open fun retryFuture(successNumber: Int): CompletableFuture<Int> = CompletableFuture
        .supplyAsync { getNumber(successNumber) }

    // リアクティブ（Coroutine）の場合
    @Retryable(attempts = "2", delay = "100ms")
    open fun retryCoroutine(successNumber: Int): Flow<Int> = flow {
        emit(getNumber(successNumber))
    }

    // 遮断機（Circuit Breaker）
    @CircuitBreaker(attempts = "1", delay = "100ms", reset = "1s")
    open fun circuitBreaker(successNumber: Int): Int {
        return getNumber(successNumber)
    }

    // 任意のアクセス回数で成功させる関数
    private fun getNumber(successNumber: Int): Int {
        // 呼び出し毎にインスタンス変数のカウンタを加算
        val current = count++
        log.info("successNumber: $successNumber, current: $current")
        if (current % successNumber != 0) {
            // カウンタが成功番号で割り切れない場合
            throw IllegalArgumentException("Exception current: $current")
        }
        return current
    }
}

// リトライが発生した場合のイベントを処理する。
@Singleton
class CustomRetryEventListener : RetryEventListener {
    val log = LoggerFactory.getLogger(this.javaClass)
    override fun onApplicationEvent(event: RetryEvent) {
        log.info("event.source: ${event.source}" +
            ", overallDelay: ${event.retryState.overallDelay}" +
            ", currentAttempt: ${event.retryState.currentAttempt()}",
            ", ${event.throwable.javaClass.simpleName}")
    }
}

/**
 * @CircuitBreaker でリトライオーバーになった場合のイベントを処理する
 */
@Singleton
class CircuitOpenEventListener : ApplicationEventListener<CircuitOpenEvent> {
    private val log = LoggerFactory.getLogger(this.javaClass)

    @Inject
    private lateinit var publisher: ApplicationEventPublisher
    override fun onApplicationEvent(event: CircuitOpenEvent) {
        log.info("event.source: ${event.source}" +
            ", currentAttempt: ${event.retryState.currentAttempt()}" +
            ", overallDelay: ${event.retryState.overallDelay}")

        // この処理に意味は無い。
        // CircuitClosedEvent を発生させるためだけの処理。
        // 結局、任意のタイミングでリトライオーバー状態を回復する方法はわからなかった。
        publisher.publishEvent(CircuitClosedEvent(event.source))
        // このメソッドでは、CircuitClosedEvent が発生しないようだ。
//        event.retryState.close(IllegalArgumentException())
    }
}

// @CircuitBreaker でのリトライオーバー状態から回復した場合に発生するイベントを処理する。
// ・・・と思ったが、発生しなかった。現時点では、不明。
@Singleton
class CircuitClosedEventListener : ApplicationEventListener<CircuitClosedEvent> {
    val log = LoggerFactory.getLogger(this.javaClass)
    override fun onApplicationEvent(event: CircuitClosedEvent) {
        log.info("event.source: ${event.source}")
    }
}
