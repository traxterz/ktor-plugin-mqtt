![LINE](https://img.shields.io/badge/line--coverage-11%25-red.svg)
[![](https://jitpack.io/v/traxterz/XXX.svg)](https://jitpack.io/#dennisschroeder/khome)

# KTOR - MQTT Plugin

## Introduction

This plugin provides a full MQTT client solution for KTOR servers. It is based on Paho Java by Eclipse.

## Documentation

**Compatibility**
- The v1.1.0 works with KTOR 1.6.5
- The v2.0.0 works with KTOR 2.1.3

**Methods available :**

- Every Paho method

Provided by the lib, available everywhere under `Mqtt.client.`

```kotlin
Mqtt.client.publishMessageTo(topic: Topic, message: String, qos: QualityOfService, retained: Boolean)
Mqtt.client.unsubscribeFrom(topic: Topic)
Mqtt.client.connectToBroker()
Mqtt.client.shutdown()
```

**Listen to topics**

> :warning: You need the Routing plugin from ktor

```kotlin
routing {
    topic(topic: Topic, QualityOfService) {
        // some stuff, MQTT message is available within the "it" variable
    }
}
```

## Examples

> Installation example that auto-connect to the broker on localhost and subscribes to the "microbit" topic

/plugins/Mqtt.kt
```kotlin
package com.example.plugins

import com.example.utils.mqtt.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureMqtt() {
    // Example topic
    val microbit: Topic = Topic("microbit")
    val microbitTopicSubscription: TopicSubscription = TopicSubscription(microbit, AtMostOnce)

    // Installs the plugin to the server so that you can use it, won't work otherwise
    install(Mqtt) {
        broker = "tcp://localhost:1883"
        autoConnect = true
        initialSubscriptions(microbitTopicSubscription)
    }

    // Allows to map function to different topics
    routing {
        topic("microbit", AtMostOnce) {
            val message = it.toString()
            println(message)
        }
    }
}
```
/Application.kt

```kotlin
package com.example

import com.example.plugins.configureHTTP
import com.example.plugins.configureRouting
import com.example.plugins.configureSecurity
import com.example.plugins.configureSerialization
import com.example.plugins.configureMqtt
import io.ktor.server.application.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    configureSecurity()
    configureHTTP()
    configureSerialization()
    configureMqtt()
    configureRouting()
}
```

> Publish message example
> 
```kotlin
package com.example.test

import io.traxterz.ktor.mqtt.AtMostOnce
import io.traxter.ktor.mqtt.Mqtt
import io.traxter.ktor.mqtt.Topic

class ExampleClass {
    suspend fun sendMessage() {
        val microbit = Topic("microbit")
        Mqtt.client.publishMessageTo(microbit, "TEST", AtMostOnce, false)
    }
}
```

## Contributors

- [@dennisschroeder](https://github.com/dennisschroeder) Base plugin
- [@julien-cpsn](https://github.com/Julien-cpsn) Migration to Ktor version 2.X