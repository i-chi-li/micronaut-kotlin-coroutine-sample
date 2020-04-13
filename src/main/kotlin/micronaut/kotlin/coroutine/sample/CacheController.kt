package micronaut.kotlin.coroutine.sample

import io.micronaut.cache.annotation.*
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.inject.Singleton

private val log: Logger = LoggerFactory.getLogger("Cache")

@Controller("/cache")
open class CacheController(
    private val cacheBean: CacheBean
) {
    @Get("/{groupId}/{userId}/{name}")
    @Produces("${MediaType.APPLICATION_JSON}; ${MediaType.CHARSET_PARAMETER}=utf-8")
    fun index(groupId: Int, userId: Int, name: String, memo: String): String {
        log.info("Start index")
        log.info("groupId: $groupId, userId: $userId, name: $name, memo: $memo")

        log.info("\n■ キャッシュ値を返すことを確認する。再作成されていた場合は、name の値が変わる。")
        log.info("selectUser 呼び出し")
        log.info("--> ${cacheBean.selectUser(groupId, userId)}")
        log.info("selectUser 再呼び出し")
        log.info("--> ${cacheBean.selectUser(groupId, userId)} : キャッシュ値が返る")

        log.info("\n■ 異なる関数でも、引数の型と順番が同一であれば、共通のキャッシュ値を返すことを確認する。")
        log.info("selectUser2 呼び出し")
        log.info("--> ${cacheBean.selectUser2(groupId, userId)} : selectUser と同じキャッシュ値が返る")

        log.info("\n■ 引数の順序や、個数が異なる場合でも、parameters で指定したキーと一致するキャッシュ値が返ることを確認する。")
        log.info("selectUser3 呼び出し")
        log.info("--> ${cacheBean.selectUser3("taro", userId, groupId, 2)} : selectUser と同じキャッシュ値が返る")

        log.info("\n■ キャッシュ値の更新が行えることを確認する。")
        log.info("updateUser 呼び出し")
        log.info("--> ${cacheBean.updateUser(groupId, userId, "jiro")} : キャッシュ値を更新")
        log.info("selectUser 呼び出し")
        log.info("--> ${cacheBean.selectUser(groupId, userId)} : 更新後のキャッシュ値が返る")

        log.info("\n■ 任意のキャッシュ値クリアが行えることを確認する。")
        log.info("updateUser 呼び出し")
        log.info("--> ${cacheBean.updateUser(groupId + 1, userId + 1, "saburo")} : 別のキャッシュ値を登録")
        log.info("deleteUser 呼び出し。指定のキャッシュ値をクリアする")
        cacheBean.deleteUser(groupId, userId)
        log.info("selectUser 呼び出し")
        log.info("--> ${cacheBean.selectUser(groupId, userId)} : 新規作成された値が返る。")
        log.info("selectUser 呼び出し（追加した値を取得）")
        log.info("--> ${cacheBean.selectUser(groupId + 1, userId + 1)} : updateUser で登録したキャッシュ値が返る。")

        log.info("\n■ 全キャッシュ値クリアが行えることを確認する。")
        log.info("deleteAll 呼び出し。全キャッシュ値をクリアする")
        cacheBean.deleteAll()
        log.info("selectUser 呼び出し")
        log.info("--> ${cacheBean.selectUser(groupId, userId)} : 新規作成された値が返る。")
        log.info("selectUser 呼び出し（追加した値を取得）")
        log.info("--> ${cacheBean.selectUser(groupId + 1, userId + 1)} : 新規作成された値が返る。")

        log.info("\n■ 一括キャッシュ値登録・更新が行えることを確認する。")
        log.info("putUsersAll 呼び出し。キャッシュ値の一括登録・更新をする")
        log.info("--> ${cacheBean.putUsersAll(10, 20)}")
        log.info("selectOtherUser 呼び出し")
        log.info("--> ${cacheBean.selectOtherUser(10, 20)} : 一括登録したキャッシュ値が返る。")
        log.info("selectUser 呼び出し")
        log.info("--> ${cacheBean.selectUser(10, 20)} : 一括登録したキャッシュ値が返る。")

        log.info("\n■ 一括キャッシュ値クリアが行えることを確認する。")
        log.info("invalidateUsersAll 呼び出し。複数キャッシュ値の一括クリアをする")
        log.info("--> ${cacheBean.invalidateUsersAll(10, 20)}")
        log.info("selectOtherUser 呼び出し")
        log.info("--> ${cacheBean.selectOtherUser(10, 20)} : 新規生成値が返る。")
        log.info("selectUser 呼び出し")
        log.info("--> ${cacheBean.selectUser(10, 20)} : 新規生成値が返る。キャッシュ名が別なため、上とは別の値となる。")

        log.info("\n■ 動的キャッシュの確認")
        log.info("selectDynamicUser 呼び出し")
        log.info("--> ${cacheBean.selectDynamicUser(1000, 100)}")
        log.info("selectDynamicUser 再呼び出し")
        log.info("--> ${cacheBean.selectDynamicUser(1000, 100)} : キャッシュ値が返る")

        log.info("\nFinish index")
        return "OK"
    }
}

