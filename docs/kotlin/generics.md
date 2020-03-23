# 総称型(Generics)
総称型を大雑把に説明する。

参考 [Javaジェネリクス：共変、反変、非変（これ以上簡単にはならない）](https://www.gwtcenter.com/covariance-and-contravariance)

## 総称型の定義方法
総称型の定義は、```List<E>``` のような形式で定義する。

定義の各名称は、以下となる。

| ```List``` | ```<E>``` |
| :---: | :---: |
| 原型<br />(raw type) | 型パラメータ<br />(type parameter) |

### 原型(row type)
原型は、通常のクラス名などを指定する。

### 型パラメータ(type parameter)
型パラメータには、以下のような種類がある。

| 型パラメータ名 | 例（Kotlin） | 例（Java） | 説明 |
| ---- | ---- | ---- | ---- |
| 仮型<br />(formal type) | E | E | 処理で変数として利用できる。<br />ただし、元の型情報は失われているため、new などはできない。 |
| 実型<br />(actual type) | String | String | 具体的な型。<br />ただし、プリミティブ型（int など）は利用できない。 |
| 境界型<br />(bounded type) | out Number | E extends Number | 制限された型となる。 |
| 非境界ワイルドカード型<br />(unbounded wildcard type) | * | ? | 任意の型を定義可能。 |
| 境界ワイルドカード型<br />(bounded wildcard type) | out T: Number | ? extends Number | 共変性の型となる。詳細は、後述する。 |
|  | in T: Number | ? super Number | 反変性の型となる。詳細は、後述する。 |

>Kotlin での非境界ワイルドカード型の補足  
>```List<Any?>``` と ```List<*>``` は、明確に異なる。  
>```List<Any?>``` は、実型の定義であり、格納する要素の型が不定なリスト型（Any?）の定義となる。  
>```List<*>``` は、非境界ワイルドカード型の定義であり、任意のリスト型の定義となる。

```kotlin
fun addAny(list: MutableList<Any?>) {
    // これは、OK
    list.add("Hello")
}

fun addSomething(list: MutableList<*>) {
    // これは、コンパイルエラーとなる
    list.add("Hello")
}

val listAny: MutableList<Any?> = mutableListOf(1, "Hello", 0.001)
// 代入が可能
val listSomething: MutableList<*> = listAny

// いずれの場合も取得は可能
val valueSomething: Any? = listSomething[0]
val valueAny: Any? = listAny[0]

// Any? 指定の場合は、設定できる
listAny[0] = 1
// * 指定の場合は、設定できない。コンパイルエラーとなる。
listSomething[0] = 1
```

## 変性
総称型における変性とは、型同士の関連性を表す性質となる。実際の継承関係と同義ではない。
主な変性には、非変（または不変）、共変および、反変がある。

変性を説明するためのクラス定義を以下に示す。
Material クラスは、すべてのクラスのスーパータイプとなる。
Animal クラスは、Material クラスのサブタイプであり、Dog および、Cat クラスのスーパータイプとなる。
Dog および、Cat クラスは、Animal クラスのサブタイプとなる。

```kotlin
open class Material
open class Animal : Material()
class Dog : Animal()
class Cat : Animal()
```

### 非変（invariant）
非変とは、型同士の関連性が無いという性質を持つ。
```List<Object>``` と、```List<String>``` は全く関連性がないなど、クラスの継承関係は考慮されない。
境界ワイルドカード型以外は、すべて非変となる。

以下のコードでは、animalList は、非変であるため、スーパータイプや、サブタイプを渡せない。
関数内では、Animal 型のみが保証されるので、読み込み、書き込みが許される。

```kotlin
fun processAnimalList(animalList: MutableList<Animal>) {
    animalList.clear() // OK

    animalList.add(Any()) // ERROR
    animalList.add(Material()) // ERROR
    animalList.add(Animal())  // OK
    animalList.add(Dog())  // OK
    animalList.add(Cat())  // OK

    val item: Any? = animalList[0]  // OK
    val item2: Animal = animalList[0]  // OK
}

processAnimalList(mutableListOf<Any>())  // ERROR
processAnimalList(mutableListOf<Material>())  // ERROR
processAnimalList(mutableListOf<Animal>())  // OK
processAnimalList(mutableListOf<Dog>())  // ERROR
processAnimalList(mutableListOf<Cat>())  // ERROR
```

### 共変（covariant）
共変とは、ワイルドカード型が、指定した型または、そのサブタイプ（extends）となり、
読み込みは許されるが、書き込みは許されないという性質を持つ。
MutableList の場合は、get() や、clear() は呼び出せるが、add() や、set() を呼び出せない。

以下のコードでは、animalList は共変であるため、スーパータイプ（Any や Material）は渡せないが、サブタイプを渡せる。
関数内では、animalList は、Animal または、そのサブタイプ（Dog や、Cat）の可能性があるので、書き込みは許されない。
なぜなら、例えば、animalList が、Dog のリストだった場合、Animal を追加できないため。
animalList は、Animal 型か、そのサブタイプであることが保証されるので、読み込みは許される。

```kotlin
fun processAnimalListWithOut(animalList: MutableList<out Animal>) {
    animalList.clear() // OK

    animalList.add(Any()) // ERROR
    animalList.add(Material()) // ERROR
    animalList.add(Animal())  // ERROR
    animalList.add(Dog())  // ERROR
    animalList.add(Cat())  // ERROR

    val item: Any? = animalList[0]  // OK
    val item2: Animal = animalList[0]  // OK
}

processAnimalListWithOut(mutableListOf<Any>())  // ERROR
processAnimalListWithOut(mutableListOf<Material>())  // ERROR
processAnimalListWithOut(mutableListOf<Animal>())  // OK
processAnimalListWithOut(mutableListOf<Dog>())  // OK
processAnimalListWithOut(mutableListOf<Cat>())  // OK
```

仮型を利用する場合。

```kotlin
fun <T : Animal> processAnimalListWithTOut(animalList: MutableList<out T>) {
    animalList.clear() // OK

    animalList.add(Any()) // ERROR
    animalList.add(Material()) // ERROR
    animalList.add(Animal())  // ERROR
    animalList.add(Dog())  // ERROR
    animalList.add(Cat())  // ERROR

    val item: Any? = animalList[0]  // OK
    val item2: Animal = animalList[0]  // OK
}

processAnimalListWithTOut<Animal>(mutableListOf<Any>())  // ERROR
processAnimalListWithTOut<Animal>(mutableListOf<Material>())  // ERROR
processAnimalListWithTOut<Animal>(mutableListOf<Animal>())  // OK
processAnimalListWithTOut<Animal>(mutableListOf<Dog>())  // OK
processAnimalListWithTOut<Animal>(mutableListOf<Cat>())  // OK
```

### 反変（contravariant）
反変とは、ワイルドカード型が、指定した型または、そのスーパータイプ（super）となり、
書き込みは許されるが、読み込みは許されないという性質を持つ。
MutableList の場合は、add() や、set() は呼び出せるが、get() を呼び出せない。

以下のコードでは、animalList は反変であるため、サブタイプは渡せないが、スーパータイプ（Any や Material）を渡せる。
関数内では、animalList は、Animal または、そのスーパータイプ（Any など）の可能性があるので、読み込みは許されない。
なぜなら、たとえば、animalList が、Any のリストだった場合、Animal 型として読み込みができないため。
animalList は、Animal 型または、そのスーパータイプであることが保証されるので、書き込みは許される。
ただし、Any および、Material 型の書き込みは許されない。
なぜなら、たとえば、animalList が、Animal 型リストだった場合、Any や、Material 型を書き込みできないため。

```kotlin
fun processAnimalListWithIn(animalList: MutableList<in Animal>) {
    animalList.clear() // OK

    animalList.add(Any()) // ERROR
    animalList.add(Material()) // ERROR
    animalList.add(Animal())  // OK
    animalList.add(Dog())  // OK
    animalList.add(Cat())  // OK
    
    val item: Any? = animalList[0]  // OK
    val item2: Animal = animalList[0]  // ERROR
}

processAnimalListWithIn(mutableListOf<Any>())  // OK
processAnimalListWithIn(mutableListOf<Material>())  // OK
processAnimalListWithIn(mutableListOf<Animal>())  // OK
processAnimalListWithIn(mutableListOf<Dog>())  // ERROR
processAnimalListWithIn(mutableListOf<Cat>())  // ERROR
```

仮型を利用する場合。
仮型を利用する場合は、型の情報が失われるため、書き込みが一切許されない。

```kotlin
fun <T : Animal> processAnimalListWithTIn(animalList: MutableList<in T>) {
    animalList.clear() // OK

    animalList.add(Any()) // ERROR
    animalList.add(Material()) // ERROR
    animalList.add(Animal())  // ERROR
    animalList.add(Dog())  // ERROR
    animalList.add(Cat())  // ERROR

    val item: Any? = animalList[0]  // OK
    val item2: Animal = animalList[0]  // ERROR
}

processAnimalListWithTIn<Animal>(mutableListOf<Any>())  // OK
processAnimalListWithTIn<Animal>(mutableListOf<Material>())  // OK
processAnimalListWithTIn<Animal>(mutableListOf<Animal>())  // OK
processAnimalListWithTIn<Animal>(mutableListOf<Dog>())  // ERROR
processAnimalListWithTIn<Animal>(mutableListOf<Cat>())  // ERROR
```

## 実際の利用コード
総称型の実際の利用方法のサンプルコードを以下に示す。
出典 [Java ジェネリクスのポイント](https://qiita.com/pebblip/items/1206f866980f2ff91e77)

PUT/GET の法則に従い、プロデューサ（データ提供側）は共変、
コンシューマ（データ消費側）は、反変とする。

ポイントは、共変および、反変を設定する条件と、
設定したことにより、呼び出し側での、継承関係のあるクラスを、
直感に即した方法で、引数として利用できるようになるということ。


```kotlin
interface Consumer<T> {
    fun apply(value: T)
}

interface Box<T> {
    fun get(): T
    fun put(element: T)
    // 共変で定義するプロデューサ関数
    fun put(box: Box<out T>)
    // 反変で定義するコンシューマ関数
    fun applyConsumer(function: Consumer<in T>)
}

class InvariantSample {
    fun foo(numBox: Box<Number>, intBox: Box<Int>) {
        numBox.put(1)
        // 共変にしないと、この行でエラーとなる
        numBox.put(intBox)
    }

    fun foo(intBox: Box<Int>, intConsumer: Consumer<Int>, numConsumer: Consumer<Number>) {
        intBox.applyConsumer(intConsumer)
        // 反変にしないと、この行でエラーとなる
        intBox.applyConsumer(numConsumer)
    }
}

// Box の実装例
class BoxImpl<T>(private var value: T): Box<T> {
    override fun get(): T {
        return value
    }

    override fun put(element: T) {
        value = element
    }

    override fun put(box: Box<out T>) {
        value = box.get()
    }

    override fun applyConsumer(function: Consumer<in T>) {
        function.apply(value)
    }
}
```
