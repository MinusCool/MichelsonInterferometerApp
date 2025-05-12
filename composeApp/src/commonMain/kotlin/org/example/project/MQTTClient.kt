package org.example.project

expect object MQTTClient {
    fun connect()
    fun disconnect()
    fun publish(message: String)
}
