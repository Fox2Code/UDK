# UDK
Universal Development Kit to develops with any version of Minecraft with any IDE

It use [Repacker](https://github.com/Fox2Code/Repacker) to get a repacked version of Minecraft

## Documentation

[Documentation](https://github.com/Fox2Code/UDK/tree/master/docs)

## Gradle
### Disclaimer the version `1.3.6` need Gradle 7.1 with `java-16` or higher if you use a version lower than `1.3.6` you can use Gradle `7.0` or lower

```Groovy
buildscript {
    repositories {
        maven {
            url 'http://62.4.29.69/maven'
        }
    }
    dependencies {
        classpath 'com.fox2code:udk:1.3.6'
    }
}

apply plugin: 'udk.vanilla'

udk {
    version = "1.17.1"
}
```

`udk.version` use same patterns as [Repacker](https://github.com/Fox2Code/Repacker)

**WIP** Wait some time to get more documentation
