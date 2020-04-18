package micronaut.kotlin.coroutine.sample

import io.micronaut.cache.annotation.Cacheable
import io.micronaut.configuration.hibernate.validator.DefaultParameterNameProvider
import io.micronaut.context.annotation.Factory
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanWrapper
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Produces
import io.micronaut.runtime.http.scope.RequestScope
import io.micronaut.validation.validator.constraints.ConstraintValidator
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext
import org.slf4j.LoggerFactory
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.*
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.inject.Named
import javax.validation.*
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size
import kotlin.reflect.KClass

/**
 * バリデータ利用サンプル
 */
@Controller("/valid")
class CustomValidatorController(
    // リクエスト単位で共通に利用する入れ物
    private val requestContainer: RequestContainer,
    // カスタムバリデータ
    @Named("CustomValidator")
    private val validator: Validator
) {
    /**
     * バリデーション
     *
     * curl -i http://localhost:8080/valid
     * curl -i http://localhost:8080/valid -H "Accept-Language: en"
     */
    @Get("/")
    @Produces(MediaType.APPLICATION_JSON)
    fun index(@Header acceptLanguage: String?): String {
        // Locale 取得
        println("acceptLanguage: $acceptLanguage")
        val locale = getLocale(acceptLanguage)
        println("lookup locale: $locale")

        // ロケール設定前
        println("requestContainer.locale: ${requestContainer.locale}")

        // リクエスト単位で有効な入れ物にロケールを格納
        // ロケールを参照する各種処理で、このロケールを参照する。
        requestContainer.locale = locale

        // ロケール設定後
        println("requestContainer.locale: ${requestContainer.locale}")

        // リクエスト毎に異なるインスタンスになっていることを確認
        println("requestContainer.id: ${requestContainer.id}")

        // バリデーション処理用 Bean を生成
        val customValidateBean = CustomValidateBean(validator)
        // バリデーションを実行し、結果を返す。
        return customValidateBean.valid()
    }

    /**
     * ロケールを取得
     * ロケールを Accept-Language ヘッダの値から取得する。
     * 設定されていない場合は、日本語ロケールを返す。
     *
     * @param acceptLanguage 受付可能言語ヘッダ
     * @return ロケールを返す。
     */
    private fun getLocale(acceptLanguage: String?): Locale {
        // クライアントが受け付けるロケールの範囲をリストで取得
        val languageRanges = Locale.LanguageRange.parse(acceptLanguage ?: "ja")
        println("languageRanges: ${languageRanges.joinToString { it.range }}")

        // システムで有効なロケール一覧を取得
        val availableLocales = Locale.getAvailableLocales().toMutableList()
        println("availableLocales: ${availableLocales.joinToString()}")

        // システムで有効なロケールから、クライアントが受け付けるロケールを抽出
        // 該当無しの場合は、日本語のロケールを利用する。
        return Locale.lookup(languageRanges, availableLocales) ?: Locale.JAPANESE
    }

}

/**
 * リクエストスコープで有効な値を、持ち回るための入れ物
 */
@RequestScope
class RequestContainer {
    // ロケール
    var locale: Locale = Locale.getDefault()

    // インスタンスを識別するための一意の値
    val id: Int = (0..1000).random()
}

// カスタムメッセージ変換クラス
// ロケールを指定するために利用する。
// micronaut-hibernate-validator ライブラリが必要
class CustomMessageInterpolator(
    private val defaultInterpolator: MessageInterpolator,
    private val defaultLocale: Locale
) : MessageInterpolator {
    private val log = LoggerFactory.getLogger(this.javaClass)
    private val id = (0..1000).random()

    override fun interpolate(messageTemplate: String, context: MessageInterpolator.Context): String {
        log.info("interpolate()[id: $id]")
        return interpolate(messageTemplate, context, defaultLocale)
    }

    // Interpolator で項目名の置換を行うには、事前に以下の作業を行う。
    // メッセージバンドルファイルに以下の設定を追加
    //     バリデーションメッセージに、項目名を埋め込むためのプレースホルダーを記載する”{itemNameKeys}”
    //     項目名を定義する。itemname.＜項目ID＞=＜表示文字列＞
    // バリデートアノテーションに設定する、項目名 Payload クラス(ItemNames 内)を定義する
    // 各バリデートアノテーションに、Payload として、項目名 Payload を設定する。
    override fun interpolate(messageTemplate: String, context: MessageInterpolator.Context, locale: Locale): String {
        log.info("interpolate(locale)[id: $id]")
        // 通常のメッセージ変換をする
        val defaultReplacedString = defaultInterpolator.interpolate(messageTemplate, context, locale)
        log.info("defaultReplacedString: $defaultReplacedString")
        // アイテム名を変換をする
        val replacedMessageTemplate = convertItemNamePlaceholder(defaultReplacedString, context)
        log.info("replacedMessageTemplate: $replacedMessageTemplate")
        return defaultInterpolator.interpolate(replacedMessageTemplate, context, locale)
    }

    private fun convertItemNamePlaceholder(messageTemplate: String, context: MessageInterpolator.Context): String {
        val itemNameKeys = context
            .constraintDescriptor
            // null 以外を収集する
            .payload.mapNotNull { payloadClazz ->
                // Payload を処理
                if (payloadClazz.interfaces.contains(ItemNames.ItemName::class.java)) {
                    // クラスを解析する
                    val introspection = BeanIntrospection.getIntrospection(payloadClazz)
                    // Payload のインスタンスを生成
                    val payload = introspection.instantiate()
                    if (payload is ItemNames.ItemName) {
                        // アイテム名キーを取得
                        return@mapNotNull payload.itemNameKey
                    }
                }
                null
            }
            // キーを置換用文字列に変換
            .joinToString { "{$it}" }
        log.info("itemNameKeys: $itemNameKeys")
        // メッセージテンプレートのアイテム名キー文字列を置換
        return messageTemplate.replace("{itemNameKeys}", itemNameKeys)
    }
}

