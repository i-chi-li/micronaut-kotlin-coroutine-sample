package micronaut.kotlin.coroutine.sample.micronaut

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.test.extensions.kotest.annotation.MicronautTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import micronaut.kotlin.coroutine.sample.JavaMembers
import micronaut.kotlin.coroutine.sample.PrivateMembers
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberExtensionFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * プライベートメンバーのテスト
 *
 * ここでは、以下のテスト方法について記載する。
 * - Java クラス
 *   - private フィールド
 *   - private メソッド
 * - Kotlin クラス
 *   - private フィールド
 *   - private メソッド
 *     - suspend メソッド
 *     - レシーバ付きメソッド
 *
 * Kotlin 標準のリフレクションを利用する方法を記載する。
 * 原則こちらを利用すること。
 *
 * さらに、Micronaut の ReflectionUtils を利用する方法も記載する。
 * 現時点では、ReflectionUtils は、suspend メソッドを扱うことができないため、
 * 利用を推奨しない。
 * しかし、将来、有用になる可能性もある。
 */
@MicronautTest
class PrivateMemberTest(
    private val javaMembers: JavaMembers,
    private val privateMembers: PrivateMembers,
    private val privateMembers2: PrivateMembers
) : StringSpec({
    "Java リフレクションでのプライベートメソッドのテスト" {
        // Java のクラスを対象にする場合は、
        // 引数の型もすべて Java 型で扱う必要がある。
        // さらに、Java では、引数の名前を持たないため、
        // 型のみで判定をする必要がある。

        // 取得したい引数の Class 型リストを作成する
        // 1 番目からが、引数の型となる。
        val targetParams: List<Class<*>> = listOf(String::class.java)
        // JavaMembers.helloWithAge(String) メソッドを取得する
        // 取得対象クラス（JavaMembers）に、インスタンス(javaMembers)を指定した場合に、
        // メソッドを取得できないことがあるので注意。
        // 未確認だが、Micronaut の Bean インスタンスは、ラッパークラスの場合があり
        // そのため、リフレクションでメソッドが取得できない場合があると考えられる。
        // または、単純に、Kotlin や、Micronaut のバグである可能性もある。
        val method: Method = JavaMembers::class.java.declaredMethods
            .find { function: Method ->
                // メソッド名が一致すること
                function.name == "helloWithAge"
                    // 引数の数が一致すること
                    && function.parameters.size == targetParams.size
                    // 引数の型が一致すること
                    && function.parameters
                    // 引数の Java クラス型リストを取得する
                    .map { parameter: Parameter -> parameter.type }
                    // 比較メソッドと、対象の引数型リストを組みにする
                    //   listOf(1, 2, 3).zip(listOf(4, 5, 6))
                    //     -> [(1, 4), (2, 5), (3,6)]
                    // zip で生成される組み数は、リストの少ない方と同数になるため、
                    // 事前に引数の数が一致することを、判定する必要がある。
                    .zip(targetParams)
                    // 引数型がすべて等しいことを判定する
                    .all { pair -> pair.first == pair.second }
                // メソッドが取得できなかった場合は、ここで例外が発生する
            }!!
            // private メソッドへのアクセスを可能にする
            .apply { isAccessible = true }

        // プライベートメソッドを呼び出す
        // null が返る場合は、例外が発生する
        val result: String = method.invoke(javaMembers, "Foo") as String
        result shouldBe "Hello Foo 0"
    }
    "Java リフレクションでのプライベートフィールドのテスト" {
        // 取得したい Java 引数型のリストを作成する
        val targetParams: List<Class<*>> = listOf(String::class.java)
        // JavaMembers.helloWithAge(String) メソッドを取得する
        val method: Method = JavaMembers::class.java.declaredMethods
            .find { function: Method ->
                function.name == "helloWithAge"
                    && function.parameters.size == targetParams.size
                    && function.parameters
                    .map { parameter: Parameter -> parameter.type }
                    .zip(targetParams)
                    .all { pair -> pair.first == pair.second }
            }!!
            .apply { isAccessible = true }

        // プライベートメソッドを呼び出す
        // null が返る場合は、例外が発生する
        val result1: String = method.invoke(javaMembers, "Bar") as String
        result1 shouldBe "Hello Bar 0"

        // プライベートフィールドを取得する
        val field: Field = JavaMembers::class.java.declaredFields
            .find { it.name == "age" }!!
            .apply { isAccessible = true }

        // プライベートフィールドの値をテストする
        field.getInt(javaMembers) shouldBe 0

        // プライベートフィールドを書き換える
        field.setInt(javaMembers, 10)

        // プライベートメソッドを呼び出す
        val result2: String? = method.invoke(javaMembers, "Bar") as? String
        result2 shouldBe "Hello Bar 10"
    }
    "Kotlin リフレクションでのプライベートメソッドのテスト" {
        // 取得したい引数の KClass 型リストを作成する
        // 1 番目は、取得対象のクラス型を指定する。
        // 2 番目からが、引数の型となる。
        val targetParams: List<KClass<*>> = listOf(PrivateMembers::class, String::class)
        // JavaMembers.helloWithAge(String) メソッドを取得する
        // 取得対象クラス（PrivateMembers）に、インスタンス(privateMembers)を指定した場合に、
        // メソッドを取得できないことがあるので注意。
        // 未確認だが、Micronaut の Bean インスタンスは、ラッパークラスの場合があり
        // そのため、リフレクションでメソッドが取得できない場合があると考えられる。
        // または、単純に、Kotlin や、Micronaut のバグである可能性もある。
        val method: KFunction<*> = PrivateMembers::class.declaredFunctions
            .find { function: KFunction<*> ->
                // メソッド名が一致すること
                function.name == "helloWithAge"
                    // 引数の数が一致すること
                    && function.parameters.size == targetParams.size
                    // 引数の型が一致すること
                    && function.parameters
                    // 引数の KClassifier クラス型リストを取得する
                    .map { parameter: KParameter -> parameter.type.classifier }
                    // 比較メソッドと、対象の引数型リストを組みにする
                    //   listOf(1, 2, 3).zip(listOf(4, 5, 6))
                    //     -> [(1, 4), (2, 5), (3,6)]
                    // zip で生成される組み数は、リストの少ない方と同数になるため、
                    // 事前に引数の数が一致することを、判定する必要がある。
                    .zip(targetParams)
                    // 引数型がすべて等しいことを判定する
                    .all { pair -> pair.first == pair.second }
                // メソッドが取得できなかった場合は、ここで例外が発生する
            }!!
            // private メソッドへのアクセスを可能にする
            .apply { isAccessible = true }

        // プライベートメソッドを呼び出す
        val result: String = method.call(privateMembers, "Foo") as String
        result shouldBe "Hello Foo 0"
    }
    "Kotlin リフレクションでのプライベートフィールドのテスト" {
        // 取得したい引数の KClass 型リストを作成する
        val targetParams: List<KClass<*>> = listOf(PrivateMembers::class, String::class)
        // プライベートメソッドを取得する
        val method: KFunction<*> = PrivateMembers::class.declaredFunctions
            .find { function: KFunction<*> ->
                function.name == "helloWithAge"
                    && function.parameters.size == targetParams.size
                    && function.parameters
                    .map { parameter: KParameter -> parameter.type.classifier }
                    .zip(targetParams)
                    .all { pair -> pair.first == pair.second }
            }!!
            .apply { isAccessible = true }

        // プライベートメソッドを呼び出す
        val result1 = method.call(privateMembers, "Bar") as String
        result1 shouldBe "Hello Bar 0"

        // プライベートフィールドを取得する
        val field: KMutableProperty<*> = PrivateMembers::class.declaredMemberProperties
            .find { it.name == "age" }!!
            .apply { isAccessible = true } as KMutableProperty<*>

        // プライベートフィールドの値をテストする
        field.getter.call(privateMembers) as Int shouldBe 0

        // プライベートフィールドを書き換える
        field.setter.call(privateMembers, 10)

        // プライベートメソッドを呼び出す
        // パラメータを選択して、値を設定する方法となる
        // 以下の方法と同義となる
        // val result2 = method.call(privateMembers, "Bar") as String
        val result2 = method.callBy(
            mapOf(
                method.parameters[0] to privateMembers,
                method.parameters[1] to "Bar"
            )
        ) as String
        result2 shouldBe "Hello Bar 10"
    }
    suspend fun trans(v: Int): String {
        delay((0L..500L).random())
        return v.toString()
    }
    "Kotlin リフレクションでの suspend プライベートメソッドのテスト" {
        // ここでは、レシーバ付き拡張関数を取得している
        // このメソッドは、suspend 関数となり、
        // 引数にも suspend 関数を引き渡すサンプルになる。
        val method: KFunction<*> = PrivateMembers::class.declaredMemberExtensionFunctions
            .find { it.name == "parallelMap" }!!
            .apply { isAccessible = true }

        // suspend の無名関数を定義する方法
        val trans2: (suspend (Int) -> String) = { v: Int ->
            delay((0L..500L).random())
            v.toString()
        }

        // suspend メソッドを呼ぶために、Coroutine スコープを開始する
        val result = withContext(Dispatchers.IO) {
            // suspend メソッドを呼び出す
            method.callSuspend(
                // 対象のクラスインスタンスを指定する
                privateMembers,
                // 入力値（レシーバ）を指定する
                listOf(1, 2, 3),
                // 変換用関数（suspend 関数）を指定する
                //   通常の関数定義を指定する方法
                // ::trans
                //   無名関数で指定する方法
                trans2
            ) as List<String>
        }
        result shouldBe listOf("1", "2", "3")
    }
    "Micronaut ReflectionUtils でのプライベートメソッドのテスト" {
        // プライベートメソッドを取得する
        val method = ReflectionUtils
            .getDeclaredMethod(PrivateMembers::class.java, "hello", String::class.java)
            // Optional で結果が返る
            .get().apply {
                // プライベートメソッドにアクセス可能に設定
                isAccessible = true
            }
        // プライベートメソッドを実行する
        val result: String = ReflectionUtils.invokeMethod(privateMembers, method, "Foo")
        result shouldBe "Hello Foo"
    }
    "Micronaut ReflectionUtils でのプライベートフィールドのテスト" {
        // プライベートメソッドを取得する
        val method = ReflectionUtils
            .getDeclaredMethod(PrivateMembers::class.java, "helloWithAge", String::class.java)
            .get().apply { isAccessible = true }

        // プライベートメソッドを呼び出す
        val result1: String = ReflectionUtils.invokeMethod(privateMembers2, method, "Bar")
        result1 shouldBe "Hello Bar 0"

        // プライベートフィールドを取得する
        val field = ReflectionUtils.findDeclaredField(PrivateMembers::class.java, "age")
            .get().apply { isAccessible = true }

        // プライベートフィールドの値をテストする
        field.getInt(privateMembers2) shouldBe 0

        // プライベートフィールドを書き換える
        field.setInt(privateMembers2, 10)

        // プライベートメソッドを呼び出す
        val result2: String = ReflectionUtils.invokeMethod(privateMembers2, method, "Bar")
        result2 shouldBe "Hello Bar 10"
    }
})
