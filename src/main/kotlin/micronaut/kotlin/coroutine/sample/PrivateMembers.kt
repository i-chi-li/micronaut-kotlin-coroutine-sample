package micronaut.kotlin.coroutine.sample

/**
 * プライベートメンバー
 */
class PrivateMembers {
    private var age: Int = 0

    private fun hello(name: String): String {
        return "Hello $name"
    }

    private fun helloWithAge(name: String): String {
        return "${hello(name)} $age"
    }
}
