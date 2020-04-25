<!-- toc -->
- select ビルダ（実験的）
  - select ビルダで Channel を選択
  - select ビルダで Channel クローズ時の処理
  - selectUnbiased ビルダ

# select ビルダ（実験的）
select ビルダを利用すると、複数の suspend 関数を同時に待機して、
最初に値を返した値を取得できる。

>select ビルダは、実験的な Coroutine の機能。
>今後、重要な変更の可能性があり、進化することが期待されている。

## select ビルダで Channel を選択
文字列を供給する fizz および、buzz プロデューサがある。
fizz プロデューサは、300 ms 毎に、"Fizz"文字列を生成し、
buzz プロデューサは、500 ms 毎に、"Buzz!"文字列を生成する。
receive Suspend 関数を利用して、1 つ以上の Channel から受信できる。
しかし、select ビルダであれば、onReceive を使用して、両方から同時に受信できる。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.fizz() = produce<String> {
    while (true) {
        delay(300)
        send("Fizz")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.buzz() = produce<String> {
    while (true) {
        delay(500)
        send("Buzz!")
    }
}

suspend fun selectFizzBuzz(fizz: ReceiveChannel<String>, buzz: ReceiveChannel<String>) {
    select<Unit> {
        fizz.onReceive {
            log.info("fizz -> '$it'")
        }
        buzz.onReceive {
            log.info("buzz -> '$it'")
        }
    }
}

fun main() = runBlocking<Unit> {
    val fizz = fizz()
    val buzz = buzz()
    repeat(7) {
        selectFizzBuzz(fizz, buzz)
    }
    coroutineContext.cancelChildren()
}
```

実行結果

```
19:16:15.541 [main @coroutine#1] INFO  sample - fizz -> 'Fizz'
19:16:15.738 [main @coroutine#1] INFO  sample - buzz -> 'Buzz!'
19:16:15.839 [main @coroutine#1] INFO  sample - fizz -> 'Fizz'
19:16:16.141 [main @coroutine#1] INFO  sample - fizz -> 'Fizz'
19:16:16.239 [main @coroutine#1] INFO  sample - buzz -> 'Buzz!'
19:16:16.443 [main @coroutine#1] INFO  sample - fizz -> 'Fizz'
19:16:16.741 [main @coroutine#1] INFO  sample - buzz -> 'Buzz!'
```

## select ビルダで Channel クローズ時の処理
Channel が閉じていると、select の onReceive 処理で失敗し、対応する select が例外をスローする。
onReceiveOrNull を使用して、Channel が閉じているときに特定のアクションを実行できる。
select は、選択した結果を返す式でもある。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun selectAorB(a: ReceiveChannel<String>, b: ReceiveChannel<String>): String =
    select {
        a.onReceiveOrNull().invoke {
            log.info("Receive 'a'")
            if (it == null) {
                "Channel 'a' is closed"
            } else {
                "a -> '$it'"
            }
        }
        b.onReceiveOrNull().invoke {
            log.info("Receive 'b'")
            if (it == null) {
                "Channel 'b' is closed"
            } else {
                "b -> '$it'"
            }
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking<Unit> {
    val a = produce<String> {
        repeat(4) {
            log.info("Send 'a'")
            send("Hello $it")
        }
    }
    val b = produce<String> {
        repeat(4) {
            log.info("Send 'b'")
            send("World $it")
        }
    }
    repeat(8) {
        log.info("main: ${selectAorB(a, b)}")
    }
    log.info("Cancel")
    coroutineContext.cancelChildren()
}
```

実行結果  
ログ出力が、```a``` に偏っている理由は、```select``` ビルダに、
複数の Channel を指定した場合、最初の方の Channel が優先となるため。

```
17:27:29.043 [main @coroutine#2] INFO  sample - Send 'a'
17:27:29.049 [main @coroutine#2] INFO  sample - Send 'a'
17:27:29.055 [main @coroutine#3] INFO  sample - Send 'b'
17:27:29.055 [main @coroutine#1] INFO  sample - Receive 'a'
17:27:29.056 [main @coroutine#1] INFO  sample - main: a -> 'Hello 0'
17:27:29.061 [main @coroutine#1] INFO  sample - Receive 'a'
17:27:29.061 [main @coroutine#1] INFO  sample - main: a -> 'Hello 1'
17:27:29.062 [main @coroutine#1] INFO  sample - Receive 'b'
17:27:29.062 [main @coroutine#1] INFO  sample - main: b -> 'World 0'
17:27:29.062 [main @coroutine#2] INFO  sample - Send 'a'
17:27:29.062 [main @coroutine#2] INFO  sample - Send 'a'
17:27:29.062 [main @coroutine#3] INFO  sample - Send 'b'
17:27:29.062 [main @coroutine#1] INFO  sample - Receive 'a'
17:27:29.062 [main @coroutine#1] INFO  sample - main: a -> 'Hello 2'
17:27:29.063 [main @coroutine#1] INFO  sample - Receive 'a'
17:27:29.063 [main @coroutine#1] INFO  sample - main: a -> 'Hello 3'
17:27:29.063 [main @coroutine#1] INFO  sample - Receive 'b'
17:27:29.063 [main @coroutine#1] INFO  sample - main: b -> 'World 1'
17:27:29.064 [main @coroutine#3] INFO  sample - Send 'b'
17:27:29.064 [main @coroutine#1] INFO  sample - Receive 'a'
17:27:29.064 [main @coroutine#1] INFO  sample - main: Channel 'a' is closed
17:27:29.064 [main @coroutine#1] INFO  sample - Receive 'a'
17:27:29.064 [main @coroutine#1] INFO  sample - main: Channel 'a' is closed
17:27:29.064 [main @coroutine#1] INFO  sample - Cancel
```

## selectUnbiased ビルダ
selectUnbiased ビルダは、Channel の選択をランダムにする。

```kotlin
val log: Logger = LoggerFactory.getLogger("sample")

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun selectUnbiasedAorB(a: ReceiveChannel<String>, b: ReceiveChannel<String>): String =
    selectUnbiased {
        a.onReceiveOrNull().invoke {
            log.info("Receive 'a'")
            if (it == null) {
                "Channel 'a' is closed"
            } else {
                "a -> '$it'"
            }
        }
        b.onReceiveOrNull().invoke {
            log.info("Receive 'b'")
            if (it == null) {
                "Channel 'b' is closed"
            } else {
                "b -> '$it'"
            }
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = runBlocking<Unit> {
    val a = produce<String> {
        repeat(4) {
            log.info("Send 'a'")
            send("Hello $it")
        }
    }
    val b = produce<String> {
        repeat(4) {
            log.info("Send 'b'")
            send("World $it")
        }
    }
    repeat(8) {
        log.info("main: ${selectUnbiasedAorB(a, b)}")
    }
    log.info("Cancel")
    coroutineContext.cancelChildren()
}
```

実行結果  
特定の Channel に偏らずに取得してる。

```
17:39:41.073 [main @coroutine#2] INFO  sample - Send 'a'
17:39:41.077 [main @coroutine#2] INFO  sample - Send 'a'
17:39:41.082 [main @coroutine#3] INFO  sample - Send 'b'
17:39:41.082 [main @coroutine#1] INFO  sample - Receive 'a'
17:39:41.083 [main @coroutine#1] INFO  sample - main: a -> 'Hello 0'
17:39:41.088 [main @coroutine#1] INFO  sample - Receive 'b'
17:39:41.088 [main @coroutine#1] INFO  sample - main: b -> 'World 0'
17:39:41.088 [main @coroutine#1] INFO  sample - Receive 'a'
17:39:41.088 [main @coroutine#1] INFO  sample - main: a -> 'Hello 1'
17:39:41.088 [main @coroutine#3] INFO  sample - Send 'b'
17:39:41.088 [main @coroutine#3] INFO  sample - Send 'b'
17:39:41.088 [main @coroutine#2] INFO  sample - Send 'a'
17:39:41.088 [main @coroutine#1] INFO  sample - Receive 'b'
17:39:41.088 [main @coroutine#1] INFO  sample - main: b -> 'World 1'
17:39:41.089 [main @coroutine#1] INFO  sample - Receive 'b'
17:39:41.089 [main @coroutine#1] INFO  sample - main: b -> 'World 2'
17:39:41.089 [main @coroutine#1] INFO  sample - Receive 'a'
17:39:41.089 [main @coroutine#1] INFO  sample - main: a -> 'Hello 2'
17:39:41.089 [main @coroutine#3] INFO  sample - Send 'b'
17:39:41.090 [main @coroutine#2] INFO  sample - Send 'a'
17:39:41.090 [main @coroutine#1] INFO  sample - Receive 'b'
17:39:41.090 [main @coroutine#1] INFO  sample - main: b -> 'World 3'
17:39:41.090 [main @coroutine#1] INFO  sample - Receive 'a'
17:39:41.090 [main @coroutine#1] INFO  sample - main: a -> 'Hello 3'
17:39:41.090 [main @coroutine#1] INFO  sample - Cancel
```
