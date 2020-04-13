package micronaut.kotlin.coroutine.sample.micronaut

import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.collections.shouldNotHaveSize
import io.kotlintest.specs.StringSpec
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MicronautTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import micronaut.kotlin.coroutine.sample.HashInfo

/**
 * Micronaut の DI 仕様を明確化するテスト。
 *
 * 確認対象は、スコープおよび、定義場所の組合せとなる。
 * スコープは、@Controller、@Singleton、@Context、@Prototype、@Infrastructure、@ThreadLocal、@Refreshable、@RequestScope となる。
 * 定義場所は、コンストラクタ、フィールド、動的取得（applicationContext.getBean()）となる。
 */
// Micronaut の DI などの機能を利用して、テストを記述するためのアノテーション
// テスト対象も自動的に起動される。
@MicronautTest
@OptIn(ExperimentalCoroutinesApi::class)
class ScopesControllerTest(
    // コンストラクタの引数が、DI される。
    private val client: ScopesClient
) : StringSpec({

    // Controller
    "Controller が一度だけ生成されること" {
        val result = (0..3).map {
            client.controller().id
        }.toSet()
        result.shouldHaveSize(1)
    }
    // Singleton
    "コンストラクタシングルトンが一度だけ生成されること" {
        val result = (0..3).map {
            client.constructorSingleton().id
        }.toSet()
        result.shouldHaveSize(1)
    }
    "フィールドシングルトンが一度だけ生成されること" {
        val result = (0..3).map {
            client.fieldSingleton().id
        }.toSet()
        result.shouldHaveSize(1)
    }
    "ダイナミックシングルトンが一度だけ生成されること" {
        val result = (0..3).map {
            client.dynamicSingleton().id
        }.toSet()
        result.shouldHaveSize(1)
    }
    // Context
    "コンストラクタコンテキストが一度だけ生成されること" {
        val result = (0..3).map {
            client.constructorContext().id
        }.toSet()
        result.shouldHaveSize(1)
    }
    "フィールドコンテキストが一度だけ生成されること" {
        val result = (0..3).map {
            client.fieldContext().id
        }.toSet()
        result.shouldHaveSize(1)
    }
    "ダイナミックコンテキストが一度だけ生成されること" {
        val result = (0..3).map {
            client.dynamicContext().id
        }.toSet()
        result.shouldHaveSize(1)
    }
    // Prototype
    "シングルトンのコンストラクタプロトタイプが呼び出し毎に生成されないこと" {
        val result = (0..3).map {
            client.constructorPrototype().id
        }.toSet()
        result.shouldHaveSize(1)
    }
    "シングルトンのフィールドプロトタイプが呼び出し毎に生成されないこと" {
        val result = (0..3).map {
            client.fieldPrototype().id
        }.toSet()
        result.shouldHaveSize(1)
    }
    "シングルトンの動的呼び出しプロトタイプが呼び出し毎に生成されいること" {
        val result = (0..3).map {
            client.dynamicPrototype().id
        }.toSet()
        result.shouldHaveSize(4)
    }
    // Infrastructure
    "シングルトンのコンストラクタインフラストラクチャが呼び出し毎に生成されないこと" {
        val result = (0..3).map {
            client.constructorInfrastructure().id
        }.toSet()
        result.shouldHaveSize(1)
    }
    "シングルトンのフィールドインフラストラクチャが呼び出し毎に生成されないこと" {
        val result = (0..3).map {
            client.fieldInfrastructure().id
        }.toSet()
        result.shouldHaveSize(1)
    }
    "シングルトンの動的呼び出しインフラストラクチャが呼び出し毎に生成されること" {
        val result = (0..3).map {
            client.dynamicInfrastructure().id
        }.toSet()
        result.shouldHaveSize(4)
    }
    "シングルトンのコンストラクタスレッドローカルがスレッド別に生成されること" {
        val result = (0..3).flatMap { index ->
            client.constructorThreadLocal().map { info ->
                println("$index ${info.id}")
                info.id
            }
        }.toSet()
        println("local: $result")
        // 異なるリクエストでも、同一スレッドになると、同じインスタンスを返すため、正確な数を指定できない。
        result.shouldNotHaveSize(1)
    }
    "シングルトンのフィールドスレッドローカルがスレッド別で生成されること" {
        val result = (0..3).flatMap { index ->
            client.fieldThreadLocal().map { info ->
                println("fieldThreadLocal $index ${info.id}")
                info.id
            }
        }.toSet()
        println("fieldThreadLocal: $result")
        // 異なるリクエストでも、同一スレッドになると、同じインスタンスを返すため、正確な数を指定できない。
        result.shouldNotHaveSize(1)
    }
    "シングルトンの動的呼び出しスレッドローカルがスレッド毎に生成されること" {
        val result = (0..3).flatMap { index ->
            client.dynamicThreadLocal().map { info ->
                println("dynamicThreadLocal $index ${info.id}")
                info.id
            }
        }.toSet()
        println("dynamicThreadLocal: $result")
        // 異なるリクエストでも、同一スレッドになると、同じインスタンスを返すため、正確な数を指定できない。
        result.shouldNotHaveSize(1)
    }
    "コンストラクタ Refreshable リフレッシュ時だけ、異なるインスタンスを生成すること" {
        val result = (0..3).flatMap { index ->
            client.constructorRefreshable().map { info ->
                println("constructorRefreshable $index ${info.id}")
                info.id
            }
        }.toSet()
        println("constructorRefreshable: $result")
        result.shouldHaveSize(5)
    }
    "フィールド Refreshable リフレッシュ時だけ、異なるインスタンスを生成すること" {
        val result = (0..3).flatMap { index ->
            client.fieldRefreshable().map { info ->
                println("fieldRefreshable $index ${info.id}")
                info.id
            }
        }.toSet()
        println("fieldRefreshable: $result")
        result.shouldHaveSize(5)
    }
    "動的呼び出し Refreshable リフレッシュ時だけ、異なるインスタンスを生成すること" {
        val result = (0..3).flatMap { index ->
            client.dynamicRefreshable().map { info ->
                println("dynamicRefreshable $index ${info.id}")
                info.id
            }
        }.toSet()
        println("dynamicRefreshable: $result")
        result.shouldHaveSize(5)
    }
    "コンストラクタ RequestScope リクエスト別に、異なるインスタンスを生成すること" {
        val result = (0..3).flatMap { index ->
            client.constructorRequestScope().map { info ->
                println("constructorRequestScope $index ${info.id}")
                info.id
            }
        }.toSet()
        println("constructorRequestScope: $result")
        result.shouldHaveSize(4)
    }
    "フィールド RequestScope リクエスト別に、異なるインスタンスを生成すること" {
        val result = (0..3).flatMap { index ->
            client.fieldRequestScope().map { info ->
                println("fieldRequestScope $index ${info.id}")
                info.id
            }
        }.toSet()
        println("fieldRequestScope: $result")
        result.shouldHaveSize(4)
    }
    "動的呼び出し RequestScope リクエスト別に、異なるインスタンスを生成すること" {
        val result = (0..3).flatMap { index ->
            client.dynamicRequestScope().map { info ->
                println("dynamicRequestScope $index ${info.id}")
                info.id
            }
        }.toSet()
        println("dynamicRefreshable: $result")
        result.shouldHaveSize(4)
    }
})

