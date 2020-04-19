@file:Suppress("MagicNumber")

package micronaut.kotlin.coroutine.sample

import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Creator
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensurePresent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.time.LocalTime
import javax.annotation.PostConstruct
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Controller("/async")
class AsynchronousController(
    private val requestDataContainer: RequestDataContainer,
    private val jobs: AsynchronousJobs,
    private val jobProcessManager: JobProcessManager
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
    @Produces(MediaType.APPLICATION_JSON)
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

        return "${LocalTime.now()} $result"
    }

    /**
     * Coroutine で ThreadLocal を利用するサンプル
     * Coroutine では、@RequestScope や、@ThreadScope が利用できないため、
     * その代替方法となる。
     *
     * curl -i http://localhost:8080/async/threadLocal
     */
    @Get("/threadLocal")
    @Produces(MediaType.APPLICATION_JSON)
    suspend fun threadLocal(): String {
        // リクエスト処理先頭で、必要な情報をコンテナに格納する。
        requestDataContainer.requestData = RequestData("foo", 10)
        requestDataContainer.authData = AuthData("token")
        // Coroutine コンテキストに変換
        val context = requestDataContainer.requestDataCoroutineContext

        // Coroutine コンテキストを Coroutine に渡すと、以後の子 Coroutine にも引き継がれる。
        // Suspend から復帰する時に、ThreadLocal の値を復元する。
        return withContext(context) {
            requestDataContainer.ensurePresent()
            // ThreadLocal からデータを取得
            val requestData = requestDataContainer.requestData
            log.info("Start global() [$requestData]")
            thread {
                log.info("Start thread")
                log.info(requestData.toString())
                log.info("Finish thread")
            }
            delay(100)

            // GlobalScope は、親 Coroutine から Coroutine コンテキストを引き継がないため、
            // 明示的に Coroutine コンテキストを渡す必要がある。
            GlobalScope.launch(context) {
                log.info("Start launch")
                // jobs 自体はインジェクトされ、requestDataContainer も内部でインジェクトしている。
                // Coroutine コンテキストを渡さない場合、異なるスレッドのため、
                // 上で渡した ThreadLocal の値は当然取得できない。
                jobs.doJobWithData()
                log.info("Finish launch")
            }

            delay(500)
            requestDataContainer.ensurePresent()
            // ThreadLocal の値を入れ替えても、他の Coroutine へは伝播しない。
            // これは、この処理が完了した後、別のリクエストが来た場合に、
            // 前のリクエストと同じスレッドで処理が開始されても、
            // ThreadLocal の値を設定した時に、処理中の別の Coroutine には影響しない事を意味する。
            requestDataContainer.requestData = RequestData("bar", 20)
            // 当然、値の中身を変更すれば、他の Coroutine でも変更が反映される。
            requestData.name = "foobar"
            requestData.age = 30
            log.info("Finish global() [$requestData]")
            "OK"
        }
    }

    /**
     * 処理を同期的および、非同期的に実行を切り替える方法および、並行処理数を制御する方法
     *
     * curl -i http://localhost:8080/async/asyncSync
     * curl -i http://localhost:8080/async/asyncSync?async=true
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Get("/asyncSync")
    @Produces(MediaType.APPLICATION_JSON)
    @Suppress("TooGenericExceptionCaught")
    suspend fun asyncSyncJob(async: Boolean?): String = coroutineScope {
        val requestId = (0..10000).random()
        // 非同期呼び出しフラグ
        val asyncFlag = async ?: false
        log.info("Start asyncSyncJob(async: $asyncFlag)[requestId: $requestId]")

        val result = if (asyncFlag) {
            // 非同期呼び出しの場合
            log.info("Async Send[$requestId]")
            // 非同期処理依頼用のメッセージを生成
            val asyncMessage = AsyncProcessMessage(requestId, ProcessInputData(requestId))
            // 非同期処理依頼実行
            jobProcessManager.sendChannel.send(asyncMessage)
            "Async Process Successful"
        } else {
            // 同期呼び出しの場合
            log.info("Sync Send[$requestId]")
            // 処理結果受け取り用オブジェクト生成
            val response = CompletableDeferred<ProcessResultData>()
            // 同期処理依頼用のメッセージを生成
            val syncMessage = SyncProcessMessage(requestId, ProcessInputData(requestId), response)
            // 同期処理依頼実行
            jobProcessManager.sendChannel.send(syncMessage)
            try {
                // タイムアウト付きで、待機をする
                withTimeout(2000) {
                    // 同期処理完了まで、待機し、値を返す
                    response.await().toString()
                }
            } catch (e: TimeoutCancellationException) {
                // タイムアウトが発生した場合
                log.info("Timeout")
                "Timeout: ${e.message}"
            } catch (e: Throwable) {
                // 処理で例外が発生した場合
                log.info("Error")
                e.printStackTrace()
                e.message ?: "Error"
            }
        }

        log.info("Finish asyncSyncJob(async: $asyncFlag)[requestId: $requestId]")
        result
    }
}


// -----------------------------------------------------------------
// Coroutine で ThreadLocal を利用するサンプル
// -----------------------------------------------------------------


/**
 * 非同期処理
 */
