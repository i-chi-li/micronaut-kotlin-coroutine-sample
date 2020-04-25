<!-- toc -->
- Introduction Advice

# Introduction Advice
Introduction Advice は、メソッドの実装を提供する機能。
インターフェースをインジェクションした場合などに、
実装クラスがなくても、値を返すなどができる。
実装方法は、Around advice と類似している。

サンプルコードでは、固定値を返すスタブ機能を実装しているが、
実装しだいで、さまざまな機能を持たせることができる。
Micronaut の @Client アノテーションが、代表的な例となる。

[サンプルコード](../../src/main/kotlin/micronaut/kotlin/coroutine/sample/IntroductionAdviceController.kt)

