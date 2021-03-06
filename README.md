# micronaut-kotlin-coroutine-sample
Micronaut フレームワークで、Kotlin Coroutine を利用するサンプル

| 名称 | バージョン |
| :--- | :--- |
| Java | 1.8 |
| Kotlin | 1.4 |
| KotlinTest | 4.1 |
| Coroutine | 1.4 |
| Micronaut | 2.1 |

このプロジェクトの記述内容は、間違っている場合もあります。
必ず、公式ドキュメントを確認するようにしてください。

このドキュメントは、公式ドキュメントを独断と偏見で解釈し、重要そうな部分を書き写しています。
正確な情報は、以下の公式サイトを参照してください。

- [Kotlin Coroutine](https://kotlinlang.org/docs/reference/coroutines/coroutines-guide.html)
- [Kotest](https://github.com/kotest/kotest)
- [Micronaut](https://micronaut.io/)

---

# 目次
- Kotlin
    - [総称型(Generics)](docs/kotlin/generics.md)
    - [Kotlin Tips](docs/kotlin/kotlin-tips.md)

- Coroutine
    - [はじめに](docs/coroutine/00_introduction.md)
    - [Coroutine の基礎](docs/coroutine/10_basics.md)
    - [キャンセルとタイムアウト](docs/coroutine/20_cancellation_and_timeouts.md)
    - [Suspend 関数](docs/coroutine/30_composing_suspending_functions.md)
    - [コンテキストとディスパッチャ](docs/coroutine/40_coroutine_context_and_dispatchers.md)
    - [非同期 Flow 処理](docs/coroutine/50_asynchronous_flow.md)
    - [チャネル](docs/coroutine/60_channels.md)
    - [例外処理と監視](docs/coroutine/70_exception_handling_and_supervision.md)
    - [変更可能な状態の共有と同時平行性](docs/coroutine/80_shared_mutable_state_and_concurrency.md)
    - [select 式（実験的）](docs/coroutine/90_select_expression_experimental.md)
    - [kotlin-coroutines-test モジュール](docs/coroutine/100_kotlinx_coroutines_test.md)
- Micronaut
    - Inversion of Control
        - [Bean Introspection](docs/micronaut/03_14_bean_introspection.md)
    - [アスペクト指向プログラミング](docs/micronaut/05_00_aspect_oriented_programming.md)
        - [Around Advice](docs/micronaut/05_01_around_advice.md)
        - [Introduction Advice](docs/micronaut/05_02_introduction_advice.md)
        - [Method Adapter Advice](docs/micronaut/05_03_method_adapter_advice.md)
        - [Validation Advice](docs/micronaut/05_04_validation_advice.md)
        - [Cache Advice](docs/micronaut/05_05_cache_advice.md)
        - [Retry Advice](docs/micronaut/05_06_retry_advice.md)
        - [Scheduled Tasks](docs/micronaut/05_07_scheduled_tasks.md)
    - The Http Server
        - [Error Handling](docs/micronaut/06_15_error_handling.md)
        - [HTTP セッション](docs/micronaut/06_22_http_sessions.md)
    - [The HTTP Client](docs/micronaut/07_00_http_client.md)
        - [@Client を使用した宣言型HTTPクライアント](docs/micronaut/07_03_client-annotation.md)
    - Language Support
        - [Kotlin and AOP Advice](docs/micronaut/13_03_03_kotlin_and_aop_advice.md)
        - [Coroutines Support](docs/micronaut/13_03_05_coroutines_support.md)
    - [Security](docs/micronaut/15_00_security.md)
        - [Micronaut Security](docs/micronaut/15_01_micronaut_security.md)
    - Tips
        - [非同期レスポンス処理](docs/micronaut/tips/asynchronous_response_processing.md)
        - [認証方式](docs/micronaut/tips/authentication.md)
        - [Coroutine での AWS サービス利用について](docs/micronaut/tips/coroutine_aws.md)

---

# 実装サンプル
各サンプルファイルは、複数のクラスなどをまとめて記載しているが、
実際に記述する場合は、コーディング規約などに基づいてきちんとしたソースコードを記述すること。

- Coroutine
    - [launch ビルダ](src/test/kotlin/micronaut/kotlin/coroutine/sample/coroutine/LaunchTest.kt)
    - [async ビルダ](src/test/kotlin/micronaut/kotlin/coroutine/sample/coroutine/AsyncTest.kt)
    - [withTimeout ビルダ](src/test/kotlin/micronaut/kotlin/coroutine/sample/coroutine/WithTimeoutTest.kt)
    - [基本的なテスト実装](src/test/kotlin/micronaut/kotlin/coroutine/sample/coroutine/BasicsTest.kt)
- Micronaut
    - [基本的なテスト実装](src/test/kotlin/micronaut/kotlin/coroutine/sample/micronaut/CoroutineControllerTest.kt)
    - [プライベートメンバーのテスト実装](src/test/kotlin/micronaut/kotlin/coroutine/sample/micronaut/PrivateMemberTest.kt)
    - [レスポンス用 XML 変換](src/main/kotlin/micronaut/kotlin/coroutine/sample/XmlController.kt)
    - [設定値の利用方法](src/main/kotlin/micronaut/kotlin/coroutine/sample/ConfigurationUsageController.kt)
    - [@Client を使用した宣言型HTTPクライアント](src/main/kotlin/micronaut/kotlin/coroutine/sample/HttpClientController.kt)
