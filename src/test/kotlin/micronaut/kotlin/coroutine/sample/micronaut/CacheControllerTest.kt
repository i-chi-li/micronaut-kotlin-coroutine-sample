package micronaut.kotlin.coroutine.sample.micronaut

import io.kotlintest.matchers.types.shouldNotBeNull
import io.kotlintest.specs.StringSpec
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MicronautTest

@MicronautTest
class CacheControllerTest(cacheClient: CacheClient) : StringSpec({
    "キャッシュの動作確認" {
        val result = cacheClient.testCache(1, 2, "foo", "memo")
        result.shouldNotBeNull()
    }
})

@Client("/cache")
interface CacheClient {
    @Get("/{groupId}/{userId}/{name}{?memo}")
    fun testCache(groupId: Int, userId: Int, name: String, memo: String): String
}
