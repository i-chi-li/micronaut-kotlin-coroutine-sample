# Validation Advice
Validation Advice は、付与することでバリデーション機能が有効となる。
Validation Advice は、JSR 380 規格で作成されている。
JSR 380 は、Bean バリデーション用の Java API 仕様であり、
@NotNull、@Min や、@Max などを利用し、プロパティをバリデーションできる。

以下の依存ライブラリ追加が必要。

こちらは、軽量版
```groovy
implementation "io.micronaut:micronaut-validation"
```

JSR 380 完全仕様で利用する場合、以下のライブラリも追加する必要がある。
kapt の方には追加不要
```groovy
implementation("io.micronaut.configuration:micronaut-hibernate-validator")
```

軽量版の場合、一部制限がありそうだ。現時点で遭遇した制限が以下となる。

- グループバリデーションで、グループ用インターフェース継承でのグルーピングができない。
- コレクションのバリデーションができない。（List<@field:NotBlank String> など）
- MessageInterpolator を利用できない。

標準のアノテーションは、以下のようなものがある。

| アノテーション | バリデーション内容 |
| --- | --- |
| AssertFalse | false であること |
| AssertTrue | true であること |
| DecimalMax | 数値かつ、指定最大値より小さいか等しいこと |
| DecimalMin | 数値かつ、指定最小値より大きいか等しいこと |
| Digits | 数値かつ、指定桁数の範囲内であること |
| Future | 日付が将来であること |
| Max |  |
| Min | The annotated element must be a number whose value must be higher or equal to the specified minimum. |
| NotNull | The annotated element must not be null. |
| Null | The annotated element must be null. |
| Past | The annotated element must be a date in the past. |
| Pattern | The annotated CharSequence must match the specified regular expression. |
| Size | The annotated element size must be between the specified boundaries (included). |

ロケールを任意に変更したい場合は、MessageInterpolator を実装する必要がある。
メッセージは、ResourceBundle から取得する。
つまり多言語対応可能となる。
各バリデータに指定できる、メッセージテンプレートは、
ResourceBundle のキーを入れ子に設定できる。

メソッドの引数にバリデーションを設定した場合は、
DI でインスタンスを取得しないと機能しない。

フィールドにバリデーションを設定した場合は、
都度バリデーションではなく、一括してバリデーションを行う。

現時点では、バリデーションメッセージに、
フィールド名を多言語化して表示する良い方法を思いつかない。

