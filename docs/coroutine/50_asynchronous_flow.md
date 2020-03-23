<!-- toc -->
- [非同期 Flow 処理](#非同期-flow-処理)
  - [複数値の表現](#複数値の表現)
  - [シーケンス](#シーケンス)
  - [Suspend 関数](#suspend-関数)
  - [Flow 処理](#flow-処理)
  - [Flow はコールド](#flow-はコールド)
  - [Flow 終端オペレータ](#flow-終端オペレータ)
  - [Flow 処理の直列性](#flow-処理の直列性)
  - [Flow のコンテキスト](#flow-のコンテキスト)
  - [withContext ビルダを利用した間違った送出](#withcontext-ビルダを利用した間違った送出)
  - [flowOn オペレータ](#flowon-オペレータ)
  - [buffer オペレータ](#buffer-オペレータ)
  - [conflate オペレータ](#conflate-オペレータ)
  - [collectLatest オペレータ](#collectlatest-オペレータ)
  - [Flow の複数組合せ](#flow-の複数組合せ)
    - [zip オペレータ](#zip-オペレータ)
    - [combine オペレータ](#combine-オペレータ)
  - [Flow の平坦化](#flow-の平坦化)
    - [flatMapConcat オペレータ](#flatmapconcat-オペレータ)
    - [flatMapMerge オペレータ](#flatmapmerge-オペレータ)
    - [flatMapLatest オペレータ](#flatmaplatest-オペレータ)
  - [Flow 例外](#flow-例外)
    - [collect を try-cache](#collect-を-try-cache)
    - [すべてキャッチされる](#すべてキャッチされる)
  - [例外の透過性](#例外の透過性)
    - [透過的な cache オペレータ](#透過的な-cache-オペレータ)
    - [宣言的な cache オペレータ](#宣言的な-cache-オペレータ)
  - [Flow の完了](#flow-の完了)
    - [命令型の finally ブロック](#命令型の-finally-ブロック)
    - [宣言型の制御](#宣言型の制御)
    - [上流の例外のみ](#上流の例外のみ)
  - [命令型と宣言型](#命令型と宣言型)
  - [Flow 処理の実行](#flow-処理の実行)
  - [Flow とリアクティブストリーム](#flow-とリアクティブストリーム)

# 非同期 Flow 処理
非同期の Suspend 関数は、単一の値を返すことしかできない。
非同期で、複数回処理結果を返す場合には、Flow を利用する。

## 複数値の表現
Kotlin で複数値を表す場合、コレクションが利用できる。  
例として 3 つの数字リストを返す関数を作成し、```forEach``` で表示する。
```kotlin
fun foo(): List<Int> = listOf(1, 2, 3)
fun main() {
    foo().forEach { println(it) }
}
```

## シーケンス
演算によって値を生成し、結果を返すような処理の場合、```Sequence``` を使用して実装が可能。
演算を行う処理が、非同期で行うような処理の場合、呼び出しスレッドをブロックすることになる。
```kotlin
fun foo(): Sequence<Int> = sequence {
    for (i in 1..3) {
        Thread.sleep(100)
        yield(i)
    }
}

fun main() {
    foo().forEach { println(it) }
}
```

## Suspend 関数
非同期演算によって値を生成するような処理を Suspend 関数で実装すれば、
呼び出し元スレッドをブロックせずに結果を返すことができる。
```kotlin
suspend fun foo(): List<Int> {
    // 擬似的な非同期処理
    delay(1000)
    return listOf(1, 2, 3)
}

fun main() = runBlocking {
    foo().forEach { println(it) }
}
```

## Flow 処理
戻り値型に ```List<Int>``` を指定すると、一括して値を返す処理となる。
非同期ストリーム型にする場合は、```Flow<Int>``` 型を指定する。
これは、同期ストリーム型（```Sequence<Int>```）のように利用が可能となる。
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")
fun foo(): Flow<Int> = flow {
    for (i in 1..3) {
        delay(100)
//        Thread.sleep(100)
        emit(i)
    }
}

fun main() = runBlocking<Unit> {
    launch {
        for (k in 1..3) {
            log.info("I'm not blocked $k")
            delay(100)
        }
    }
    foo().collect { log.info("$it") }
}
```
実行結果  
メインスレッドをブロックすることなく、100 ms 毎に番号を表示する。
同時に、メインスレッドで実行する別 Coroutine からも 100 ms 毎にメッセージ表示し、
メインスレッドがブロックされていないことの裏付けができる。
```
17:47:58.662 [main @coroutine#2] INFO  sample - I'm not blocked 1
17:47:58.762 [main @coroutine#1] INFO  sample - 1
17:47:58.767 [main @coroutine#2] INFO  sample - I'm not blocked 2
17:47:58.862 [main @coroutine#1] INFO  sample - 2
17:47:58.868 [main @coroutine#2] INFO  sample - I'm not blocked 3
17:47:58.963 [main @coroutine#1] INFO  sample - 3
```

Flow には、以下の特徴がある。

- ```flow``` は、Flow 型の Coroutine ビルダ
- ```flow { ... }``` ブロック内では、Suspend 関数を呼び出せる
- ```foo()``` 関数は、Suspend 関数である必要が無い
- 値を Flow から返すには、```emit``` 関数を利用する
- 値を収集するには、```collect``` 関数を利用する

上記コードの Thread.sleep をコメントインし、delay をコメントアウトすると、
以下のような結果となる。
```
18:02:47.655 [main @coroutine#1] INFO  sample - 1
18:02:47.759 [main @coroutine#1] INFO  sample - 2
18:02:47.860 [main @coroutine#1] INFO  sample - 3
18:02:47.863 [main @coroutine#2] INFO  sample - I'm not blocked 1
18:02:47.968 [main @coroutine#2] INFO  sample - I'm not blocked 2
18:02:48.069 [main @coroutine#2] INFO  sample - I'm not blocked 3
```

## Flow はコールド
>リアクティブプログラミングにおいてストリームには、ホットとコールドがある。
>
>コールド
>
>- 取得開始まで値を生成しない
>- 同一インスタンスでも取得を再度行うと、常に先頭からデータを返す
>
>ホット
>
>- 取得せずとも値を生成し続ける
>- 同一インスタンスから取得を複数箇所で行うと、すべて現時点で生成した同じデータを返す。
>
>参考 ： [RxJS を学ぼう #4 - COLD と HOT について学ぶ / ConnectableObservable](https://tech.recruit-mp.co.jp/front-end/post-11558/)

Flow は、```sequence``` のようにコールドである。

コールドの特性を次のコードで確認できる。
```kotlin
fun foo(): Flow<Int> = flow {
    println("Flow started")
    for (i in 1..3) {
        delay(100)
        emit(i)
    }
}

fun main() = runBlocking<Unit> {
    println("Calling foo ...")
    val flow = foo()
    println("Calling collect ...")
    flow.collect { println(it) }
    println("Calling collect again ...")
    flow.collect { println(it) }
}
```
実行結果  
同一インスタンスから取得しているにも関わらず、```collect``` する度に新たに先頭からデータを返している。
```
Calling foo ...
Calling collect ...
Flow started
1
2
3
Calling collect again ...
Flow started
1
2
3
```
```foo``` 関数は、即座に値を返すため、Suspend 関数である必要がない。
Flow は、```collect``` 関数を呼び出す度に、新規に開始される。

## Flow のキャンセル
Flow は、Coroutine の通常のキャンセルに準拠する。
```kotlin
fun foo(): Flow<Int> = flow {
    println("Flow started")
    for (i in 1..3) {
        delay(100)
        println("Emitting $i")
        emit(i)
    }
}

fun main() = runBlocking<Unit> {
    withTimeoutOrNull(250) {
        foo().collect { println(it) }
    }
    println("Done")
}
```
実行結果  
2 までの出力となっていることに注目。
```
Flow started
Emitting 1
1
Emitting 2
2
Done
```

## Flow ビルダ
Flow ビルダには、以下のようなものがある。

- ```flow { ... }```
- ```flowOf``` (固定値から生成)
- ```.asFlow()``` 拡張機能（各種コレクションおよび、シーケンスの拡張）

```kotlin
fun main() = runBlocking<Unit> {
    flow<Int> {
        for (i in 0..3) {
            emit(i)
        }
    }.collect { println(it) }
    flowOf(1, 2, 3).collect { println(it) }
    (1..3).asFlow().collect { println(it) }
}
```

## Flow 集合操作オペレータ
Flow は、コレクションや、シーケンスと同様に、```map``` や ```filter``` のような Flow 集合操作オペレータを利用して変換できる。
Flow 集合操作オペレータには、以下のような特徴がある。

- 操作処理内で Suspend 関数を呼び出せる
- 戻り値は、flow となる
- コールドである
- Suspend 関数ではない
- 呼び出すと即座に変換後の flow を返す

以下のコードのように、Flow に変換し ```map``` 処理内で、Suspend 関数（```performRequest```）を利用することができる。
これは、Flow 集合操作オペレータ内で、長時間の非同期処理を、スレッドのブロック無しで、行えることを意味する。
```kotlin
suspend fun performRequest(request: Int): String {
    delay(1000)
    return "response $request"
}

fun main() = runBlocking<Unit> {
    (1..3).asFlow()
        .map { performRequest(it) }
        .collect { println(it) }
}
```

## transform オペレータ
Flow ```transform``` オペレータは、汎用的に利用可能で、 ```map``` や、```filter``` のような変換処理を行ったり、
より複雑な変換処理を行うことができる。  
Flow ```transform``` オペレータ処理では、任意の回数 ```emit``` 関数で値を送出できる。
```kotlin
suspend fun performRequest(request: Int): String {
    delay(1000)
    return "response $request"
}

fun main() = runBlocking<Unit> {
    (1..3).asFlow()
        .transform {
            // 任意の回数 emit を呼び出し可能
            emit("Making request $it")
            emit(performRequest(it))
        }
        .collect { println(it) }
}
```

## サイズ制限オペレータ
```take``` などのサイズ制限オペレータは、指定した制限に達すると、Flow の実行をキャンセルする。
Flow のキャンセルは、例外をスローすることで行うため、```try-catch-finally``` でリソースの管理を行うことができる。
```kotlin
fun numbers(): Flow<Int> = flow {
    try {
        emit(1)
        emit(2)
        println("この行は、実行されない")
        emit(3)
    } finally {
        println("numbers の finally")
    }
}

fun main() = runBlocking<Unit> {
    numbers()
        // 最初の 2 つのみ取得
        .take(2)
        .collect { println(it) }
}
```
実行結果  
"numbers の finally" は、表示されていないことに注目。
```
1
2
numbers の finally
```

## Flow 終端オペレータ
Flow 終端オペレータは、Flow の収集を行う Suspend 関数である。
 ```collect``` オペレータは、最も基本的な Flow 終端オペレータである。  
Flow 終端オペレータには、以下のようなものがある。

- ```toList```、```toSet```  
  その他のコレクションに変換
- ```first```、```single```  
  最初の値を取得、単一の値を取得（コレクションに複数含まれていると例外をスローする）
- ```reduce```、```fold```  
  値を集約。reduce は、空のコレクションの場合、例外をスローする。
  fold の場合は、空のコレクションでも初期値を設定できるため、例外をスローしない。
  この二つの関数は、「@ExperimentalCoroutinesApi」になっている。

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking {
    val collection = listOf<Int>(1, 2, 3)
    val list = collection.map { it * 2 }.toList()
    println("list: $list")
    val set = collection.map { it to "val" }.toSet()
    println("set: $set")
    val first = collection.map { it }.first()
    println("first: $first")
    val single = collection.take(1).map { it }.single()
    println("single: $single")
    val reduce = collection.map { it * 3 }.reduce { a, b -> a + b }
    println("reduce: $reduce")
    val fold = collection.map { it * 4 }.fold(10) { a, b -> a + b }
    println("fold: $fold")
}
```

## Flow 処理の直列性
Flow 処理は、特別なオペレータを利用しない限り、直列処理となる。
デフォルトでは、新たな Coroutine を生成しない。
コレクションから出力した値毎に中間のオペレータで順次処理され、Flow 終端オペレータへデータが渡される。

コレクションの値を偶数のみにフィルタリングして、文字列に変換する例。
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")
fun main() = runBlocking {
    (1..5).asFlow()
        // 偶数のみ次へ渡す
        .filter {
            log.info("Filer $it")
            it % 2 == 0
        }
        .map {
            log.info("Map $it")
            "string $it"
        }
        .collect {
            log.info("Collect $it")
        }
}
```
実行結果  
同一スレッド、同一 Coroutine で順次処理されていることに注目。
```
12:59:24.473 [main @coroutine#1] INFO  sample - Filer 1
12:59:24.476 [main @coroutine#1] INFO  sample - Filer 2
12:59:24.476 [main @coroutine#1] INFO  sample - Map 2
12:59:24.476 [main @coroutine#1] INFO  sample - Collect string 2
12:59:24.476 [main @coroutine#1] INFO  sample - Filer 3
12:59:24.476 [main @coroutine#1] INFO  sample - Filer 4
12:59:24.476 [main @coroutine#1] INFO  sample - Map 4
12:59:24.476 [main @coroutine#1] INFO  sample - Collect string 4
12:59:24.476 [main @coroutine#1] INFO  sample - Filer 5
```

## Flow のコンテキスト
Flow の収集処理は、常に呼び出し元 Coroutine のコンテキストを継承して実行する。
次のコードのような場合、foo が返す flow がどのような実装でも、
呼び出し元のコンテキストで collect 処理が実行される。
この性質をコンテキスト保全と呼ぶ。

```kotlin
fun main() = runBlocking {
    withContext(context) {
        foo().collect { println(it) }
    }
}
```

## withContext ビルダを利用した間違った送出
長時間 CPU を占有する処理は、Dispatchers.Default コンテキストで実行をする必要があり、
UI を更新する処理の場合は、Dispatchers.Main で実行する必要がある。
このような場合、コンテキストを切り替えるために withContext ビルダを利用するが、
flow { ... } ビルダ処理内では、コンテキスト保全の性質を厳守する必要があり、
異なるコンテキストから値を送出した場合、例外がスローされる。
```kotlin
fun foo(): Flow<Int> = flow {
    // flow ビルダ内で コンテキストを変更する間違った実装
    withContext(Dispatchers.Default) {
        for (i in 1..3) {
            // CPU をブロック
            Thread.sleep(100)
            emit(i)
        }
    }
}

fun main() = runBlocking {
    foo().collect { println("collected $it") }
}
```
実行結果
```
Exception in thread "main" java.lang.IllegalStateException: Flow invariant is violated:
    Flow was collected in [CoroutineId(1), "coroutine#1":BlockingCoroutine{Active}@52508397, BlockingEventLoop@4d0ef7f1],
    but emission happened in [CoroutineId(1), "coroutine#1":DispatchedCoroutine{Active}@400741f7, DefaultDispatcher].
    Please refer to 'flow' documentation or use 'flowOn' instead
```

## flowOn オペレータ
flowOn オペレータは、コンテキストのディスパッチャを変更した場合、
新しい Coroutine を生成し、flow 処理を実行する。
flowOn オペレータは、デフォルトである Flow 処理の直列処理を並列処理に変更する。
flowOn オペレータは、前述の 「withContext ビルダを利用した間違った送出」で発生した例外でも言及している。

flowOn オペレータの実装例
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")
fun foo(): Flow<Int> = flow {
    for (i in 1..3) {
        Thread.sleep(100)
        log.info("Emitting $i")
        emit(i)
    }
// flow ビルダのコンテキストを切り替える正しい方法
}.flowOn(Dispatchers.Default)

fun main() = runBlocking {
    foo().collect { log.info("collected $it") }
}
```
実行結果  
collect 処理は、main スレッド、flow 処理では、別のスレッドであることに注目。
```
09:23:28.814 [DefaultDispatcher-worker-1 @coroutine#2] INFO  sample - Emitting 1
09:23:28.825 [main @coroutine#1] INFO  sample - collected 1
09:23:28.925 [DefaultDispatcher-worker-1 @coroutine#2] INFO  sample - Emitting 2
09:23:28.925 [main @coroutine#1] INFO  sample - collected 2
09:23:29.025 [DefaultDispatcher-worker-1 @coroutine#2] INFO  sample - Emitting 3
09:23:29.025 [main @coroutine#1] INFO  sample - collected 3
```

## buffer オペレータ
buffer オペレータは、Flow の各処理で、長期実行される非同期処理が関連するような場合、
全体の処理時間を改善するための方法として有用となる。
例として、foo() の flow 処理中の emit に 100 ms かかるとする。
collect 処理も、1 要素 300 ms かかる時、全体の処理時間を確認する。
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")
fun foo(): Flow<Int> = flow {
    for (i in 1..3) {
        delay(100)
        log.info("Emit $i")
        emit(i)
    }
}

fun main() = runBlocking {
    val time = measureTimeMillis {
        foo().collect {
            delay(300)
            log.info("Collect $it")
        }
    }
    log.info("Collected in $time ms")
}
```
実行結果  
全体の処理時間は、約 1200 ms （1 要素の処理は、約 400 ms）かかる。  
処理は、要素毎に emit（100 ms） → collect（300 ms） の順に直列処理となるため、
各処理時間の合計が全体の処理時間となる。
```
09:59:00.147 [main @coroutine#1] INFO  sample - Emit 1
09:59:00.459 [main @coroutine#1] INFO  sample - Collect 1
09:59:00.562 [main @coroutine#1] INFO  sample - Emit 2
09:59:00.864 [main @coroutine#1] INFO  sample - Collect 2
09:59:00.964 [main @coroutine#1] INFO  sample - Emit 3
09:59:01.266 [main @coroutine#1] INFO  sample - Collect 3
09:59:01.266 [main @coroutine#1] INFO  sample - Collected in 1230 ms
```

flow 処理では、buffer オペレータを利用することで、emit と collect を並列処理できるようになる。
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")
fun foo(): Flow<Int> = flow {
    for (i in 1..3) {
        delay(100)
        log.info("Emit $i")
        emit(i)
    }
}

fun main() = runBlocking {
    val time = measureTimeMillis {
        foo()
            .buffer()
            .collect {
                delay(300)
                log.info("Collect $it")
            }
    }
    log.info("Collected in $time ms")
}
```
実行結果  
emit 処理と collect 処理が、別の Coroutine で動作し、
collect 処理の完了を待たずに、emit 処理が動作するため、全体の処理時間が短縮されている。
つまり、以下のような処理フローとなる。

1. 初回 emit（100 ms）送出後、即座に 2 回目の emit 処理開始
1. 初回 collect（300 ms）処理開始
1. 初回 collect 処理と並行して、2 回目の emit 処理が実行される
1. 以後、collect 処理と emit 処理は並行で稼働する

全体の処理時間は、初回 emit（100 ms）＋emit（300 ms） 3 回分で、合計 1000 ms となる。
```
10:19:30.000 [main @coroutine#2] INFO  sample - Emit 1
10:19:30.113 [main @coroutine#2] INFO  sample - Emit 2
10:19:30.214 [main @coroutine#2] INFO  sample - Emit 3
10:19:30.314 [main @coroutine#1] INFO  sample - Collect 1
10:19:30.615 [main @coroutine#1] INFO  sample - Collect 2
10:19:30.916 [main @coroutine#1] INFO  sample - Collect 3
10:19:30.935 [main @coroutine#1] INFO  sample - Collected in 1073 ms
```

flowOn では、ディスパッチャを変更した場合に、バッファリングと同様の仕組みを利用する。
buffer オペレータの場合は、明示的なディスパッチャの変更無しに、Coroutine が切り替わる。

## conflate オペレータ
flow で送出される途中の値は無視し、最新の値のみでも処理に問題が無い場合（マウスカーソルの絶対座標など）、
conflate オペレータを利用して、中間の値を破棄できる。
最新の値は、collect 処理が開始する時点の値となる。collect 処理中の値は破棄される。
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")
fun foo(): Flow<Int> = flow {
    for (i in 1..3) {
        delay(100)
        log.info("Emit $i")
        emit(i)
    }
}

fun main() = runBlocking {
    val time = measureTimeMillis {
        foo()
            .conflate()
            .collect {
                delay(300)
                log.info("Collect $it")
            }
    }
    log.info("Collected in $time ms")
}
```
実行結果  
collect 処理中に送出された 2 が破棄されていることに注目。
```
10:53:35.454 [main @coroutine#2] INFO  sample - Emit 1
10:53:35.567 [main @coroutine#2] INFO  sample - Emit 2
10:53:35.668 [main @coroutine#2] INFO  sample - Emit 3
10:53:35.767 [main @coroutine#1] INFO  sample - Collect 1
10:53:36.068 [main @coroutine#1] INFO  sample - Collect 3
10:53:36.088 [main @coroutine#1] INFO  sample - Collected in 772 ms
```

## collectLatest オペレータ
collectLatest オペレータは、新しく値が emit された場合、実行中の collect 処理をキャンセルして、
新たに collect 処理を開始するオペレータである。  
その他の xxxLatest 系オペレータも、collectLatest オペレータと同様に、新しい値が来る度に、
実行中の処理をキャンセルして、新たに処理を開始する。
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")
fun foo(): Flow<Int> = flow {
    for (i in 1..3) {
        delay(100)
        log.info("Emit $i")
        emit(i)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking {
    val time = measureTimeMillis {
        foo()
            .collectLatest {
                log.info("Collecting $it")
                // 通常 try-cache は不要だが、動作を確認するために記載している
                try {
                    delay(300)
                } catch (e: CancellationException) {
                    log.info("Cancelled: [isActive: ${isActive}] $e")
                    throw e
                }
                log.info("Done $it")
            }
    }
    log.info("Collected in $time ms")
}
```
実行結果  
collect 処理（300 ms）中に、次の emit が発生したため、
collect 処理がキャンセルされ、次の値の処理を開始している。
collect 処理が完了できるのは、最後の値のみとなっている。
```
12:06:39.000 [main @coroutine#2] INFO  sample - Emit 1
12:06:39.023 [main @coroutine#3] INFO  sample - Collecting 1
12:06:39.125 [main @coroutine#2] INFO  sample - Emit 2
12:06:39.308 [main @coroutine#3] INFO  sample - Cancelled: [isActive: true] kotlinx.coroutines.flow.internal.ChildCancelledException: Child of the scoped flow was cancelled
12:06:39.314 [main @coroutine#4] INFO  sample - Collecting 2
12:06:39.416 [main @coroutine#2] INFO  sample - Emit 3
12:06:39.418 [main @coroutine#4] INFO  sample - Cancelled: [isActive: true] kotlinx.coroutines.flow.internal.ChildCancelledException: Child of the scoped flow was cancelled
12:06:39.420 [main @coroutine#5] INFO  sample - Collecting 3
12:06:39.720 [main @coroutine#5] INFO  sample - Done 3
12:06:39.760 [main @coroutine#1] INFO  sample - Collected in 906 ms
```

## Flow の複数組合せ
Flow を複数組合せる方法は、複数存在する。

### zip オペレータ
Flow の zip オペレータは、Kotlin 標準 Sequence.zip 拡張関数のように、2 つの flow の対応する値を結合する。
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking {
    val nums = (1..3).asFlow()
    val strs = flowOf("one", "two", "three")
    nums.zip(strs) { num, str -> "$num -> $str" }
        .collect { println(it) }
}
```
実行結果
```
1 -> one
2 -> two
3 -> three
```

### combine オペレータ
combine オペレータは、flow から送出された最新の値を処理する時点で、未送出側 flow の値を、再送出して処理する。

zip オペレータの場合
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking {
    val nums = (1..3).asFlow().onEach { delay(300) }
    val strs = flowOf("one", "two", "three").onEach { delay(400) }
    val startTime = System.currentTimeMillis()
    nums.zip(strs) { num, str -> "$num -> $str" }
        .collect {
            log.info("$it at ${System.currentTimeMillis() - startTime} ms from start")
        }
}
```
実行結果   
collect 処理は、nums が 300 ms で値を送出しても、strs の値送出間隔（400 ms）と同期して処理を行う。
```
13:00:32.756 [main @coroutine#1] INFO  sample - 1 -> one at 435 ms from start
13:00:33.155 [main @coroutine#1] INFO  sample - 2 -> two at 835 ms from start
13:00:33.555 [main @coroutine#1] INFO  sample - 3 -> three at 1235 ms from start
```

combine オペレータの場合
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking {
    val nums = (1..3).asFlow().onEach { delay(300) }
    val strs = flowOf("one", "two", "three").onEach { delay(400) }
    val startTime = System.currentTimeMillis()
    nums.combine(strs) { num, str -> "$num -> $str" }
        .collect {
            log.info("$it at ${System.currentTimeMillis() - startTime} ms from start")
        }
}
```
実行結果  
ログの 1 行目は、nums と strs の両方の値が揃うまで出力されないため、起動から 400 ms 後となる。  
nums の処理は、collect で処理される前に、次の送出処理を開始している。
ログの 2 行目は、nums の 2 つ目の値が送出される、起動から 600 ms 後（最初の送出 300 ms + 次の送出 300 ms）となる。
その時点では、strs の 2 つ目の値（"two"）は送出されていないため、
1 つ目の値（"one"）が再送出される。  
ログの 3 行目は、2 行目とは逆に、nums の値（2）が再送出されている。
```
12:59:09.514 [main @coroutine#1] INFO  sample - 1 -> one at 454 ms from start
12:59:09.710 [main @coroutine#1] INFO  sample - 2 -> one at 652 ms from start
12:59:09.926 [main @coroutine#1] INFO  sample - 2 -> two at 868 ms from start
12:59:10.011 [main @coroutine#1] INFO  sample - 3 -> two at 953 ms from start
12:59:10.329 [main @coroutine#1] INFO  sample - 3 -> three at 1271 ms from start
```

## Flow の平坦化
Flow は、非同期の値であるため、各値が、さらに別の Flow 処理などを行うことは普通である。

例として、500 ms 間隔で 2 つの値を送出する Flow 処理があり、
その処理をさらに別の Flow 処理から呼び出すようなコードを以下に示す。
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

fun requestFlow(i: Int): Flow<String> = flow {
    emit("$i: First")
    delay(500)
    emit("$i: Second")
}

fun main() = runBlocking {
    val startTime = System.currentTimeMillis()
    (1..3).asFlow().onEach { delay(100) }
        .map { requestFlow(it) }
        .collect {
            log.info("$it at ${System.currentTimeMillis() - startTime} ms from start")
        }
}
```
実行結果  
collect 処理で受け取る値が、```Flow``` 型となっている。
```
15:29:14.469 [main @coroutine#1] INFO  sample - kotlinx.coroutines.flow.SafeFlow@3bfdc050 at 117 ms from start
15:29:14.574 [main @coroutine#1] INFO  sample - kotlinx.coroutines.flow.SafeFlow@42e26948 at 224 ms from start
15:29:14.675 [main @coroutine#1] INFO  sample - kotlinx.coroutines.flow.SafeFlow@57baeedf at 325 ms from start
```

上記のような ```Flow<Flow<String>>``` 型を処理するような場合に、Flow の平坦化が必要となる。

### flatMapConcat オペレータ

>flatMapConcat オペレータには、注意事項がある。以下のリファレンスページを参照。
>https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/flat-map-concat.html

flatMapConcat および flattenConcat オペレータは、結合モードで動作する。
結合モードの場合、collect 処理は、内部の flow 処理完了後行われる。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

fun requestFlow(i: Int): Flow<String> = flow {
    emit("$i: First")
    delay(500)
    emit("$i: Second")
}

@OptIn(FlowPreview::class)
fun main() = runBlocking {
    val startTime = System.currentTimeMillis()
    (1..3).asFlow().onEach { delay(100) }
        .flatMapConcat { requestFlow(it) }
        .collect {
            log.info("$it at ${System.currentTimeMillis() - startTime} ms from start")
        }
}
```
実行結果
```
15:37:03.175 [main @coroutine#1] INFO  sample - 1: First at 631 ms from start
15:37:03.687 [main @coroutine#1] INFO  sample - 1: Second at 1147 ms from start
15:37:03.788 [main @coroutine#1] INFO  sample - 2: First at 1248 ms from start
15:37:04.289 [main @coroutine#1] INFO  sample - 2: Second at 1749 ms from start
15:37:04.390 [main @coroutine#1] INFO  sample - 3: First at 1850 ms from start
15:37:04.891 [main @coroutine#1] INFO  sample - 3: Second at 2351 ms from start
```

### flatMapMerge オペレータ
>このオペレータは、現時点でプレビュー機能なため、原則利用しないこと。
>
>flatMapMerge オペレータには、注意事項がある。以下のリファレンスページを参照。
>https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/flat-map-merge.html

flatMapMerge および flattenMerge オペレータは、平坦化モードで動作する。
平坦化モードは、全ての Flow の送出データを同時に収集し、単一の Flow にマージする。
したがって、より早く値が送出されるようになる。
両オペレータとも、同時に収集する並行 Flow 数を指定できる（デフォルトは、DEFAULT_CONCURRENCY）。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

fun requestFlow(i: Int): Flow<String> = flow {
    emit("$i: First")
    delay(500)
    emit("$i: Second")
}

@OptIn(FlowPreview::class)
fun main() = runBlocking {
    val startTime = System.currentTimeMillis()
    (1..3).asFlow().onEach { delay(100) }
        .flatMapMerge { requestFlow(it) }
        .collect {
            log.info("$it at ${System.currentTimeMillis() - startTime} ms from start")
        }
}
```
実行結果
```
16:01:54.824 [main @coroutine#1] INFO  sample - 1: First at 610 ms from start
16:01:54.940 [main @coroutine#1] INFO  sample - 2: First at 732 ms from start
16:01:55.042 [main @coroutine#1] INFO  sample - 3: First at 834 ms from start
16:01:55.318 [main @coroutine#1] INFO  sample - 1: Second at 1110 ms from start
16:01:55.442 [main @coroutine#1] INFO  sample - 2: Second at 1234 ms from start
16:01:55.545 [main @coroutine#1] INFO  sample - 3: Second at 1337 ms from start
```

### flatMapLatest オペレータ
flatMapLatest オペレータは、最新処理モードで動作する。
最新処理モードは、新しい値が送出されると、処理中の collect がキャンセルされ、最新値の処理が開始される。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

fun requestFlow(i: Int): Flow<String> = flow {
    emit("$i: First")
    delay(500)
    emit("$i: Second")
}

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking {
    val startTime = System.currentTimeMillis()
    (1..3).asFlow().onEach { delay(100) }
        .flatMapLatest { requestFlow(it) }
        .collect {
            log.info("$it at ${System.currentTimeMillis() - startTime} ms from start")
        }
}
```
実行結果  
1 ～ 3 行目は、"Second"の送出前に次の数値が送出されたため、"Second"が破棄されている。  
4 行目は、"Second"が送出されたため、数値（3）が再送出された。
```
16:40:06.829 [main @coroutine#1] INFO  sample - 1: First at 164 ms from start
16:40:07.025 [main @coroutine#1] INFO  sample - 2: First at 361 ms from start
16:40:07.128 [main @coroutine#1] INFO  sample - 3: First at 464 ms from start
16:40:07.628 [main @coroutine#1] INFO  sample - 3: Second at 964 ms from start
```

## Flow 例外
Flow オペレータ内の emit や、処理で例外が発生すると、collect 処理などが完了する可能性がある。
このような場合の例外の処理方法を説明する。

### collect を try-cache
collect を try-cache することで、例外を処理できる。
```kotlin
fun foo(): Flow<Int> = flow {
    for (i in 1..3) {
        println("Emitting $i")
        emit(i)
    }
}

fun main() = runBlocking<Unit> {
    try {
        foo().collect {
            println(it)
            // 条件が false になった場合、IllegalStateException をスローする
            // 追加の処理で返した文字列が、例外のメッセージに含まれる。
            check(it <= 1) { "Collected $it" }
        }
    } catch (e: Throwable) {
        println("Caught $e")
    }
}
```
実行結果
```
Emitting 1
1
Emitting 2
2
Caught java.lang.IllegalStateException: Collected 2
```

### すべてキャッチされる

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

fun foo(): Flow<String> =
    flow {
        for (i in 1..3) {
            log.info("Emitting $i")
            emit(i)
        }
    }
        .map {
            check(it <= 1) { "Crashed on $it" }
            "string $it"
        }

fun main() = runBlocking<Unit> {
    try {
        foo().collect { log.info(it) }
    } catch (e: Throwable) {
        log.info("Caught $e")
    }
}
```
実行結果  
別スレッドで発生した例外を main スレッドでキャッチしていることに注目。
```
17:31:32.576 [main @coroutine#1] INFO  sample - Emitting 1
17:31:32.595 [main @coroutine#1] INFO  sample - string 1
17:31:32.595 [main @coroutine#1] INFO  sample - Emitting 2
17:31:32.597 [main @coroutine#1] INFO  sample - Caught java.lang.IllegalStateException: Crashed on 2
```

## 例外の透過性
Flow は、例外を透過的に扱うという原則があり、try/cache ブロック内から emit することは、この原則に反する。
この原則を厳守することにより、collect で発生する例外を try/cache で処理できることを保証できる。
catch オペレータは、例外の透過性原則を厳守し、例外処理をカプセル化することができる。
catch オペレータに指定した処理は、発生した例外を判断し、以下のような、さまざまな処理を行う事ができる。

- throw で例外を再スロー
- emit を行い、値の送出に差し替える
- 例外を無視する、ログ出力するまたは、他のコードで処理する

```kotlin
fun foo(): Flow<String> =
    flow {
        for (i in 1..3) {
            println("Emitting $i")
            emit(i)
        }
    }
        .map {
            check(it <= 1) { "Crashed on $it" }
            "string $it"
        }

fun main() = runBlocking<Unit> {
    try {
        foo()
            // 例外を throw する場合
//            .catch { throw it }
            // 例外を送出に差し替える場合
            .catch { emit("Caught $it") }
            // 例外を無視する場合
//            .catch { }
            // 例外を処理する場合
//            .catch { println("logging") }
            .collect { println(it) }
    } catch (e: Throwable) {
        println("Caught $e")
    }
}
```
実行結果
```
Emitting 1
string 1
Emitting 2
Caught java.lang.IllegalStateException: Crashed on 2
```

### 透過的な cache オペレータ
cache オペレータは、上流の例外のみキャッチする。
以下のコードのように、下流の例外には、関与しない。
```kotlin
fun foo(): Flow<Int> = flow {
    for (i in 1..3) {
        println("Emitting $i")
        emit(i)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking<Unit> {
    foo()
        .catch { println("Caught $it") }
        .collect {
            // ここで発生した例外は、上の cache オペレータではキャッチしない
            check(it <= 1) { "Collected $it" }
            println(it)
        }
}
```
実行結果
```
Emitting 1
1
Emitting 2
Exception in thread "main" java.lang.IllegalStateException: Collected 2
・・・
```

### 宣言的な cache オペレータ
前述の処理で発生する例外を cache オペレータで処理したい場合、
collect オペレータの処理は、cache オペレータより前の onEach オペレータに移動し、
collect オペレータは、引数無しで実行することで実現できる。
collect オペレータ以後には、cache オペレータ呼び出しはできない。
```kotlin
fun foo(): Flow<Int> = flow {
    for (i in 1..3) {
        println("Emitting $i")
        emit(i)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking<Unit> {
    foo()
        .onEach {
            check(it <= 1) { "Collected $it" }
            println(it)
        }
        .catch { println("Caught $it") }
        .collect()
        // collect の後に catch 呼び出しはできない。
}
```
実行結果
```
Emitting 1
1
Emitting 2
Caught java.lang.IllegalStateException: Collected 2
```

## Flow の完了
Flow 収集完了時になんらかの処理を実行する必要がある場合、
命令型または、宣言型で設定することができる。

### 命令型の finally ブロック
try/finally ブロックで、完了時処理を設定できる。
```kotlin
fun foo(): Flow<Int> = flow {
    for (i in 1..3) {
        println("Emitting $i")
        emit(i)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking<Unit> {
    try {
        foo().collect { println(it) }
    } finally {
        println("Done")
    }
}
```
実行結果
```
Emitting 1
1
Emitting 2
2
Emitting 3
3
Done
```

### 宣言型の制御
onCompletion オペレータは、全ての収集が完了した時点で処理を実行する。
```kotlin
fun foo(): Flow<Int> = flow {
    for (i in 1..3) {
        println("Emitting $i")
        emit(i)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking<Unit> {
    foo()
        .onCompletion { println("Done") }
        .collect { println(it) }
}
```
実行結果
```
Emitting 1
1
Emitting 2
2
Emitting 3
3
Done
```

onCompletion オペレータは、Flow 処理が正常に完了か、
例外発生で完了かを判断するための null 可 Throwable 型パラメータを利用できる。
```kotlin
fun foo(): Flow<Int> = flow {
    emit(1)
    throw RuntimeException()
}

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking<Unit> {
    foo()
        .onCompletion { cause ->
            if (cause != null) {
                println("Flow completed exceptionally")
            }
        }
        .catch { cause -> println("Caught exception") }
        .collect { println(it) }
}
```
実行結果  
例外発生で終了した場合でも、onCompletion 処理が呼び出されている。
onCompletion 処理では、例外発生をしたかを判定して、処理を行う事ができる。
onCompletion 処理では例外制御はできない、継続して下流に例外が流れる。
実行結果では、catch 処理が実行されていることで、それが分かる。
```
1
Flow completed exceptionally
Caught exception
```

### 上流の例外のみ
onCompletion オペレータは、catch オペレータと同様に、下流の例外は感知しません。
```kotlin
fun foo(): Flow<Int> = (1..3).asFlow()

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking<Unit> {
    foo()
        .onCompletion { println("Flow completed with $it") }
        .collect {
            check(it <= 1) { "Collected $it" }
            println(it)
        }
}
```
実行結果  
onCompletion 処理では、下流で発生した例外は、検知出来ない。
```
1
Flow completed with null
Exception in thread "main" java.lang.IllegalStateException: Collected 2
・・・
```

## 命令型と宣言型
命令型と宣言型の優劣や、どちらかを推奨することは無い。
利用者側の都合で任意に選択できる。

## Flow 処理の実行
onEach オペレータは、特定の flow から送出された値に対して、イベント処理のような設定が可能となる。
ただし、onEach は、中間処理なので、必ず collect 終端処理が必要となる。
終端処理を呼び出さなかった場合、なにも起きないので注意。

```kotlin
fun events(): Flow<Int> = (1..3).asFlow().onEach { delay(100) }

fun main() = runBlocking<Unit> {
    events()
        .onEach { event -> println("Event: $event") }
        .collect()
    println("Done")
}
```
実行結果
```
Event: 1
Event: 2
Event: 3
Done
```

launchIn 終端オペレータを利用すると、収集処理を別の Coroutine で起動する事ができる。
launchIn の引数には、Coroutine を実行する CoroutineScope を指定する必要がある。
以下のコードでは、runBlocking を指定しているため、main 関数は、
flow 処理の完了を待ってから終了することになる。
```kotlin
fun events(): Flow<Int> = (1..3).asFlow().onEach { delay(100) }

fun main() = runBlocking<Unit> {
    events()
        .onEach { event -> println("Event: $event") }
        // 別 Coroutine で flow 処理を起動
        .launchIn(this)
    println("Done")
}
```
実行結果  
収集処理が完了するまえに、"Done"が表示されていることに注目
```
Done
Event: 1
Event: 2
Event: 3
```

実際の処理では、launchIn 終端オペレータの引数に渡す CoroutineScope は、
アプリケーションに比べ、より短い生存期間となる。
flow 処理は、CoroutineScope の生存期間が切れると、関連処理も含め、即座にキャンセルされる。
イベント処理を解除するような仕組みは、Coroutine の構造化された並行性により、
構造化された範囲はすべてキャンセルされるため、不要となる。

launchIn 終端オペレータが返す、Job を利用して、スコープ全体をキャンセルしたり、結合したりせず、
任意の Flow 収集 Coroutine のみをキャンセルできる。

## Flow とリアクティブストリーム
[Reactive Streams](https://www.reactive-streams.org/) 、
RxJava や Project Reactor のような、リアクティブフレームワークに
精通している人にとって、Flow の設計は、非常に馴染みあるかもしれない。

実際、Flow の設計は、Reactive Stream とそれらの実装に触発されている。
しかし、Flow の主な目標は、可能な限りシンプルな設計で、Kotlin と Suspend 処理との親和性を保ち、
構造化された並行性を尊重することである。
これら目標の達成は、リアクティブ先駆者たちの途方もない研鑽無しでは、為し得なかっただろう。
詳細については、
[Reactive Streams と Kotlin Flows](https://medium.com/@elizarov/reactive-streams-and-kotlin-flows-bfd12772cda4) 
を参照。

概念は異なるが、Flow は、リアクティブストリームであり、
リアクティブ（仕様および TCK 準拠）パブリッシャーに変換したり、その逆の変換などができる。
変換に利用できる統合モジュールは、以下となる。

- kotlinx-coroutines-reactive （リアクティブストリーム用）
- kotlinx-coroutines-reactor （Project Reactor 用）
- kotlinx-coroutines-rx2 （RxJava2 用）

上記統合モジュールには、Flow 間の変換、Reactor コンテキストとの統合、
さまざまなリアクティブ実装との協調など、Suspend 処理と親和性のある機能が含まれる。

