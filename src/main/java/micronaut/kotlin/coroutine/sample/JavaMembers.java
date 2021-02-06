package micronaut.kotlin.coroutine.sample;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class JavaMembers {
    private int age = 0;

    private String hello(String name) {
        return "Hello " + name;
    }

    private String helloWithAge() {
        return hello("no name") + " " + age;
    }

    private String helloWithAge(String name) {
        return hello(name) + " " + age;
    }
}
