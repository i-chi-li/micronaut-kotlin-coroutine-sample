package micronaut.kotlin.coroutine.sample

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Value
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import javax.inject.Inject
import javax.inject.Singleton

// ここで利用している設定値は、src/main/resources/application.yml に定義している
// 環境変数や、コマンドオプションで値を上書きする方法は、そちらを参照

// 設定値を取得するには、Bean 定義をしてインジェクションをする必要がある。
// （@Controller、＠Singleton や、＠Prototype など）
@Controller("/configurations")
class ConfigurationUsageController(
    private val bean: ConfigurationUsageBean
){
    // 変更不可（val）にできない
    // lateinit は、nullable 型にできない

    // @Value は、設定値を変数の一部として定義できる
    // @Property は、設定値のキーを name に直接指定して定義する

    // @Value で値を設定する
    // @Value は、@field:Value とする必要は無い
    // private 変数値の一部に設定値を埋め込む
    // kotlin では、"\${config-key}" の様に $ をエスケープする必要がある
    @Value("\${custom-vals.sample-value1}-\${custom-vals.sample-value3}-hoge1")
    private lateinit var field1: String

    // @Value で値を設定する
    // public 変数値の一部に設定値を埋め込む
    // public フィールドの初期値を設定する
    @set:Inject
    @setparam:Value("\${custom-vals.sample-value2}-\${custom-vals.sample-value3}-hoge2")
    lateinit var field2: String

    // @Property で値を設定する
    // @field:Property と記述する必要がある
    // private フィールドの初期値を設定する
    @field:Property(name = "custom-vals.sample-value3")
    private lateinit var field3: String

    // @Property で値を設定する
    // public フィールドの初期値を設定する場合
    // public フィールドの初期値を設定する
    @set:Inject
    @setparam:Property(name = "custom-vals.sample-value4")
    lateinit var field4: String

    @Get
    fun get(): String {
        println("field1: $field1," +
            " field2: $field2," +
            " field3: $field3," +
            " field4: $field4," +
            " bean.field1: ${bean.field1}," +
            " bean.field2: ${bean.field2}"
        )

        // フィールド値を変更する
        field2 = "bar"

        println("field1: $field1," +
            " field2: $field2," +
            " field3: $field3," +
            " field4: $field4," +
            " bean.field1: ${bean.field1}," +
            " bean.field2: ${bean.field2}"
        )
        return "OK"
    }
}

@Singleton
class ConfigurationUsageBean {
    @Value("\${custom-vals.sample-value1}-bean")
    lateinit var field1: String

    // public フィールドの初期値を設定する場合
    @set:Inject
    @setparam:Property(name = "custom-vals.sampleValue2")
    var field2: String? = null
}
