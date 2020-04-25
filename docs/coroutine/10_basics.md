<!-- toc -->
- Coroutine の基礎
  - Coroutine ライブラリについて
  - Coroutine の構成要素
    - Coroutine スコープ
    - ディスパッチャ
    - Suspend 関数
  - 処理完了待ち
    - join 関数
    - 構造化された並行性(Structured concurrency)
    - coroutineScope ビルダ
  - 軽量な Coroutine
  - デーモンスレッドのような Global スコープの Coroutine
- Thread と Coroutine の違い

# Coroutine の基礎

Coroutine とは、非同期処理や、非ブロッキング処理を実現する機能。  
Coroutine は、基本的に軽量スレッドとして利用する。  

Coroutine に関する機能に、```@ExperimentalCoroutinesApi``` や、
```@ObsoleteCoroutinesApi``` アノテーションが付与されているものがある。

- ```@ExperimentalCoroutinesApi```
  - 今後、変更される可能性がある。そのままの場合もある
  - 利用する場合は、後述の設定が必要となる。
- ```@ObsoleteCoroutinesApi```
  - 設計上の問題がある
  - 現時点では、代替機能が無い
  - 今後は非推奨となる

## 非同期処理
非同期処理とは、処理開始後、結果を待たずに次の処理へ進み、
処理が完了した時点で通知をするような処理を指す。
処理完了の通知には、一般的にコールバックという仕組みを利用する。
処理が完了した時点で、コールバック処理が実行され、コールバック処理内で結果を取得できる。

## 非ブロッキング処理
非ブロッキング処理は、処理開始後、結果を待たずに次の処理へ進む点は、
非同期処理と同様だが、処理完了の通知をしない点で異なる。
結果を取得するには、呼び出し元で処理が完了しているかを確認し、
完了していれば、結果を取得する。
完了していない場合は、必要があれば他の処理を行った後、
再び確認を行うような処理の流れとなる。

## @ExperimentalCoroutinesApi 機能利用時の設定
```@ExperimentalCoroutinesApi``` が付与された機能を利用する場合には、
以下のいずれかのアノテーションをクラスまたは、メソッドに付与する。
(@UseExperimental は非推奨となったので、代わりに @OptIn を利用する)

- ```@ExperimentalCoroutinesApi```
- ```@OptIn(ExperimentalCoroutinesApi::class)```

```@OptIn(ExperimentalCoroutinesApi::class)``` を利用した場合、
コンパイル時に以下のように警告が表示される。
```
w: ～: This class can only be used with the compiler argument '-Xopt-in=kotlin.RequiresOptIn'
```

この警告への対応は、```build.gradle``` に ```freeCompilerArgs``` 行を以下のように追加する。
設定後、IntelliJ のエディタ上でも警告が消える。

```groovy
compileKotlin {
    kotlinOptions {
        jvmTarget = '1.8'
        //Will retain parameter names for Java reflection
        javaParameters = true
        // 「@ExperimentalCoroutinesApi」が付与された機能を利用する場合に追加
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
}
```

## @FlowPreview アノテーションについて
```@FlowPreview``` アノテーションは、Flow のプレビュー機能に付与されているアノテーションとなる。
Flow のプレビュー機能には、バイナリ互換性とソース互換性の両方を含む、下位互換性の保証はない。
API とセマンティクスは、次のリリースで変更される可能性がある。  
原則として利用しないこと。

## Coroutine ライブラリについて
Coroutine を利用するには、ライブラリを導入する必要がある。
ライブラリの一覧は、
[GitHub - Kotlin/kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines)
を参照。

以下調べたものだけ概要を説明をする。
- org.jetbrains.kotlinx:kotlinx-coroutines-core  
  - 必須ライブラリ
  - Coroutine の基礎となる機能を含む
  - Java 標準クラスへの機能拡張を含む
