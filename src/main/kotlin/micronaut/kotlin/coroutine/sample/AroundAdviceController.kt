package micronaut.kotlin.coroutine.sample

import io.micronaut.aop.Around
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.cache.annotation.Cacheable
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Type
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.inject.qualifiers.Qualifiers
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.Charset
import java.util.*
import javax.inject.Named
import javax.inject.Singleton

@Controller("/around")
class AroundAdviceController(
    private val notNullExample: NotNullExample
) {
    /**
     * curl http://localhost:8080/around/notnull
     * curl http://localhost:8080/around/notnull?name=foo
     */
    @Get("/notnull{?name}")
    @Produces("${MediaType.APPLICATION_JSON}; ${MediaType.CHARSET_PARAMETER}=utf-8")
    fun notNull(name: String?): String {
        val result = runCatching {
            notNullExample.doWork(name)
        }
        return result.getOrElse { it.message ?: "No message" }
    }

    /**
     * curl http://localhost:8080/around/notnullunit
     * curl http://localhost:8080/around/notnullunit?name=foo
     */
    @Get("/notnullunit{?name}")
    @Produces("${MediaType.APPLICATION_JSON}; ${MediaType.CHARSET_PARAMETER}=utf-8")
    fun notNullUnit(name: String?): String {
        val result = runCatching {
            notNullExample.doWorkUnit(name)
        }
        return result.toString()
    }
}

/**
 * null 引数拒否
 * メソッド・インターセプト処理の本体を定義
 */
@Singleton
class NotNullInterceptor : MethodInterceptor<Any, Any> {
    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        val nullParam =
            // 修飾対象メソッド情報から、引数を格納したマップを取得
            context.parameters
                // 引数のマップから、変数名と値を組みにした、セットコレクションを取り出す
                .entries
                // セットコレクションのストリーム処理を開始
                .stream()
                // ストリームをフィルタリングする
                .filter { entry ->
                    // 引数の値を取得
                    val argumentValue = entry.value.value
                    // 引数の値が null の場合、抽出対象となる
                    Objects.isNull(argumentValue)
                }
                // 最初の null を取り出す
                .findFirst()
        return if (nullParam.isPresent) {
            // null の引数があった場合
            throw IllegalArgumentException("Null parameter [${nullParam.get().key}] not allowed")
        } else {
            // null の引数が無かった場合
            context.proceed()
        }
    }
}

/**
 * null 引数拒否
 * メソッド・インターセプト・アノテーションを定義
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FILE,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Around
@Type(NotNullInterceptor::class)
annotation class NotNull

/**
 * null 引数拒否アノテーションの適用例
 * AOP アノテーションを適用する場合、open 修飾子付き（オーバーライド可能）とする必要がある。
 */
@Singleton
open class NotNullExample {
    // 引数の型は、null 許可となっているが、
    // 引数に null を渡すと、アノテーションの機能により例外が発生する
    @NotNull
    open fun doWork(taskName: String?): String {
        return "Doing job: $taskName"
    }

    @NotNull
    open fun doWorkUnit(taskName: String?) {
        println("Foo")
    }
}

/*
 --------------------------------------------------------------------------------------------
 以下「@Factory Beans に付与する AOP アドバイスについて」のサンプルコード
 @Factory Beans に付与する AOP アドバイスは、クラスと、メソッドに付与する場合で、それぞれ振る舞いが変わる。
 --------------------------------------------------------------------------------------------
 */

open class MyBean() {
    constructor(name: String, id: Int = (0..1000).random()) : this() {
        this.name = name
        this.id = id
    }

    var name: String = "foo"
    var id: Int = (0..1000).random()
    val num: Int
        // MyBean にキャッシュ機能が付与されていない場合、毎回取得値が変わる
        get() = (0..1000).random()

    override fun toString(): String {
        return "name: $name, id: $id, num: $num"
    }
}

/*
 キャッシュ AOP アドバイスを @Factory Beans に付与した場合、@Factory Beans 自体に適用される。
 この場合は、myBean() メソッドで生成する MyBean のインスタンスが、キャッシュされる。
 */
@Cacheable("my-cache1")
@Factory
open class MyFactory1 {

    @Prototype
    @Named("myBean1")
    open fun myBean(): MyBean {
        println("MyFactory1: Create MyBean")
        return MyBean("1")
    }
}

@Factory
open class MyFactory2 {

    @Prototype
    /*
     メソッドに Bean スコープ（＠Prototype 等）と共に、キャッシュ AOP アドバイスを付与した場合、
     @Factory Beans が生成する Bean に AOP アドバイスが適用される。
     この場合は、MyBean の public メソッドすべてに、キャッシュ機能が付与される。
     しかし、MyBean インスタンス自体のキャッシュはされない。
     */
    @Cacheable("my-cache2")
    @Named("myBean2")
    open fun myBean(): MyBean {
        println("MyFactory2: Create MyBean")
        return MyBean("2")
    }
}

/*
キャッシュ機能を付与しない普通の @Factory Beans
 */
@Factory
open class MyFactory3 {

    @Prototype
    @Named("myBean3")
    open fun myBean(): MyBean {
        println("MyFactory3: Create MyBean")
        return MyBean("3")
    }
}

@Controller("/factory")
class FactoryController(
    private val applicationContext: ApplicationContext
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    // curl http://localhost:8080/factory/1
    @Get("/1")
    @Produces("${MediaType.APPLICATION_JSON}; ${MediaType.CHARSET_PARAMETER}=utf-8")
    fun get1(): String {
        val bean = applicationContext.getBean(MyBean::class.java, Qualifiers.byName("myBean1"))
        // bean.toString() にキャッシュ機能が無いため、毎回異なる num の値となる。
        log.info("$bean : 呼び出し毎に、同じインスタンスだが、bean.toString() にキャッシュ機能が無いため、num の値は毎回変わる。")
        return "OK"
    }

    // curl http://localhost:8080/factory/2
    @Get("/2")
    @Produces("${MediaType.APPLICATION_JSON}; ${MediaType.CHARSET_PARAMETER}=utf-8")
    fun get2(): String {
        log.info("Start get2")
        repeat(3) {
            val bean = applicationContext.getBean(MyBean::class.java, Qualifiers.byName("myBean2"))
            log.info("[$it] bean.name: ${bean.name}, bean.id: ${bean.id}, bean.num: ${bean.num} : Getter を使用")
            log.info("[$it] bean.toString(): ${bean.toString()}")
            log.info("[$it] 呼び出し毎に、異なるインスタンスだが、キャッシュ機能が付与されているため、Getter メソッド以外の toString() 等は、キャッシュされた値が返る。")
        }
        log.info("Finish get2")
        return "OK"
    }

    // curl http://localhost:8080/factory/3
    @Get("/3")
    @Produces("${MediaType.APPLICATION_JSON}; ${MediaType.CHARSET_PARAMETER}=utf-8")
    fun get3(): String {
        val bean = applicationContext.getBean(MyBean::class.java, Qualifiers.byName("myBean3"))
        log.info("$bean : 呼び出し毎に、異なるインスタンスで、num の値が変わる。")
        return "OK"
    }
}
