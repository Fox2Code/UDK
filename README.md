# UDK
Universal Developement Kit to develeop with any version of Minecraft with any IDE

It use [Repacker](https://github.com/Fox2Code/Repacker) to get a repacked version of Minecraft

## Gradle

```Groovy
buildscript {
    repositories {
        maven {
            url 'https://jitpack.io'
        }
    }
    dependencies {
        classpath('com.github.Fox2Code:UDK:1.0.1') {
            transitive = false
        }
    }
}

apply plugin: 'udk.vanilla'

udk {
    version = "1.15"
}
```

`udk.version` use same patterns as [Repacker](https://github.com/Fox2Code/Repacker)

**WIP** Wait some time to get more documentation
