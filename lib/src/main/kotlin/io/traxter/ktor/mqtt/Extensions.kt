package io.traxter.ktor.mqtt

import io.ktor.application.feature
import io.ktor.routing.Route
import io.ktor.routing.application
import io.ktor.routing.route
import io.ktor.util.pipeline.ContextDsl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.eclipse.paho.mqttv5.client.IMqttMessageListener
import org.eclipse.paho.mqttv5.client.IMqttToken
import org.eclipse.paho.mqttv5.client.MqttActionListener
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.MqttSubscription
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun IMqttToken.await(): IMqttToken =
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

class TopicContext(val topic: Topic, private val mqttClient: Mqtt) {
    suspend fun unsubscribe() = mqttClient.unsubscribe(topic.value).await()
    suspend fun sendMessage(topic: String, msg: String, qos: QualityOfService = AtMostOnce, retained: Boolean = true) =
        mqttClient.publish(topic, MqttMessage(msg.toByteArray(), qos.level, retained, MqttProperties())).await()
}

@ContextDsl
fun Route.topic(topic: String, qos: QualityOfService = AtMostOnce, listener: MessageListener): Job {
    val client = application.feature(Mqtt)
    val validTopic = Topic(topic)
    return client.launch {
        client.subscribe(validTopic.value, qos.level).await()
        client.addTopicListener(validTopic, listener)
    }
}

suspend fun Mqtt.publishMessageTo(
    topic: Topic,
    msg: String,
    qos: QualityOfService,
    retained: Boolean
) {
    val message = MqttMessage()
    message.payload = msg.toByteArray()
    publish(topic.value, message.payload, qos.level, retained).await()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Mqtt.subscribeTo(topic: Topic, qos: QualityOfService, listener: IMqttMessageListener) {
    subscribe(MqttSubscription(topic.value, qos.level), listener).await()
}

suspend fun Mqtt.unsubscribeFrom(topic: Topic): IMqttToken = unsubscribe(topic.value).await()
