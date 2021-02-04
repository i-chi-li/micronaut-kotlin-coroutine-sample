package micronaut.kotlin.coroutine.sample.micronaut

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.core.reflect.ReflectionUtils
import micronaut.kotlin.coroutine.sample.PrivateMembers

/**
 * プライベートメンバーのテスト
 *
 * Micronaut では、プライベートメンバーの試験方法が、
 * 通常の Java および、Kotlin で試験する方法と異なり、
 * ReflectionUtils を利用する必要がある。
 */
class PrivateMemberTest : StringSpec({
    "プライベートメソッドのテスト" {
        // テスト対象のインスタンスを生成する
        val instance = PrivateMembers()

        // プライベートメソッドを取得する
        val method = ReflectionUtils
            // 第一引数は、インスタンスで指定するとメソッドを取得できないので注意
            // つまり instance::class.java のような指定はだめ
            .getDeclaredMethod(PrivateMembers::class.java, "hello", String::class.java)
            // Optional で結果が返る
            .get().apply {
                // プライベートメソッドにアクセス可能に設定
                isAccessible = true
            }
        // プライベートメソッドを実行する
        val result: String = ReflectionUtils.invokeMethod(instance, method, "Foo")
        result shouldBe "Hello Foo"
    }
    "プライベートフィールドのテスト" {
        // テスト対象のインスタンスを生成する
        val instance = PrivateMembers()

        // プライベートメソッドを取得する
        // 第一引数は、インスタンスで指定するとメソッドを取得できないので注意
        // つまり instance::class.java のような指定はだめ
        val method = ReflectionUtils
            .getDeclaredMethod(PrivateMembers::class.java, "helloWithAge", String::class.java)
            .get().apply { isAccessible = true }

        // プライベートメソッドを呼び出す
        val result1: String = ReflectionUtils.invokeMethod(instance, method, "Bar")
        result1 shouldBe "Hello Bar 0"

        // プライベートフィールドを取得する
        val field = ReflectionUtils.findDeclaredField(PrivateMembers::class.java, "age")
            .get().apply { isAccessible = true }

        // プライベートフィールドの値をテストする
        field.getInt(instance) shouldBe 0

        // プライベートフィールドを書き換える
        field.setInt(instance, 10)

        // プライベートメソッドを呼び出す
        val result2: String = ReflectionUtils.invokeMethod(instance, method, "Bar")
        result2 shouldBe "Hello Bar 10"
    }
})