@Client("/scopes")
interface ScopesClient {
    @Get(value = "/controller", processes = [MediaType.APPLICATION_JSON])
    fun controller(): HashInfo

    @Get(value = "/constructorSingleton", processes = [MediaType.APPLICATION_JSON])
    fun constructorSingleton(): HashInfo

    @Get(value = "/dynamicSingleton", processes = [MediaType.APPLICATION_JSON])
    fun dynamicSingleton(): HashInfo

    @Get(value = "/fieldSingleton", processes = [MediaType.APPLICATION_JSON])
    fun fieldSingleton(): HashInfo

    @Get(value = "/constructorContext", processes = [MediaType.APPLICATION_JSON])
    fun constructorContext(): HashInfo

    @Get(value = "/fieldContext", processes = [MediaType.APPLICATION_JSON])
    fun fieldContext(): HashInfo

    @Get(value = "/dynamicContext", processes = [MediaType.APPLICATION_JSON])
    fun dynamicContext(): HashInfo

    @Get(value = "/constructorPrototype", processes = [MediaType.APPLICATION_JSON])
    fun constructorPrototype(): HashInfo

    @Get(value = "/fieldPrototype", processes = [MediaType.APPLICATION_JSON])
    fun fieldPrototype(): HashInfo

    @Get(value = "/dynamicPrototype", processes = [MediaType.APPLICATION_JSON])
    fun dynamicPrototype(): HashInfo

    @Get(value = "/constructorInfrastructure", processes = [MediaType.APPLICATION_JSON])
    fun constructorInfrastructure(): HashInfo

    @Get(value = "/fieldInfrastructure", processes = [MediaType.APPLICATION_JSON])
    fun fieldInfrastructure(): HashInfo

    @Get(value = "/dynamicInfrastructure", processes = [MediaType.APPLICATION_JSON])
    fun dynamicInfrastructure(): HashInfo

    @Get(value = "/constructorThreadLocal", processes = [MediaType.APPLICATION_JSON])
    fun constructorThreadLocal(): List<HashInfo>

    @Get(value = "/fieldThreadLocal", processes = [MediaType.APPLICATION_JSON])
    fun fieldThreadLocal(): List<HashInfo>

    @Get(value = "/dynamicThreadLocal", processes = [MediaType.APPLICATION_JSON])
    fun dynamicThreadLocal(): List<HashInfo>

    @Get(value = "/constructorRefreshable", processes = [MediaType.APPLICATION_JSON])
    fun constructorRefreshable(): List<HashInfo>

    @Get(value = "/fieldRefreshable", processes = [MediaType.APPLICATION_JSON])
    fun fieldRefreshable(): List<HashInfo>

    @Get(value = "/dynamicRefreshable", processes = [MediaType.APPLICATION_JSON])
    fun dynamicRefreshable(): List<HashInfo>

    @Get(value = "/constructorRequestScope", processes = [MediaType.APPLICATION_JSON])
    fun constructorRequestScope(): List<HashInfo>

    @Get(value = "/fieldRequestScope", processes = [MediaType.APPLICATION_JSON])
    fun fieldRequestScope(): List<HashInfo>

    @Get(value = "/dynamicRequestScope", processes = [MediaType.APPLICATION_JSON])
    fun dynamicRequestScope(): List<HashInfo>
}