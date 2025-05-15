package org.example.project

expect object MQTTClient {
    var onMessageReceived: (String) -> Unit
    fun connect()
    fun disconnect()
    fun publish(message: String)
}
