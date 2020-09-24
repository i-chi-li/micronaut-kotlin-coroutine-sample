# Declarative HTTP Clients with @Client
（@Client を使用した宣言型 HTTP クライアント）

下位レベルの HTTP クライアントの動作についての
理解が得られたので、Clientアノテーションを介した
宣言型クライアントに対する Micronaut のサポートを説明する。

基本的に @Client アノテーションは、
任意のインターフェースまたは、抽象クラスで宣言でき、
Introduction Advice を使用することで、
コンパイル時に抽象メソッドが実装され、
HTTP クライアントの作成を大幅に簡素化できる。

では、簡単な例から。次のようなクラスがある。

`Pet.java`
```kotlin
class Pet {
    var name: String? = null
    var age: Int = 0
}
```

新しい Pet インスタンスを保存するための
クライアント、サーバ共通のインターフェースを定義する。

`PetOperations.java`
```kotlin
import io.micronaut.http.annotation.Post
import io.micronaut.validation.Validated
import io.reactivex.Single

import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank

@Validated
interface PetOperations {
    @Post
    fun save(@NotBlank name: String, @Min(1L) age: Int): Single<Pet>
}
```

サーバー側とクライアント側の両方で使用できる
Micronaut の HTTP アノテーションが、
インターフェースでどのように使用されているかに注目。
また、`javax.validation` 制約を使用して引数を検証できる。

@Produces や @Consumes などの一部のアノテーションは、
サーバー側とクライアント側の使用法のセマンティクスが、
異なることに注意。

たとえば @Produces コントローラーメソッド（サーバー側）では、
メソッドの戻り値が @Produces どのようにフォーマットされるかを示し、
クライアントでは、サーバーに送信されるときに、
メソッドのパラメーターが、どのようにフォーマットされるかを示す。

これは、最初は少し混乱するが、
生成/消費サーバーとクライアント間の
異なるセマンティクスを考慮すると、実際には論理的である。
サーバーは、引数を消費してクライアントに応答を返すが、
クライアントは、引数を消費して出力をサーバへ送信する。

javax.validation 機能を使用するには、validation モジュールが必要となる。

```groovy
implementation("io.micronaut:micronaut-validator")
```

Micronaut のサーバー側で PetOperations インターフェースを実装する。

`PetController.java`
```kotlin
import io.micronaut.http.annotation.Controller
import io.reactivex.Single

@Controller("/pets")
open class PetController : PetOperations {

    override fun save(name: String, age: Int): Single<Pet> {
        val pet = Pet()
        pet.name = name
        pet.age = age
        // save to database or something
        return Single.just(pet)
    }
}
```

次は、コンパイル時にクライアントを自動実装するために、
テストコード（src/test/java） として @Client を使用した、宣言型クライアントを定義する。

`PetClient.java`
```kotlin
import io.micronaut.http.client.annotation.Client
import io.reactivex.Single

@Client("/pets") 
interface PetClient : PetOperations { 

    override fun save(name: String, age: Int): Single<Pet>  
}
```

上記の例では、save メソッドをオーバーライドしていることに注意。
`-parameters` オプションを指定せずにコンパイルする場合、
Java はパラメーター名をバイトコードに保持しないため、これが必要になります。
`-parameters` を指定してコンパイルする場合は、オーバーライドする必要はありません。

クライアントを定義したら、@Inject で簡単に配置できる。
@Client に指定できる値は次のいずれかとなる。

- 絶対 URI。例 https://api.twitter.com/1.1
- 相対 URI。この場合、対象となるサーバーは、現在のサーバーになる（テストに役立つ）。
- サービス識別子。このトピックの詳細については、サービスの検出に関するセクションを参照。

本番環境では、通常、サービス ID と
サービスディスカバリを使用してサービスを自動的に検出する。

上記の save メソッドに関して注意すべき、もう1つの重要な点は、
Single 型を返すこととなる。
これは非ブロッキングリアクティブタイプであり、
通常は HTTP クライアントをブロックしない。
ブロックする HTTP クライアント（単体テストケースなど）を
記述したい場合があるが、これはまれなケースとなる。

次の表は、@Client で使用できる、一般的な戻り値の型を示している。

表1 Micronaut Response Types

| 型 | 説明 | コード例 |
| --- | --- | --- |
| Publisher | パブリッシャーインターフェイスを実装するすべてのタイプ | Flowable\<String> hello() |
| HttpResponse | HttpResponse とオプションのレスポンスボディタイプ | Single\<HttpResponse\<String>> hello() |
| Publisher | POJO を発行するパブリッシャー実装 | Mono\<Book> hello() |
| CompletableFuture | Java CompletableFuture インスタンス | CompletableFuture\<String> hello() |
| CharSequence | String 型のようなブロッキングネイティブ型。 | String hello() |
| T | 任意の単純な POJO タイプ。 | Book show() |

一般に、パブリッシャーインターフェイスに変換できる
リアクティブタイプは、RxJava 1.x、RxJava 2.x、および
Reactor 3.x で定義されたリアクティブタイプを含む（ただしこれらに限定されない）
戻りタイプとしてサポートされる。

CompletableFuture インスタンスを返すこともサポートされており、
他のタイプを返すとブロッキング要求が発生するため、
テスト以外には推奨されないことに注意。

