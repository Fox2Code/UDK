# UDK
Universal Developement Kit to develeop with any version of Minecraft with any IDE

It use [Repacker](https://github.com/Fox2Code/Repacker) to get a repacked version of Minecraft

## Documentation

[Documentation](https://github.com/Fox2Code/UDK/tree/master/docs)

## Gradle

```Groovy
buildscript {
    repositories {
        maven {
            url 'http://62.4.29.69/maven'
        }
    }
    dependencies {
        classpath 'com.fox2code:udk:1.3.0'
    }
}

apply plugin: 'udk.vanilla'

udk {
    version = "1.15.2"
}
```

`udk.version` use same patterns as [Repacker](https://github.com/Fox2Code/Repacker)

**WIP** Wait some time to get more documentation
