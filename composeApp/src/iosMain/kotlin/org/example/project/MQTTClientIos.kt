package org.example.project

actual object MQTTClient {
    actual fun connect() {
        println("MQTT not implemented for iOS.")
    }

    actual fun disconnect() {
        println("MQTT not implemented for iOS.")
    }

    actual fun publish(message: String) {
        println("MQTT not implemented for iOS. Tried to publish: $message")
    }
}
