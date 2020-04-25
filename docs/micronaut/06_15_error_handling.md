<!-- toc -->
- Error Handling

# Error Handling
Micronaut では、エラーのハンドリング処理を個別に定義できる。  
エラーハンドラは、未処理例外および、HTTP レスポンスステータスコードに対して定義できる。  
エラーハンドラの適用範囲は、グローバルおよび、ローカルを選択できる。  
エラーハンドラは、グローバル、ローカル問わず、Controller 内に定義する必要がある。  
グローバル定義は、すべての Controller に適用される。
ローカル定義は、定義した Controller のみに適用される。
ローカル定義は、グローバル定義より優先される。
適用範囲が同じで、条件が同じハンドラは、一つのみ定義できる。

[サンプルコード](../../src/main/kotlin/micronaut/kotlin/coroutine/sample/GlobalErrorHandlerController.kt)
