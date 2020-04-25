<!-- toc -->
- Around Advice
  - Around Advice の作成方法
  - @Around アノテーション
  - @Factory Beans に付与する AOP アドバイスについて

# Around Advice
Around Advice とは、メソッドの動作の前後で、処理を修飾する仕組みとなる。

## Around Advice の作成方法
Around Advice は、MethodInterceptor を実装することで作成する。
[サンプル Interceptor コード](../../src/main/kotlin/micronaut/kotlin/coroutine/sample/AroundAdviceController.kt)
は、null 値のパラメータを拒否する。

## @Around アノテーション
@Around アノテーションを付与すると、コンパイル時にプロキシクラスが生成される。
@Around アノテーションに以下の引数を設定すると、プロキシクラスの振る舞いを変更できる。

- proxyTarget （ デフォルトは false ）  
  true に設定するとプロキシは、super() を呼び出すサブクラスの代わりに、
  元の Bean インスタンスに委任する。
- hotswap （ デフォルトは false ）  
  true に設定するとプロキシは、proxyTarget = true と同様の効果に加え、
  生成するプロキシで、HotSwappableInterceptedProxy を実装し、
  各メソッド呼び出しを ReentrantReadWriteLock でラップすることで、
  実行時にターゲットインスタンスをスワップできるようにする。
- lazy （ デフォルトは false ）  
  false に設定すると、プロキシインスタンスが作成される時に、即座にプロキシターゲットを初期化する。
  true に設定すると、プロキシターゲットの初期化を、各メソッド呼び出しまで遅延する。

## @Factory Beans に付与する AOP アドバイスについて
@Factory Beans に付与する AOP アドバイスは、クラスと、メソッドに付与する場合で、それぞれ振る舞いが変わる。

キャッシュ AOP アドバイスを @Factory Beans クラスに付与した場合、
@Factory Beans の public メソッド全体に適用される。
（非 public メソッドには適用されない。）
この場合は、myBean() メソッドで生成する MyBean のインスタンスが、キャッシュされる。

メソッドに Bean スコープ（＠Prototype 等）と共に、キャッシュ AOP アドバイスを付与した場合、
@Factory Beans が生成する Bean に AOP アドバイスが適用される。
この場合は、MyBean の public メソッドすべてに、キャッシュ機能が付与される。
しかし、MyBean インスタンス自体のキャッシュはされない。