- org.jetbrains.kotlinx:kotlinx-coroutines-reactive
  - リアクティブストリーム機能
  - [Micronaut](https://micronaut.io/) で Coroutine を利用する場合には必須
- org.jetbrains.kotlinx:kotlinx-coroutines-debug
  - デバッグ用ライブラリ
  - Coroutine に関する追加情報を取得できる
  - オーバーヘッドが大きいが、一部機能を切ることにより、本番稼働時にも組み込み可能
  - デバッグ JVM エージェント
  - Coroutine の状態監視、スタックトレース、一覧表示などの機能がある
- org.jetbrains.kotlinx:kotlinx-coroutines-test
  - テスト用ユーティリティライブラリ
  - JUnit 4 のみ対応（Ver 1.3.5 時点）

## Coroutine の構成要素
Coroutine は、以下の要素で構成される（主要なもののみ）。
- Coroutine スコープ
- Coroutine ビルダ
- ディスパッチャ
- ジョブ
- Suspend 関数

Coroutine の実装例

```kotlin
fun main() {
    // Coroutine を生成し、バックグラウンドで実行開始
    // launch の戻り値は「ジョブ」
    val job =
        // GlobalScope は、「Coroutine スコープ」
        GlobalScope
            // launch は、「Coroutine ビルダ」
            .launch(
                // 「ディスパッチャ」指定
                Dispatchers.Default,
                // 実行タイミング指定
                start = CoroutineStart.DEFAULT
            ) {
                // delay は、「Suspend 関数」
                delay(1000L)
                println("World!")
            }
    // メインスレッドで並行して継続実行
    println("Hello,")
    // runBlocking は、「Coroutine ビルダ」
    // Coroutine 処理となるが、メインスレッドをブロックする
    runBlocking {
        // 「ジョブ」が完了するまで待機
        // join は、「Suspend 関数」
        job.join()
    }
}
```


### Coroutine スコープ
```Coroutine スコープ```は、Coroutine のスコープ（生存期間）を規定する。
- GlobalScope  
  このコンテキストで起動した Coroutine の有効期間は、アプリケーションの起動期間と同じとなる。  
  ただし、生成された Coroutine は、アプリケーションが終了すると、デーモンスレッドと同様に処理未完了でも終了する。
- MainScope  
  メインスレッドが、スコープとなる。Job は、スーパーバイザモード（子がキャンセルされても影響を受けない）で動作する。
- CoroutineScope  
  指定したコンテキストで、新たな Coroutine スコープを生成する。
  コンテキストに Job が含まれていない場合、通常の Job を生成する。

### Coroutine ビルダ
```Coroutine ビルダ```は、Coroutine 処理を生成する。
ビルダ毎に呼び出し可能な場所が異なる。
呼び出し可能な場所は、Coroutine スコープ内、Suspend 関数内または、どこでも可能に分類される。

- launch  
  Suspend 関数。Coroutine スコープ内で呼び出し可能。
  戻り値の無い処理を定義する。  
  新しい Coroutine を起動する。現在のスレッドはブロックしない。  
  戻り値には、```Job``` インスタンスが返る。  
  thread { ... }（Thread と同じ）と用途は同じ。  
  デフォルトでは、即時実行。  
  この Coroutine でキャッチしなかった例外は、デフォルトでは、親ジョブをキャンセルする動作となる。
- async  
  Suspend 関数。Coroutine スコープ内で呼び出し可能。
  戻り値のある処理を定義する。  
  新しい Coroutine を非同期で起動する。現在のスレッドはブロックしない。  
  オプションにより、即時実行や、遅延実行など選択可能。
  戻り値には、```Deferred```` インスタンスが返る。
- runBlocking  
  通常の（Suspend ではない）関数。どこでも呼び出し可能。
  新しい Coroutine を起動する。現在のスレッドをブロックする（割込可能）。
  ブロッキング処理と非ブロッキング処理との橋渡しに利用する。  
  コルーチンからは利用しないこと。  
  例えば、```main``` 関数の場合、```GlobalScope.launch``` で処理を開始すると、
  現在のスレッドをブロックしないため、即座に ```main``` 関数は終了し、アプリケーションも終了する。  
  したがって、```GlobalScope.launch``` で起動した処理が完了できない。  
  このように、スレッドをブロックする前提の処理との橋渡しのために ```runBlocking``` を利用する。
  runBlocking<Unit> の総称型指定（Unit）は、main 関数の戻り値が Unit であることを明示するため。
  指定しない場合で、runBlocking ブロックの最後で Unit 以外の値をもった行があると、
  型推論で、自動的に戻り値が Unit 以外となりエラーとなるため明示が必要。
  ```kotlin
  fun main() = runBlocking<Unit> { // start main coroutine
      GlobalScope.launch { // launch a new coroutine in background and continue
          delay(1000L)
          println("World!")
      }
      println("Hello,") // main coroutine continues here immediately
      delay(2000L)      // delaying for 2 seconds to keep JVM alive
  }
  ```
- coroutineScope  
  Suspend 関数。Suspend 関数内で呼び出し可能。
  新しい Coroutine を起動する。現在のスレッドは、ブロックしない。
  指定された Coroutine ブロックと、その子 Coroutine が、すべて完了するまで、中断する。
  Coroutine ブロックや、子 Coroutine が、キャンセルされると、
  このスコープや、他のすべての子 Coroutine や、親スコープもキャンセルされる。
- supervisorScope  
  Suspend 関数。Suspend 関数内で呼び出し可能。
  新しい Coroutine を起動する。現在のスレッドは、ブロックしない。
  親スコープから、Coroutine コンテキストを継承するが、
  ジョブを SupervisorJob で上書きする。
  子 Coroutine のキャンセルは、このスコープおよび、他の子 Coroutine には影響しない。
  このスコープのキャンセルは、すべての子 Coroutine をキャンセルするが、
  親スコープは、キャンセルしない。
- flow  
  通常関数。どこでも呼び出し可能。現在のスレッドは、ブロックしない。
  値の取得時点で処理を開始し、取得の度に値生成処理が実行されるストリーム処理を行う。
  コールドという特性を持ち、ストリーム処理を開始すると、
  必ず最初から値の生成処理が始まる。
- withTimeout  
  Suspend 関数。Suspend 関数内で呼び出し可能。
  タイムアウト有りで、Coroutine を起動する。現在のスレッドは、ブロックしない。
  戻り値を返せる。
  タイムアウトになると、TimeoutCancellationException が発生する。
- withTimeoutOrNull  
  withTimeout と同様。
  ただし、タイムアウトになると、戻り値に null が返る。
- withContext  
  withContextは、現在の Coroutine を切り替えることなく、
  Coroutine コンテキストのみを切り替えることができる。
  したがって、withContext(Dispatchers.IO) {...} のように、
  ディスパッチャを指定しても、スレッドは切り替わらない。
  withContext は、新たに Coroutine を生成しないため、Coroutine ビルダではない。

### ディスパッチャ
```ディスパッチャ```は、生成する Coroutine 処理の実行スレッドを規定する。
以下のようなディスパッチャが利用可能。
- Dispatchers.Default  
  JVM 共通バックグラウンドスレッドプールのスレッドで実行される。
  スレッドプールの最大数は、最低 2 個で、CPU プロセッサ数と同数となる。
  それ以上のスレッドが必要になった場合、スレッドが空くまで待機となる。
- Dispatchers.Main  
  main スレッドで実行される。
- Dispatchers.IO  
  外部インターフェースとの通信などに利用する。
  スレッドプール待ちでブロックされずに実行できる。
  JVM 共通バックグラウンドスレッドプールにスレッドが追加され、そのスレッドで実行する。
  追加したスレッドは、必要に応じて開放もされる。
  スレッド数の上限は、64 または、CPU プロセッサ数のどちらか大きい方となる。
- Dispatchers.Unconfined  
  特殊なディスパッチャ。基本的に利用しないこと。
  最初の Suspend 関数までは、呼び出し元のスレッドで実行され、
  再開後は、異なるスレッドで実行される。
- newSingleThreadContext  
  新規スレッドを作成して実行する。専用のスレッドは、非常に高コストとなる。
  作成したスレッドの close や、再利用は、独自に管理する必要がある。

### ジョブ
```ジョブ```とは、生成された Coroutine 処理を指す。
以下のようなものがある。
- Job  
  launch の戻り値。処理の戻り値なし。
- Deferred  
  async の戻り値。処理の戻り値あり。
  Job を継承している。

### Suspend 関数
```Suspend 関数```とは、```suspend``` 修飾子を付与して定義した関数を指す。  
```Suspend 関数```は、Coroutine 処理内でしか呼び出すことができない。  
```Suspend 関数```は、呼び出し元スレッドを中断させ、別の処理を行えるように（スレッド切替）する境界となる。
```Suspend 関数```には、delay 関数（Thread.sleep(...)と用途は同じ）などがある。  
中断（Suspend）とは、呼び出し元スレッドを待機（wait）させるのではなく、
中断し、別の処理をさせるように切り替えるという意味となる。

## 処理完了待ち
Coroutine の処理完了待ちをする方法には、
```Job.join() 関数```、```構造化された並行性(Structured concurrency)``` および、
```coroutineScope ビルダ``` などがある。

### join 関数
launch 関数などの戻り値（Job インスタンス）の join() 関数で完了の待ち合わせが可能。

### 構造化された並行性(Structured concurrency)
>構造化された並行性(Structured concurrency) は、
>Coroutine に親子関係を持たせる仕組み。  
>親の Coroutine は、子供の Coroutine が終了するまで待つ。  
>Coroutine 内で、Coroutine が持つ Coroutine ビルダ（launch など）を呼び出すことで、
>（```launch{ launch { ... } }``` のように）
>利用可能となる。

```GlobalScope.launch``` ビルダを使用する場合、
Coroutine は軽量ではあるが、実行中にメモリリソースを消費する。
新しく起動した Coroutine への参照が無くなっても、依然として実行し続ける。
Coroutine 内のコードが、ハング（無限に待機など）するような事態で、
Coroutine を起動しすぎて、メモリ不足になった場合は、どのようになるだろうか？
起動したすべての Coroutine への参照を、手動で保持し、
それらを待ち合わせる（join）必要があると、エラーが発生しやくすなる。

望ましい解決策として、 **構造化された並行性** を利用できる。
通常のスレッド（スレッドは、常にグローバル）のように、
GlobalScope で Coroutine を起動する代わりに、
実行する処理の任意のスコープで、Coroutine を実行できる。

以下のコードは、```runBlocking``` ビルダを使用して、メイン関数を Coroutine に変換している。
```runBlocking``` ビルダを含む、すべての Coroutine ビルダは、生成した CoroutineScope を、
コードブロックのスコープに含める（子にする）。
外部スコープ（ここでは、```runBlocking```）は、スコープ内で起動された、
すべての Coroutine が完了するまで完了しないため、
明示的に完了を待ち合わせる必要がなく、スコープ内で Coroutine を起動できる。
したがって、実装をより単純化することができる。

```kotlin
fun main() = runBlocking {
  println("start main")
  launch {
      println("start launch")
      delay(1000L)
      println("finish launch")
  }
  println("finish main")
}
```

実行結果は以下のように Coroutine の終了を待つ処理となる。

```
start main
finish main
start launch
finish launch
```

以下のように孫階層も可能。

```kotlin
fun main() = runBlocking {
  println("start main")
  launch {
      println("start launch")
      launch {
          println("start child")
          delay(2000L)
          println("finish child")
      }
      delay(1000L)
      println("finish launch")
  }
  println("finish main")
}
```

結果は、以下のように 孫 Coroutine も終了するまで待機するようになる。

```
start main
finish main
start launch
start child
finish launch
finish child
```

### coroutineScope ビルダ
多様なビルダによって生成される Coroutine スコープに加えて、
```coroutineScope``` ビルダを利用して、独自のスコープを定義することができる。
独自の親 Coroutine スコープ内で生成した、すべての子 Coroutine が、
終了するまで、親 Coroutine も終了しない。

```runBlocking``` ビルダと ```coroutineScope``` ビルダは、共通して、それ自身と、
すべての子が完了するのを待機する。
異なる点として、```runBlocking``` ビルダは、呼び出し元のスレッドをブロックするのに対し、
```coroutineScope``` ビルダは、呼び出し元スレッドを Suspend して開放し、
他の処理を行えるようにすることである。
この違いがあるため、```runBlocking``` ビルダは通常の関数であり、
```coroutineScope``` ビルダは、Suspend 関数となっている。

以下 ```coroutineScope``` ビルダの使用例

```kotlin
fun main() = runBlocking {
    launch {
        delay(200L)
        println("Task from runBlocking")
    }
    coroutineScope {
        launch {
            delay(500L)
            println("Task from nested launch")
        }
        delay(100L)
        println("Task from coroutine scope")
    }
    println("Coroutine scope is over")
}
```

実行結果  
```coroutineScope``` 処理完了の待ち合わせを明示的に行わずに、
最後のメッセージが、```coroutineScope``` 処理完了後に出力されていることに注目。

```
Task from coroutine scope
Task from runBlocking
Task from nested launch
Coroutine scope is over
```

## 拡張関数によるリファクタリング
```launch {...}``` 内のコードブロックを、拡張関数として定義する。
以下のように、「関数の抽出」リファクタリングは、
新しい Suspend 関数を定義することで行う。
これが最初の Suspend 関数となる。
Suspend 関数は、通常の関数と同様に、Coroutine 内で利用可能だが、
加えて、delay 関数などを含む、Suspend 関数を利用すると、
Coroutine の実行を一時的に中断する役割もある。

```kotlin
suspend fun doWorld() {
    delay(1000)
    println("World!")
}

fun main() = runBlocking<Unit> {
    launch { doWorld() }
    println("Hello,")
}
```
実行結果
```
Hello,
World!
```

しかし、抽出された関数に現在のスコープで呼び出される、Coroutine ビルダが含まれているような場合、
抽出された関数の suspend 修飾子では、不十分である。
```doWorld``` 関数を ```CoroutineScope``` の拡張関数にする方法もあるが、API が不明確のため、常に有効な手段とは限らない。
慣用的な解決策は、ターゲット関数を含む、クラスのフィールドとして、
明示的な ```CoroutineScope``` を持つか、外部クラスが ```CoroutineScope``` を実装する時に、
暗黙的なフィールドを持つこととなる。
```CoroutineScope``` （```coroutineContext```）を使用できるが、このメソッドの実行範囲を制御できなくなるため、
そのようなアプローチは、構造的に安全ではない。
このビルダを使用できるのは、プライベート API のみとなる。

## 軽量な Coroutine
以下のコードは、10 万 Coroutine を生成し、各 Coroutine 処理が、1 秒後に "." を出力する。
これをスレッドで試すとどうなるだろうか・・・

```kotlin
fun main() = runBlocking<Unit> {
    repeat(100_000) {
        launch {
            delay(1000)
            print(".")
        }
    }
}
```

## デーモンスレッドのような Global スコープの Coroutine
次のコードは、```GlobalScope``` で実行時間の長い Coroutine を生成し、
"I'm Sleeping" を 1 秒に 2 回出力し、暫くしてからメイン関数から戻る。

```kotlin
fun main() = runBlocking<Unit> {
    GlobalScope.launch {
        repeat(1000) {
            println("I'm sleeping $it ...")
            delay(500)
        }
    }
    delay(1300)
}
```

実行結果

```
I'm sleeping 0 ...
I'm sleeping 1 ...
I'm sleeping 2 ...
```

デーモンスレッドと同様に、```GlobalScope``` で起動した Coroutine は、
実行中であっても、プロセスは継続しない。

# Thread と Coroutine の違い
生成コストと、実行時間の違いを比べてみる。
ただし、単純化しているため、実際の Thread 処理とは、乖離している可能性がある。

まずは、Thread 利用例。環境によっては、メモリ不足などでエラーとなる。  
当方の環境での実行時間は、35 秒程度であった。

```kotlin
fun main() {
    val start = System.currentTimeMillis()
    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        println("\ntime: ${System.currentTimeMillis()-start}")
    })
    repeat(100_000) {
        thread {
            Thread.sleep(1000L)
            print(".")
        }
    }
}
```

続いて、Coroutine 利用例。  
当方の環境での実行時間は、2 秒程度であった。
実行スレッド名を確かめたが、すべて main スレッドで処理されていた。

```kotlin
fun main() = runBlocking {
    val start = System.currentTimeMillis()
    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        println("\ntime: ${System.currentTimeMillis()-start}")
    })
    repeat(100_000) {
        launch {
            delay(1000L)
            print(".")
        }
    }
}
```

蛇足として、```Executor``` の ```CachedThreadPool``` で同様に確認した。
多少条件を変えながら実行してみたが、Thread の場合とそれほどの差は無かった。
しかし、```CachedThreadPool``` 以外では、さらに遅くなった。

```kotlin
fun main() {
    val start = System.currentTimeMillis()
    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        println("\ntime: ${System.currentTimeMillis()-start}")
    })
    val exec = Executors.newCachedThreadPool()
//    val exec = Executors.newFixedThreadPool(100)
//    val exec = Executors.newScheduledThreadPool(100)
    repeat(100_000) {
        exec.execute {
            Thread.sleep(1000L)
            print(".")
//            println(Thread.currentThread().name)
        }
    }
    exec.shutdown()
    exec.awaitTermination(1, TimeUnit.MINUTES)
}
```