@Singleton
// このクラスで利用するデフォルトのキャッシュ名を指定する。
@CacheConfig("user")
open class CacheBean {
    @Cacheable
    // Cache Advice を利用する場合は、open にする必要がある。
    open fun selectUser(groupId: Int, userId: Int): User {
        return User(groupId, userId, (1..1000).random().toString())
    }

    @Cacheable
    // キャッシュのキーは、引数の型と順番のみで決まり、変数名は関係が無い。
    // したがって、この関数は、selectUser と同じキャッシュ値を返す。
    open fun selectUser2(num1: Int, num2: Int): User {
        return User(num1, num2, (1..1000).random().toString())
    }

    // キャッシュのキーは、任意の引数と順序を指定可能。
    // 戻り値のキャッシュ値は、キーに指定した引数でのみ決定される。
    // つまり、それ以外の引数が異なっても、同じキャッシュ値を返す。
    @Cacheable(parameters = ["grp", "id"])
    open fun selectUser3(name: String, id: Int, grp: Int, times: Int): User {
        return User(grp, id, name.repeat(times))
    }

    // 引数に対応するキャッシュ値を、戻り値で更新する。
    @CachePut(parameters = ["groupId", "userId"])
    open fun updateUser(groupId: Int, userId: Int, name: String): User {
        return User(groupId, userId, name)
    }

    // 引数に対応するキャッシュ値をクリアする。
    @CacheInvalidate
    open fun deleteUser(groupId: Int, userId: Int) {
    }

    // all=true の場合、全キャッシュ値をクリアする。
    // all 指定無しで、引数が無い場合、何もクリアしないので注意。
    @CacheInvalidate(all = true)
    open fun deleteAll() {
    }

    // 別のキャッシュ名で定義
    @Cacheable(cacheNames = ["other-user"])
    open fun selectOtherUser(groupId: Int, userId: Int): User {
        return User(groupId, userId, (0..1000).random().toString())
    }

    // キャッシュの一括登録・更新
    @PutOperations(CachePut("user"), CachePut("other-user"))
    open fun putUsersAll(groupId: Int, userId: Int): User {
        return User(groupId, userId, (0..1000).random().toString())
    }

    // 複数キャッシュ値の一括クリア
    @InvalidateOperations(CacheInvalidate("user"), CacheInvalidate("other-user"))
    open fun invalidateUsersAll(groupId: Int, userId: Int) {
    }

    // application.yml には、"dynamic"キャッシュは、未定義
    @Cacheable(cacheNames = ["dynamic"])
    open fun selectDynamicUser(groupId: Int, userId: Int): User {
        return User(groupId, userId, (0..1000).random().toString())
    }
}

// ユーザデータ
data class User(val groupId: Int, val userId: Int, val name: String, var memo: String? = "none")
