# Bean Introspection
Bean Introspection とは、リフレクションを利用せず、
Bean をインスタンス化したり、プロパティを取得したりするための機能となる。

Bean Introspection を有効にするには、以下の依存ライブラリを追加する必要がある。

```
annotationProcessor("io.micronaut:micronaut-inject-java:1.3.x")
runtime("io.micronaut:micronaut-core:1.3.x")
```

Bean Validation でカスタムバリデータを作成する場合は、有用な機能となる。

## @Introspected アノテーション
@Introspected アノテーションは、Bean Introspection を
有効にしたいクラスに付与する。
Bean Introspection が有効になったクラスは、
BeanIntrospection API を介してアクセスできるようになる。
@Introspected アノテーションは、パッケージに付与することで、
パッケージ内のすべてのクラスで Bean Introspection を有効化できる。

```kotlin
import io.micronaut.core.annotation.Introspected

@Introspected
data class Person(var name : String) {
    var age : Int = 18
}
```

## BeanIntrospection API
BeanIntrospection API で、クラスのインスタンス生成と、
プロパティの設定を行うサンプルコード。


```kotlin
val introspection = BeanIntrospection.getIntrospection(Person::class.java)
val person : Person = introspection.instantiate("John")
print("Hello ${person.name}")

val property : BeanProperty<Person, String>
    = introspection.getRequiredProperty("name", String::class.java)
property.set(person, "Fred")
val name = property.get(person)
print("Hello ${person.name}")
```

## 複数コンストラクタ
複数のコンストラクタが定義されている場合、
BeanIntrospection API では、どのコンストラクタで、
インスタンスを生成するかを @Creator アノテーションで指定する必要がある。
@Creator アノテーションは、複数定義することが可能となる。
引数無しの場合は、デフォルトの構築方法となり、
引数有りの最初のメソッドは、主要な構築メソッドとして利用される。

```kotlin
import io.micronaut.core.annotation.Creator
import io.micronaut.core.annotation.Introspected

import javax.annotation.concurrent.Immutable

@Introspected
@Immutable
class Vehicle @Creator constructor(
        val make: String, val model: String, val axels: Int
) { 
    constructor(make: String, model: String) : this(make, model, 2) {}
}
```

## static 生成メソッド
static メソッドで、インスタンスを生成する場合は、コンストラクタと同様に、
@Creator アノテーションを付与する。

```kotlin
import io.micronaut.core.annotation.Creator
import io.micronaut.core.annotation.Introspected
import javax.annotation.concurrent.Immutable

@Introspected
@Immutable
class Business private constructor(val name: String) {
    companion object {
        @Creator 
        fun forName(name: String): Business {
            return Business(name)
        }
    }
}
```

## 列挙型（Enum 型）
列挙型も @Introspected アノテーションを付与できる。
それによって、標準の valueOf メソッドで生成が可能となる。

## 既存のクラスを Bean Introspection する方法
既存のクラスを Bean Introspection する場合は、
以下のように、別途クラスを定義できる。

```kotlin
import io.micronaut.core.annotation.Introspected

@Introspected(classes = [Person::class])
class PersonConfiguration
``` 

## 既存アノテーション付与クラスへの Bean Introspection 有効化
既存のアノテーションを付与したクラスへの Bean Introspection 有効化は、
AnnotationMapper を記載することで可能となる。

[名前ベースの設定コード例](https://github.com/micronaut-projects/micronaut-core/blob/master/inject/src/main/java/io/micronaut/inject/beans/visitor/EntityIntrospectedAnnotationMapper.java)

上記の実装クラスを、以下のように定義する。

```
src/main/resources/META-INF/services/io.micronaut.inject.annotation.AnnotationMapper

io.micronaut.inject.beans.visitor.EntityIntrospectedAnnotationMapper
```

## BeanWrapper API
BeanProperty は、特定クラスのプロパティをローレベルで読み書きする機能となる。
したがって、自動で型変換などは行わない。
set および、get メソッドに渡す値は、引数の型と一致する必要がある。
異なった場合は、例外が発生する。

BeanWrapper は、型の自動変換を提供する機能となる。

```kotlin
val wrapper = BeanWrapper.getWrapper(Person("Fred")) 

wrapper.setProperty("age", "20") 
val newAge = wrapper.getRequiredProperty("age", Int::class.java) 

println("Person's age now $newAge")
```

## Jackson と Bean Introspection
Jackson に Bean Introspection を統合するには、以下の設定を行う。
設定することにより、Jackson が Bean Introspection API を介して、
Bean の読み書きを行うようになる。
パフォーマンスや、GraalVM の利用にも有効に機能する。

```
jackson:
    bean-introspection-module: true
```