// カスタム・バリデータ・ファクトリ
@Factory
open class CustomValidatorFactory : AutoCloseable {
    // デフォルトのバリデーションファクトリ
    @Inject
    private lateinit var defaultFactory: ValidatorFactory

    // デフォルトのメッセージ処理用インスタンス
    // MessageInterpolator は、インジェクトできないため、インスタンス生成後に初期化する。
    // デフォルトの MessageInterpolator を取得する理由は、カスタム MessageInterpolator の処理で、
    // 基本の処理は、デフォルトの MessageInterpolator に委譲することを推奨しているため。
    private lateinit var defaultInterpolator: MessageInterpolator

    @Inject
    private lateinit var defaultParameterNameProvider: DefaultParameterNameProvider

    // リクエスト単位で共有できる入れ物
    // リクエストパラメータから取得したロケールを参照するために利用する
    @Inject
    private var requestContainer: RequestContainer? = null

    /**
     * インスタンス生成後に呼び出される。
     */
    @PostConstruct
    fun initialize() {
        println("Initialize CustomValidatorFactory")
        // デフォルトの MessageInterpolator を取得
        defaultInterpolator = defaultFactory.messageInterpolator
    }

    /**
     * インスタンス破棄前に呼び出される。
     * この処理の必要性は未調査
     */
    @PreDestroy
    @Throws(Exception::class)
    override fun close() {
        println("Close CustomValidatorFactory")
//        defaultFactory.close()
    }

    /**
     * カスタムバリデータのファクトリメソッド
     * 名前を付与して、標準のバリデータを上書きしないようにする。
     */
    @RequestScope
    @Named("CustomValidator")
    fun getValidator(): Validator {
        println("Start getValidator()")
        // リクエストパラメータから取得したロケールを利用する
        val locale = requestContainer?.locale ?: Locale.getDefault()
        println("locale: $locale")
        println("Finish getValidator()")
        return getValidator(locale)
    }

    /**
     * カスタムバリデータを生成
     * クラス内部でのみ利用しているが、キャッシュ Advice （AOP）を利用する為、private にできない。
     */
    @Cacheable("custom-validator")
    open fun getValidator(locale: Locale): Validator {
        println("Start getValidator($locale)")
        val interpolator = CustomMessageInterpolator(defaultInterpolator, locale)
        println("Finish getValidator($locale)")
        return Validation
            .byDefaultProvider()
            .configure()
            .messageInterpolator(interpolator)
            //.parameterNameProvider(CustomParameterNameProvider(defaultParameterNameProvider))
            .buildValidatorFactory()
            .validator
    }
}

/**
 * バリデーションを利用する Bean
 */
class CustomValidateBean(
    // カスタムバリデータ
    private val validator: Validator
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    fun valid(): String {
        log.info("Start valid")
        // バリデート対象を生成
        val bean = PojoBean()
        // 値を設定
        bean.name = ""
        // バリデート実行
        val result = validator.validate(bean)
        val message = result.joinToString("\n") { it.message }
        log.info("message: $message")
        log.info("Finish valid")
        return message
    }
}

/**
 * POJO クラス
 * POJO クラスをバリデーションする場合は、必ず @Introspected を付与する必要がある。
 * @Introspected は、リフレクション情報を生成する対象に付与する。
 * Micronaut は、動的リフレクションを行わないため、コンパイル時にリフレクション情報を予め生成しておく。
 */
@Introspected
// 指定したフィールドのいずれかが、空でないことをチェック
// fields には、チェックするフィールド名、payload には、項目名を設定
@NotEmptyAny(fields = ["ad", "jc"], payload = [ItemNames.AD::class, ItemNames.JC::class])
class PojoBean {
    @field:NotBlank(payload = [ItemNames.Name::class])
    var name: String = ""

    @field:Size(min = 3, max = 10, payload = [ItemNames.Memo::class])
    var memo: String = ""

