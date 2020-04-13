# Scheduled Tasks
バックグラウンドでの実行タスクは、@Scheduled アノテーションおよび、動的定義で設定できる。
スケジュールタスクの実行に利用するスレッドプールは、設定で変更できる。
変更できる内容は、並行数や、プール数など。
設定は、グループ毎に定義でき、タスク毎にスレッドプールを分けることもできる。

## @Scheduled アノテーション
@Scheduled 設定できる属性は以下となる。
属性に設定する値は、設定ファイルに定義した値を参照することもできる。

| 属性 | デフォルト値 | 説明 |
| ---- | ---- | ---- |
| fixedRate | - | 指定の間隔固定でタスクを実行する。前回のタスク実行が完了していない場合、完了後、即時にタスクを実行する。 |
| fixedDelay | - | 前回のタスク実行完了から、指定間隔が経過した後、タスクを実行する。 |
| cron | - | cron 形式で起動時間を設定できる。 |
| initialDelay | - | 一度だけ、起動から設定間隔後にタスクを実行する。他の属性と併用すると、サーバ起動直後の初回タスク実行を遅延させることができる。 |

cron の設定値の書式は、[cron - Wikipedia](https://en.wikipedia.org/wiki/Cron) を参照

## タスクの動的定義
タスクの動的定義は、TaskScheduler インターフェースで行う。
TaskScheduler は、インジェクトして取得する。
タスクは、以下の API で定義できる。
各 API の戻り値は、タスクのキャンセルや、タスクの戻り値を取得するために利用する。

- ```ScheduledFuture<?> schedule(String, Runnable)```  
  単発実行タスクを定義する。起動時間を cron 形式で指定する。タスクの戻り値無し。
- ```ScheduledFuture<?> schedule(Duration, Runnable)```  
  単発実行タスクを定義する。起動の遅延時間を指定する。タスクの戻り値無し。
- ```<V> ScheduledFuture<V> schedule(String, Callable<V>)```  
  単発実行タスクを定義する。起動時間を cron 形式で指定する。タスクの戻り値有り。
- ```<V> ScheduledFuture<V> schedule(Duration, Callable<V>)```  
  単発実行タスクを定義する。起動の遅延時間を指定する。タスクの戻り値有り。
- ```ScheduledFuture<?> scheduleAtFixedRate(@Nullable Duration, Duration, Runnable)```  
  定期実行タスクを定義する。初回起動の遅延時間と固定の繰り返し間隔を指定する。タスクの戻り値無し。
- ```ScheduledFuture<?> scheduleWithFixedDelay(@Nullable Duration, Duration, Runnable)```  
  定期実行タスクを定義する。初回起動の遅延時間と前回実行完了からの繰り返し間隔を指定する。タスクの戻り値無し。


## 制限事項

- suspend 関数は、タスクにできない。（runBlocking を利用すれば Coroutine を利用可能）
- cron 形式でも @hourly など利用できないものがある
