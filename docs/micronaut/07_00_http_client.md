# The HTTP Client

> Micronaut CLIを使用してプロジェクトを作成している場合、
> http-client依存関係がデフォルトで含まれている。

マイクロサービスアーキテクチャの重要なコンポーネントは、
マイクロサービス間のクライアント通信となる。
そのことを念頭に置いて、Micronaut は、
低レベル API と、高レベル AOP 駆動 API の両方を備えた、
組み込み HTTP クライアントを備えている。

> Micronaut の HTTP サーバーを使用するかどうかに関係なく、
> 機能が豊富なクライアント実装であるため、
> アプリケーションで Micronaut HTTP クライアントを使用することを推奨する。

HTTP クライアントを使用するには、クラスパスに http-client 依存関係が必要となる。

```groovy
implementation "io.micronaut:micronaut-http-client"
```

高レベルの API は低レベルの HTTP クライアント上に構築されているため、
最初に低レベルのクライアントを紹介する。

> 低レベルの API とは、RxHttpClient を指す。
> より高レベルのものを、宣言型クライアントとする。
> 宣言型クライアントは、インターフェースとしてクライアントの機能宣言し、
> 内部実装を Micronaut 側で用意する仕組みとなる。
> この仕組みは、Introduction Advice を利用して実現している。

> RxHttpClient の主な、リクエスト処理メソッドには、exchange および、retrieve がある。  
> exchange は、HttpResponse を取得でき、
> より詳細なレスポンス処理を行いたい場合に利用する。  
> retrieve は、HttpStatus または、レスポンスボディを任意の型で取得でき、
> ステータスまたは、ボディのみを処理したい場合に利用する。
