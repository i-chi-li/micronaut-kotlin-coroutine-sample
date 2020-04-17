package micronaut.kotlin.coroutine.sample

import io.micronaut.context.ApplicationContext
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.time.LocalTime
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext

@Controller("/async")
class AsynchronousController(
    private val applicationContext: ApplicationContext,
    private val requestDataContainer: RequestDataContainer,
    private val jobs: AsynchronousJobs
) {

    /** ロガー */
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * グローバルスコープでジョブを実行
     *
     * curl -i http://localhost:8080/async/globalScope
     * curl -i http://localhost:8080/async/globalScope?sync=true
     */
    @Get("/globalScope")
    @Produces("${MediaType.APPLICATION_JSON}; ${MediaType.CHARSET_PARAMETER}=utf-8")
    suspend fun globalScope(sync: Boolean?): String {
        log.info("Start globalScope()")

        // ジョブ処理結果を待つフラグ
        val syncFlag = sync ?: false

        // 何かの処理をする
        delay(500)

        // グローバルスコープで、別スレッド処理を開始する
        val deferred = GlobalScope.async {
            log.info("Before Call doJob()")
            // タイムアウト設定
            // タイムアウトすると、実行中のジョブもキャンセルされる
            withTimeoutOrNull(20000) {
                jobs.doJob()
            }
            log.info("After Call doJob()")
            // 処理が完了した場合の戻り値
            "GlobalScope job successful"
        }

        // Kotlin では、if は、式のため、各ブロック最後の評価値を戻り値として返す。
        val result = if (syncFlag) {
            // ジョブの完了を待つ場合
            // タイムアウト設定
            // タイムアウトしても、実行中のジョブは続行する
            withTimeoutOrNull(20000) {
                // ジョブの完了を待ち、値を取得する。
                deferred.await()
            }
                // タイムアウトした場合に返す値
                ?: "TimeOuted"
        } else {
            // ジョブの完了を待たない場合
            ""
        }
        log.info("Finish globalScope()")

        return "${LocalTime.now().toString()} $result"
    }

    /**
     * グローバルスコープでジョブを実行
     *
     * curl -i http://localhost:8080/async/global
     */
    @Get("/global")
    @Produces("${MediaType.APPLICATION_JSON}; ${MediaType.CHARSET_PARAMETER}=utf-8")
    suspend fun global(): String {
        // リクエスト処理先頭で、必要な情報をコンテナに格納する。
        requestDataContainer.requestData = RequestData("foo", 10)
        requestDataContainer.authData = AuthData("token")
        val context = requestDataContainer.requestDataCoroutineContext

        return withContext(context) {
            val requestData = requestDataContainer.requestData
            log.info("Start global() [$requestData]")
            thread {
                log.info("Start thread")
                log.info(requestData.toString())
                log.info("Finish thread")
            }
            delay(100)

            GlobalScope.launch(context) {
                log.info("Start launch")
                jobs.doJobWithData()
                log.info("Finish launch")
            }

            delay(500)
            requestDataContainer.requestData = RequestData("bar", 20)
            requestData.name = "hoge"
            requestData.age = 30
            log.info("Finish global() [$requestData]")
            "OK"
        }
    }
}

/**
 * 非同期処理
 */
@Singleton
class AsynchronousJobs(
    private val requestDataContainer: RequestDataContainer
) {
    /** ロガー */
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * ジョブ実行
     */
    suspend fun doJob() {
        log.info("Start doJob()")
        delay(10000)
        log.info("Finish doJob()")
    }

    /**
     * ジョブ実行
     */
    suspend fun doJobWithData() {
        log.info("Start doJobWithData()")
        val data = requestDataContainer.requestData
        log.info("before data: $data")
        delay(10000)
        val data2 = requestDataContainer.requestData
        log.info("after data: $data")
        log.info("after data2: $data2")
        log.info("Finish doJobWithData()")
    }
}

/**
 * スレッドローカルで値を保持する入れ物
 */
@Singleton
class RequestDataContainer {
    // リクエストデータをスレッドローカルで保持
    private val _requestData = ThreadLocal<RequestData>()

    // リクエストデータのプロパティ
    var requestData: RequestData
        // スレッドローカルの値を設定・取得する。
        set(data: RequestData) = _requestData.set(data)
        get() = _requestData.get()

    // 認証データをスレッドローカルで保持
    private val _authData = ThreadLocal<AuthData>()

    // 認証データのプロパティ
    var authData: AuthData
        // スレッドローカルの値を設定・取得する。
        set(data: AuthData) = _authData.set(data)
        get() = _authData.get()

    // 保持しているデータの Coroutine コンテキストを生成する
    val requestDataCoroutineContext: CoroutineContext
        get() = _requestData.asContextElement(requestData) +
            _authData.asContextElement(authData)
}

/**
 * リクエストデータ
 */
class RequestData(
    var name: String,
    var age: Int
) {
    val id: Int = (0..1000).random()

    override fun toString(): String {
        return "id: $id, name: $name, age: $age"
    }
}

/**
 * 認証データ
 */
data class AuthData(
    val token: String
)
