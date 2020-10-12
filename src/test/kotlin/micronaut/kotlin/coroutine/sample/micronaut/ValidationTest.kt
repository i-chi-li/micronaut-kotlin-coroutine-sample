package micronaut.kotlin.coroutine.sample.micronaut

import io.kotest.assertions.throwables.shouldThrowExactlyUnit
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.Introspected
import io.micronaut.test.extensions.kotest.annotation.MicronautTest
import io.micronaut.validation.validator.constraints.ConstraintValidator
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext
import java.time.Duration
import javax.inject.Singleton
import javax.validation.Constraint
import javax.validation.ConstraintViolationException
import javax.validation.Payload
import javax.validation.Validator
import javax.validation.constraints.AssertTrue
import javax.validation.constraints.DecimalMax
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size
import javax.validation.groups.Default
import kotlin.reflect.KClass

@MicronautTest
class ValidationTest(
    private val applicationContext: ApplicationContext,
    private val validator: Validator,
    private val bean: Bean
) : StringSpec({
    "Data Class validation" {
        // data class で定義した Bean のバリデーションサンプル
        // 値設定時には、バリデーションされない
        val dataBean = DataBean("", 10)
        // バリデーションメソッドでバリデーション結果を取得する
        val constraintViolations = validator.validate(dataBean)
        constraintViolations.forEach {
            println(it.message)
        }
    }
    "Parameter validation" {
        // メソッドのパラメータバリデーションサンプル
        val newBean = Bean()
        // DI せず、通常生成したクラスでは、メソッドパラメータバリデーションは機能しない。
        newBean.validParams("")
        val result = validator.validate(newBean)
        println(result.size)

        val exception = shouldThrowExactlyUnit<ConstraintViolationException> {
            // DI した Bean は、メソッドパラメータバリデーションが有効となる
            bean.validParams("")
        }
        println(exception.message)
    }
    "Field validation" {
        // フィールドパラメータのバリデーションサンプル
        val pojoBean = Pojo("1", null)
        // フィールドパラメータにバリデーションを指定した場合、即座にはバリデーションされない
        pojoBean.flagTrue = false
        pojoBean.decimalMax10 = 11
        pojoBean.decimalMaxStr10 = "11"
        pojoBean.max10 = 11

        // バリデーションを実行
        val constraintViolations = validator.validate(pojoBean)
        // バリデーション結果を確認
        constraintViolations.forEach { println(it.message) }
        constraintViolations.size shouldBe 6
    }
    "Custom message validation" {
        // バリデーション時のメッセージを変更するサンプル
        println("Start Custom message validation")
        val customBean = CustomValidationBean()
        customBean.val1 = 100
        customBean.val2 = 200
        val validResult = validator.validate(customBean)
        validResult.forEach {
            println(it.message)
            println(it.messageTemplate)
        }
        println("Finish Custom message validation")
    }
    "Group validation" {
        // バリデーションをグループ化するサンプル
        println("Start Group validation")
        // バリデーション対象のグループを指定
        arrayOf(
            // デフォルトのみ
            arrayOf(),
            // GroupA のみ
            arrayOf(GroupA::class.java),
            // GroupB のみ
            arrayOf(GroupB::class.java),
            // デフォルトおよび、GroupA
            arrayOf(Default::class.java, GroupA::class.java)
        )
            .forEach { groups ->
                println("-- groups: [${groups.joinToString { it.simpleName }}]")
                val b = GroupValidationBean()
                b.groupDefault = 100
                b.groupA = 200
                b.groupAB = 200
                val result = validator.validate(b, *groups)
                result.forEach {
                    println("${it.propertyPath}: ${it.message}")
                }
            }
        println("Finish Group validation")
    }
    "Constructor validation" {
        // コンストラクタバリデーションのサンプル
        println("Start Constructor validation")
        val b = ConstructorValidationBean("nam", "")
        val result = validator.validate(b)
        result.forEach { println("${it.propertyPath}: ${it.message}") }
        println("Finish Constructor validation")
    }
    "Defining Additional Constraints" {
        println("Start Defining Additional Constraints")
        // 新規にバリデータを作成するサンプル
        val b = applicationContext.getBean(HolidayService::class.java)
        // 正常に設定する場合
        b.startHoliday("Fred", "P2D")
        val exception = shouldThrowExactlyUnit<ConstraintViolationException> {
            // 不正な duration 値を設定する場合
            b.startHoliday("Fred", "junk")
        }
        println(exception.message)
        println("Finish Defining Additional Constraints")
    }

})

// DI 用 Bean
@Singleton
open class Bean {
    // バリデーションを引数に設定した場合は、呼び出し時に即座にバリデーションされる。
    // AOP アノテーションを付与する場合、かならず open 定義が必要。
    open fun validParams(@NotBlank name: String) {
        println("Hello $name")
    }
}

