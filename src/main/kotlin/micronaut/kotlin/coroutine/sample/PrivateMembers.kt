package micronaut.kotlin.coroutine.sample

import io.micronaut.context.annotation.Prototype
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Prototype
class PrivateMembers {
    private var age: Int = 0

    private fun hello(name: String): String {
        return "Hello $name"
    }

    private fun helloWithAge(): String {
        return "hello"
    }

    private fun helloWithAge(name: String = "no name"): String {
        return "${hello(name)} $age"
    }

    /**
     * レシーバの値を translator 関数で変換してリストで返す
     *
     * translator 関数での処理は、並列実行する
     *
     * @param translator 変換関数
     * @param I 入力値型
     * @param O 出力値型
     */
    private suspend fun <I, O> Iterable<I>.parallelMap(translator: suspend (I) -> O): List<O> = coroutineScope {
        map { from ->
            async {
                translator(from)
            }
        }
            .awaitAll()
    }
}
