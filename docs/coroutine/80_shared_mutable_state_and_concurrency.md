<!-- toc -->
- 変更可能な状態の共有と並行性
  - 並行性の問題
  - volatile は解決手段にならない
  - スレッドセーフなデータ構造
  - 細かいスレッド束縛制御
  - 粗いスレッド束縛制御
  - 相互排他

# 変更可能な状態の共有と並行性
Coroutine は、Dispatchers.Default などのマルチスレッドディスパッチャを指定して、
並行に実行することができるが、よくある並行性の多くの問題も持ち込むこととなる。
主な問題は、変更可能な状態へのアクセスの同期となる。
Coroutine 領域での、これら問題に対する多くの解決策は、
マルチスレッド世界での解決策に類似するが、Coroutine 特有の解決策もある。

## 並行性の問題
同じアクションを 1000 回実行する 100 個の Coroutine を起動し、
比較のための完了時間の計測も行う。

計測用の Suspend 関数を定義する。
```kotlin
suspend fun massiveRun(action: suspend () -> Unit) {
    // Coroutine 起動数
    val n = 100
    // Coroutine 内処理の繰り返し回数
    val k = 1000
    val time = measureTimeMillis {
        coroutineScope {
            repeat(n) {
                launch {
                    repeat(k) { action() }
                }
            }
        }
    }
    println("Completed ${n * k} actions in $time ms")
}
```

マルチスレッド処理となる Dispatchers.Default を指定して、
共有の変更可能な変数を加算していく、非常に単純な処理から始める。

```kotlin
var counter = 0
fun main() = runBlocking {
    withContext(Dispatchers.Default) {
        massiveRun {
            counter++
        }
    }
    println("Counter = $counter")
}
```

実行結果  
理論的には Counter の値は、100 Coroutine 処理から 1000 回 1 を加算するので、
100000 となるはず。  
実際には、100 Coroutine 処理が、非同期で複数のスレッドから、同時にカウンタを加算するため、
Counter が 100000 になることは、ほぼ無い。

```
Completed 100000 actions in 27 ms
Counter = 56732
```

## volatile は解決手段にならない
変数を volatile で定義することで、並行性の問題が解決されるという、
一般的な誤解を検証する。

```kotlin
// Kotlin での volatile 定義方法
@Volatile
var counter = 0

fun main() = runBlocking {
    withContext(Dispatchers.Default) {
        massiveRun {
            counter++
        }
    }
    println("Counter = $counter")
}
```

実行結果  
この処理は、前より遅くなったが、依然として Counter は、100000 にならない。
なぜなら、volatile 変数は、読み取りと書き込みの
線形化（「atomic」に関する専門用語）を保証しているに過ぎないため。

```
Completed 100000 actions in 29 ms
Counter = 71421
```

## スレッドセーフなデータ構造
スレッドおよび、Coroutine の両方において、有効で一般的な解決策は、
スレッドセーフ（同期、線形化または、アトミック）なデータ構造を利用することとなる。
これにより、共有した状態を操作する場合に不可欠な、同期処理を提供できる。
単純な加算処理の場合、アトミックな操作を行う
 AtomicInteger クラスの incrementAndGet 関数を利用できる。
 
スレッドセーフなデータ構造を利用すれば、
特定の問題に対する、最速の解決策となる。
この解決策は、簡素なカウンタ、コレクション、キューおよび、一般的なデータ構造の
基本的な操作で、有効に機能する。
しかしながら、複雑な状態や、スレッドセーフな実装を、すぐに適用することが困難な、
複雑な操作を、簡単に拡張することは難しい。

```kotlin
var counter = AtomicInteger()

fun main() = runBlocking {
    withContext(Dispatchers.Default) {
        massiveRun {
            counter.incrementAndGet()
        }
    }
    println("Counter = $counter")
}
```

実行結果

```
Completed 100000 actions in 32 ms
Counter = 100000
```

## 細かいスレッド束縛制御
スレッドの束縛制御は、特定の共有する状態へのすべての操作を、
単一のスレッドで行うように束縛制御する手法となる。
通常、この手法は、UI アプリケーションで、すべての UI 状態を、
単一のアプリケーションスレッドや、イベントスレッドに限定するなどのように利用する。
この手法は、Coroutine のシングルスレッドコンテキストを利用して簡単に適用できる。

```kotlin
@OptIn(ObsoleteCoroutinesApi::class)
val counterContext = newSingleThreadContext("CounterContext")
var counter = 0

fun main() = runBlocking {
    withContext(Dispatchers.Default) {
        massiveRun {
            withContext(counterContext) {
                counter++
            }
        }
    }
    println("Counter = $counter")
}
```

実行結果  
上記コードは、頻繁にスレッドを束縛制御するため、動作が非常に遅くなる。
各加算処理は、withContext ブロックで、マルチスレッドの Dispatchers.Default から、
シングルスレッドに切り替わる。

```
Completed 100000 actions in 792 ms
Counter = 100000
```

## 粗いスレッド束縛制御
実際には、スレッドの束縛制御は、大きな塊で実行され、
状態を更新する業務処理の大部分は、単一のスレッドに束縛制御される。
次のコードでは、最初からシングルスレッドコンテキストで、
各 Coroutine を実行している。

