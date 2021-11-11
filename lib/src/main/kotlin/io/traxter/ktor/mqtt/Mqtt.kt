package io.traxter.ktor.mqtt

import io.ktor.application.Application
import io.ktor.application.ApplicationFeature
import io.ktor.application.ApplicationStopPreparing
import io.ktor.application.feature
import io.ktor.application.log
import io.ktor.routing.Route
import io.ktor.routing.application
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.ContextDsl
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.eclipse.paho.mqttv5.client.IMqttToken
import org.eclipse.paho.mqttv5.client.MqttActionListener
import org.eclipse.paho.mqttv5.client.MqttCallback
import org.eclipse.paho.mqttv5.client.MqttClientPersistence
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence
import org.eclipse.paho.mqttv5.common.MqttException
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resumeWithException
import org.eclipse.paho.mqttv5.client.MqttAsyncClient as PahoMqttClient

typealias MessageObserver = suspend TopicScope.(message: MqttMessage) -> Unit

class MqttClient(private val config: Configuration, private val logger: Logger) : CoroutineScope {
    private val client = PahoMqttClient(config.broker, config.clientId)
    private val parent: CompletableJob = Job()
    override val coroutineContext: CoroutineContext
        get() = parent

    class Configuration {
        var connectionOptions = MqttConnectionOptions()
        var persistence: MqttClientPersistence = MemoryPersistence()
        var broker: String = "tcp://localhost:1883"
        var clientId = "ktor_mqtt_client"
        var autoConnect: Boolean = false

        fun connectionOptions(configure: MqttConnectionOptions.() -> Unit) {
            connectionOptions.apply(configure)
        }
    }

    private val messageObserver = ConcurrentHashMap<String, MessageObserver>()

    fun connect(vararg topics: Pair<Int, String>) = with(client) {
        setCallback(
            object : MqttCallback {
                override fun connectComplete(reconnect: Boolean, serverURI: String) =
                    logger.info("connected to broker: $serverURI").also {
                        topics.forEach { topic ->
                            client.subscribe(topic.second, topic.first).actionCallback = object : MqttActionListener {
                                override fun onSuccess(asyncActionToken: IMqttToken) =
                                    logger.info("successfully subscribed to topic: [ ${topic.second} ] with qos: [ ${topic.first} ]")

                                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) =
                                    logger.error("could not subscribe to topic: [ ${topic.second} ] due to: [ ${exception.message} ]")
                            }
                        }
                    }

                override fun authPacketArrived(reasonCode: Int, properties: MqttProperties) {
                    logger.debug("Auth reason code: [ $reasonCode ]  with reason: [ ${properties.reasonString} ]")
                }

                override fun disconnected(disconnectResponse: MqttDisconnectResponse) {
                    logger.warn("disconnected from broker due to: [ ${disconnectResponse.reasonString} ]")
                }

                override fun mqttErrorOccurred(exception: MqttException) {
                    logger.error("an error occurred: [ ${exception.message} ]")
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    messageObserver[topic]?.let { launch { it.invoke(TopicScope(topic, this@MqttClient), message) } }
                    logger.debug("received ${message.toDebugString()} from topic [ $topic ]")
                }

                override fun deliveryComplete(token: IMqttToken) {
                    logger.debug("delivered message ${String(token.message.payload)} ")
                }
            }
        )
        connect(config.connectionOptions).waitForCompletion()
    }

    suspend fun unsubscribe(topic: String) =
        client.unsubscribe(topic).awaitToken()

    suspend fun sendMessage(topic: String, msg: String) =
        with(client) {
            val message = MqttMessage()
            message.payload = msg.toByteArray()
            client.publish(topic, message.payload, 0, true).awaitToken()
        }

    suspend fun subscribeTopic(topic: String, qos: Int = 0) =
        messageObserver[topic] ?: client.subscribe(topic, qos)
            .awaitToken()

    fun addTopicListener(topic: String, observer: MessageObserver) =
        messageObserver.put(topic, observer)

    fun close() = client.close()

    private fun shutdown() {
        logger.info("shutting down MqttClient")
        messageObserver.clear()
        close()
        parent.complete()
        client.disconnectForcibly()
    }

    companion object Feature : ApplicationFeature<Application, Configuration, MqttClient> {
        override val key: AttributeKey<MqttClient> = AttributeKey("MqttClient")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): MqttClient {
            val config = Configuration().apply(configure)
            val logger = pipeline.log
            val client = MqttClient(config, logger)

            if (config.autoConnect) client.connect()

            pipeline.environment.monitor.subscribe(ApplicationStopPreparing) { client.shutdown() }

            return client
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun IMqttToken.awaitToken(): IMqttToken =
    suspendCancellableCoroutine { cont ->
        actionCallback = object : MqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken) {
                cont.resume(asyncActionToken, null)
            }

            override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                cont.resumeWithException(exception)
            }
        }
    }

class TopicScope(val topic: String, private val mqttClient: MqttClient) {
    suspend fun unsubscribe() = mqttClient.unsubscribe(topic)
    suspend fun sendMessage(topic: String, msg: String) = mqttClient.sendMessage(topic, msg)
}

@ContextDsl
fun Route.topic(topic: String, observer: MessageObserver): Job {
    val client = application.feature(MqttClient)
    return client.launch {
        client.subscribeTopic(topic)
        client.addTopicListener(topic, observer)
    }
}
