package org.example.project

import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

actual object MQTTClient {
    private const val broker = "ssl://358587e8751a4fbf8655e5a2fb5be16c.s1.eu.hivemq.cloud:8883"
    private const val clientId = "InterferometerKMPClient"
    private const val username = "MinusCool"
    private const val password = "Interfero123"
    private const val topic = "motor/commands"

    private val persistence = MemoryPersistence()
    private var mqttClient: MqttClient? = null

    actual var onMessageReceived: (String) -> Unit = {}

    actual fun connect() {
        try {
            mqttClient = MqttClient(broker, clientId, persistence)
            val connOpts = MqttConnectOptions().apply {
                isCleanSession = true
                userName = username
                password = this@MQTTClient.password.toCharArray()
            }

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    println("Connection lost: ${cause?.message}")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val received = message?.toString() ?: ""
                    println("Message received: $received")
                    onMessageReceived(received)  // Kirim ke UI
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    // Tidak perlu diproses untuk sekadar menerima pesan
                }
            })

            println("Connecting to broker: $broker")
            mqttClient?.connect(connOpts)
            mqttClient?.subscribe(topic)  // Subscribe ke topik saat connect
            println("Connected and subscribed to $topic")

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
            mqttClient?.publish(topic, MqttMessage(message.toByteArray()))
            println("Message published: $message")
        } catch (e: MqttException) {
            println("Error Publishing: ${e.message}")
        }
    }
}
