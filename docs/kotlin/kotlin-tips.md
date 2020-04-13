# Kotlin Tips

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