```kotlin
@OptIn(ObsoleteCoroutinesApi::class)
val counterContext = newSingleThreadContext("CounterContext")
var counter = 0

fun main() = runBlocking {
    withContext(counterContext) {
        massiveRun {
            counter++
        }
    }
    println("Counter = $counter")
}
```

実行結果  
処理時間が短縮され、処理結果も正しい。

```
Completed 100000 actions in 37 ms
Counter = 100000
```

## 相互排他
並行性問題に対する、相互排他という解決策は、
共有の状態へのすべての変更を、同時に実行されることのない、
クリティカルセッションで保護するということ。
ブロッキングの世界では、通常、同期または、ReentrantLock を利用する。
Coroutine の代替は、ミューテックスと呼ばれている。
重要なセクションを区切るためのロックおよび、ロック解除機能がある。
主な相違点は、Mutex.lock が Suspend 関数であることであり、
スレッドをブロックしないこととなる。

withLock 拡張関数は、
```mutex.lock(); try { ... } finally { mutex.unlock() }``` 形式を
便利に扱えるようにしたもの。

```kotlin
var mutex = Mutex()
var counter = 0

fun main() = runBlocking {
    withContext(Dispatchers.Default) {
        massiveRun {
            mutex.withLock {
                counter++
            }
        }
    }
    println("Counter = $counter")
}
```

実行結果  
このコードでは、ロック粒度が細かいため、処理時間が遅くなっている。
特定の共有する状態を、定期的に変更するような場合に適していて、スレッドの制限もしない。

```
Completed 100000 actions in 256 ms
Counter = 100000
```

## アクター
アクターは、Coroutine と、Coroutine 内部に保持する状態および、
他の Coroutine との通信をする Channel の組合せで構成する。
単純なアクターは、関数として定義できるが、
複雑な状態を扱うアクターは、クラス定義が適している。

アクターのメールボックス Channel を、スコープに結合して、
メッセージを受信し、送信 Channel を結果のジョブオブジェクトに結合する
アクター Coroutine ビルダがある。
これにより、アクターへの単一の参照をハンドルとして持ち運ぶことができる。

アクターを利用する、最初のステップは、アクターが処理する、
メッセージのクラスを定義することとなる。
Kotlin のシールドクラスは、この用途に適している。
カウンターを加算するための IncCounter メッセージと、
その値を取得するための GetCounter メッセージを使用して、
CounterMsg シールドクラスを定義する。

>シールドクラス（sealed class）とは、定義したクラスの継承を制限する仕組み。  
>継承クラスは、シールドクラスと同一のファイル内でのみ定義できる。
>継承クラスは、それぞれ全く別の実装が行えるため、用途はかなり広い。
>ある意味では、列挙型クラスの拡張とも言える。
>継承クラスが限定されるため、when での条件分岐に default が不要となり、
>かつ、すべて継承クラスを条件に列挙しないとコンパイルエラーとなる。

GetCounter メッセージは、応答を送信する必要がある。
CompletableDeferred 通信プリミティブは、将来知られる（通信される）
単一の値を表すために利用する。

アクターが、どのコンテキストで実行されるかは、問題とならない。
アクターは、Coroutine であり、Coroutine は、順次実行されるため、
特定の Coroutine でのみ、状態の変更を許可するように制限することで、
共有された状態の変更問題の解決策として有効に機能する。

以下のコードから読み取れるように、アクター内部で保持する状態は、
メッセージを通じて、アクターでのみ変更できるため、ロックが不要となる。
アクターは、負荷がかかった状態では、Channel のキューが空になるまで、
コンテキストの切替をせずに処理できるため、ロックするよりも効率的に処理できる。

>アクター Coroutine ビルダは、プロデュース Coroutine ビルダと対になることに注意。
>アクターは、メッセージを受信する Channel に関連付けられているが、
>プロデューサ（main の withContext ブロック）は、要素を送信する Channel に関連付けられている。

```kotlin
// カウンターアクターの Channel へ送信するメッセージタイプを定義
sealed class CounterMsg

// カウンタ加算用のメッセージを定義（シングルトンオブジェクト）
object IncCounter : CounterMsg()

// メッセージ取得用のメッセージを定義（クラス）
class GetCounter(val response: CompletableDeferred<Int>) : CounterMsg()

// この関数は、カウンターアクターを生成し、起動する。
@OptIn(ObsoleteCoroutinesApi::class)
fun CoroutineScope.counterActor() = actor<CounterMsg> {
    var counter = 0
    // アクターに定義されている Channel を処理
    for (msg in channel) {
        when (msg) {
            // カウンタ加算メッセージの場合
            is IncCounter -> counter++
            // カウンタ取得メッセージの場合
            is GetCounter -> msg.response.complete(counter)
        }
    }
}

fun main() = runBlocking<Unit> {
    val counter = counterActor()
    withContext(Dispatchers.Default) {
        massiveRun {
            counter.send(IncCounter)
        }
    }
    val response = CompletableDeferred<Int>()
    counter.send(GetCounter(response))
    println("Counter = ${response.await()}")
    counter.close()
}
```

実行結果  

```
Completed 100000 actions in 451 ms
Counter = 100000
```

