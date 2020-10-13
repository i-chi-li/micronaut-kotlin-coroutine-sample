package micronaut.kotlin.coroutine.sample

import io.micronaut.cache.annotation.Cacheable
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.event.ServerShutdownEvent
import io.micronaut.scheduling.DefaultTaskExceptionHandler
import io.micronaut.scheduling.annotation.Scheduled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PostConstruct
import javax.inject.Singleton
import kotlin.random.Random

/**
 * スケジュールタスク（SQS および Coroutine を含む）コントローラ
 *
 * このクラスでは、スケジュールタスクの挙動や、SQS および、 Coroutine を
 * 一緒に使用した場合の検証を行う。
 * 事前に、SQS キューおよび、S3 バケットを作成しておく必要がある。
 *
 *
 * 以下のように起動するとこのクラスが有効となる
 * gradlew --no-build-cache clean run -Pprofile=＜AWS プロファイル＞ \
 * -Pregion=ap-northeast-1 \
 * -Psqs=＜SQS キュー名＞ \
 * -Pbucket=＜S3 バケット名＞ \
 * -Papi=https://httpbin.org/post
 *
 * 例：
 * gradlew --no-build-cache clean run -Pprofile=sandbox -Pregion=ap-northeast-1 \
 * -Psqs=aaa.fifo -Pbucket=sample-bucket-202010121533abc -Papi=https://httpbin.org/post
 *
 * S3 へデータファイルを作成する方法（一度作成すれば良い）
 * curl http://localhost:8080/sqs/s3
 *
 * SQS キューを登録する方法（キュー処理後削除される）
 * curl http://localhost:8080/sqs/sqs
 *
 * シャットダウンイベントを発生させる（Docker コンテナを停止する）
 * -t オプションは、コンテナ強制停止までの猶予時間（秒）
 * docker stop -t 120 ＜コンテナID＞
 */