// Pojo Bean でバリデーションをする場合は、@Introspected が必須となる。
@Introspected
class Pojo(
    @field:Size(min = 5, max = 10) var param1: String,
    @field:NotNull val param2: Int?
) {
    @field:AssertTrue
    var flagTrue: Boolean = true

    @field:DecimalMax("10")
    var decimalMax10: Int = 0

    @field:DecimalMax("10")
    var decimalMaxStr10: String = "0"

    @field:Max(10)
    var max10: Int = 0
}

// data class をバリデーションするサンプル
// Data Bean でバリデーションをする場合は、@Introspected が必須となる。
@Introspected
data class DataBean(
    // バリデーションは、代入時には行われない
    @field:NotBlank var name: String,
    @field:Min(18) var age: Int
)

// メッセージを変更するサンプル
@Introspected
class CustomValidationBean {
    // メッセージを書き換える（{validatedValue} は、入力値で、{value} は、制限値）
    @field:Max(value = 10, message = "{validatedValue} は、{value} 以下で入力してください。")
    var val1: Int = 0

    // メッセージ中にテンプレートの入れ子をする
    @field:Max(value = 10, message = "val2 は、{javax.validation.constraints.Max.message}")
    var val2: Int = 0
}

// バリデーションのグループ化
// バリデーション用のグループを定義
interface GroupA
interface GroupB

@Introspected
class GroupValidationBean {
    // groups 未指定の場合、javax.validation.groups.Default となる
    @field:Max(value = 10)
    var groupDefault: Int = 0

    @field:Max(value = 10, groups = [GroupA::class])
    var groupA: Int = 0

    @field:Max(value = 10, groups = [GroupA::class, GroupB::class])
    var groupAB: Int = 0
}

// コンストラクタバリデーション
@Introspected
class ConstructorValidationBean(
    @field:Size(min = 5, max = 10) var name: String,
    @field:NotBlank var memo: String
)

// 新規バリデータを定義するサンプル
// アノテーションを定義
@Retention(AnnotationRetention.RUNTIME)
// バリデータの宣言
// micronaut-hibernate-validator ライブラリを利用する場合は、バリデータ実装クラスを指定する必要がある。
// 空（validatedBy = []）にすると以下のようなエラーとなる。
// HV000030: No validator could be found for constraint 'micronaut.kotlin.coroutine.sample.micronaut.DurationPattern' validating type 'java.lang.String'. Check configuration for 'startHoliday.duration'
// したがって、＠Factory での定義もできない。
@Constraint(validatedBy = [MyValidator::class])
annotation class DurationPattern(
    // micronaut-hibernate-validator ライブラリを利用する場合、以下の3つは、必ず定義する必要がある。さもなくば以下のようなエラーとなる。
    // HV000074: micronaut.kotlin.coroutine.sample.micronaut.DurationPattern contains Constraint annotation, but does not contain a message parameter.
    // HV000074: micronaut.kotlin.coroutine.sample.micronaut.DurationPattern contains Constraint annotation, but does not contain a groups parameter.
    // HV000074: micronaut.kotlin.coroutine.sample.micronaut.DurationPattern contains Constraint annotation, but does not contain a payload parameter.
    val message: String = "invalid duration ({validatedValue})",
    val groups: Array<KClass<out Any>> = [],
    val payload: Array<KClass<out Payload>> = []
)

// 新規バリデータの実装
// @Factory で無名クラスを生成する方法
// micronaut-hibernate-validator ライブラリを利用しない場合のみこの方法が可能
// ファクトリで、実装クラスをインスタンス化して渡す方法もエラーとなってしまう。
//@Factory
//class MyValidatorFactory {
//    // メソッドの戻り値で、アノテーションと関連付いている
//    @Singleton
//    fun durationPatternValidator(): ConstraintValidator<DurationPattern, CharSequence> {
//        return ConstraintValidator { value, _, _ ->
//            value == null || value.toString().matches("^PT?[\\d]+[SMHD]{1}$".toRegex())
//        }
//    }
//}

// 新規バリデータの実装
// クラスを実装する方法
// micronaut-hibernate-validator ライブラリを利用する場合、クラス定義が必須
// なぜなら、アノテーション定義に、このクラスを指定する必要があり、無名クラスでは設定できないため。
class MyValidator : ConstraintValidator<DurationPattern, CharSequence> {
    override fun isValid(
        value: CharSequence?,
        annotationMetadata: AnnotationValue<DurationPattern>,
        context: ConstraintValidatorContext
    ): Boolean {
        return value == null || value.toString().matches("^PT?[\\d]+[SMHD]{1}$".toRegex())
    }
}

// 新規バリデータの利用例
@Singleton
open class HolidayService {
    open fun startHoliday(@NotEmpty persion: String,
                          @DurationPattern duration: String): String {
        val d = Duration.parse(duration)
        val mins = d.toMinutes()
        return "person $persion is off on holiday for $mins minutes"
    }
}
