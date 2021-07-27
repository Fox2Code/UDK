# UDK
Universal Development Kit to develops with any version of Minecraft with any IDE

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
        classpath 'com.fox2code:udk:1.3.6'
    }
}

apply plugin: 'udk.vanilla'

udk {
    version = "1.17.1"
}
```

### Disclaimer the version `1.4.0` need Gradle 7.1 or higher if you use a version lower than `1.4.0` you can use Gradle `7.0` or lower

### You need `java-16` for 1.17+, if you are using an older minecraft version and want to use an older JDK you will need to add these lines
```Groovy
compileJava {
    sourceCompatibility = targetCompatibility = 1.8
}
```

`udk.version` use same patterns as [Repacker](https://github.com/Fox2Code/Repacker)

**WIP** Wait some time to get more documentation
