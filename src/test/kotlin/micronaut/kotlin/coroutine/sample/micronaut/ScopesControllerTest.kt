package micronaut.kotlin.coroutine.sample.micronaut

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest.annotation.MicronautTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
        result.size shouldBe 1
    }
    // Singleton
    "コンストラクタシングルトンが一度だけ生成されること" {
        val result = (0..3).map {
            client.constructorSingleton().id
        }.toSet()
        result.size shouldBe 1
    }
    "フィールドシングルトンが一度だけ生成されること" {
        val result = (0..3).map {
            client.fieldSingleton().id
        }.toSet()
        result.size shouldBe 1
    }
    "ダイナミックシングルトンが一度だけ生成されること" {
        val result = (0..3).map {
            client.dynamicSingleton().id
        }.toSet()
        result.size shouldBe 1
    }
    // Context
    "コンストラクタコンテキストが一度だけ生成されること" {
        val result = (0..3).map {
            client.constructorContext().id
        }.toSet()
        result.size shouldBe 1
    }
    "フィールドコンテキストが一度だけ生成されること" {
        val result = (0..3).map {
            client.fieldContext().id
        }.toSet()
        result.size shouldBe 1
    }
    "ダイナミックコンテキストが一度だけ生成されること" {
        val result = (0..3).map {
            client.dynamicContext().id
        }.toSet()
        result.size shouldBe 1
    }
    // Prototype
    "シングルトンのコンストラクタプロトタイプが呼び出し毎に生成されないこと" {
        val result = (0..3).map {
            client.constructorPrototype().id
        }.toSet()
        result.size shouldBe 1
    }
    "シングルトンのフィールドプロトタイプが呼び出し毎に生成されないこと" {
        val result = (0..3).map {
            client.fieldPrototype().id
        }.toSet()
        result.size shouldBe 1
    }
    "シングルトンの動的呼び出しプロトタイプが呼び出し毎に生成されいること" {
        val result = (0..3).map {
            client.dynamicPrototype().id
        }.toSet()
        result.size shouldBe 4
    }
    // Infrastructure
    "シングルトンのコンストラクタインフラストラクチャが呼び出し毎に生成されないこと" {
        val result = (0..3).map {
            client.constructorInfrastructure().id
        }.toSet()
        result.size shouldBe 1
    }
    "シングルトンのフィールドインフラストラクチャが呼び出し毎に生成されないこと" {
        val result = (0..3).map {
            client.fieldInfrastructure().id
        }.toSet()
        result.size shouldBe 1
    }
    "シングルトンの動的呼び出しインフラストラクチャが呼び出し毎に生成されないこと" {
        val result = (0..3).map {
            println("dynamicInfrastructureID: ${client.dynamicInfrastructure().id}")
            client.dynamicInfrastructure().id
        }.toSet()
        result.size shouldBe 1
    }
    "シングルトンのコンストラクタスレッドローカルがスレッド別に生成されること" {
        // ローカルスレッドを利用しているため、別スレッドで処理を行う必要がある
        val result = (0..3).map { index ->
            async(Dispatchers.IO) {
                client.constructorThreadLocal().map { info ->
                    println("constructorThreadLocal $index ${info.id}")
                    info.id
                }
            }
        }
            .flatMap {
                it.await()
            }
            .toSet()
        println("local: $result")
        // 異なるリクエストでも、同一スレッドになると、同じインスタンスを返すため、正確な数を指定できない。
        result.size shouldNotBe 1
    }
    "シングルトンのフィールドスレッドローカルがスレッド別で生成されること" {
        // ローカルスレッドを利用しているため、別スレッドで処理を行う必要がある
        val result = (0..3).map { index ->
            async(Dispatchers.IO) {
                client.fieldThreadLocal().map { info ->
                    println("fieldThreadLocal $index ${info.id}")
                    info.id
                }
            }
        }
            .flatMap {
                it.await()
            }
            .toSet()
        println("fieldThreadLocal: $result")
        // 異なるリクエストでも、同一スレッドになると、同じインスタンスを返すため、正確な数を指定できない。
        result.size shouldNotBe 1
    }
    "シングルトンの動的呼び出しスレッドローカルがスレッド毎に生成されること" {
        // ローカルスレッドを利用しているため、別スレッドで処理を行う必要がある
        val result = (0..3).map { index ->
            async(Dispatchers.IO) {
                client.dynamicThreadLocal().map { info ->
                    println("dynamicThreadLocal $index ${info.id}")
                    info.id
                }
            }
        }
            .flatMap {
                it.await()
            }
            .toSet()
        println("dynamicThreadLocal: $result")
        // 異なるリクエストでも、同一スレッドになると、同じインスタンスを返すため、正確な数を指定できない。
        result.size shouldNotBe 1
    }
    "コンストラクタ Refreshable リフレッシュ時だけ、異なるインスタンスを生成すること" {
        val result = (0..3).flatMap { index ->
            client.constructorRefreshable().map { info ->
                println("constructorRefreshable $index ${info.id}")
                info.id
            }
        }.toSet()
        println("constructorRefreshable: $result")
        result.size shouldBeGreaterThanOrEqual 3
    }
    "フィールド Refreshable リフレッシュ時だけ、異なるインスタンスを生成すること" {
        val result = (0..3).flatMap { index ->
            client.fieldRefreshable().map { info ->
                println("fieldRefreshable $index ${info.id}")
                info.id
            }
        }.toSet()
        println("fieldRefreshable: $result")
        result.size shouldBeGreaterThanOrEqual 4
    }
    "動的呼び出し Refreshable リフレッシュ時だけ、異なるインスタンスを生成すること" {
        val result = (0..3).flatMap { index ->
            client.dynamicRefreshable().map { info ->
                println("dynamicRefreshable $index ${info.id}")
                info.id
            }
        }.toSet()
        println("dynamicRefreshable: $result")
        // リフレッシュを４回行うと、初回呼び出し前の既存インスタンスと合計して５インスタンスとなる
        result.size shouldBeGreaterThanOrEqual 4
    }
    "コンストラクタ RequestScope リクエスト別に、異なるインスタンスを生成すること" {
        val result = (0..3).flatMap { index ->
            client.constructorRequestScope().map { info ->
                println("constructorRequestScope $index ${info.id}")
                info.id
            }
        }.toSet()
        println("constructorRequestScope: $result")
        result.size shouldBe 4
    }
    "フィールド RequestScope リクエスト別に、異なるインスタンスを生成すること" {
        val result = (0..3).flatMap { index ->
            client.fieldRequestScope().map { info ->
                println("fieldRequestScope $index ${info.id}")
                info.id
            }
        }.toSet()
        println("fieldRequestScope: $result")
        result.size shouldBe 4
    }
    "動的呼び出し RequestScope リクエスト別に、異なるインスタンスを生成すること" {
        val result = (0..3).flatMap { index ->
            client.dynamicRequestScope().map { info ->
                println("dynamicRequestScope $index ${info.id}")
                info.id
            }
        }.toSet()
        println("dynamicRefreshable: $result")
        result.size shouldBe 4
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
