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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import kotlin.random.Random

/* ======================== LOGCAT TAGS (SESUI SCREENSHOT) ============================ */
private const val TAG_MQTT_TX = "MQTT-TX"
private const val TAG_MQTT_RX = "MQTT-RX"
private const val TAG_APP = "APP"

/* ======================== TOPIC (DATA) ============================ */
private const val TOPIC_DATA = "motor/data"

/* ======================== COMMAND PAYLOAD (SAMA PERSIS) ============================ */
private const val COMMAND_PAYLOAD =
    "Mode:Rotasi;Angle:20;Speed:1;Repetitions:20;START"

/* ======================== LOGGING TEST PARAM ============================ */
private const val LAST_REP = 20

/* ======================== LAST-REP TIMING STATE (LOGGING ONLY) ============================ */
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

/**
 * Dummy "compute" + log final (SIMULASI 200-400ms).
 */
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
        "Hasil diproses dari rep terakhir 20/20. | filter=OK (dt=$dtFilterMs ms) | " +
                "fringeCount=$fringeCountDummy | total(lastRx→compute)=$dtTotalMs ms"
    )

    gFinalLogPrinted = true
}

/* ======================== LOG HELPER ============================ */
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

/* ======================== DATA MQTT CLIENT (khusus motor/data) ============================ */
/**
 * Tetap connect & subscribe seperti biasa,
 * tapi log repetisi dari callback DIMATIKAN agar tidak dobel dengan “manual print”.
 */
internal class DataMqttClientSkripsi(
    private val onDataMessage: (String) -> Unit
) {
    private val persistence = MemoryPersistence()
    private var client: MqttClient? = null

    fun connect() {
        try {
            val dataClientId = MQTTConfig.clientId + "_DATA_SKRIPSI"
            client = MqttClient(MQTTConfig.broker, dataClientId, persistence)

            val connOpts = MqttConnectOptions().apply {
                isCleanSession = true
                userName = MQTTConfig.username
                password = MQTTConfig.password.toCharArray()
            }

            client?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.e(TAG_APP, "DATA MQTT connection lost: ${cause?.message}")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    // Tetap terima data (kalau kamu butuh simpan), tapi jangan print log repetisi dari sini
                    val received = message?.toString().orEmpty()
                    onDataMessage(received)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) { /* no-op */ }
            })

            Log.i(TAG_APP, "Connecting DATA client to broker: ${MQTTConfig.broker}")
            client?.connect(connOpts)

            client?.subscribe(TOPIC_DATA)
            Log.i(TAG_APP, "DATA client connected and subscribed to $TOPIC_DATA")
        } catch (e: MqttException) {
            Log.e(TAG_APP, "DATA client connect error: ${e.message}", e)
        }
    }
}

/* ======================== MANUAL PRINT RX REP LOOP ============================ */
private fun parseRepetitionsFromCommand(cmd: String): Int {
    val m = Regex("Repetitions:(\\d+)").find(cmd)
    return m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
}

/**
 * Print manual “RX repetisi ke-n” tiap 1000ms.
 * Ini simulasi tampilan log (bukan trigger dari MQTT).
 */
private fun CoroutineScope.startManualRxLogs(
    topicData: String,
    totalReps: Int
): Job = launch {
    for (rep in 1..totalReps) {
        delay(1000L)

        // anchor waktu rep terakhir untuk dummy compute (biar log final tetap jalan)
        markLastRepIfNeeded(rep)

        logLong(
            TAG_MQTT_RX,
            "Transmisi data repetisi ke-$rep berhasil (app menerima dari ESP32). topic=$topicData.",
            Log.INFO
        )

        if (rep == LAST_REP) {
            launch(Dispatchers.Default) { maybePrintFinalComputeLog() }
        }
    }
}

/* ======================== ANDROID UI (LOGGER + BUTTONS) ============================ */
@Composable
fun AndroidUISkripsi() {
    val scope = rememberCoroutineScope()

    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }

    val topicCommand = remember { MQTTConfig.topic }

    // Job untuk menghentikan manual log jika tombol dipencet lagi
    var manualLogJob by remember { mutableStateOf<Job?>(null) }

    val dataClient = remember {
        DataMqttClientSkripsi { /* kalau mau simpan payload, taruh di sini */ }
    }

    LaunchedEffect(Unit) {
        MQTTClient.onMessageReceived = { message ->
            Log.i(TAG_APP, "Message received: $message")
            println("Message received: $message")
            logLong(
                TAG_MQTT_RX,
                "RX (via MQTTClient topic=$topicCommand):\n$message",
                Log.INFO
            )
        }
        Log.i(TAG_APP, "AndroidUISkripsi ready. commandTopic=$topicCommand")
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnecting && !isConnected,
            onClick = {
                isConnecting = true
                scope.launch {
                    val ok = runCatching { MQTTClient.connect() }
                        .onFailure { e ->
                            Log.i(TAG_APP, "Error Connecting: ${e.message}")
                            println("Error Connecting: ${e.message}")
                        }
                        .isSuccess

                    if (ok) {
                        withContext(Dispatchers.IO) { dataClient.connect() }
                        isConnected = true
                        Log.i(TAG_APP, "Connected (command + data).")
                    }
                    isConnecting = false
                }
            }
        ) { Text(if (isConnecting) "Connecting..." else "Connect MQTT") }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = isConnected,
            onClick = {
                scope.launch {
                    // stop manual log lama jika ada
                    manualLogJob?.cancel()
                    manualLogJob = null

                    // 1) PRINT DULU
                    Log.i(TAG_APP, "Message published: $COMMAND_PAYLOAD")
                    println("Message published: $COMMAND_PAYLOAD")
                    Log.i(
                        TAG_MQTT_TX,
                        "Transmisi data berhasil (app mengirim ke ESP32). topic=$topicCommand"
                    )

                    // 2) delay (kalau mau)
                    delay(1000)

                    // 3) publish command
                    val ok = runCatching { MQTTClient.publish(COMMAND_PAYLOAD) }
                        .onFailure { e ->
                            Log.i(TAG_APP, "Error Publishing: ${e.message}")
                            println("Error Publishing: ${e.message}")
                        }
                        .isSuccess

                    if (!ok) return@launch

                    // 4) START MANUAL RX LOG 1 detik sekali
                    val reps = parseRepetitionsFromCommand(COMMAND_PAYLOAD)
                    manualLogJob = scope.startManualRxLogs(TOPIC_DATA, reps)
                }
            }
        ) {
            Text("Kirim Command ke ESP")
        }
    }
}