@Singleton
@Requires(beans = [AwsInfrastructure::class])
class ScheduledTasksSQSCoroutine(
    private val awsInfrastructure: AwsInfrastructure
) {
    companion object {
        /** ロガー */
        private val log: Logger = LoggerFactory.getLogger(this::class.java)

        /** SQS キューがなかった場合の次のキュー取得までのミリ秒 */
        const val PROCESS_RATE: Long = 60 * 1_000
        /** SQS メッセージの可視性タイムアウトを更新する間隔（ミリ秒） */
        const val CHANGE_VISIBILITY_CYCLE_MILLISECONDS: Long = 20_000
        /** SQS メッセージの可視性タイムアウト */
        const val VISIBILITY_SECONDS: Int = 30
    }

    private val s3Client: S3Client = awsInfrastructure.s3Client
    private val sqsClient: SqsClient = awsInfrastructure.sqsClient
    private val sqsQueueUrl: String = awsInfrastructure.getQueueUrl(awsInfrastructure.sqsQueueName)
    private val apiClient: ApiClient = awsInfrastructure.apiClient

    /** 次の SQS キュー取得日時（ミリ秒） */
    private var nextProcessTime: Long = 0L

    /**
     *  SQS キュー処理中フラグ
     *  シャットダウン処理で、タスク実行中かを判定するために利用する
     */
    var nowProcess: AtomicBoolean = AtomicBoolean(false)

    /**
     * SQS キュー処理
     * シャットダウン処理で、タスクをキャンセルするために利用する
     */
    private var job: Job? = null

    /**
     * 前タスク完了後、指定間隔で実行し、例外をスローするタスク
     *
     * 例外発生後、次の起動タイミングで再び実行される。
     * Micronaut が異常終了することはない。
     */
//    @Scheduled(fixedDelay = "5s")
//    internal fun oneMinutesAfterException(): Unit = runBlocking {
//        if (!nowProcess.get()) {
//            return@runBlocking
//        }
//
//        log.info("【開始】例外が発生するタスク")
//        when (Random.nextInt(3)) {
//            0 -> {
//                throw SubCustomException("サブカスタム例外が発生しました")
//            }
//            1 -> {
//        throw CustomException("カスタム例外が発生しました")
//            }
//            else -> {
//                throw IllegalArgumentException("何かのエラーが発生しました")
//            }
//        }
//    }

    /**
     * 前タスク完了後、指定間隔で実行し、例外が発生するタスク
     */
    @Scheduled(fixedDelay = "500ms")
    internal fun withSqs() = runBlocking {
        if (nextProcessTime > System.currentTimeMillis()) {
            // 現在日時が次回実行日時に満たない場合
            return@runBlocking
        }
        log.info("SQS キュー処理タスク開始")
        nowProcess.set(true)
        job = launch() {

            runCatching {
                // 処理を一括してキャンセルできるように親 Job を定義する
                // SQS キューからメッセージリストを取得する
                val messages = getSqsMessages()

                if (messages.isEmpty()) {
                    // SQS キューメッセージが無かった場合
                    // 一定期間後まで、SQS キューを取得しないように設定する
                    nextProcessTime = System.currentTimeMillis() + PROCESS_RATE
                    log.info("キューがありませんでした。次の SQS キュー受信時間: $nextProcessTime")
                }

                // メッセージリストを処理する
                messages.forEach { message ->
                    // S3 のファイルキーを取得する
                    // SQS キュー本文には、S3 のファイルキーが 1 つだけ入っている前提
                    val s3FileKey = message.body()

                    // S3 からファイルの内容を取得する
                    val content = getS3FileContent(s3FileKey)

                    // 処理対象のデータを行で分割する
                    val dataList = content.lines()
                    // データを登録したキューを取得する
                    val receiveChannel: ReceiveChannel<String> = getChannel(dataList, this)

                    // メッセージ処理を非同期で起動する
                    val jobMessageProcess = launchMessageProcess(receiveChannel)

                    // メッセージ処理ジョブが処理中の間、
                    // SQS メッセージの可視性タイムアウトを定期的に延長する処理を非同期で起動する
                    launchChangeMessageVisibility(jobMessageProcess, message.receiptHandle())

                    // SQS キューの処理が完了するするまで待機する
                    // SQS キューの削除の順番を担保するために必要となる
                    jobMessageProcess.join()

                    log.info("SQS キューを削除する ${message.messageId()}")
                    // SQS キューを削除する
                    deleteSqsQueue(message)
                }
            }
                .onFailure { throwable ->
                    // 例外が発生した場合
                    log.error("SQS キュー処理タスクで例外が発生しました ${throwable.message}")
                    // 例外が発生した場合は、次の処理まで間を開ける
                    nextProcessTime = System.currentTimeMillis() + PROCESS_RATE
                }
                .also {
                    nowProcess.set(false)
                    log.info("SQS キュー処理タスク終了")
                }
                .getOrThrow()
        }
    }

    /**
     * 実行中の処理をキャンセルする
     */
    fun cancel() {
        // キャンセル後、即時に次のタスク実行がされないように設定する
        nextProcessTime = System.currentTimeMillis() + PROCESS_RATE
        // タスクをキャンセルする
        job?.cancel()
    }

    /**
     * SQS キューリストを取得する
     *
     * @return SQS キューリストを返す
     */
    private fun getSqsMessages(): List<Message> {
        log.info("SQS キュー取得開始")
        // SQS キューを取得する
        val receiveMessageResponse = sqsClient.receiveMessage { request ->
            request
                .queueUrl(sqsQueueUrl)
                .maxNumberOfMessages(1)
        }
        log.info("SQS キュー取得終了")
        return receiveMessageResponse.messages()
    }

    /**
     * S3 からファイルの内容を取得する
     *
     * @param s3FileKey S3 ファイルキー
     * @return ファイルの内容を返す
     */
    private fun getS3FileContent(s3FileKey: String): String {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(awsInfrastructure.s3BucketName)
            .key(s3FileKey)
            .build()
        // S3 からファイルを取得する
        val s3Object: ResponseBytes<GetObjectResponse> =
            s3Client.getObject(getObjectRequest, ResponseTransformer.toBytes())
        return s3Object.asUtf8String()
    }

    /**
     * メッセージ処理を非同期で起動する
     *
     * @param receiveChannel メッセージの内容を順に取得するチャンネル
     * @return メッセージ処理ジョブを返す
     */
    private fun CoroutineScope.launchMessageProcess(receiveChannel: ReceiveChannel<String>): Job {
        return launch {
            // キュー処理を並列起動する
            repeat(3) { num ->
                log.info("コルーチン $num 起動準備開始")
                // 非同期に実行する(launch)
                // 別々のスレッドを割り当てる(Dispatchers.IO)
                launch(Dispatchers.IO) {
                    runCatching {
                        log.info("コルーチン $num 開始")

                        // キューを処理する
                        consumeChannel(receiveChannel)
                    }
                        .onFailure { throwable ->
                            log.error("キュー処理で例外が発生しました ${throwable.message}")
                        }
                        .also {
                            log.info("コルーチン $num 終了")
                        }
                        .getOrThrow()
                }
                log.info("コルーチン $num 起動準備完了")
            }
            log.info("この Coroutine スコープのジョブがすべて完了するまで待機する")
        }
    }

    /**
     * メッセージ処理ジョブが処理中の間、SQS メッセージの可視性タイムアウトを定期的に延長する
     *
     * @param jobMessageProcess メッセージ処理ジョブ
     * @param receiptHandle 受信ハンドラ
     */
    private fun CoroutineScope.launchChangeMessageVisibility(jobMessageProcess: Job, receiptHandle: String) {
        launch {
            delay(CHANGE_VISIBILITY_CYCLE_MILLISECONDS)
            while (jobMessageProcess.isActive) {
                log.info("可視性タイムアウトを延長する")
                val response = sqsClient.changeMessageVisibility { request ->
                    request.queueUrl(sqsQueueUrl)
                        .receiptHandle(receiptHandle)
                        .visibilityTimeout(VISIBILITY_SECONDS)
                }
                log.info(response.toString())
                delay(CHANGE_VISIBILITY_CYCLE_MILLISECONDS)
            }
        }
    }

    /**
     * SQS キューを削除する
     *
     * @param message SQS キューメッセージ
     */
    private fun deleteSqsQueue(message: Message) {
        // SQS キューを削除する
        val deleteMessageRequest = DeleteMessageRequest.builder()
            .queueUrl(sqsQueueUrl)
            .receiptHandle(message.receiptHandle())
            .build()
        sqsClient.deleteMessage(deleteMessageRequest)
    }

    /**
     * データリストから順にデータを取得できるキューを取得する
     *
     * @param dataList データリスト
     * @param coroutineScope コルーチンスコープ
     * @return キューを返す
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getChannel(
        dataList: List<String>,
        coroutineScope: CoroutineScope
    ): ReceiveChannel<String> = coroutineScope.produce(capacity = 200) {
        log.info("Start getChannel")
        // データリスト中のデータを順にキューへ登録する
        for (data in dataList) {
            log.info("チャンネルにデータを登録する $data")
            // データをキューに登録する
            send(data)
        }
        log.info("Finish getChannel")
    }

    /**
     * キューを処理する
     *
     * @param receiveChannel 受信チャンネル
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun consumeChannel(
        receiveChannel: ReceiveChannel<String>
    ) {
        receiveChannel.consumeEach { data ->
            log.info("API 呼び出し $data")
            apiClient.post(data)
            delay(Random.nextLong(2_000, 10_000))
        }
    }
}

/**
 * サーバシャットダウンイベントリスナ
 */
@Singleton
class ServerShutdownListener(
    private val scheduledTasksSQSCoroutine: ScheduledTasksSQSCoroutine
) : ApplicationEventListener<ServerShutdownEvent> {
    companion object {
        /** ロガー */
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun onApplicationEvent(event: ServerShutdownEvent) {
        log.info("environment: ${event.source.environment}")
        log.info("scheme: ${event.source.scheme}")
        log.info("host: ${event.source.host}")
        log.info("port: ${event.source.port}")
        log.info("uri: ${event.source.uri}")
        log.info("url: ${event.source.url}")
        log.info("isKeepAlive: ${event.source.isKeepAlive}")
        log.info("isForceExit: ${event.source.isForceExit}")
        log.info("isRunning: ${event.source.isRunning}")
        log.info("isServer: ${event.source.isServer}")

        // SQS 処理中は、シャットダウンを待機する処理
        // 1 秒間隔で、処理が完了しているかを確認している。
        log.info("処理中フラグ: ${scheduledTasksSQSCoroutine.nowProcess.get()}")
        runBlocking {
            log.info("処理待機開始")
            runCatching {
                // SQS 処理完了を待機する（タイムアウト有り）
                joinSqsProcess()
            }
                .onFailure { throwable ->
                    // 処理内で、Suspend 関数を利用する場合、
                    // キャンセル例外がスローされないようにする設定
                    withContext(NonCancellable) {
                        when (throwable) {
                            is TimeoutCancellationException -> {
                                log.info("タイムアウトしました ${throwable.message}")
                                // SQS 処理をキャンセルをする
                                scheduledTasksSQSCoroutine.cancel()
                                // SQS 処理完了を待機する（タイムアウト有り）
                                joinSqsProcess()
                            }
                            else -> {
                                log.info("想定外の例外が発生しました", throwable)
                            }
                        }
                        // withContext(NonCancellable) 内でないとキャンセル例ががスローされてしまう
                        delay(1000)
                    }
                }
            log.info("処理待機終了")
        }
        log.info("処理中フラグ: ${scheduledTasksSQSCoroutine.nowProcess.get()}")
    }

    /**
     * SQS 処理を待ち合わせする
     */
    private suspend fun joinSqsProcess() {
        withTimeout(25_000) {
            while (scheduledTasksSQSCoroutine.nowProcess.get()) {
                delay(1000)
                log.info("処理中フラグ: ${scheduledTasksSQSCoroutine.nowProcess.get()}")
            }
        }
    }
}

/**
 * HTTP API クライアント
 */
@Client("\${api.url}")
interface ApiClient {
    @Post("/post")
    fun post(name: String): String
}

/**
 * データ準備用コントローラ
 *
 * - S3 に処理用データを生成する
 * - SQS キューを登録する
 * - API 呼び出しスタブ
 *
 * @property awsInfrastructure
 */
@Controller("/sqs")
@Requires(beans = [AwsInfrastructure::class])
class SQSQueueController(
    private val awsInfrastructure: AwsInfrastructure
) {
    companion object {
        /** ロガー */
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val s3Client: S3Client = awsInfrastructure.s3Client
    private val sqsClient: SqsClient = awsInfrastructure.sqsClient
    private val sqsQueueUrl: String = awsInfrastructure.getQueueUrl(awsInfrastructure.sqsQueueName)

    @PostConstruct
    fun initialize() {
        log.info("initialize SQSQueueController")
    }

    /**
     * S3 にデータファイルをアップロードする
     */
    @Get("/s3")
    fun addS3Data() {
        // ファイルキー
        val fileKey = "aaa/s3data.txt"
        // ファイルの中身
        val content = (0..20).joinToString("\n").toByteArray()
        // アップロードリクエストパラメータ
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(awsInfrastructure.s3BucketName)
            .key(fileKey)
            .contentType(MediaType.TEXT_PLAIN)
            .contentLength(content.size.toLong())
            .build()
        // アップロードファイルの中身
        val requestBody = RequestBody.fromByteBuffer(ByteBuffer.wrap(content))
        // S3 にファイルをアップロードする
        s3Client.putObject(putObjectRequest, requestBody)
    }

    /**
     * SQS キューを登録する
     */
    @Get("/sqs")
    fun addQueue() {
        val sendMessageBatchRequest = SendMessageBatchRequest.builder()
            .queueUrl(sqsQueueUrl)
            .entries(
                SendMessageBatchRequestEntry.builder()
                    .id("id1")
                    .messageBody("aaa/s3data.txt")
                    .messageGroupId("1")
                    .messageDeduplicationId(System.nanoTime().toString())
                    .build()
            )
            .build()
        sqsClient.sendMessageBatch(sendMessageBatchRequest)
    }
}

/**
 * スケジュールタスク用エラーハンドラ
 *
 * このクラスで処理される例外の条件は、タスク Bean 型と、例外型が一致している（継承ではだめ）場合となる。
 * ただし、例外型を Throwable や Exception にすると DefaultTaskExceptionHandler が優先されてしまう。
 * これを実現する場合は、DefaultTaskExceptionHandler を差し替える必要がある。
 *
 * エラーハンドラを利用する場合、
 * Throwable や、カスタムクラスを継承した例外の処理は、
 * DefaultTaskExceptionHandler が優先されてしまうことを考慮すると、
 * DefaultTaskExceptionHandler を差し替える方法（@Replaces）が無難だと考える。
 */
//@Singleton
//@Requires(beans = [ScheduledTasksSQSCoroutine::class])
////@Replaces(DefaultTaskExceptionHandler::class)
//class CustomTaskExceptionHandler : TaskExceptionHandler<ScheduledTasksSQSCoroutine, CustomException> {
//    companion object {
//        /** ロガー */
//        private val log: Logger = LoggerFactory.getLogger(this::class.java)
//    }
//
//    override fun handle(bean: ScheduledTasksSQSCoroutine?, throwable: CustomException) {
//        log.info("CustomTaskExceptionHandler !!!!!!!!!!!!!!!!!!!!!")
//    }
//}

@Singleton
@Requires(beans = [ScheduledTasksSQSCoroutine::class])
@Replaces(DefaultTaskExceptionHandler::class)
class CustomTaskExceptionHandler : DefaultTaskExceptionHandler() {
    companion object {
        /** ロガー */
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun handle(bean: Any?, throwable: Throwable) {
        if (bean is ScheduledTasksSQSCoroutine && throwable is CustomException) {
            log.info(throwable.message)
        } else {
            super.handle(bean, throwable)
        }
    }
}

/**
 * AWS サービス管理用
 *
 * @property region リージョン
 * @property sqsQueueName SQS キュー名
 * @property s3BucketName S3 バケット名
 * @property apiClient SQS キュークライアント
 */
@Context
@Requirements(
    Requires(property = "region"),
    Requires(property = "sqs.queue.name"),
    Requires(property = "s3.bucket.name"),
    Requires(property = "api.url")
)
open class AwsInfrastructure(
    @Property(name = "region")
    val region: String,
    @Property(name = "sqs.queue.name")
    val sqsQueueName: String,
    @Property(name = "s3.bucket.name")
    val s3BucketName: String,
    val apiClient: ApiClient
) {
    companion object {
        /** ロガー */
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    /** SQS クライアント */
    val sqsClient: SqsClient = SqsClient.builder()
        .region(Region.of(region))
        .build()

    /** S3 クライアント */
    val s3Client: S3Client = S3Client.builder()
        .region(Region.of(region))
        .build()

    /**
     * インスタンス初期化処理
     */
    @PostConstruct
    fun initialize() {
        log.info("initialize AwsInfrastructure")
        log.info("region: $region, sqsQueueName: $sqsQueueName," +
            " s3BucketName: $s3BucketName, sqsQueueClient: $apiClient")
    }

    /**
     * SQS キュー URL を取得する
     *
     * @param sqsQueueName SQS キュー名
     * @return SQS キュー URL を返す
     */
    @Cacheable("sqs-queue-url")
    open fun getQueueUrl(sqsQueueName: String): String {
        // SQS キュー名から、キュー URL を取得する
        val getQueueUrlRequest = GetQueueUrlRequest.builder()
            .queueName(sqsQueueName)
            .build()
        val getQueueUrlResponse = sqsClient.getQueueUrl(getQueueUrlRequest)
        return getQueueUrlResponse.queueUrl()
    }
}

/**
 * カスタム例外
 *
 * @property message メッセージ
 * @property cause 理由
 */
open class CustomException(
    override val message: String? = null,
    override val cause: Throwable? = null
) : Exception()

/**
 * サブカスタム例外
 *
 * @property message メッセージ
 * @property cause 理由
 */
class SubCustomException(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CustomException()
