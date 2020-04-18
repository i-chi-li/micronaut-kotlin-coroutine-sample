package micronaut.kotlin.coroutine.sample

import io.micronaut.runtime.Micronaut

object Application {

    @JvmStatic
    fun main(args: Array<String>) {
        Micronaut.build()
            .packages("micronaut.kotlin.coroutine.sample")
            .mainClass(Application.javaClass)
            .start()
    }
}