@Singleton
class AsynchronousJobs(
    // インジェクトする
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
        // 内部で ThreadLocal を利用して値を取得している。
        val data = requestDataContainer.requestData
        log.info("before data: $data")
        // Suspend 関数を呼び出す。
        delay(10000)
        // Suspend 関数後は、異なるスレッドの場合がある
        // 同一 Coroutine であれば、スレッドが異なっても、
        // Coroutine コンテキストを渡しておけば、ThreadLocal の値を自動的に復元する
        val data2 = requestDataContainer.requestData
        // Suspend 関数呼び出し前後で、ThreadLocal の値が変わらないことを確認する
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
        set(data) = _requestData.set(data)
        get() = _requestData.get()

    // 認証データをスレッドローカルで保持
    private val _authData = ThreadLocal<AuthData>()

    // 認証データのプロパティ
    var authData: AuthData
        // スレッドローカルの値を設定・取得する。
        set(data) = _authData.set(data)
        get() = _authData.get()

    // 保持しているデータの Coroutine コンテキストを生成する
    val requestDataCoroutineContext: CoroutineContext
        get() = _requestData.asContextElement(requestData) +
            _authData.asContextElement(authData)

    /**
     * ThreadLocal の値を Coroutine コンテキストで設定していることを保証するチェック
     * Coroutine コンテキストを設定していない場合は、IllegalStateException が発生する。
     */
    suspend fun ensurePresent() {
        // ThreadLocal の値が、明示的に復元されていない場合、例外が発生する。
        _requestData.ensurePresent()
        _authData.ensurePresent()
    }
}

/**
 * リクエストデータ
 */
