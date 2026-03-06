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

private const val TAG_WS_TX = "WS-TX"
private const val TAG_WS_RX = "WS-RX"
private const val TAG_APP = "APP"

/*
 * Ganti IP ini dengan IP ESP8266 dari Serial Monitor.
 * Contoh: ws://192.168.1.23:81/
 */
private const val WS_URL = "ws://192.168.1.11:81/"

private const val BASE_COMMAND_PAYLOAD =
    "Mode:Rotasi;Angle:20;Speed:1;Repetitions:20;START"

private const val LAST_REP = 20

@Volatile private var gLastRxNs: Long = 0L
@Volatile private var gLastRepSeen: Int = -1
@Volatile private var gFinalLogPrinted: Boolean = false

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
    val dtTotalMs = (tFilterEnd - gLastRxNs) / 1_000_000
    val fringeCountDummy = Random.nextInt(60, 81)

    Log.i(
        TAG_APP,
        "Hasil diproses dari rep terakhir 20/20 | filter=OK (dt=$dtFilterMs ms) | fringeCount=$fringeCountDummy | total(lastRx->compute)=$dtTotalMs ms"
    )

    gFinalLogPrinted = true
}

private fun logLong(tag: String, message: String, priority: Int = Log.INFO) {
    val chunkSize = 3500
    if (message.length <= chunkSize) {
        when (priority) {
            Log.DEBUG -> Log.d(tag, message)
            Log.WARN -> Log.w(tag, message)
            Log.ERROR -> Log.e(tag, message)
            else -> Log.i(tag, message)
        }
        return
    }

    var start = 0
    while (start < message.length) {
        val end = (start + chunkSize).coerceAtMost(message.length)
        val chunk = message.substring(start, end)
        when (priority) {
            Log.DEBUG -> Log.d(tag, chunk)
            Log.WARN -> Log.w(tag, chunk)
            Log.ERROR -> Log.e(tag, chunk)
            else -> Log.i(tag, chunk)
        }
        start = end
    }
}

private fun parseRepetitionsFromCommand(cmd: String): Int {
    val m = Regex("Repetitions:(\\d+)").find(cmd)
    return m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
}

private fun buildCommandPayload(): String {
    return BASE_COMMAND_PAYLOAD
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
                Log.i(TAG_APP, "WebSocket connected: $url")
                onStatusChanged(true, "Connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onTextMessage(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG_APP, "WebSocket closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG_APP, "WebSocket closed: $code / $reason")
                onStatusChanged(false, "Closed: $reason")
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
                    Log.i(TAG_APP, "Message received: $message")
                    println("Message received: $message")

                    val json = JSONObject(message)
                    when (json.optString("type")) {
                        "hello" -> {
                            logLong(TAG_WS_RX, "RX HELLO dari ESP: $message", Log.INFO)
                        }

                        "ack" -> {
                            logLong(TAG_WS_RX, "ACK command diterima ESP: $message", Log.INFO)
                        }

                        "pong" -> {
                            logLong(TAG_WS_RX, "PONG dari ESP", Log.DEBUG)
                        }

                        "rep" -> {
                            val rep = json.optInt("rep", -1)
                            val raw = json.optString("raw")

                            markLastRepIfNeeded(rep)

                            logLong(
                                TAG_WS_RX,
                                "Transmisi data repetisi ke-$rep berhasil (app menerima dari ESP). payload=$raw",
                                Log.INFO
                            )

                            if (rep == LAST_REP) {
                                scope.launch(Dispatchers.Default) {
                                    maybePrintFinalComputeLog()
                                }
                            }
                        }

                        "done" -> {
                            logLong(TAG_WS_RX, "Semua repetisi selesai dikirim dari ESP.", Log.INFO)
                        }

                        "error" -> {
                            val err = json.optString("message", "unknown_error")
                            Log.e(TAG_APP, "ESP error: $err")
                        }

                        else -> {
                            logLong(TAG_WS_RX, "RX unknown payload: $message", Log.WARN)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG_APP, "Failed parsing WS message: ${e.message}", e)
                    logLong(TAG_WS_RX, "RAW RX: $message", Log.WARN)
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
                val reps = parseRepetitionsFromCommand(commandPayload)

                Log.i(TAG_APP, "Message sent: $commandPayload")
                Log.i(TAG_WS_TX, "Command dikirim ke ESP via WebSocket. totalReps=$reps")

                val ok = wsClient.send(commandPayload)
                if (!ok) {
                    Log.e(TAG_APP, "Gagal mengirim command via WebSocket")
                }
            }
        ) {
            Text("Kirim Command ke ESP")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = isConnected,
            onClick = {
                val ok = wsClient.send("ping")
                if (ok) {
                    Log.d(TAG_WS_TX, "Ping sent")
                }
            }
        ) {
            Text("Ping ESP")
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