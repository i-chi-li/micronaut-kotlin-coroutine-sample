<!-- toc -->
- [Micronaut Security](https://micronaut-projects.github.io/micronaut-security/latest/guide/)
  - 利用方法
  - Authentication Providers
  - Security Rules
    - IP パターンルール
    - Secured アノテーション
  - Intercept URL Map
  - ビルトイン・エンドポイントのセキュリティ
  - Authentication Strategies
    - [Basic Auth](https://micronaut-projects.github.io/micronaut-security/latest/guide/#basicAuth)
    - [Session Authentication](https://micronaut-projects.github.io/micronaut-security/latest/guide/#session)
    - JSON Web Token
      - Reading JWT Token
      - JWT Token Generation
      - JWT Token Validation
      - Claims Generation
      - Token Render
    - LDAP Authentication
      - Configuration
      - Extending Default Behavior
  - Rejection Handling
  - Token Propagation
  - ビルトイン セキュリティ コントローラ
    - [Login Controller](https://micronaut-projects.github.io/micronaut-security/latest/guide/#login)
    - [Logout Controller](https://micronaut-projects.github.io/micronaut-security/latest/guide/#logout)
    - Refresh Controller
    - Keys Controller
  - Retrieve the authenticated user
    - User outside of a controller
  - Security Events
  - OAuth 2.0
    - Installation
    - OpenID Connect
    - Flows
      - Authorization Code
      - Password
    - Endpoints
      - OpenID End Session
      - Introspection
      - Revocation
      - OpenID User Info
    - Custom Clients

# [Micronaut Security](https://micronaut-projects.github.io/micronaut-security/latest/guide/)
Micronaut Security モジュールは、アプリケーション向けのフル機能で、
カスタマイズ可能なセキュリティソリューションとなる。

## 利用方法
以下を依存ライブラリに追加する。

```groovy
implementation "io.micronaut:micronaut-security"
kapt "io.micronaut:micronaut-security"
```

application.yml に以下の設定を追加する。
それ以外の設定項目は、
[SecurityConfigurationProperties Property](https://micronaut-projects.github.io/micronaut-security/latest/api/io/micronaut/security/config/SecurityConfigurationProperties.html)
を参照。

```yaml
micronaut:
    security:
        enabled: true
```

## Authentication Providers
ユーザを認証するには、AuthenticationProvider インターフェースの
実装を提供する必要がある。
AuthenticationProvider インターフェースの実装は、複数登録できる。
複数実装を登録した場合、認証が成功するまで、すべての実装が呼び出される。
すべての実装で認証に失敗した場合に、認証失敗のレスポンスをする。

```kotlin
@Singleton
class AuthenticationProviderUserPassword : AuthenticationProvider {
    override fun authenticate(
        authenticationRequest: AuthenticationRequest<*, *>
    ): Publisher<AuthenticationResponse> {
        return if (
            authenticationRequest.identity == "users"
            && authenticationRequest.secret == "password"
        ) {
            Flowable.just(UserDetails("user", listOf()))
        } else {
            Flowable.just(AuthenticationFailed())
        }
    }
}
```

UserFetcher、PasswordEncoder、AuthoritiesFetcherの実装を提供しない限り、DelegatingAuthenticationProviderは有効になりません。

## Security Rules
特定のエンドポイントへのアクセスを、
匿名ユーザまたは、認証済みユーザに許可する判定は、
Security Rule のコレクションによって決定される。
Security Rule は、SecurityRule インターフェース実装を Bean として登録することで独自実装ができる。
Security Rule は、複数登録でき、判定結果が一つでも REJECTED の場合、認証失敗となる。
Security Rule には、優先度があり、getOrder 関数をオーバーライドすることで変更できる。
Security Rule の優先度は、デフォルト 0 となり、小さいほど（マイナスを含む）優先度が高い。
Micronaut には、複数の組み込み Security Rule が付属している。
@Secured アノテーションでの設定は、SecuredAnnotationRule で処理され、
intercept-url-map の設定は、ConfigurationInterceptUrlMapRule で処理されるため、
独自の Security Rule を優先したい場合は、組み込みの Security Rule より
優先度を高く設定する必要がある。 
Security Rule を呼び出して判定を行う処理は、SecurityFilter クラスで行われている。

```kotlin
@Singleton
class CustomSecurityRule : SecurityRule {
    override fun getOrder(): Int {
        return Ordered.HIGHEST_PRECEDENCE
    }

    override fun check(
        request: HttpRequest<*>,
        routeMatch: RouteMatch<*>?,
        claims: MutableMap<String, Any>?
    ): SecurityRuleResult {
        val auth = request.headers.authorization
        return if (auth.isPresent) {
            println("Authorization header: ${auth.get()}")
            SecurityRuleResult.ALLOWED
        } else {
            println("Finish check failed")
            SecurityRuleResult.REJECTED
        }
    }
}
```

### IP パターンルール
セキュリティを有効にすると、任意の IP アドレスからのトラフィックが、
デフォルトで許可される。
ただし、以下のように、IP パターンのホワイトリストから、
送信されていないトラフィックは拒否できる。

```yaml
micronaut:
  security:
    enabled: true
    ip-patterns:
      - 127.0.0.1
      - 192.168.1.*
```

### Secured アノテーション
@Secured アノテーションは、コントローラまたは、
コントローラのアクションレベルで、アクセスを制御できる。

```kotlin
@Controller("/auth")
// デフォルトでは、認証済みでアクセスをするように設定
@Secured(SecurityRule.IS_AUTHENTICATED)
class AuthenticationController {
    /**
     * 特定のロールで認証を行う場合
     */
    @Get("/admin")
    @Produces(MediaType.APPLICATION_JSON)
    // 特定のロールを指定する設定
    @Secured("ROLE_ADMIN", "ROLE_X")
    fun withRoles(): String {
        return "OK"
    }

    /**
     * 認証を行わない場合
     */
    @Get("/anonymous")
    @Produces(MediaType.APPLICATION_JSON)
    // 匿名アクセスを許可する設定
    @Secured(SecurityRule.IS_ANONYMOUS)
    fun anonymous(): String {
        return "OK"
    }

    /**
     * 認証方法の指定を行わない場合、Class に設定した値を継承する
     */
    @Get("/authenticated")
    @Produces(MediaType.APPLICATION_JSON)
    fun authenticated(): String {
        return "OK"
    }
}
```

認証指定のアノテーションは、JSR 250 で定義されている、以下のアノテーションも利用できる。

- javax.annotation.security.PermitAll
- javax.annotation.security.RolesAllowed
- javax.annotation.security.DenyAll

```kotlin
@Controller("/auth")
class JSR250AuthenticationController {
    @Get("/admin")
    @Produces(MediaType.APPLICATION_JSON)
    // JSR 250 アノテーションで指定
    @RolesAllowed("ROLE_ADMIN", "ROLE_X")
    fun withRoles(): String {
        return "OK"
    }

    @Get("/anonymous")
    @Produces(MediaType.APPLICATION_JSON)
    // JSR 250 アノテーションで指定
    @PermitAll
    fun anonymous(): String {
        return "OK"
    }
}
```

## Intercept URL Map
インターセプト URL マップを利用して、
エンドポイントでの認証と承認のアクセス設定を行える。
以下のように、エンドポイントは、pattern と、
HTTP メソッド（オプショナル）の組合せによって、判定する。
特定のリクエスト URI が、複数のインターセプト URL マップと一致する場合、
リクエストメソッドと一致する、http-method を指定した設定を使用する。
メソッドを指定せず、リクエスト URI に一致するマッピングが複数ある場合、
最初のマッピングを利用する。

認証設定は、一覧の上方が優先となる。
1. メソッドの @Secured 指定
1. クラスの @Secured 指定
1. インターセプト URL マップ

パターンの判定は、以下のようになる。
- pattern: /auth/data  
  /auth/data のみが対象となる
- pattern: /auth/data/*  
  /auth/data/foo 、/auth/data/bar などが対象となる。  
  しかし、/auth/data や、/auth/data/foo/bar などは対象外となる。
- pattern: /auth/data/**  
  /auth/data 、/auth/data/foo 、/auth/data/foo/bar 、/auth/data/foo/bar/hoge などが対象となる。

```yaml
micronaut:
    security:
        enabled: true
        intercept-url-map:
            - pattern: /images/*
              http-method: GET
              access:
                  - isAnonymous()
            - pattern: /books
              access:
                  - isAuthenticated()
            - pattern: /books/grails
              http-method: POST
              access:
                  - ROLE_GRAILS
                  - ROLE_GROOVY
            - pattern: /books/grails
              http-method: PUT
              access:
                  - ROLE_ADMIN
```

以下の例では、「pattern: /v1/myResource/**」に一致し、
HTTP メソッド の GET を使用する URI 全ての HTTP リクエストでは
誰でもアクセスできるように定義している。
同じ URI パターンだが、GET と異なる HTTP メソッドのリクエストには、
認証が必要となる。

```yaml
micronaut:
    security:
        enabled: true
        intercept-url-map:
            - pattern: /v1/myResource/**
              httpMethod: GET
              access:
                  - isAnonymous()
            - pattern: /v1/myResource/**
              access:
                  - isAuthenticated()
```

## ビルトイン・エンドポイントのセキュリティ
セキュリティを有効にすると、組み込みエンドポイントは、
設定した「sensitive」値に応じて、保護される。

```yaml
endpoints:
  beans:
    enabled: true
    # 保護される設定
    sensitive: true 
  info:
    enabled: true
    # 保護されない設定
    sensitive: false 
```

## Authentication Strategies

### [Basic Auth](https://micronaut-projects.github.io/micronaut-security/latest/guide/#basicAuth)
Micronaut は、基本的な HTTP 認証方式を定義する RFC7617 をサポートする。
これは、Base64 でエンコードした ユーザID とパスワードを資格情報として送信する。

以下割愛

### [Session Authentication](https://micronaut-projects.github.io/micronaut-security/latest/guide/#session)
Micronaut は、セッションベースの認証をサポートする。
セッションモジュールを有効にする必要がある。
詳細は、[HTTP セッション](06_22_http_sessions.md) を参照。

Session Authentication を有効にするためには、依存ライブラリの追加が必要となる。

```groovy
implementation "io.micronaut:micronaut-session"
implementation "io.micronaut:micronaut-security"
implementation "io.micronaut:micronaut-security-session"
```

詳細は、
[Session-Based Authentication Micronaut Guide](http://guides.micronaut.io/micronaut-security-session/guide/index.html)
を参照。

リクエスト時の認証済みチェックで、未認証の場合は、AuthorizationException がスローされる。
Session Authentication を有効にすると、DefaultAuthorizationExceptionHandler が登録され、
SessionSecurityfilterRejectionHandler の reject 関数の処理で、
unauthorized-target-url に設定した URL か、設定されていない場合、
"/" に レスポンスステータス「303 See Other」で、遷移する。

ただし、独自のエラーハンドラ（@Error および、ExceptionHandler）で、
AuthorizationException （親クラス・インターフェースを含む）を、
処理する場合は、DefaultAuthorizationExceptionHandler が呼び出されないため、
遷移先を独自のエラーハンドラで指定する必要がある。

@Error は、同じ例外を処理する ExceptionHandler インターフェース実装よりも優先される。

### JSON Web Token
作成中

#### Reading JWT Token

#### JWT Token Generation

#### JWT Token Validation

#### Claims Generation

#### Token Render

### LDAP Authentication
作成中

#### Configuration

#### Extending Default Behavior


## Rejection Handling
Micronaut では、未許可リクエストおよび、未認証リクエスト時のレスポンスをカスタマイズできる。
カスタマイズするには、デフォルト実装を置き換える（@Replaces）ように、
RejectionHandler の実装 Bean を登録する必要がある。

UnauthorizedRejectionUriProvider および、ForbiddenRejectionUriProvider の Bean が、
存在する場合、RejectionHandler に、
汎用のリダイレクトハンドラ（RedirectRejectionHandler）が登録される。

独自の RejectionHandler を登録する場合は、
デフォルトの Bean を置き換える（@Replaces）必要がある。

JWT ベースのセキュリティを使用している場合は、
HttpStatusCodeRejectionHandler を置き換える。

セッションベースのセキュリティを使用している場合は、
SessionSecurityfilterRejectionHandler を置き換える。

セッションベースのセキュリティを使用して
RedirectRejectionHandler を使用している場合は、
`micronaut.security.session.legacy-redirect-handler: false`
で SessionSecurityfilterRejectionHandler を無効にする必要があります。

## Token Propagation


## ビルトイン セキュリティ コントローラ

### [Login Controller](https://micronaut-projects.github.io/micronaut-security/latest/guide/#login)
Login Controller を有効にするには、以下の設定を行う。
Login Controller を有効にすると、ログイン処理を行う Controller が追加される。
ログイン処理のエンドポイントは、path で設定できる。
ログイン処理のエンドポイントは、POST メソッドで、
MediaType.APPLICATION_FORM_URLENCODED および、MediaType.APPLICATION_JSON 形式に対応している。
認証に利用するデータは、パラメータ名が username と password で送信する。
ログインパラメータが未指定や、空文字列の場合は、処理に入る前のバリデーション処理で、エラーレスポンスが返る。
主要なログイン処理は、AuthenticationProvider インターフェースを実装した Bean に委譲される。
ログイン判定処理後のレスポンス処理は、LoginHandler インターフェースを実装した Bean に委譲される。

```yaml
micronaut:
    security:
        enabled: true
        endpoints:
            login:
                enabled: true
                # オプショナル
                path: /login
```

### [Logout Controller](https://micronaut-projects.github.io/micronaut-security/latest/guide/#logout)
Logout Controller を有効にするには、以下の設定を行う。
主要なログアウト処理は、LogoutHandler インターフェースの実装 Bean に委譲される。

```yaml
micronaut:
    security:
        enabled: true
        endpoints:
            logout:
                enabled: true
                # オプショナル
                path: /logout
```

### Refresh Controller
デフォルトでは、発行されたアクセストークンは一定期間後に期限切れになり、
更新トークンとペアになります。
更新を容易にするために、構成プロパティを使用してOauthControllerを有効にすることができます。

### Keys Controller
JSON Web Key（JWK）は、暗号鍵を表すJSONオブジェクトです。
オブジェクトのメンバーは、その値を含むキーのプロパティを表します。
一方、JWKセットは、JWKのセットを表すJSONオブジェクトです。
JSONオブジェクトには、JWKの配列である「キー」メンバーが必要です。
KeysControllerを有効にして、JWKセットを返すエンドポイントを公開できます。

## Retrieve the authenticated user
多くの場合、認証されたユーザーを取得する必要があります。
コントローラのメソッドのパラメータとしてjava.security.Principalをバインドできます。

より詳細なレベルが必要な場合は、Authenticationをコントローラのメソッドのパラメータとしてバインドできます。

### User outside of a controller
コントローラの外部で現在認証されているユーザーにアクセスする必要がある場合は、
認証と承認に関連する便利なメソッドのセットを提供するSecurityService Beanを注入できます。

## Security Events
Micronautセキュリティクラスは、サブスクライブできるいくつかのApplicationEventsを生成します。

## OAuth 2.0

### Installation

### OpenID Connect

### Flows

#### Authorization Code

#### Password

### Endpoints

#### OpenID End Session

#### Introspection

#### Revocation

#### OpenID User Info

### Custom Clients










