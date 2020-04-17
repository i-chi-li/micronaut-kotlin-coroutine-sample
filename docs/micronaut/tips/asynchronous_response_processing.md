# 非同期レスポンス処理
レスポンス処理を、非同期で行う方法を記載する。
ここで言う非同期とは、レスポンスを返した後も、処理を継続することを意味する。

以下のような非同期レスポンス処理の実現方法が考えられる。

- Coroutine GlobalScope による別スレッドでの処理
- Coroutine Channel 機能で処理
- Micronaut のタスク機能をイベント起動で処理

## Coroutine GlobalScope による別スレッドでの処理
以下の ```globalScope()``` 関数を参照

[非同期レスポンス処理サンプルコード](../../../docs/micronaut/tips/asynchronous_response_processing.md)


