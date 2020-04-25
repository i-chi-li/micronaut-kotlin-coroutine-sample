<!-- toc -->
- Kotlin Tips
  - 入れ子の Lambda ブロック内から、親の this を利用する方法
  - KotlinのListとSequenceって何が違うの？
  - Sequenceオブジェクト生成方法
  - generateSequence の実装例
  - @Target に指定する AnnotationTarget の種別
  - TypeAlias 機能

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
