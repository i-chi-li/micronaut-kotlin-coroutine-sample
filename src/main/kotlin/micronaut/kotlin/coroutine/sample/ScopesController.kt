package micronaut.kotlin.coroutine.sample

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Prototype
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.runtime.context.scope.Refreshable
import io.micronaut.runtime.context.scope.ThreadLocal
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import io.micronaut.runtime.http.scope.RequestScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.annotation.PostConstruct
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * このクラスは、Micronaut の依存性注入（DI）に関する仕様を明確にするためのテストに利用する。
 * 主要な観点は、各スコープと、DI される場所によって、インスタンスが生成される状態を確認すること。
 *
 * 各スコープを持った Bean は、起動時に生成されるものを除き、
 * そのクラス特有のメソッドにアクセスした時点でインスタンスが生成される。
 * 注意するべき点は、インジェクトされたインスタンスや、applicationContext.getBean() で取得した直後のインスタンスは、
 * まだ初期化されておらず、hashCode() の値が、同じ値を返す。
 * そのクラス固有のメソッドにアクセスした時点で、初期化処理が行われ、インスタンスが生成される。
 */
@Controller("/scopes")
@Suppress("LongParameterList", "TooManyFunctions", "MagicNumber")
class ScopesController(
    // コンストラクタシングルトン
    private val constructorSingletonBean: SingletonBean,
    // コンストラクタコンテキスト
    private val constructorContextBean: ContextBean,
    // コンストラクタプロトタイプ
    private val constructorPrototypeBean: PrototypeBean,
// TODO @Infrastructure は、Bean として認識されない問題が発生しているため除外
//    // コンストラクタインフラストラクチャ
//    private val constructorInfrastructureBean: InfrastructureBean,
    // コンストラクタスレッドローカル
    private val constructorThreadLocalBean: ThreadLocalBean,
    // コンストラクタリフレッシャブルローカル
    // リクエスト処理中でも、リフレッシュされるとインスタンスが切り替わる！！
    private val constructorRefreshableBean: RefreshableBean,
    // コンストラクタリクエスト
    private val constructorRequestScopeBean: RequestScopeBean
) : Bean {
    override val id = Random.nextInt(0, Int.MAX_VALUE)

    // コンテキスト取得用
    @Inject
    private lateinit var applicationContext: ApplicationContext

    // フィールドシングルトン
    @Inject
    private lateinit var fieldSingletonBean: SingletonBean

    // フィールドコンテキスト
    @Inject
    private lateinit var fieldContextBean: ContextBean

    // フィールドプロトタイプ
    @Inject
    private lateinit var fieldPrototypeBean: PrototypeBean

// TODO @Infrastructure は、Bean として認識されない問題が発生しているため除外
//    // フィールドインフラストラクチャ
//    @Inject
//    private lateinit var fieldInfrastructureBean: InfrastructureBean

    // フィールドスレッドローカル
    @Inject
    private lateinit var fieldThreadLocalBean: ThreadLocalBean

    // フィールドリフレッシャブル
    // リクエスト処理中でも、リフレッシュされるとインスタンスが切り替わる！！
    @Inject
    private lateinit var fieldRefreshableBean: RefreshableBean

    // フィールドリクエスト
    @Inject
    private lateinit var fieldRequestScopeBean: RequestScopeBean

    /**
     * Controller
     * Controller のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/controller
     */
    @Get("/controller")
    @Produces(MediaType.APPLICATION_JSON)
    fun controller(): HashInfo {
        return HashInfo("controller", this)
    }

    /**
     * Singleton
     * constructorSingletonBean のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/constructorSingleton
     */
    @Get("/constructorSingleton")
    @Produces(MediaType.APPLICATION_JSON)
    fun constructorSingleton(): HashInfo {
        return HashInfo("constructorSingletonBean", constructorSingletonBean)
    }

    /**
     * Singleton
     * fieldSingletonBean のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/fieldSingleton
     */
    @Get("/fieldSingleton")
    @Produces(MediaType.APPLICATION_JSON)
    fun fieldSingleton(): HashInfo {
        return HashInfo("fieldSingletonBean", fieldSingletonBean)
    }

    /**
     * Singleton
     * dynamicSingletonBean のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/dynamicSingleton
     */
    @Get("/dynamicSingleton")
    @Produces(MediaType.APPLICATION_JSON)
    fun dynamicSingleton(): HashInfo {
        return HashInfo("dynamicSingletonBean", applicationContext.getBean(SingletonBean::class.java))
    }

    /**
     * Context
     * constructorContextBean のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/constructorContext
     */
    @Get("/constructorContext")
    @Produces(MediaType.APPLICATION_JSON)
    fun constructorContext(): HashInfo {
        return HashInfo("constructorContextBean", constructorContextBean)
    }

    /**
     * Context
     * fieldContextBean のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/fieldContext
     */
    @Get("/fieldContext")
    @Produces(MediaType.APPLICATION_JSON)
    fun fieldContext(): HashInfo {
        return HashInfo("fieldContextBean", fieldContextBean)
    }


    /**
     * Context
     * dynamicContextBean のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/dynamicContext
     */
    @Get("/dynamicContext")
    @Produces(MediaType.APPLICATION_JSON)
    fun dynamicContext(): HashInfo {
        return HashInfo("dynamicContext", applicationContext.getBean(ContextBean::class.java))
    }

    /**
     * Prototype
     * constructorPrototypeBean のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/constructorPrototype
     */
    @Get("/constructorPrototype")
    @Produces(MediaType.APPLICATION_JSON)
    fun constructorPrototype(): HashInfo {
        return HashInfo("constructorPrototypeBean", constructorPrototypeBean)
    }

    /**
     * Prototype
     * fieldPrototypeBean のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/fieldPrototype
     */
    @Get("/fieldPrototype")
    @Produces(MediaType.APPLICATION_JSON)
    fun fieldPrototype(): HashInfo {
        return HashInfo("fieldPrototypeBean", fieldPrototypeBean)
    }

    /**
     * Prototype
     * dynamicPrototypeBean のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/dynamicPrototype
     */
    @Get("/dynamicPrototype")
    @Produces(MediaType.APPLICATION_JSON)
    fun dynamicPrototype(): HashInfo {
        return HashInfo("dynamicPrototypeBean", applicationContext.getBean(PrototypeBean::class.java))
    }

// TODO @Infrastructure は、Bean として認識されない問題が発生しているため除外
//    /**
//     * Infrastructure
//     * constructorInfrastructureBean のハッシュを取得
//     *
//     * curl -i http://localhost:8080/scopes/constructorInfrastructure
//     */
//    @Get("/constructorInfrastructure")
//    @Produces(MediaType.APPLICATION_JSON)
//    fun constructorInfrastructure(): HashInfo {
//        return HashInfo("constructorInfrastructureBean", constructorInfrastructureBean)
//    }
//
//    /**
//     * Infrastructure
//     * fieldInfrastructureBean のハッシュを取得
//     *
//     * curl -i http://localhost:8080/scopes/fieldInfrastructure
//     */
//    @Get("/fieldInfrastructure")
//    @Produces(MediaType.APPLICATION_JSON)
//    fun fieldInfrastructure(): HashInfo {
//        return HashInfo("fieldInfrastructureBean", fieldInfrastructureBean)
//    }
//
//    /**
//     * Infrastructure
//     * dynamicInfrastructureBean のハッシュを取得
//     *
//     * curl -i http://localhost:8080/scopes/dynamicInfrastructure
//     */
//    @Get("/dynamicInfrastructure")
//    @Produces(MediaType.APPLICATION_JSON)
//    fun dynamicInfrastructure(): HashInfo {
//        return HashInfo("dynamicInfrastructureBean", applicationContext.getBean(InfrastructureBean::class.java))
//    }

    /**
     * ThreadLocal
     * constructorThreadLocalBean のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/constructorThreadLocal
     */
    @Get("/constructorThreadLocal")
    @Produces(MediaType.APPLICATION_JSON)
    suspend fun constructorThreadLocal(): List<HashInfo> = coroutineScope {
        (1..3).map {
            async {
                HashInfo("constructorThreadLocalBean", constructorThreadLocalBean)
            }
        }.awaitAll()
    }

    /**
     * ThreadLocal
     * fieldThreadLocalBean のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/fieldThreadLocal
     */
    @Get("/fieldThreadLocal")
    @Produces(MediaType.APPLICATION_JSON)
    suspend fun fieldThreadLocal(): List<HashInfo> = coroutineScope {
        (1..3).map {
            async {
                HashInfo("fieldThreadLocalBean", fieldThreadLocalBean)
            }
        }.awaitAll()
    }

    /**
     * ThreadLocal
     * dynamicThreadLocalBean のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/dynamicThreadLocal
     */
    @Get("/dynamicThreadLocal")
    @Produces(MediaType.APPLICATION_JSON)
    suspend fun dynamicThreadLocal(): List<HashInfo> = coroutineScope {
        (1..3).map {
            async {
                val bean = applicationContext.getBean(ThreadLocalBean::class.java)
                HashInfo("dynamicThreadLocalBean", bean)
            }
        }.awaitAll()
    }

    /**
     * Refreshable
     * constructorRefreshableBean のハッシュを取得
     * リクエスト処理中でも、リフレッシュされるとインスタンスが切り替わる！！
     *
     * curl -i http://localhost:8080/scopes/constructorRefreshable
     */
    @Get("/constructorRefreshable")
    @Produces(MediaType.APPLICATION_JSON)
    suspend fun constructorRefreshable(): List<HashInfo> = coroutineScope {
        // 同じインスタンスが返るはず
        val same = (1..3).map {
            HashInfo("constructorRequestScopeBean", constructorRefreshableBean)
        }
        // Bean のリフレッシュを実施
        applicationContext.publishEvent(RefreshEvent())
        // 上とは異なる同じインスタンスが返るはず
        val diff = (1..3).map {
            HashInfo("constructorRefreshableBean", constructorRefreshableBean)
        }
        same + diff
    }

    /**
     * Refreshable
     * fieldRefreshableBean のハッシュを取得
     * リクエスト処理中でも、リフレッシュされるとインスタンスが切り替わる！！
     *
     * curl -i http://localhost:8080/scopes/fieldRefreshable
     */
    @Get("/fieldRefreshable")
    @Produces(MediaType.APPLICATION_JSON)
    suspend fun fieldRefreshable(): List<HashInfo> = coroutineScope {
        // 同じインスタンスが返るはず
        val same = (1..3).map {
            HashInfo("fieldRequestScopeBean", fieldRefreshableBean)
        }
        // Bean のリフレッシュを実施
        applicationContext.publishEvent(RefreshEvent())
        // 上とは異なる同じインスタンスが返るはず
        val diff = (1..3).map {
            HashInfo("fieldRefreshableBean", fieldRefreshableBean)
        }
        same + diff
    }

    /**
     * Refreshable
     * dynamicRefreshableBean のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/dynamicRefreshable
     */
    @Get("/dynamicRefreshable")
    @Produces(MediaType.APPLICATION_JSON)
    suspend fun dynamicRefreshable(): List<HashInfo> = coroutineScope {
        // 同じインスタンスが返るはず
        val same = (1..3).map {
            val bean = applicationContext.getBean(RefreshableBean::class.java)
            HashInfo("dynamicRequestScopeBean", bean)
        }
        // Bean のリフレッシュを実施
        applicationContext.publishEvent(RefreshEvent())
        // 上とは異なる同じインスタンスが返るはず
        val diff = (1..3).map {
            val bean = applicationContext.getBean(RefreshableBean::class.java)
            HashInfo("dynamicRefreshableBean", bean)
        }
        same + diff
    }

    /**
     * RequestScope
     * constructorRequestScopeBean のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/constructorRequestScope
     */
    @Get("/constructorRequestScope")
    @Produces(MediaType.APPLICATION_JSON)
    suspend fun constructorRequestScope(): List<HashInfo> = coroutineScope {
        // 同じインスタンスが返るはず
        (1..3).map {
            HashInfo("constructorRequestScopeBean", constructorRequestScopeBean)
        }
    }

    /**
     * RequestScope
     * fieldRequestScopeBean のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/fieldRequestScope
     */
    @Get("/fieldRequestScope")
    @Produces(MediaType.APPLICATION_JSON)
    suspend fun fieldRequestScope(): List<HashInfo> = coroutineScope {
        // 同じインスタンスが返るはず
        (1..3).map {
            HashInfo("fieldRequestScopeBean", fieldRequestScopeBean)
        }
    }

    /**
     * RequestScope
     * dynamicRequestScopeBean のハッシュを取得
     *
     * curl -i http://localhost:8080/scopes/dynamicRequestScope
     */
    @Get("/dynamicRequestScope")
    @Produces(MediaType.APPLICATION_JSON)
    suspend fun dynamicRequestScope(): List<HashInfo> = coroutineScope {
        // 同じインスタンスが返るはず
        (1..3).map {
            val bean = applicationContext.getBean(RequestScopeBean::class.java)
            HashInfo("dynamicRequestScopeBean", bean)
        }
    }
}

