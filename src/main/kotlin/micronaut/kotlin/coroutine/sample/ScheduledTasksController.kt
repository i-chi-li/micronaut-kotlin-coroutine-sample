package micronaut.kotlin.coroutine.sample

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import io.micronaut.scheduling.annotation.Scheduled
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SingletonTasks {
    private val log = LoggerFactory.getLogger(this.javaClass)
    private val id = (0..1000).random()

    /**
     * 5 分固定間隔で実行するタスク
     */
    @Scheduled(fixedRate = "5m")
    internal fun everyFiveMinutes() = runBlocking {
        log.info("Executing everyFiveMinutes() [instance: $id]")
        delay(120_000)
        log.info("Finish everyFiveMinutes() [instance: $id]")
    }

    /**
     * 前回のタスク実行から 5 分後に実行するタスク
     */
    @Scheduled(fixedDelay = "5m")
    internal fun fiveMinutesAfterLastExecution() = runBlocking {
        log.info("Executing fiveMinutesAfterLastExecution() [instance: $id]")
        delay(120_000)
        log.info("Finish fiveMinutesAfterLastExecution() [instance: $id]")
    }

    /**
     * 毎週月曜日、午前 10:15:00 （サーバ時間）に実行するタスク
     */
    @Scheduled(cron = "0 15 10 ? * MON")
    internal fun everyMondayAtTenFifteenAm() {
        log.info("Executing everyMondayAtTenFifteenAm() [instance: $id]")
    }

    /**
     * application.yml に設定した値で実行間隔を設定するタスク
     */
    @Scheduled(fixedDelay = "\${schedule.conf.fixed}")
    internal fun fromConfig() {
        log.info("Executing fromConfig() [instance: $id]")
    }

    /**
     * サーバ起動 30 秒後に初回のタスクを実行し、以後は、固定間隔でタスクを実行するタスク
     */
    @Scheduled(initialDelay = "30s", fixedDelay = "1m")
    internal fun initAndFixed() {
        log.info("Executing initAndFixed() [instance: $id]")
    }
}

@Controller("/tasks")
class ScheduledTasksController(
    @Named(TaskExecutors.SCHEDULED)
    private var taskScheduler: TaskScheduler
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    @Get("/dynamic")
    @Produces("${MediaType.TEXT_PLAIN}; ${MediaType.CHARSET_PARAMETER}=utf-8")
    fun dynamic(): String {
        val task1 = taskScheduler.schedule(Duration.ofSeconds(0), Callable {
            log.info("Start dynamic task")
            "Finish dynamic task"
        })

        val task2 = taskScheduler.schedule(Duration.ofSeconds(3), dynamicTaskAnonymous)
        return "task1: ${task1.get()}, task2: ${task2.get(5, TimeUnit.SECONDS)}"
    }
}

val dynamicTaskAnonymous = Callable {
    val log = LoggerFactory.getLogger("dynamicTaskAnonymous")
    log.info("Start dynamicTaskAnonymous")
    runBlocking {
        delay(1000)
    }
    "Finish dynamicTaskAnonymous"
}
