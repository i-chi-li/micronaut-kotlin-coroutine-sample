<!-- toc -->
- 認証方式
  - 概要
  - Security モジュールの設定
  - ログイン認証 API 型
    - 実行方法
  - 事前定義手順型
    - 実行方法

# 認証方式
認証方式の例として、ログイン認証 API 型および、事前定義手順型を記載する。

## 概要
認証トークンは、`Authorization: Bearer`ヘッダーで送信する。
認証トークンは、Base64 エンコードされた「ユーザ識別子:シークレット」形式とする。
認証トークンの取得処理は、TokenReader インターフェースを実装して行う。
認証トークンの認証処理は、TokenValidator インターフェースを実装して行う。
セキュリティルールは、SecurityRule インターフェースを実装して行う。

処理は、以下の順で行われる。

1. 認証トークンの取得
2. 認証トークンの認証
3. セキュリティルール検証

各処理の大まかな役割は、以下のようになる。
- 認証トークンは、ユーザ識別子と、パスワードの認証を行う
- @Secured アノテーションおよび、intercept-url-map は、
  静的なロール（認証要否含む）設定を行う
- セキュリティルールは、動的なロールの判定や、
  リクエスト情報を利用した判定などの認可処理を行う。

## Security モジュールの設定
以下を依存ライブラリに追加する。

```groovy
implementation "io.micronaut:micronaut-security"
kapt "io.micronaut:micronaut-security"
```

application.yml に以下の設定を追加する。
Basic 認証用の処理を無効にする理由は、
Authorization ヘッダの値を処理してしまい、
独自に実装した処理と競合してしまうため。

カスタムロールは、@Secured または、intercept-url-map の access に指定した、
すべての値が、認証処理（CustomAuthTokenValidator クラス）で、
認証結果に格納するロールに含まれている場合に、認証成功となる。

```yaml
micronaut:
    security:
        # セキュリティ機能を有効化
        enabled: true
        token:
            basic-auth:
                # Basic 認証用の処理を無効にする（デフォルトで有効となっている）
                enabled: false
        intercept-url-map:
            -   pattern: /favicon.ico
                http-method: GET
                access:
                    - isAnonymous()
            -   pattern: /auth/**
                access:
                    - isAuthenticated()
                    # カスタムロールを指定できる
                    - CUSTOM_ROLE_1
            -   pattern: /totp/**
                access:
                    - isAuthenticated()
```

## ログイン認証 API 型
ログイン認証 API 型とは、ログイン用の API を用意し、
ログイン認証に成功した場合、トークンが返却され、
以後、認証が必要な API へのアクセス時に、
トークンを渡すような方式とする。
例では、Authorization ヘッダでトークンを渡すこととする。
サーバ・クライアント間のデータ送受信は JSON 形式で行うこととする。

[ログイン認証 API 型サンプルコード](../../../src/main/kotlin/micronaut/kotlin/coroutine/sample/AuthLoginController.kt)

サンプルコードでの、トークン認証は、
AuthTOTPController 側の CustomAuthTokenValidator クラスで行っている。

### 実行方法
- AuthLoginController.login 処理を呼び出す(呼び出し方は、関数のコメントを参照)
- `{"result": "Authorization: Bearer dXNlcjE6MjcxODk4"}` のようなレスポンスが返る
- AuthDataController.index 処理を呼び出す(呼び出し方は、関数のコメントを参照)  
  呼び出す際に、上述のレスポンスの値を利用する。

## 事前定義手順型
事前定義手順型とは、事前に秘密キーを発行しておき、
クライアントでは、接続時に秘密キーを利用して事前に取り決めた手順で、
認証用の一時トークンを生成してサーバへ送信し、
サーバでは、受信した一時トークンを判定し、認証を行う方式とする。
例では、事前定義手順に、Time-based One-time Password (TOTP) を利用する。
TOTP を扱うためのライブラリは、
[wstrange/GoogleAuth](https://github.com/wstrange/GoogleAuth)
を利用する。
例では、Authorization ヘッダで一時トークンを渡すこととする。
サーバ・クライアント間のデータ送受信は JSON 形式で行うこととする。

[事前定義手順型サンプルコード](../../../src/main/kotlin/micronaut/kotlin/coroutine/sample/AuthTOTPController.kt)

### 実行方法
- AuthTOTPController.generateKey 処理を呼び出す(呼び出し方は、関数のコメントを参照)
- `{"userId": "user1", "secretKey": "7XLJURTNSUZEEGCI"}` のようなレスポンスが返る  
  内部で、userId と secretKey が保持される。
- AuthTOTPController.getCode 処理を呼び出す(呼び出し方は、関数のコメントを参照)
- `{"result": "Authorization: Bearer dXNlcjE6OTY4MzA="}` のようなレスポンスが返る  
  内部で保持している userId の secretKey でトークンを生成している。
  本来は、クライアント側で行う処理となる。
- AuthTOTPController.list 処理を呼び出す(呼び出し方は、関数のコメントを参照)  
  呼び出す際に、上述のレスポンスの値を利用する。