// ハッシュ情報格納用
data class HashInfo(
    val name: String, val className: String, val id: Int, val threadName: String = Thread.currentThread().name) {
    constructor(name: String, bean: Bean, threadName: String = Thread.currentThread().name) :
        this(name, bean.javaClass.simpleName, bean.id, threadName)
}

private fun printClassHash(@Suppress("SameParameterValue") name: String, bean: Bean) {
    println("${bean.javaClass.simpleName} $name id[${bean.id}]")
}

interface Bean {
    val id: Int
}

/**
 * @Singleton は、アプリケーションに 1 つのインスタンスのみ作成される。
 * インスタンスは、最初に利用された時点で生成される。
 */
@Singleton
class SingletonBean : Bean {
    override val id = Random.nextInt(0, Int.MAX_VALUE)

    @PostConstruct
    fun init() {
        printClassHash("init", this)
    }
}

/**
 * @Context は、アプリケーションに 1 つのインスタンスのみ作成される。
 * インスタンスは、アプリケーションの起動時に生成される。
 */
@Context
class ContextBean : Bean {
    override val id = Random.nextInt(0, Int.MAX_VALUE)

    @PostConstruct
    fun init() {
        printClassHash("init", this)
    }
}

/**
 * @Prototype は、毎回生成される。
 */
