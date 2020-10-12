package micronaut.kotlin.coroutine.sample.micronaut

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest.annotation.MicronautTest

@MicronautTest
class CacheControllerTest(cacheClient: CacheClient) : StringSpec({
    "キャッシュの動作確認" {
        val result = cacheClient.testCache(1, 2, "foo", "memo")
        result shouldNotBe null
    }
})

@Client("/cache")
interface CacheClient {
    @Get("/{groupId}/{userId}/{name}{?memo}")
    fun testCache(groupId: Int, userId: Int, name: String, memo: String): String
}
