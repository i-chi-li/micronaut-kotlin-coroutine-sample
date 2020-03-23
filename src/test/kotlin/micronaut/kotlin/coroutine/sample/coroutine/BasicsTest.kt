package micronaut.kotlin.coroutine.sample.coroutine

import io.kotlintest.*
import io.kotlintest.data.forall
import io.kotlintest.extensions.TopLevelTest
import io.kotlintest.matchers.haveLength
import io.kotlintest.matchers.startWith
import io.kotlintest.matchers.string.include
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.row
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlin.math.max

// タグの定義
// タグは、テスト実行対象を以下のように指定できる。
// gradlew test -Dkotest.tags.include=Linux -Dkotest.tags.exclude=Local
// タグ定義は、異なるパッケージに複数定義してもよい。同一の名称であれば、同じものと判断される。
object Linux : Tag()
object Local : Tag()

/**
 * 基本のテストコード
 *
 * テストクラスは、StringSpec を継承している。
 * Spec には、多数の種類があるが、基本的にはテストケースの記載方法が異なるだけで、できることは同じである。
 * Spec の詳細は、以下を参照。
 * https://github.com/kotest/kotest/blob/3.3.2/doc/styles.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlainTest : StringSpec({
    // ここで指定した文字列は、テスト結果に出力される。
    // IntelliJ でテストを実行すると、結果表示が文字化けする。
    // ただし、テストレポートファイルは、正常に出力されている。
    // テストレポートは、build/reports/tests/test/ ディレクトリに格納される。
    // レポートをブラウザで開くには、IntelliJ で「Run」Tool Windows テスト結果ツリーの上部アイコン
    // 「Open Gradle test report」を利用できる。
    "文字列長が 5 であること" {
        // 判定に利用するメソッドの一覧は、以下を参照。
        // https://github.com/kotest/kotest/blob/3.3.2/doc/matchers.md
        "hello".length.shouldBe(5)
        "hello".shouldBe(haveLength(5))
        // Suspend 関数が利用可能。
        delay(10)
    }
    "文字列に'el'が含まれていること" {
        "hello".shouldBe(include("el"))
    }
    "データ駆動型テスト" {
        forall(
            row(1, 5, 5),
            row(1, 0, 1),
            row(0, 0, 0)
        ) { a, b, maxVal ->
            max(a, b).shouldBe(maxVal)
        }
    }
    "例外発生テスト" {
        val exception = shouldThrow<IllegalAccessException> {
            throw IllegalAccessException("Something went wrong")
        }
        exception.message.should(startWith("Something"))
    }
    "テスト実行の設定".config(timeout = 100.milliseconds, invocations = 5, threads = 2, tags = setOf(Linux, Local)) {
        println("タイムアウト 100 ミリ秒、実行回数 5 回、スレッド数 2 、タグを設定、Thread name: [${Thread.currentThread().name}]")
        delay(10)
    }
}) {
    override fun beforeSpecClass(spec: Spec, tests: List<TopLevelTest>) {
        super.beforeSpecClass(spec, tests)
        println("テストクラス開始前に一度だけ実行")
    }

    override fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        println("テストクラス開始前に毎回実行（インスタンス化する度に複数回実行）")
    }

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)
        println("各テストケース実行前に毎回実行")
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        super.afterTest(testCase, result)
        println("各テストケース実行後に毎回実行")
    }

    override fun afterSpec(spec: Spec) {
        super.afterSpec(spec)
        println("テストクラス完了後に毎回実行")
    }

    override fun afterSpecClass(spec: Spec, results: Map<TestCase, TestResult>) {
        super.afterSpecClass(spec, results)
        println("テストクラス完了後に一度だけ実行")
    }
}

/*
実行結果

テストクラス開始前に一度だけ実行
テストクラス開始前に毎回実行（インスタンス化する度に複数回実行）
各テストケース実行前に毎回実行
各テストケース実行後に毎回実行
各テストケース実行前に毎回実行
各テストケース実行後に毎回実行
各テストケース実行前に毎回実行
各テストケース実行後に毎回実行
各テストケース実行前に毎回実行
各テストケース実行後に毎回実行
各テストケース実行前に毎回実行
タイムアウト 100 ミリ秒、実行回数 5 回、スレッド数 2 、タグを設定、Thread name: [pool-3-thread-2 @coroutine#21]
タイムアウト 100 ミリ秒、実行回数 5 回、スレッド数 2 、タグを設定、Thread name: [pool-3-thread-1 @coroutine#20]
タイムアウト 100 ミリ秒、実行回数 5 回、スレッド数 2 、タグを設定、Thread name: [pool-3-thread-2 @coroutine#22]
タイムアウト 100 ミリ秒、実行回数 5 回、スレッド数 2 、タグを設定、Thread name: [pool-3-thread-1 @coroutine#23]
タイムアウト 100 ミリ秒、実行回数 5 回、スレッド数 2 、タグを設定、Thread name: [pool-3-thread-2 @coroutine#24]
各テストケース実行後に毎回実行
テストクラス完了後に毎回実行
テストクラス完了後に一度だけ実行
 */
