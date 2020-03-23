<!-- toc -->
- [Channel](#channel)
  - [Channel の基礎](#channel-の基礎)
  - [Channel のクローズと受信処理の終了](#channel-のクローズと受信処理の終了)
  - [Channel プロデューサの構築](#channel-プロデューサの構築)
  - [パイプライン](#パイプライン)
  - [Pipeline での素数生成](#pipeline-での素数生成)
  - [ファンアウト（Fan-out）](#ファンアウトfan-out)
  - [ファンイン（Fan-in）](#ファンインfan-in)
  - [Channel バッファ](#channel-バッファ)
  - [Channel 処理順の公正性](#channel-処理順の公正性)
  - [ticker Channel](#ticker-channel)

# Channel
遅延値は、Coroutine 間で単一の値を転送する便利な方法を提供する。
Channel は、値のストリームを転送する方法を提供する。

## Channel の基礎
Channel は、BlockingQueue と概念的によく似ている。
主な違いの一つは、キュー投入・取得時にスレッドのブロック処理ではなく、Suspend 処理となること。
（投入は、```send```、取得は、```receive```）

>BlockingQueue は、キュー取得時に、キューが格納されていない場合や、
>キュー投入時に、キューがいっぱいの場合に待機する。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")
fun main() = runBlocking<Unit> {
    val channel = Channel<Int>()
    launch {
        for (i in 1..5) {
            log.info("Send ${i * i}")
            channel.send(i * i)
        }
    }
    // Channel を close していないため、for ループでは、終了できない
    repeat(5) { log.info("${channel.receive()}") }
    log.info("Done!")
}
```
実行結果
```
10:38:54.089 [main @coroutine#2] INFO  sample - Send 1
10:38:54.093 [main @coroutine#2] INFO  sample - Send 4
10:38:54.095 [main @coroutine#1] INFO  sample - 1
10:38:54.095 [main @coroutine#1] INFO  sample - 4
10:38:54.095 [main @coroutine#2] INFO  sample - Send 9
10:38:54.095 [main @coroutine#2] INFO  sample - Send 16
10:38:54.095 [main @coroutine#1] INFO  sample - 9
10:38:54.095 [main @coroutine#1] INFO  sample - 16
10:38:54.095 [main @coroutine#2] INFO  sample - Send 25
10:38:54.096 [main @coroutine#1] INFO  sample - 25
10:38:54.096 [main @coroutine#1] INFO  sample - Done!
```

## Channel のクローズと受信処理の終了
Channel は、キューと異なり、Channel を閉じることで、これ以上要素がないことを明示できる。
通常、受信側では、for ループで要素を取得することになる。

Channel クローズの概念は、特別なクローズトークンを Channel に送信するようなものと理解できる。
クローズトークンを受信した時点で、for ループなどの受信処理が停止するため、
クローズ前に送信した要素は、すべて受信できることが保証される。
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")
fun main() = runBlocking<Unit> {
    val channel = Channel<Int>()
    launch {
        for (i in 1..5) {
            log.info("Send ${i * i}")
            channel.send(i * i)
        }
        channel.close()
    }
    for (i in channel) {
        log.info("$i")
    }
    log.info("Done!")
}
```
処理結果
```
11:02:58.146 [main @coroutine#2] INFO  sample - Send 1
11:02:58.150 [main @coroutine#2] INFO  sample - Send 4
11:02:58.152 [main @coroutine#1] INFO  sample - 1
11:02:58.152 [main @coroutine#1] INFO  sample - 4
11:02:58.152 [main @coroutine#2] INFO  sample - Send 9
11:02:58.152 [main @coroutine#2] INFO  sample - Send 16
11:02:58.153 [main @coroutine#1] INFO  sample - 9
11:02:58.153 [main @coroutine#1] INFO  sample - 16
11:02:58.153 [main @coroutine#2] INFO  sample - Send 25
11:02:58.155 [main @coroutine#1] INFO  sample - 25
11:02:58.155 [main @coroutine#1] INFO  sample - Done!
```

## Channel プロデューサの構築
Coroutine が要素のシーケンスを生成するパターンは、一般的であり、
並列処理などでよく見られる、プロデューサ/コンシューマと呼ばれるパターンである。
プロデューサ関数の戻り値に Channel を返すように実装もできるが、
プロデューサ関数からは、結果を返すという常識に反してしまう。
このような場合、簡単に Coroutine を生成できる、produce ビルダがあり、
さらに、consumeEach 拡張関数は、受信処理側で for ループの代替となる。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

@OptIn(ExperimentalCoroutinesApi::class)
// CoroutineScope の拡張関数でないと、produce ビルダが利用できない
fun CoroutineScope.produceSquares(): ReceiveChannel<Int> = produce {
    for (i in 1..5) {
        log.info("Send ${i * i}")
        send(i * i)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking<Unit> {
    val squares = produceSquares()
    squares.consumeEach { log.info("$it") }
    log.info("Done!")
}
```
実行結果
```
11:33:27.709 [main @coroutine#2] INFO  sample - Send 1
11:33:27.713 [main @coroutine#2] INFO  sample - Send 4
11:33:27.715 [main @coroutine#1] INFO  sample - 1
11:33:27.715 [main @coroutine#1] INFO  sample - 4
11:33:27.715 [main @coroutine#2] INFO  sample - Send 9
11:33:27.715 [main @coroutine#2] INFO  sample - Send 16
11:33:27.715 [main @coroutine#1] INFO  sample - 9
11:33:27.715 [main @coroutine#1] INFO  sample - 16
11:33:27.715 [main @coroutine#2] INFO  sample - Send 25
11:33:27.717 [main @coroutine#1] INFO  sample - 25
11:33:27.732 [main @coroutine#1] INFO  sample - Done!
```

## パイプライン
パイプラインとは、1 つの Coroutine が、（おそらく無限に）値をストリームに送信し、
別の Coroutine が、そのストリームを受信して処理を行うパターンを指す。

>Coroutine を生成する関数を定義する場合、すべて CoroutineScope の拡張関数とする必要がある。
>それにより、構造化された同時実効性を利用して、グローバル Coroutine の制御漏れを防ぐことが容易となる。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

@OptIn(ExperimentalCoroutinesApi::class)
// CoroutineScope の拡張関数でないと、produce ビルダが利用できない
fun CoroutineScope.produceNumbers() = produce<Int> {
    var x = 1
    while (true) {
        log.info("Send number $x")
        send(x++)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
// CoroutineScope の拡張関数でないと、produce ビルダが利用できない
fun CoroutineScope.square(numbers: ReceiveChannel<Int>): ReceiveChannel<Int> = produce {
    for (x in numbers) {
        log.info("Send square ${x * x}")
        send(x * x)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking<Unit> {
    val numbers = produceNumbers()
    // パイプラインを繋げる
    val squares = square(numbers)
    repeat(5) {
        log.info("${squares.receive()}")
    }
    log.info("Done!")
    // 子 Coroutine をキャンセル
    coroutineContext.cancelChildren()
}
```
実行結果
```
12:39:46.804 [main @coroutine#2] INFO  sample - Send number 1
12:39:46.809 [main @coroutine#3] INFO  sample - Send square 1
12:39:46.811 [main @coroutine#2] INFO  sample - Send number 2
12:39:46.811 [main @coroutine#2] INFO  sample - Send number 3
12:39:46.811 [main @coroutine#1] INFO  sample - 1
12:39:46.811 [main @coroutine#3] INFO  sample - Send square 4
12:39:46.811 [main @coroutine#3] INFO  sample - Send square 9
12:39:46.811 [main @coroutine#1] INFO  sample - 4
12:39:46.811 [main @coroutine#1] INFO  sample - 9
12:39:46.811 [main @coroutine#2] INFO  sample - Send number 4
12:39:46.811 [main @coroutine#3] INFO  sample - Send square 16
12:39:46.812 [main @coroutine#2] INFO  sample - Send number 5
12:39:46.812 [main @coroutine#2] INFO  sample - Send number 6
12:39:46.812 [main @coroutine#1] INFO  sample - 16
12:39:46.812 [main @coroutine#3] INFO  sample - Send square 25
12:39:46.812 [main @coroutine#3] INFO  sample - Send square 36
12:39:46.812 [main @coroutine#1] INFO  sample - 25
12:39:46.812 [main @coroutine#1] INFO  sample - Done!
12:39:46.866 [main @coroutine#2] INFO  sample - Send number 7
```

## Pipeline での素数生成
パイプラインの一端を垣間見るため、無限の素数を生成する Pipeline で確認を行う。
以下の main 関数で呼び出している、numbersFrom と filter 関数は、
呼び出し元 CoroutineScope （この場合は、runBlocking となる）の拡張関数として実行するので、
生成した Coroutine の親は、runBlocking となる。
したがって、構造化された同時実効性によって、親の runBlocking の子 Coroutine をキャンセルすると、
上記関数で生成した Coroutine すべてがキャンセル対象となる。
つまり、Coroutine ビルダで生成した Job を管理する必要がないということになる。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

@OptIn(ExperimentalCoroutinesApi::class)
// CoroutineScope の拡張関数でないと、produce ビルダが利用できない
fun CoroutineScope.numbersFrom(start: Int) = produce<Int> {
    var x = start
    while (true) {
        log.info("numbers: Send number $x")
        send(x++)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
// CoroutineScope の拡張関数でないと、produce ビルダが利用できない
fun CoroutineScope.filter(numbers: ReceiveChannel<Int>, prime: Int): ReceiveChannel<Int> = produce {
    // 指定した素数で割りきれる数値を破棄する
    for (x in numbers) {
        log.info("filter: number $x, prime $prime")
        if (x % prime != 0) {
            log.info("filter: Send filter $x")
            send(x)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking<Unit> {
    // 数列を 2 （最小の素数）から開始
    var cur = numbersFrom(2)
    repeat(10) {
        // 素数を取得
        val prime = cur.receive()
        log.info("main: Prime $prime")
        cur = filter(cur, prime)
    }
    log.info("Done!")
    // 子 Coroutine をキャンセル
    coroutineContext.cancelChildren()
}
```
実行結果  
numbersFrom(2) -> filter(2) -> filter(3) -> filter(5) -> filter(7) ...
```
13:17:47.901 [main @coroutine#2] INFO  sample - numbers: Send number 2
13:17:47.906 [main @coroutine#2] INFO  sample - numbers: Send number 3
13:17:47.907 [main @coroutine#1] INFO  sample - main: Prime 2
13:17:47.909 [main @coroutine#3] INFO  sample - filter: number 3, prime 2
13:17:47.909 [main @coroutine#3] INFO  sample - filter: Send filter 3
13:17:47.910 [main @coroutine#2] INFO  sample - numbers: Send number 4
13:17:47.910 [main @coroutine#2] INFO  sample - numbers: Send number 5
13:17:47.910 [main @coroutine#1] INFO  sample - main: Prime 3
13:17:47.910 [main @coroutine#3] INFO  sample - filter: number 4, prime 2
13:17:47.910 [main @coroutine#3] INFO  sample - filter: number 5, prime 2
13:17:47.910 [main @coroutine#3] INFO  sample - filter: Send filter 5
13:17:47.911 [main @coroutine#4] INFO  sample - filter: number 5, prime 3
13:17:47.911 [main @coroutine#4] INFO  sample - filter: Send filter 5
13:17:47.911 [main @coroutine#2] INFO  sample - numbers: Send number 6
13:17:47.911 [main @coroutine#3] INFO  sample - filter: number 6, prime 2
13:17:47.911 [main @coroutine#1] INFO  sample - main: Prime 5
13:17:47.911 [main @coroutine#2] INFO  sample - numbers: Send number 7
13:17:47.911 [main @coroutine#2] INFO  sample - numbers: Send number 8
13:17:47.911 [main @coroutine#3] INFO  sample - filter: number 7, prime 2
13:17:47.911 [main @coroutine#3] INFO  sample - filter: Send filter 7
13:17:47.912 [main @coroutine#3] INFO  sample - filter: number 8, prime 2
13:17:47.912 [main @coroutine#4] INFO  sample - filter: number 7, prime 3
13:17:47.912 [main @coroutine#4] INFO  sample - filter: Send filter 7
13:17:47.912 [main @coroutine#2] INFO  sample - numbers: Send number 9
13:17:47.912 [main @coroutine#2] INFO  sample - numbers: Send number 10
13:17:47.912 [main @coroutine#5] INFO  sample - filter: number 7, prime 5
13:17:47.912 [main @coroutine#5] INFO  sample - filter: Send filter 7
13:17:47.912 [main @coroutine#3] INFO  sample - filter: number 9, prime 2
13:17:47.912 [main @coroutine#3] INFO  sample - filter: Send filter 9
13:17:47.912 [main @coroutine#3] INFO  sample - filter: number 10, prime 2
13:17:47.912 [main @coroutine#1] INFO  sample - main: Prime 7
・・・
```

上記処理は、iterator 標準ライブラリの Coroutine ビルダを利用して、
同様な Pipeline を構築できる。
produce を iterator 、send を yield 、receive を next、ReceiveChannel を Iterator に置き換えることで、
CoroutineScope を除去し、runBlocking も不要となる。

produce ビルダを利用する利点は、ディスパッチャを指定することで、
複数 CPU での並列処理に容易に変更できることとなる。

今回の素数を取得する処理は、非常に非現実的な方法となっている。
上記 Pipeline は、非同期処理の Suspend 処理が含まれるため、
sequence では実装できない。
完全な非同期動作となる produce と異なり、iterator を利用した Pipeline は、
任意の一時停止を許さないためである（？？？）。

## ファンアウト（Fan-out）
複数の Coroutine が同じ Channel から受信し、作業を分散する場合がある。
毎秒 10 個の数値を、定期的に生成する produce Coroutine からはじめる。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

@OptIn(ExperimentalCoroutinesApi::class)
// 連番の数値を送信するプロデューサを返す関数
fun CoroutineScope.produceNumbers() = produce<Int> {
    var x = 1
    while (true) {
        send(x++)
        delay(100)
    }
}

// Channell を処理する Coroutine を生成する関数
fun CoroutineScope.launchProcessor(id: Int, channel: ReceiveChannel<Int>) = launch {
    for (msg in channel) {
        log.info("Processor #$id received $msg")
    }
}

fun main() = runBlocking<Unit> {
    val producer = produceNumbers()
    // Channell の処理 Coroutine を 5 並列で起動する。
    repeat(5) { launchProcessor(it, producer) }
    delay(950)
    // 連番取得プロデューサをキャンセルすると構造化された同時実効性により Coroutine はすべてキャンセルされる
    // キャンセルされた時に Channel がクローズされるので、launchProcessor 内の for ループも終了する
    producer.cancel()
}
```
実行結果  
1 つの Channel の値を、複数の処理で分散して処理していることに注目。
```
15:27:53.258 [main @coroutine#3] INFO  sample - Processor #0 received 1
15:27:53.370 [main @coroutine#3] INFO  sample - Processor #0 received 2
15:27:53.471 [main @coroutine#4] INFO  sample - Processor #1 received 3
15:27:53.574 [main @coroutine#5] INFO  sample - Processor #2 received 4
15:27:53.684 [main @coroutine#6] INFO  sample - Processor #3 received 5
15:27:53.807 [main @coroutine#7] INFO  sample - Processor #4 received 6
15:27:53.919 [main @coroutine#3] INFO  sample - Processor #0 received 7
15:27:54.018 [main @coroutine#4] INFO  sample - Processor #1 received 8
15:27:54.153 [main @coroutine#5] INFO  sample - Processor #2 received 9
```

## ファンイン（Fan-in）
複数の Coroutine 処理から、同一の Channel に送信する場合もある。
文字列の Channel と、文字列を指定の遅延でこの Channel に送信する Suspend 関数を作成する。
この Suspend 関数を並列処理した場合を確認する。
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

suspend fun sendString(channel: SendChannel<String>, s: String, time: Long) {
    while (true) {
        delay(time)
        channel.send(s)
    }
}

fun main() = runBlocking {
    val channel = Channel<String>()
    launch { sendString(channel, "foo", 200L) }
    launch { sendString(channel, "BAR!", 500L) }
    repeat(6) {
        log.info(channel.receive())
    }
    coroutineContext.cancelChildren()
}
```
動作結果
```
15:52:45.940 [main @coroutine#1] INFO  sample - foo
15:52:46.140 [main @coroutine#1] INFO  sample - foo
15:52:46.238 [main @coroutine#1] INFO  sample - BAR!
15:52:46.341 [main @coroutine#1] INFO  sample - foo
15:52:46.542 [main @coroutine#1] INFO  sample - foo
15:52:46.739 [main @coroutine#1] INFO  sample - BAR!
```

## Channel バッファ
これまでのコードは、 Channel がバッファリングされていないため、
送信者と受信者がお互いに出会ったときに、要素を転送する（ランデブーと呼ばれる）。
最初に送信した場合、受信されるまで、Suspend する。
逆に最初に受信した場合、送信されるまで、Suspend する。

Channel 生成関数と produce ビルダは、capacity パラメータを指定して、バッファサイズを指定できる。
バッファを利用すると、送信者は複数の要素を送信してから Suspend するようになる。
この挙動は、バッファが一杯になるとブロックをする、BlockingQueue を同様。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

fun main() = runBlocking {
    val channel = Channel<Int>(4)
    val sender = launch {
        repeat(10) {
            log.info("Sending $it")
            channel.send(it)
        }
    }
    // 受信処理はせずに、待機のみする
    delay(1000)
    sender.cancel()
}
```
実行結果  
バッファが 4 つなので、5 つ目を送信中に Suspend している。
```
16:10:36.266 [main @coroutine#2] INFO  sample - Sending 0
16:10:36.280 [main @coroutine#2] INFO  sample - Sending 1
16:10:36.281 [main @coroutine#2] INFO  sample - Sending 2
16:10:36.282 [main @coroutine#2] INFO  sample - Sending 3
16:10:36.283 [main @coroutine#2] INFO  sample - Sending 4
```

## Channel 処理順の公正性
Channel への送受信は、複数の Coroutine からの呼び出し順に対して公正となる。
ただし、executor の特性によっては、不公正に見える場合もある。
送受信の順序は、先入れ先出しの順となる。
以下のコードは、2 つの Coroutine 「ping」と「pong」が、
共有の「テーブル」Channel から「ball」を受信する。
```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

data class Ball(var hits: Int)

// Channel に送信された Ball を取得し、hits を加算して再び、Channel に送信する
suspend fun player(name: String, table: Channel<Ball>) {
    for (ball in table) {
        ball.hits++
        log.info("$name $ball")
        delay(300)
        table.send(ball)
    }
}

fun main() = runBlocking {
    val table = Channel<Ball>()
    launch { player("ping", table) }
    launch { player("pong", table) }
    // ボールを Channel に送信
    table.send(Ball(0))
    // 1 秒後にすべての処理をキャンセル
    delay(1000)
    coroutineContext.cancelChildren()
}
```
実行結果  
「ping」Coroutine を最初に定義したので、「Ball」を受信する最初の Coroutine となっている。
「ping」Coroutine は、即座に受信を開始するが、既に受信を待っている「pong」Coroutine が受信する。
```
16:25:20.312 [DefaultDispatcher-worker-1 @coroutine#2] INFO  sample - ping Ball(hits=1)
16:25:20.617 [DefaultDispatcher-worker-1 @coroutine#3] INFO  sample - pong Ball(hits=2)
16:25:20.919 [DefaultDispatcher-worker-1 @coroutine#2] INFO  sample - ping Ball(hits=3)
16:25:21.221 [DefaultDispatcher-worker-2 @coroutine#3] INFO  sample - pong Ball(hits=4)
```

## ticker Channel
ticker Channel は、特別なランデブー Channel で、
この Channel の最後の受信以降、指定した時間が経過する度に、値を生成し続ける。
この特性は、ウィンドウ処理や、その他時間依存の処理を行う、
複雑な時間ベースの生成 Pipeline とオペレータを作成する場合に有用となる。

>ticker のデフォルトディスパッチャは、 Dispatchers.Unconfined となる。
>ticker は、現時点では、構造化された同時実効性と統合されていないため、
>将来 API が変更される可能性がある。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

@OptIn(ObsoleteCoroutinesApi::class)
fun main() = runBlocking<Unit> {
    val tickerChannel = ticker(delayMillis = 100, initialDelayMillis = 0)
    var nextElement = withTimeoutOrNull(1) { tickerChannel.receive() }
    log.info("Initial element is available immediately: $nextElement")

    nextElement = withTimeoutOrNull(50) { tickerChannel.receive() }
    log.info("Next element is not ready in 50 ms: $nextElement")

    nextElement = withTimeoutOrNull(60) { tickerChannel.receive() }
    log.info("Next element is ready in 100 ms: $nextElement")

    log.info("Consumer pauses for 150 ms")
    delay(150)

    nextElement = withTimeoutOrNull(1) { tickerChannel.receive() }
    log.info("Next element is available immediately after large consumer delay: $nextElement")

    nextElement = withTimeoutOrNull(60) { tickerChannel.receive() }
    log.info("Next element is ready in 50 ms after consumer pause in 150 ms: $nextElement")

    tickerChannel.cancel()
}
```
実行結果
```
16:54:29.631 [main @coroutine#1] INFO  sample - Initial element is available immediately: kotlin.Unit
16:54:29.682 [main @coroutine#1] INFO  sample - Next element is not ready in 50 ms: kotlin.Unit
16:54:29.986 [main @coroutine#1] INFO  sample - Next element is ready in 100 ms: null
16:54:29.986 [main @coroutine#1] INFO  sample - Consumer pauses for 150 ms
16:54:30.151 [main @coroutine#1] INFO  sample - Next element is available immediately after large consumer delay: kotlin.Unit
16:54:30.192 [main @coroutine#1] INFO  sample - Next element is ready in 50 ms after consumer pause in 150 ms: kotlin.Unit
```
