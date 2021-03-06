<!-- toc -->
- [Kotlin and AOP Advice](https://docs.micronaut.io/latest/guide/index.html#openandaop)

# [Kotlin and AOP Advice](https://docs.micronaut.io/latest/guide/index.html#openandaop)
Micronaut は、リフレクションを利用せず、コンパイル時の AOP API を提供する。
Micronaut の AOP アドバイスを利用すると、
コンパイル時に AOP 機能を持ったサブクラスを生成する。
Kotlin クラスは、デフォルトで、final であるため、サブクラス生成時に問題となる。
この問題の解決方法として、Kotlin all-open プラグインが提供されている。
Kotlin all-open プラグインは、指定したアノテーションを付与したクラスを、
自動的に open にする機能を持つ。
このプラグインを利用できない場合は、AOP アドバイスを付与した、
すべてのクラスおよび、関数を open で宣言する必要がある。
