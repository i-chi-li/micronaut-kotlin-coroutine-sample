<!-- toc -->

# Coroutine での AWS サービス利用について
Coroutine を利用して、AWS サービスにアクセスする、実践的な例を説明する。

[サンプルソース](../../../src/main/kotlin/micronaut/kotlin/coroutine/sample/ScheduledTasksSQSCoroutineController.kt)


## 概要
Micronaut のスケジュールタスクで処理を行う。
スケジュールタスクでは、以下の処理を行う。
- SQS キューを取得する
- SQS キューのメッセージに記載された、S3 バケット中のファイルを取得する
- ファイルに記載された値を設定して、API（スタブ） を呼び出す

Micronaut のイベントリスナー機能で、サーバシャットダウンイベントを処理する
イベント処理では、以下の処理を行う。
- スケジュールタスクの完了を待機（タイムアウト有り）
- 待機がタイムアウトした場合
  - スケジュールタスクをキャンセルする
  - スケジュールタスクの完了を待機（タイムアウト有り）
  - 待機がタイムアウトした場合
    - 強制的にシャットダウンする


## AWS ECS 上で実行する場合の補足
このサンプルは、ECS 上での実行も想定している。
ECS 上での実行は、Docker 上での実行と、ほぼ差異が無い。
ECS タスクの停止は、Docker でのコンテナ停止と同様に以下のようになる。
- SIGTERM が発生する
- 停止を待機する（デフォルトは 30 秒）
- タイムアウトした場合、SIGKILL が発生する

詳細は、以下を参照。
[Amazon Elastic Container Service
 APIリファレンス # StopTask](https://docs.aws.amazon.com/ja_jp/AmazonECS/latest/APIReference/API_StopTask.html)



