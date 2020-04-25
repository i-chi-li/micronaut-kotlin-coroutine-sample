<!-- toc -->
- Suspend 関数
  - 順次処理
  - async ビルダを利用した並列処理
  - async ビルダでの遅延処理開始
  - 非同期形式の関数
  - async ビルダでの構造化された並行性

# Suspend 関数
Suspend 関数の作成方法と、利用方法について記載する。

最初に、以後共通で利用する Suspend 関数を以下のように作成する。  
各関数は、1 秒の遅延を入れてある。
```kotlin
suspend fun doSomethingUsefulOne(): Int {
    delay(1000L)
    return 13
}

suspend fun doSomethingUsefulTwo(): Int { 
    delay(1000L)
    return 29
}
```

## 順次処理
通常、Coroutine 内の処理は、Suspend 関数であっても順次呼び出しとなる。
Suspend 関数をそのまま呼び出す場合は、通常の関数呼び出しと変わらない。
```kotlin
fun main() = runBlocking {
    val time = measureTimeMillis {
        val one = doSomethingUsefulOne()
        val two = doSomethingUsefulTwo()
        println("The answer is ${one + two}")
    }
    println("Completed in $time ms")
}
```
実行結果  
順次呼び出しなので、処理時間は、2 秒程度となる。
```
The answer is 42
Completed in 2033 ms
```

## async ビルダを利用した並列処理
 ```async``` ビルダは。その他の Coroutine と並列に動作する点で、```launch``` ビルダと同様。
```async``` ビルダは、```Deferred``` を返す点で異なる。
```Deferred``` は、```await``` 関数で処理の戻り値を取得できる。
Suspend 関数を並列に非同期呼び出しを行う例。  
Suspend 関数を非同期に呼び出す場合は、```async``` ブロックを利用する。
```kotlin
fun main() = runBlocking {
    val time = measureTimeMillis {
        val one = async { doSomethingUsefulOne() }
        val two = async { doSomethingUsefulTwo() }
        println("The answer is ${one.await() + two.await()}")
    }
    println("Completed in $time ms")
}
```
実行結果  
関数を並列に呼び出しているため、処理時間は、1 秒程度となっている。
```
The answer is 42
Completed in 1176 ms
```

## async ビルダでの遅延処理開始
遅延処理開始は、```async(start = CoroutineStart.LAZY) { ... }``` で行う。
通常の並列処理と結果は同じだが、実際に処理を開始するのは、```start()``` を呼び出した時点となる。  
必ず実行されるわけではない処理を、事前に定義することができる。  
```start()``` を呼び出さず、```await()``` を呼び出した場合、
```await()``` 内部で処理を開始して、処理完了を待つため、順次処理と同様になる。  
Kotlin で遅延呼び出しを行う ```lazy``` 関数があるが、Coroutine スコープではないため、
Suspend 関数を呼び出せない。そのため、この方法が代替手段となる。
```kotlin
fun main() = runBlocking {
    val time = measureTimeMillis {
        val one = async(start = CoroutineStart.LAZY) { doSomethingUsefulOne() }
        val two = async(start = CoroutineStart.LAZY) { doSomethingUsefulTwo() }
        one.start()
        two.start()
        println("The answer is ${one.await() + two.await()}")
    }
    println("Completed in $time ms")
}
```

## 非同期形式の関数
>【注意】この定義方法は、強く非推奨である。  
>この定義方法は、他のプログラミング言語では一般的であるが、
>await() 呼び出しの直前に例外が発生した場合などで、処理が中止されたにも関わらず、
>この関数の処理は、続行したままとなる。
>よって、次に説明する ```async ビルダでの構造化された並行性``` で定義することを推奨する。

非同期で処理を行う関数（Suspend 関数では無い）の定義方法。
この形式の関数名の末尾には、```Async``` を付与し、遅延処理を明示することを推奨する。  
非同期関数形式での定義例。
```kotlin
fun somethingUsefulOneAsync() = GlobalScope.async { 
    doSomethingUsefulOne()
}

fun somethingUsefulTwoAsync() = GlobalScope.async { 
    doSomethingUsefulTwo()
}
```
非同期関数形式で定義した関数の呼び出し例。  
Coroutine スコープ外からの呼び出しが可能。
```kotlin
fun main() {
    val time = measureTimeMillis { 
        val one = somethingUsefulOneAsync()
        val two = somethingUsefulTwoAsync()
        runBlocking { 
            // 処理完了待ち（await()）呼び出しは、Suspend 関数か、ブロッキングスコープ内で行う必要がある
            println("The anser s ${one.await() +two.await()}")
        }
    }
}
```

## async ビルダでの構造化された並行性
 ```async { ... }``` は、```coroutineScope``` の拡張関数であり、
```coroutineScope``` を親とし、```async { ... }``` を子供として定義できる。
```kotlin
suspend fun concurrentSum(): Int = coroutineScope {
    val one = async { doSomethingUsefulOne() }
    val two = async { doSomethingUsefulTwo() }
    one.await() + two.await()
}
```

呼び出しは、以下のようにする。
もし、```concurrentSum()``` 内で例外がスローされた場合、
呼び出し元の Coroutine もキャンセルされる。
```kotlin
fun main() = runBlocking {
    val time = measureTimeMillis {
        println("The answer is ${concurrentSum()}")
    }
    println("Completed in $time ms")
}

```
実行結果  
処理時間から判断すると、期待通り、並列実行になっている。
```
The answer is 42
Completed in 1062 ms
```

では、本当に子供の例外が、親に伝播するかを実際に確認する。  
以下の実装例では、```two``` でスローした例外によって、
```one``` の処理がキャンセルされ、呼び出し元にも例外が伝播することを確認する。
```kotlin
fun main() = runBlocking<Unit> {
    try {
        failedConcurrentSum()
    } catch (e: ArithmeticException) {
        println("Computation failed with ArithmeticException")
    }
}

suspend fun failedConcurrentSum(): Int = coroutineScope {
    val one = async<Int> {
        try {
            delay(Long.MAX_VALUE)
            42
        } finally {
            println("First child was cancelled")
        }
    }
    val two = async<Int> {
        println("Second child throws an exception")
        throw ArithmeticException()
    }
    one.await() + two.await()
}
```
実行結果  
```one``` の処理が、キャンセルされ、呼び出し元にも例外が伝播している。
```
Second child throws an exception
First child was cancelled
Computation failed with ArithmeticException
```
