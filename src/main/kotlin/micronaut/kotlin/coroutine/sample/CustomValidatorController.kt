package micronaut.kotlin.coroutine.sample

import io.micronaut.cache.annotation.Cacheable
import io.micronaut.context.annotation.Factory
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Produces
import io.micronaut.runtime.http.scope.RequestScope
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.*
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import javax.validation.*
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

@Controller("/valid")
class CustomValidatorController(
    private val requestContainer: RequestContainer,
    private val customValidateBean: CustomValidateBean
) {
    @Get("/")
    @Produces("${MediaType.APPLICATION_JSON}; ${MediaType.CHARSET_PARAMETER}=utf-8")
    fun index(@Header acceptLanguage: String?): String {
        // Locale 取得
        println("acceptLanguage: $acceptLanguage")
        val languageRanges = Locale.LanguageRange.parse(acceptLanguage ?: "ja")
        println("languageRanges: ${languageRanges.joinToString { it.range }}")
        val availableLocales = Locale.getAvailableLocales().toMutableList()
        println("availableLocales: ${availableLocales.joinToString()}")
        val locale = Locale.lookup(languageRanges, availableLocales) ?: Locale.getDefault()
        println("lookup locale: $locale")
        // ロケール設定前
        println("requestContainer.locale: ${requestContainer.locale}")
        requestContainer.locale = locale
        // ロケール設定後
        println("requestContainer.locale: ${requestContainer.locale}")
        // リクエスト毎に異なるインスタンスになっていることを確認
        println("requestContainer.id: ${requestContainer.id}")

        return customValidateBean.valid()
    }
}

/**
 * リクエストスコープの入れ物
 */
@RequestScope
class RequestContainer {
    var locale: Locale = Locale.getDefault()
    val id: Int = (0..1000).random()
}

// カスタムメッセージ補間
// ロケールを指定するために利用する。
// micronaut-hibernate-validator ライブラリが必要
class CustomMessageInterpolator(
    private val defaultInterpolator: MessageInterpolator,
    private val defaultLocale: Locale
) : MessageInterpolator {

    override fun interpolate(messageTemplate: String, context: MessageInterpolator.Context): String {
        return defaultInterpolator.interpolate(messageTemplate, context, defaultLocale)
    }

    override fun interpolate(messageTemplate: String, context: MessageInterpolator.Context, locale: Locale): String {
        return defaultInterpolator.interpolate(messageTemplate, context, locale)
    }
}

// カスタム・バリデータ・ファクトリ
@Factory
open class CustomValidatorFactory : AutoCloseable {
    @Inject
    private lateinit var defaultFactory: ValidatorFactory

    // これは、インジェクトできない。
    private lateinit var defaultInterpolator: MessageInterpolator

    @Inject
    private var requestContainer: RequestContainer? = null

    @PostConstruct
    fun initialize() {
        println("Initialize CustomValidatorFactory")
        defaultInterpolator = defaultFactory.messageInterpolator
    }

    @PreDestroy
    @Throws(Exception::class)
    override fun close() {
        println("Close CustomValidatorFactory")
//        defaultFactory.close()
    }

    @RequestScope
    @Named("CustomValidator")
    fun getValidator(): Validator {
        println("Start getValidator()")
        val locale = requestContainer?.locale ?: Locale.getDefault()
        println("locale: $locale")
        println("Finish getValidator()")
        return getValidator(locale)
    }

    @Cacheable("custom-validator")
    open fun getValidator(locale: Locale): Validator {
        println("Start getValidator($locale)")
        val interpolator = CustomMessageInterpolator(defaultInterpolator, locale)
        println("Finish getValidator($locale)")
        return Validation
            .byDefaultProvider()
            .configure()
            .messageInterpolator(interpolator)
            .buildValidatorFactory()
            .validator
    }
}

@Singleton
class CustomValidateBean(
    @Named("CustomValidator")
    private val validator: Validator
) {
    fun valid(): String {
        println("Start valid")
        val bean = PojoBean()
        bean.name = ""
        val result = validator.validate(bean)
        val message = result.joinToString { "${it.propertyPath}: ${it.message} [${it.messageTemplate}]" }
        println(message)
        println("Finish valid")
        return message
    }
}

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ItemName(
    val name: String
)

@Introspected
class PojoBean {
    @field:NotBlank
    @field:ItemName("pojoName")
    var name: String = ""

    @field:Size(min = 3, max = 10)
    var memo: String = ""
}

// カスタム・パラメータ名プロバイダ
class CustomParameterNameProvider : ParameterNameProvider {
    override fun getParameterNames(constructor: Constructor<*>?): MutableList<String> {
        println(constructor)
        return mutableListOf("foo", "bar")
    }

    override fun getParameterNames(method: Method?): MutableList<String> {
        println(method)
        return mutableListOf("heee")
    }
}