    var ad: String = ""
    var jc: String = ""
}

/**
 * カスタムパラメータ名プロバイダ
 * 何に利用するのか、よく分かっていない。
 */
class CustomParameterNameProvider(
    private val defaultParameterNameProvider: ParameterNameProvider
) : ParameterNameProvider {
    /** ロガー */
    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun getParameterNames(constructor: Constructor<*>): MutableList<String> {
        log.info("getParameterNames: constructor: $constructor")
        val paramNames = defaultParameterNameProvider.getParameterNames(constructor)
        log.info("constructor paramNames: ${paramNames.toString()}")
        return paramNames
    }

    override fun getParameterNames(method: Method): MutableList<String> {
        log.info("getParameterNames: method: $method")
        method.parameters.map { log.info("param: ${it.name}") }
        val paramNames = defaultParameterNameProvider.getParameterNames(method)
        log.info("method paramNames: ${paramNames.toString()}")
        return paramNames
    }
}

/**
 * カスタムバリデータ
 * 指定したフィールドのいずれか一つに空でない値が入っていることをチェック
 *
 * @property message メッセージ
 * @property groups グループ配列
 * @property payload ペイロード配列
 * @property fields チェック対象フィールド配列
 */
@Introspected
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
// バリデータの宣言
// micronaut-hibernate-validator ライブラリを利用する場合は、バリデータ実装クラスを指定する必要がある。
// 空（validatedBy = []）にすると以下のようなエラーとなる。
// HV000030: No validator could be found for constraint 'micronaut.kotlin.coroutine.sample.micronaut.DurationPattern'
//   validating type 'java.lang.String'. Check configuration for 'startHoliday.duration'
// したがって、＠Factory での定義もできない。
@Constraint(validatedBy = [NotEmptyAny.NotEmptyAnyValidator::class])
annotation class NotEmptyAny(
    // micronaut-hibernate-validator ライブラリを利用する場合、message, groups, payload は、必ず定義する必要がある。
    // さもなくば以下のようなエラーとなる。
    // HV000074: micronaut.kotlin.coroutine.sample.micronaut.DurationPattern contains Constraint annotation,
    //   but does not contain a message parameter.
    // HV000074: micronaut.kotlin.coroutine.sample.micronaut.DurationPattern contains Constraint annotation,
    //   but does not contain a groups parameter.
    // HV000074: micronaut.kotlin.coroutine.sample.micronaut.DurationPattern contains Constraint annotation,
    //   but does not contain a payload parameter.
    val message: String = "{custom.validation.constraints.NotEmptyAny.message}",
    val groups: Array<KClass<out Any>> = [],
    val payload: Array<KClass<out Payload>> = [],
    val fields: Array<String>
) {
    /**
     * いずれか一つに空でない値が入っていること
     * 実装クラス
     */
    class NotEmptyAnyValidator : ConstraintValidator<NotEmptyAny, Any> {
        private lateinit var fields: Array<String>
        override fun initialize(constraintAnnotation: NotEmptyAny) {
            fields = constraintAnnotation.fields
        }

        /**
         * バリデーション処理
         *
         * @param value バリデーション対象
         * @param annotationMetadata アノテーションメタ情報
         * @param context バリデータコンテキスト
         * @return バリデーション結果が適正であれば true 、それ以外は、false を返す。
         */
        override fun isValid(
            value: Any?,
            annotationMetadata: AnnotationValue<NotEmptyAny>,
            context: ConstraintValidatorContext
        ): Boolean {
            if (value == null) {
                return true
            }

            // Micronaut で Bean の値をリフレクションのように取得するための機能
            val wrapper = BeanWrapper.getWrapper(value)
            // 指定したフィールド名を順に取得する
            return fields.map { wrapper.getProperty(it, Any::class.java) }
                // Optional で有効な値が入っているものをフィルタリング
                .filter { it.isPresent }
                // 値を文字列に変換
                .map { it.get().toString() }
                // いずれかの値が空でない場合は、true となる
                .any { it.isNotEmpty() }
        }
    }
}

/**
 * バリデーション対象の項目名を定義するためのクラス
 */
// 内部クラスも解析可能になる。
// ただし、インターフェースや、抽象クラスの内部クラスは、直接付与しても解析できないので注意。
@Introspected
class ItemNames {
    /**
     * バリデーション対象の項目名を定義するためのインターフェース
     */
    interface ItemName : Payload {
        val itemNameKey: String
    }

    /** 名称 */
    class Name : ItemName {
        override val itemNameKey: String = "itemname.name"
    }

    /** メモ */
    class Memo : ItemName {
        override val itemNameKey: String = "itemname.memo"
    }

    /** 西暦 */
    class AD : ItemName {
        override val itemNameKey: String = "itemname.ad"
    }

    /** 和暦 */
    class JC : ItemName {
        override val itemNameKey: String = "itemname.jc"
    }
}