@Prototype
class PrototypeBean : Bean {
    override val id = Random.nextInt(0, Int.MAX_VALUE)

    @PostConstruct
    fun init() {
        printClassHash("init", this)
    }
}

// TODO @Infrastructure は、Bean として認識されない問題が発生しているため除外
///**
// * @Infrastructure は、毎回生成される。@Replaces で置換できない。
// */
//@Infrastructure
//class InfrastructureBean : Bean {
//    override val id = Random.nextInt(0, Int.MAX_VALUE)
//
//    @PostConstruct
//    fun init() {
//        printClassHash("init", this)
//    }
//}

/**
 * @ThreadLocal は、同一スレッドでは同一インスタンス。
 * 別スレッドでは、別インスタンス。
 */
@ThreadLocal
class ThreadLocalBean : Bean {
    override val id = Random.nextInt(0, Int.MAX_VALUE)

    @PostConstruct
    fun init() {
        printClassHash("init", this)
    }
}

/**
 * @Refreshable は、リフレッシュ可能なインスタンス。リフレッシュするまでは、同じインスタンスとなる。
 */
@Refreshable
class RefreshableBean : Bean {
    override val id = Random.nextInt(0, Int.MAX_VALUE)

    @PostConstruct
    fun init() {
        printClassHash("init", this)
    }
}

/**
 * @RequestScope は、リクエスト毎にインスタンスを生成する。
 */
@RequestScope
class RequestScopeBean : Bean {
    override val id = Random.nextInt(0, Int.MAX_VALUE)

    @PostConstruct
    fun init() {
        printClassHash("init", this)
    }
}
