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

    var onConnectionStateChanged: (Boolean, String?) -> Unit = { _, _ -> }

    fun isConnected(): Boolean = mqttClient?.isConnected == true

    actual fun connect() {
        try {
            if (mqttClient?.isConnected == true) {
                onConnectionStateChanged(true, null)
                return
            }

            mqttClient = MqttClient(MQTTConfig.broker, MQTTConfig.clientId, persistence)
            val connOpts = MqttConnectOptions().apply {
                isCleanSession = true
                userName = MQTTConfig.username
                password = MQTTConfig.password.toCharArray()
                connectionTimeout = 10
                keepAliveInterval = 20
                isAutomaticReconnect = false
            }

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    println("Connection lost: ${cause?.message}")
                    onConnectionStateChanged(false, cause?.message ?: "connection lost")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val t = topic ?: ""
                    val payload = message?.payload ?: ByteArray(0)
                    onMessageReceived(t, payload)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
            })

            mqttClient?.connect(connOpts)
            mqttClient?.subscribe(MQTTConfig.topicCommand)
            mqttClient?.subscribe(MQTTConfig.topicData)
            mqttClient?.subscribe(MQTTConfig.topicStatus)

            onConnectionStateChanged(true, null)
            println("Connected and subscribed.")
        } catch (e: MqttException) {
            println("Error Connecting: ${e.message}")
            onConnectionStateChanged(false, e.message)
        }
    }

    actual fun disconnect() {
        try {
            mqttClient?.disconnect()
            onConnectionStateChanged(false, null)
            println("Disconnected")
        } catch (e: MqttException) {
            println("Error Disconnecting: ${e.message}")
            onConnectionStateChanged(false, e.message)
        }
    }

    actual fun publish(message: String) {
        try {
            val client = mqttClient
            require(client != null && client.isConnected) { "MQTT is not connected" }

            client.publish(
                MQTTConfig.topicCommand,
                MqttMessage(message.toByteArray(Charsets.UTF_8))
            )
        } catch (e: Exception) {
            println("Error Publishing: ${e.message}")
            onConnectionStateChanged(false, e.message)
        }
    }
}