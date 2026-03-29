package org.example.project

import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

actual object MQTTClient {
    private val persistence = MemoryPersistence()
    private var mqttClient: MqttClient? = null

    // kirim topic + payload ke UI
    actual var onMessageReceived: (String, String) -> Unit = { _, _ -> }

    actual fun connect() {
        try {
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
                    val received = message?.toString() ?: ""
                    println("Message received [$t]: $received")
                    onMessageReceived(t, received)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                }
            })

            println("Connecting to broker: ${MQTTConfig.broker}")
            mqttClient?.connect(connOpts)

            // subscribe hanya ke topic output dari ESP32
            mqttClient?.subscribe(MQTTConfig.topicData)
            mqttClient?.subscribe(MQTTConfig.topicStatus)

            println(
                "Connected and subscribed to ${MQTTConfig.topicData} and ${MQTTConfig.topicStatus}"
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
                MqttMessage(message.toByteArray())
            )
            println("Message published to ${MQTTConfig.topicCommand}: $message")
        } catch (e: MqttException) {
            println("Error Publishing: ${e.message}")
        }
    }
}