class RequestData(
    var name: String,
    var age: Int
) {
    private val id: Int = (0..1000).random()

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


// -----------------------------------------------------------------
// 処理を同期的および、非同期的に実行を切り替える方法および、並行処理数を制御する方法
// -----------------------------------------------------------------

/**
 * 処理に入力するデータ
 * @property inputData 入力データ
 */
data class ProcessInputData(val inputData: Int)

/**
 * 処理の結果データ
 *
 * @property resultData 結果データ
 */
data class ProcessResultData(val resultData: String)

/**
 * 処理依頼用メッセージのシールド基底クラス
 *
 * @property messageId メッセージID
 */
sealed class ProcessMessage(val messageId: Int) {
    override fun toString(): String {
        return "$messageId ${javaClass.simpleName}"
    }
}

/**
 * 非同期処理依頼用メッセージ
 * 戻り値は受け取らない
 *
 * @property messageId メッセージID
 * @property inputData 入力データ
 */
class AsyncProcessMessage(
    messageId: Int,
    val inputData: ProcessInputData
) : ProcessMessage(messageId)

/**
 * 同期処理依頼用メッセージ
 *
 * @property messageId メッセージID
 * @property inputData 処理に入力するデータ
 * @property response 処理から結果を受け取るためのインスタンス
 */
class SyncProcessMessage(
    messageId: Int,
    val inputData: ProcessInputData,
    val response: CompletableDeferred<ProcessResultData>
) : ProcessMessage(messageId)

/**
 * ジョブ処理管理
 *
 * ジョブをキューで受け付ける。
 *
 * @property coroutineContext Coroutine コンテキスト
 */
@Singleton
class JobProcessManager(
    override val coroutineContext: CoroutineContext
) : CoroutineScope {

    /**
     * コンストラクタ
     * ＠Creator アノテーションを付与することで、インジェクトをする場合に、このコンストラクタを使用する。
     */
    @Creator
    constructor() : this(EmptyCoroutineContext)

    /**
     * キュー容量。デフォルトは、10
     */
    @Value("\${job.process.capacity:10}")
    var capacity: Int = 10
        internal set

    /**
     * 処理の並行数。デフォルトは、2
     */
    @Value("\${job.process.paralleNumber:2}")
    var parallelNumber: Int = 2
        internal set

    /**
     * 処理依頼送信用 Channel
     */
    lateinit var sendChannel: SendChannel<ProcessMessage>
        private set

    /**
     * ロガー
     */
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * 主要な処理をするクラスを生成
     */
    private val jobProcessor = JobProcessor("jobProcessor-0")

    /**
     * インスタンス生成後処理
     * actor の初期化は、ここで行う。
     * capacity などの値に @Value で指定した値が代入されるタイミングは、
     * プロパティを直接初期化する後になるため、それより後のタイミングで呼び出されるここで行う必要がある。
     */
    @PostConstruct
    @ExperimentalCoroutinesApi
    @OptIn(ObsoleteCoroutinesApi::class)
    fun initialize() {
        log.info("Start initialize")
        // 処理依頼送信用 Channel の初期化
        // capacity は、キューの容量
        sendChannel = actor(capacity = capacity) {
            log.info("Initialize actor [capacity: $capacity]")
            // parallelNumber は、キューの並列処理数
            repeat(parallelNumber) {
                log.info("Create consumer $it")
                launch(Dispatchers.IO) {
                    log.info("Start actor launch $it")
                    consumeEach { message ->
                        // キューを受信した場合
                        log.info("Receive: $message")
                        when (message) {
                            // 非同期処理依頼メッセージの場合
                            is AsyncProcessMessage -> doAsyncProcessing(message.inputData)
                            // 同期処理依頼メッセージの場合
                            is SyncProcessMessage -> doSyncProcessing(message.inputData, message.response)
                        }
                    }
                    log.info("Finish actor launch $it")
                }
            }
        }
        log.info("Finish initialize")
    }

    /**
     * 同期処理
     *
     * @param inputData 入力データ
     * @param response 結果返却用
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun doSyncProcessing(
        inputData: ProcessInputData, response: CompletableDeferred<ProcessResultData>) {
        log.info("Start doSyncProcessing($inputData)")
        try {
            // 主要な処理を実行
            val result = jobProcessor.doProcess(inputData)
            log.info("result: $result")
            // 処理結果返信
            response.complete(result)
        } catch (e: Throwable) {
            // 例外発生時は、発生した例外を処理依頼元へ返す。
            response.completeExceptionally(e)
        } finally {
            log.info("Finish doSyncProcessing($inputData)")
        }
    }

    /**
     * 非同期処理
     *
     * @param inputData 入力データ
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun doAsyncProcessing(inputData: ProcessInputData) {
        log.info("Start doAsyncProcessing($inputData)")
        try {
            // 主要な処理を実行
            // 処理結果は、返さない
            val result = jobProcessor.doProcess(inputData)
            log.info("result: $result")
        } catch (e: Throwable) {
            // 例外が発生した場合は、ここで処理をする。
            e.printStackTrace()
        } finally {
            log.info("Finish doAsyncProcessing($inputData)")
        }
    }
}

/**
 * メイン処理を定義
 *
 * @property processorName 処理名
 */
class JobProcessor(private val processorName: String) {
    /** ロガー */
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * メイン処理実行
     *
     * 通常の関数として定義可能だが、Suspend 関数にしておいた方が、便利な関数を利用できる
     *
     * @param inputData 入力データ
     * @return 処理結果を返す。
     */
    suspend fun doProcess(inputData: ProcessInputData): ProcessResultData {
        log.info("Start doProcess($inputData)")
        // ランダムに待機
        delay((1000..5000).random().toLong())
        // 処理結果データを生成
        val result = ProcessResultData("$processorName, inputData: ${inputData.inputData}")
        log.info("Finish doProcess($inputData): $result")
        // 処理結果を返す
        return result
    }
}
