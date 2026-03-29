package org.example.project

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

actual object MQTTClient {
    private val persistence = MemoryPersistence()
    private var mqttClient: MqttClient? = null

    actual var onMessageReceived: (String, ByteArray) -> Unit = { _, _ -> }

    actual fun connect() {
        try {
            if (mqttClient?.isConnected == true) return

            mqttClient = MqttClient(MQTTConfig.broker, MQTTConfig.clientId, persistence)
            val connOpts = MqttConnectOptions().apply {
                isCleanSession = true
                userName = MQTTConfig.username
                password = MQTTConfig.password.toCharArray()
            }

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    println("Connection lost: ${cause?.message}")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val t = topic ?: ""
                    val payload = message?.payload ?: ByteArray(0)
                    println("Message received [$t]: ${payload.size} bytes")
                    onMessageReceived(t, payload)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                }
            })

            println("Connecting to broker: ${MQTTConfig.broker}")
            mqttClient?.connect(connOpts)

            mqttClient?.subscribe(MQTTConfig.topicCommand)
            mqttClient?.subscribe(MQTTConfig.topicData)
            mqttClient?.subscribe(MQTTConfig.topicStatus)

            println(
                "Connected and subscribed to " +
                        "${MQTTConfig.topicCommand}, ${MQTTConfig.topicData}, ${MQTTConfig.topicStatus}"
            )
        } catch (e: MqttException) {
            println("Error Connecting: ${e.message}")
        }
    }

    actual fun disconnect() {
        try {
            mqttClient?.disconnect()
            println("Disconnected")
        } catch (e: MqttException) {
            println("Error Disconnecting: ${e.message}")
        }
    }

    actual fun publish(message: String) {
        try {
            mqttClient?.publish(
                MQTTConfig.topicCommand,
                MqttMessage(message.toByteArray(Charsets.UTF_8))
            )
            println("Message published to ${MQTTConfig.topicCommand}: $message")
        } catch (e: MqttException) {
            println("Error Publishing: ${e.message}")
        }
    }
}