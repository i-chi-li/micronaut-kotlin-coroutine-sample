<!-- toc -->
- Cache Advice
  - キャッシュ名定義
  - アノテーション一覧

# Cache Advice
Cache Advice は、メソッドにキャッシュ機能を付与するためのアノテーション。
デフォルトのバックエンドは、Caffeine となる。

- キャッシュは、「キャッシュ名」単位で管理する。
- キャッシュ名は、ケバブケースで定義すること（other-user 等）
- キャッシュ名は、静的定義と動的定義ができる。
- キャッシュのキーは、関数の引数型と順序で作成される。
- キーになるのは、型であり、引数名ではない。
- キーに利用する引数は、順序も含め、任意に指定できる。

## キャッシュ名定義
キャッシュ名は、src/main/resources/application.yml に定義する。

設定例
```yaml
micronaut:
    caches:
        user:
            # 値のシリアライズとデシリアライズに使用される文字セット
            charset: UTF-8
            # 初期キャッシュ容量
            initial-capacity: 0
            # 最大キャッシュ登録数
            maximumSize: 5
            # 登録の最大の重み
            maximum-weight: 100
            # 書き込み後のキャッシュの有効期限
            expire-after-write: 0
            # アクセス後のキャッシュの有効期限
            expire-after-access: 0
            # レコード統計を有効化
            record-stats: true
            # テストモードを有効化
            test-mode: true
        other-user:
            maximumSize: 5
            record-stats: true
            test-mode: true
```

## アノテーション一覧

- @CacheConfig  
  キャッシュ名を指定。
  クラスに指定するとその他のアノテーションのデフォルト値となる。
  このアノテーションでデフォルト値を指定しない場合、
  各アノテーションで個別に指定する必要がある。
- @Cacheable  
  呼び出し時に戻り値をキャッシュする。
  次から処理をせずにキャッシュ値を返す。
  キャッシュ値を参照するような処理に付与する。
- @CachePut  
  呼び出し時に戻り値をキャッシュする。
  次の呼び出しでも処理をする。
  キャッシュ値を返さない。
  キャッシュ値の登録や、更新をするような処理に付与する。
- @CacheInvalidate  
  キャッシュをクリアする。
  個別の値または、全体をクリアする。
- @InvalidateOperations  
  複数の @CacheInvalidate を定義できる。一括削除処理などに付与する。
- @PutOperations  
  複数の @CachePut を定義できる。一括登録・更新処理などに付与する。 

[サンプルコード](../../../src/main/kotlin/micronaut/kotlin/coroutine/sample/CacheController.kt)

実行結果

```
Start index
groupId: 1, userId: 2, name: taro, memo: abc

■ キャッシュ値を返すことを確認する。再作成されていた場合は、name の値が変わる。
selectUser 呼び出し
--> User(groupId=1, userId=2, name=141, memo=none)
selectUser 再呼び出し
--> User(groupId=1, userId=2, name=141, memo=none) : キャッシュ値が返る

■ 異なる関数でも、引数の型と順番が同一であれば、共通のキャッシュ値を返すことを確認する。
selectUser2 呼び出し
--> User(groupId=1, userId=2, name=141, memo=none) : selectUser と同じキャッシュ値が返る

■ 引数の順序や、個数が異なる場合でも、parameters で指定したキーと一致するキャッシュ値が返ることを確認する。
selectUser3 呼び出し
--> User(groupId=1, userId=2, name=141, memo=none) : selectUser と同じキャッシュ値が返る

■ キャッシュ値の更新が行えることを確認する。
updateUser 呼び出し
--> User(groupId=1, userId=2, name=jiro, memo=none) : キャッシュ値を更新
selectUser 呼び出し
--> User(groupId=1, userId=2, name=jiro, memo=none) : 更新後のキャッシュ値が返る

■ 任意のキャッシュ値クリアが行えることを確認する。
updateUser 呼び出し
--> User(groupId=2, userId=3, name=saburo, memo=none) : 別のキャッシュ値を登録
deleteUser 呼び出し。指定のキャッシュ値をクリアする
selectUser 呼び出し
--> User(groupId=1, userId=2, name=540, memo=none) : 新規作成された値が返る。
selectUser 呼び出し（追加した値を取得）
--> User(groupId=2, userId=3, name=saburo, memo=none) : updateUser で登録したキャッシュ値が返る。

■ 全キャッシュ値クリアが行えることを確認する。
deleteAll 呼び出し。全キャッシュ値をクリアする
selectUser 呼び出し
--> User(groupId=1, userId=2, name=766, memo=none) : 新規作成された値が返る。
selectUser 呼び出し（追加した値を取得）
--> User(groupId=2, userId=3, name=231, memo=none) : 新規作成された値が返る。

■ 一括キャッシュ値登録・更新が行えることを確認する。
putUsersAll 呼び出し。キャッシュ値の一括登録・更新をする
--> User(groupId=10, userId=20, name=644, memo=none)
selectOtherUser 呼び出し
--> User(groupId=10, userId=20, name=644, memo=none) : 一括登録したキャッシュ値が返る。
selectUser 呼び出し
--> User(groupId=10, userId=20, name=644, memo=none) : 一括登録したキャッシュ値が返る。

■ 一括キャッシュ値クリアが行えることを確認する。
invalidateUsersAll 呼び出し。複数キャッシュ値の一括クリアをする
--> kotlin.Unit
selectOtherUser 呼び出し
--> User(groupId=10, userId=20, name=378, memo=none) : 新規生成値が返る。
selectUser 呼び出し
--> User(groupId=10, userId=20, name=962, memo=none) : 新規生成値が返る。キャッシュ名が別なため、上とは別の値となる。

Finish index
```
