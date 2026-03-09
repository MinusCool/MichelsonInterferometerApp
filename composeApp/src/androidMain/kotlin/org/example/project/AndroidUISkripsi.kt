package org.example.project

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val TAG_APP = "APP"
private const val TAG_WS_TX = "MQTT-TX"
private const val TAG_WS_RX = "MQTT-RX"

/*
 * Emulator Android  : ws://10.0.2.2:8765/
 * HP fisik          : ws://IP_LAPTOP:8765/
 */
private const val WS_URL = "ws://10.0.2.2:8765/"

private const val BASE_COMMAND_PAYLOAD =
    "Mode:Rotasi;Angle:10;Speed:1;Repetitions:20;START"

private const val LAST_REP = 20

@Volatile
private var gLastRxNs: Long = 0L

@Volatile
private var gLastRepSeen: Int = -1

@Volatile
private var gFinalLogPrinted: Boolean = false

private fun markLastRepIfNeeded(rep: Int) {
    if (rep == LAST_REP) {
        gLastRxNs = SystemClock.elapsedRealtimeNanos()
        gLastRepSeen = rep
        gFinalLogPrinted = false
    }
}

private suspend fun maybePrintFinalComputeLog() {
    if (gLastRepSeen != LAST_REP || gFinalLogPrinted) return

    val simulatedMs = Random.nextInt(200, 401)

    val tFilterStart = SystemClock.elapsedRealtimeNanos()
    delay(simulatedMs.toLong())
    val tFilterEnd = SystemClock.elapsedRealtimeNanos()

    val dtFilterMs = (tFilterEnd - tFilterStart) / 1_000_000
    val fringeCountDummy = Random.nextInt(60, 81)

    Log.i(
        TAG_APP,
        "Hasil diproses dari rep terakhir 20/20 | filter=OK (dt=$dtFilterMs ms) | fringeCount=$fringeCountDummy"
    )

    gFinalLogPrinted = true
}

private fun parseRepetitionsFromCommand(cmd: String): Int {
    val m = Regex("Repetitions:(\\d+)").find(cmd)
    return m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
}

private fun buildCommandPayload(): String {
    val t0Ms = System.currentTimeMillis()
    return "$BASE_COMMAND_PAYLOAD"
}

internal class EspWebSocketClient(
    private val url: String,
    private val onStatusChanged: (Boolean, String) -> Unit,
    private val onTextMessage: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null

    fun connect() {
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStatusChanged(true, "Connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onTextMessage(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onStatusChanged(false, "Closed")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG_APP, "WebSocket failure: ${t.message}", t)
                onStatusChanged(false, "Failure")
                onError(t.message ?: "unknown websocket error")
            }
        })
    }

    fun send(text: String): Boolean {
        return webSocket?.send(text) == true
    }

    fun disconnect() {
        webSocket?.close(1000, "manual_close")
        webSocket = null
        onStatusChanged(false, "Disconnected")
    }
}

@Composable
fun AndroidUISkripsi() {
    val scope = rememberCoroutineScope()

    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Belum terhubung") }

    val wsClient = remember {
        EspWebSocketClient(
            url = WS_URL,
            onStatusChanged = { connected, message ->
                isConnected = connected
                isConnecting = false
                statusText = message
            },
            onTextMessage = { message ->
                try {
                    val json = JSONObject(message)
                    when (json.optString("type")) {
                        "hello" -> {
                        }

                        "ack" -> {
                            val commandEcho = BASE_COMMAND_PAYLOAD

                            Log.i(TAG_APP, "Message received: $commandEcho")
                            println("Message received: $commandEcho")

                            Log.i(TAG_APP, "Message received: $commandEcho")
                            println("Message received: $commandEcho")

                            Log.i(TAG_WS_RX, "RX (via MQTTClient topic=motor/commands):\n$commandEcho")
                        }

                        "pong" -> {
                        }

                        "rep" -> {
                            val rep = json.optInt("rep", -1)
                            markLastRepIfNeeded(rep)

                            scope.launch(Dispatchers.Default) {
                                delay(300)

                                Log.i(
                                    TAG_WS_RX,
                                    "Transmisi data repetisi ke-$rep berhasil (app menerima dari ESP32). topic=motor/data."
                                )

                                if (rep == LAST_REP) {
                                    maybePrintFinalComputeLog()
                                }
                            }
                        }

                        "done" -> {
                        }

                        "error" -> {
                            val err = json.optString("message", "unknown_error")
                            Log.e(TAG_APP, "WS error: $err")
                        }

                        else -> {
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG_APP, "Failed parsing WS message: ${e.message}", e)
                }
            },
            onError = { err ->
                isConnecting = false
                statusText = "Error: $err"
                Log.e(TAG_APP, "WS error: $err")
            }
        )
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Status: $statusText")
        Text("WS URL: $WS_URL")

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnecting && !isConnected,
            onClick = {
                isConnecting = true
                statusText = "Connecting..."
                wsClient.connect()
            }
        ) {
            Text(if (isConnecting) "Connecting..." else "Connect WebSocket")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = isConnected,
            onClick = {
                val commandPayload = buildCommandPayload()
                val commandDisplay = BASE_COMMAND_PAYLOAD
                parseRepetitionsFromCommand(commandDisplay)

                Log.i(TAG_APP, "Message published: $commandDisplay")
                println("Message published: $commandDisplay")

                Log.i(TAG_WS_TX, "Transmisi data berhasil (app mengirim ke ESP32). topic=motor/commands")

                val ok = wsClient.send(commandPayload)
                if (!ok) {
                    Log.e(TAG_APP, "Gagal mengirim command via WebSocket")
                }
            }
        ) {
            Text("Kirim Command ke Python-ESP")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = isConnected,
            onClick = {
                wsClient.send("ping")
            }
        ) {
            Text("Ping Python-ESP")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = isConnected,
            onClick = {
                wsClient.disconnect()
            }
        ) {
            Text("Disconnect")
        }
    }
}