package micronaut.kotlin.coroutine.sample

import io.micronaut.aop.Introduction
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Type
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import java.time.LocalDateTime
import javax.inject.Singleton

/*
Introduction Advice のサンプルコード
アノテーションを付与した対象にスタブ機能を付与する
 */

/*
Introduction Advice の実体を定義
 */
@Singleton
class StubIntroduction : MethodInterceptor<Any, Any> {
    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        // 対象の Stub アノテーションに定義されている"value"値を取得
        return context.getValue<Any>(
            // アノテーションクラスを指定
            Stub::class.java,
            // 対象の戻り値型を指定
            context.returnType.type
            // 該当の戻り値がない場合 null を返す
        ).orElse(null)
    }
}

/*
Stub アノテーションを定義
 */
// @Introduction を必ず付与する
@Introduction
// この Advice の実装クラスを指定する
@Type(StubIntroduction::class)
// @Bean を付与すると、＠Stub を付与した、すべてのタイプが Bean となる
@Bean
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FILE,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
// 引数の value は、戻り値に利用する引数
annotation class Stub(val value: String = "")

/*
Stub アノテーション付与のサンプル
抽象クラスや、インターフェースに @Stub を付与できる
 */
@Stub
interface StubExample {
    // 任意の対象に個別の戻り値を設定できる
    // ここでは、number パラメータの Getter メソッドが対象となる
    @get:Stub("10")
    val number: Int

    val date: LocalDateTime?

    // 読み書き可能なパラメータに指定する
    @get:Stub("hello")
    var string: String
}

@Controller("/intro")
class IntroductionAdviceController(
    val stubExample: StubExample
) {
    @Get("/")
    @Produces(MediaType.APPLICATION_JSON)
    fun index(): String {
        println("number: ${stubExample.number}, date: ${stubExample.date}, string: ${stubExample.string}")

        // 読み書き可能なパラメータに書き込みをしても、エラーとはならない。
        println("stubExample.string = \"foo\"")
        stubExample.string = "foo"
        // string の値は、依然として "hello" のままとなっている
        println("number: ${stubExample.number}, date: ${stubExample.date}, string: ${stubExample.string} : string の値は、依然として \"hello\" のままとなっている")
        return "OK"
    }
}
