<!-- toc -->
- Validation Advice
  - 依存ライブラリ
  - 制限事項
    - 基本機能の制限事項
    - 全般の制限事項
  - 標準バリデーション・アノテーション
  - 多言語対応

# Validation Advice
Validation Advice は、付与することでバリデーション機能が有効となる。
Validation Advice は、JSR 380 規格で作成されている。
JSR 380 は、Bean バリデーション用の Java API 仕様であり、
@NotNull、@Min や、@Max などを利用し、プロパティをバリデーションできる。

[バリデータのサンプルコード](../../src/main/kotlin/micronaut/kotlin/coroutine/sample/CustomValidatorController.kt)

## 依存ライブラリ
以下の依存ライブラリ追加が必要。

このライブラリは、基本機能のみ実装している。
```groovy
implementation "io.micronaut:micronaut-validation"
```

JSR 380 完全仕様で利用する場合、以下のライブラリも追加する必要がある。
kapt の方には追加不要
```groovy
implementation("io.micronaut.configuration:micronaut-hibernate-validator")
```

## 制限事項

### 基本機能の制限事項
基本機能のみの場合、一部制限がある。現時点で遭遇した制限が以下となる。

- グループバリデーションで、グループ用インターフェース継承でのグルーピングができない。
- コレクションのバリデーションができない。（List<@field:NotBlank String> など）
- MessageInterpolator を利用できない。
- Controller での入力値に対するバリデーションができない。

### 全般の制限事項
- メソッドの引数にバリデーションを設定した場合は、
  DI でインスタンスを取得しないと機能しない。
- フィールドにバリデーションを設定した場合は、
  都度バリデーションではなく、一括してバリデーションを行う。

## 標準バリデーション・アノテーション
標準のアノテーションは、以下のようなものがある。

| アノテーション | バリデーション内容 |
| --- | --- |
| AssertFalse | false であること |
| AssertTrue | true であること |
| DecimalMax | 数値かつ、指定最大値より小さいか等しいこと |
| DecimalMin | 数値かつ、指定最小値より大きいか等しいこと |
| Digits | 数値かつ、指定桁数の範囲内であること |
| Future | 未来の日付であること |
| Max | 数値が、指定値より大きいか等しいこと |
| Min | 数値が、指定値より小さいか等しいこと |
| NotNull | null でないこと |
| Null | null であること |
| Past | 過去の日付であること |
| Pattern | 指定した正規表現と一致すること |
| Size | 文字列やコレクションの要素数が、指定の最小値以上かつ、指定の最大値以下であること |

## 多言語対応
ロケールを任意に変更したい場合は、MessageInterpolator を実装する必要がある。
メッセージは、ResourceBundle から取得する。
つまり多言語対応可能となる。
各バリデータに指定できる、メッセージテンプレートは、
ResourceBundle のキーを入れ子に設定できる。
