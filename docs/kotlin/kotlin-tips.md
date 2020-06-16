<!-- toc -->
- Kotlin Tips
  - よく見る記法
  - コレクションのアクセス
  - 入れ子の Lambda ブロック内から、親の this を利用する方法
  - KotlinのListとSequenceって何が違うの？
  - Sequenceオブジェクト生成方法
  - generateSequence の実装例
  - @Target に指定する AnnotationTarget の種別
  - TypeAlias 機能
  - 変数の遅延初期化
  - 可視性修飾子

# Kotlin Tips

## よく見る記法
- `!`
  - [Calling Java code from Kotlin](https://kotlinlang.org/docs/reference/java-interop.html)
- `!!`、`?.`、`?:`
  - [Kotlin - Null Safety](https://kotlinlang.org/docs/reference/null-safety.html)
- `is`、`as`、`as?`
  - [Type Checks and Casts: 'is' and 'as'](https://kotlinlang.org/docs/reference/typecasts.html)
- `in`
  - [Basic Syntax](https://kotlinlang.org/docs/reference/basic-syntax.html)
- `<in T>`、`<out T>`
  - [Generics](https://kotlinlang.org/docs/reference/generics.html)  
  - [総称型(Generics)](generics.md)

## コレクションのアクセス
[Kotlin のコレクション使い方メモ](https://qiita.com/opengl-8080/items/36351dca891b6d9c9687)

## 入れ子の Lambda ブロック内から、親の this を利用する方法
Lambda ブロックを入れ子にする処理などで、ブロックの親の this を利用したい場合がある。
その場合、「this@xxxx」のように記述する。

以下にコード例を示す。  
sequence ブロック内で、```List<T>``` を利用する場合は、
```this@asSpecialSequence``` のように記述する。  
sequence ブロック内で、```Sequence<T>``` を明示的に利用する場合は、
```this@sequence``` のように記述する。

```kotlin
fun <T> List<T>.asSpecialSequence(): Sequence<T> {
    // List<T> の拡張関数なので、ここでの this は、List<T> となる。
    return sequence {
        // sequence ブロック内の this は、Sequence<T> となる。
        // List<T> を利用するには、以下のように記述する。
        for(i in this@asSpecialSequence) {
            yield(i)
        }
    }
}
```

## KotlinのListとSequenceって何が違うの？
[KotlinのListとSequenceって何が違うの？](https://qiita.com/ktzw/items/9aa251a44c11900c8b5f)

## Sequenceオブジェクト生成方法
[【Kotlin】Sequenceオブジェクト生成方法7種](https://qiita.com/sdkei/items/cc3d7846b09c87603718)

## generateSequence の実装例
[【Kotlin】generateSequenceを使って反復法を実装する](https://qiita.com/wrongwrong/items/87818dd0a996c54b94e3)

## @Target に指定する AnnotationTarget の種別

| 設定値 | 対象 |
| ----- | ----- |
| CLASS | Class、 interface または、object 。annotation クラスも含む |
| ANNOTATION_CLASS | Annotation クラスのみ |
| TYPE_PARAMETER | Generic type parameter (未サポート) |
| PROPERTY | クラスのプロパティ |
| FIELD | フィールド（プロパティのバッキングフィールドも含む） |
| LOCAL_VARIABLE | ローカル変数 |
| VALUE_PARAMETER | 関数や、コンストラクタの引数 |
| CONSTRUCTOR | プライマリまたは、セカンダリコンストラクタのみ |
| FUNCTION | 関数のみ (コンストラクタを除く) |
| PROPERTY_GETTER | プロパティゲッターのみ |
| PROPERTY_SETTER | プロパティセッターのみ |
| TYPE | 型。@JvmWildcard などで利用している。 |
| EXPRESSION | Any expression（未調査） |
| FILE | ファイル全体。@JvmPackageName などで利用している。 |
| TYPEALIAS | TypeAlias 機能 |

## TypeAlias 機能

- [【Kotlin】 TypeAliasで関数リテラルに名前を付ける](https://qiita.com/AAkira/items/8f4e465cb12d4d395d8b)
- [Kotlin typealiasの効果的な使いどころ](https://www.yo1000.com/kotlin-typealias/)
- [Kotlin: typealiasを使ってめんどうなアノテーションを省略する](https://satoshun.github.io/2018/07/typealias_omit_annotation/)
- [[Kotlin1.1-RC] Type aliasesの使い所](https://dev.classmethod.jp/articles/kotlin1-1-rc-type-aliases/)

## 変数の遅延初期化
- lateinit
  - プリミティブ型は不可
  - val 不可、var 可
  - nullable 不可
  - private 推奨(初期化が行われる前に外部からアクセスされるのを防ぐため？)
- by lazy
  - すべての型で利用可能
  - val、var 可
  - 一度だけ値の初期化を行う
  - 値はキャッシュされ、二回目以降は最初の値を常に返す
  - readonly
- Delegates.notNull
  - すべての型で利用可能
  - val、var 可
  - プロパティにアクセスする前に値をセットする必要がある
  - 「可変かつ non-null な値の初期化を遅延したい」時に使う

## 可視性修飾子
パッケージレベル（パッケージ内に直接宣言）の場合
- public: どこからでも利用可能。指定無しの場合 public となる
- private: 同じファイルの中でのみ利用可能
- internal: 同じモジュール内でのみ利用可能
- protected: 利用不可

クラス・インターフェースの場合
- public: どこからでも利用可能。指定無しの場合 public となる
- private: 同じクラス中でのみ利用可能
- internal: 同じモジュール内でのみ利用可能
- protected: サブクラスからも利用可能

## Class と KClass について
Class は、Java のクラス型。
KClass は、Kotlin のクラス型。

### 型判定
Java の場合
```
if(Integer.valueOf(1) instanceof Number) {}
```

Kotlin の場合
```kotlin
payload.kotlin.isSubclassOf(CustomCodeSealedClass::class)
```

## object 定義について
object は、Kotlin でのシングルトンオブジェクトを作成するための構文。

KClass から、object で定義したインスタンスを取得するには、以下のようにする。
```kotlin
val instance = payload.kotlin.objectInstance
```
