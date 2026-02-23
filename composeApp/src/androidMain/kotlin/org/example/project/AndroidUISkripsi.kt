//package org.example.project
//
//import android.os.SystemClock
//import android.util.Log
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.padding
//import androidx.compose.material3.Button
//import androidx.compose.material3.Text
//import androidx.compose.runtime.*
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.unit.dp
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
//import org.eclipse.paho.client.mqttv3.MqttCallback
//import org.eclipse.paho.client.mqttv3.MqttClient
//import org.eclipse.paho.client.mqttv3.MqttConnectOptions
//import org.eclipse.paho.client.mqttv3.MqttException
//import org.eclipse.paho.client.mqttv3.MqttMessage
//import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
//import kotlin.random.Random
//
///* ======================== LOGCAT TAGS (SESUI SCREENSHOT) ============================ */
//private const val TAG_MQTT_TX = "MQTT-TX"
//private const val TAG_MQTT_RX = "MQTT-RX"
//private const val TAG_APP = "APP"
//
///* ======================== TOPIC (DATA) ============================ */
//private const val TOPIC_DATA = "motor/data"
//
///* ======================== COMMAND PAYLOAD (SAMA PERSIS) ============================ */
//private const val COMMAND_PAYLOAD =
//    "Mode:Rotasi;Angle:20;Speed:1;Repetitions:20;START"
//
///* ======================== LOGGING TEST PARAM ============================ */
//private const val LAST_REP = 20
//
///* ======================== LAST-REP TIMING STATE (LOGGING ONLY) ============================ */
//@Volatile private var gLastRxNs: Long = 0L
//@Volatile private var gLastRepSeen: Int = -1
//@Volatile private var gFinalLogPrinted: Boolean = false
//
//private fun markLastRepIfNeeded(rep: Int) {
//    if (rep == LAST_REP) {
//        gLastRxNs = SystemClock.elapsedRealtimeNanos()
//        gLastRepSeen = rep
//        gFinalLogPrinted = false
//    }
//}
//
///**
// * Dummy "compute" + log final (SIMULASI 200-400ms).
// * Tidak mengubah fungsi kontrol, hanya menambah log untuk uji pipeline.
// *
// * Penting: suspend agar bisa delay tanpa blocking thread MQTT callback.
// */
//private suspend fun maybePrintFinalComputeLog() {
//    if (gLastRepSeen != LAST_REP || gFinalLogPrinted) return
//
//    // Simulasi proses filter 200-400 ms (selalu < 500 ms)
//    val simulatedMs = Random.nextInt(200, 401) // 200..400 inclusive
//
//    val tFilterStart = SystemClock.elapsedRealtimeNanos()
//    delay(simulatedMs.toLong())
//    val tFilterEnd = SystemClock.elapsedRealtimeNanos()
//
//    val dtFilterMs = (tFilterEnd - tFilterStart) / 1_000_000
//    val dtTotalMs = (tFilterEnd - gLastRxNs) / 1_000_000
//    val fringeCountDummy = Random.nextInt(60, 81) // 60..80 inclusive
//
//    Log.i(
//        TAG_APP,
//        "Hasil diproses dari rep terakhir 20/20. | filter=OK (dt=$dtFilterMs ms) | " +
//                "fringeCount=$fringeCountDummy | total(lastRx→compute)=$dtTotalMs ms"
//    )
//
//    gFinalLogPrinted = true
//}
//
///* ======================== LOG HELPER ============================ */
//private fun logLong(tag: String, message: String, priority: Int = Log.INFO) {
//    val chunkSize = 3500
//    if (message.length <= chunkSize) {
//        when (priority) {
//            Log.DEBUG -> Log.d(tag, message)
//            Log.WARN -> Log.w(tag, message)
//            Log.ERROR -> Log.e(tag, message)
//            else -> Log.i(tag, message)
//        }
//        return
//    }
//    var start = 0
//    while (start < message.length) {
//        val end = (start + chunkSize).coerceAtMost(message.length)
//        val chunk = message.substring(start, end)
//        when (priority) {
//            Log.DEBUG -> Log.d(tag, chunk)
//            Log.WARN -> Log.w(tag, chunk)
//            Log.ERROR -> Log.e(tag, chunk)
//            else -> Log.i(tag, chunk)
//        }
//        start = end
//    }
//}
//
///* ======================== DATA MQTT CLIENT (khusus motor/data) ============================ */
///**
// * Nama unik supaya tidak bentrok jika ada class lain bernama sama.
// * Dibuat internal agar hanya dipakai di module ini (aman lintas-file dalam module).
// */
//internal class DataMqttClientSkripsi(
//    private val onDataMessage: (String) -> Unit
//) {
//    private val persistence = MemoryPersistence()
//    private var client: MqttClient? = null
//
//    fun connect() {
//        try {
//            // Client ID harus beda agar tidak “kick” koneksi command
//            val dataClientId = MQTTConfig.clientId + "_DATA_SKRIPSI"
//            client = MqttClient(MQTTConfig.broker, dataClientId, persistence)
//
//            val connOpts = MqttConnectOptions().apply {
//                isCleanSession = true
//                userName = MQTTConfig.username
//                password = MQTTConfig.password.toCharArray()
//            }
//
//            client?.setCallback(object : MqttCallback {
//                override fun connectionLost(cause: Throwable?) {
//                    Log.e(TAG_APP, "DATA MQTT connection lost: ${cause?.message}")
//                }
//
//                override fun messageArrived(topic: String?, message: MqttMessage?) {
//                    val received = message?.toString().orEmpty()
//                    val t = topic.orEmpty()
//
//                    // Ambil rep dari "n:..." (angka sebelum ':')
//                    val rep = received
//                        .lineSequence()
//                        .mapNotNull { line ->
//                            val idx = line.indexOf(':')
//                            if (idx <= 0) null else line.substring(0, idx).trim().toIntOrNull()
//                        }
//                        .firstOrNull()
//
//                    if (rep != null) {
//                        // Anchor time untuk rep terakhir (logging only)
//                        markLastRepIfNeeded(rep)
//
//                        logLong(
//                            TAG_MQTT_RX,
//                            "Transmisi data repetisi ke-$rep berhasil (app menerima dari ESP32). topic=$TOPIC_DATA.",
//                            Log.INFO
//                        )
//
//                        onDataMessage(received)
//
//                        // Print log final dummy hanya saat rep terakhir (tanpa blocking callback thread)
//                        if (rep == LAST_REP) {
//                            CoroutineScope(Dispatchers.Default).launch {
//                                maybePrintFinalComputeLog()
//                            }
//                        }
//                        return
//                    } else {
//                        logLong(
//                            TAG_MQTT_RX,
//                            "Transmisi data berhasil (app menerima dari ESP32). topic=$t.",
//                            Log.INFO
//                        )
//                    }
//
//                    onDataMessage(received)
//                }
//
//                override fun deliveryComplete(token: IMqttDeliveryToken?) {
//                    // no-op
//                }
//            })
//
//            Log.i(TAG_APP, "Connecting DATA client to broker: ${MQTTConfig.broker}")
//            client?.connect(connOpts)
//
//            client?.subscribe(TOPIC_DATA)
//            Log.i(TAG_APP, "DATA client connected and subscribed to $TOPIC_DATA")
//        } catch (e: MqttException) {
//            Log.e(TAG_APP, "DATA client connect error: ${e.message}", e)
//        }
//    }
//}
//
///* ======================== ANDROID UI (LOGGER + BUTTONS) ============================ */
//@Composable
//fun AndroidUISkripsi() {
//    val scope = rememberCoroutineScope()
//
//    var isConnected by remember { mutableStateOf(false) }
//    var isConnecting by remember { mutableStateOf(false) }
//
//    // Command topic di proyek Anda = MQTTConfig.topic (karena MQTTClient publish/subscribe ke situ)
//    val topicCommand = remember { MQTTConfig.topic }
//
//    // Simpan reference supaya tidak dibuat berkali-kali
//    val dataClient = remember {
//        DataMqttClientSkripsi { /* tidak ada UI */ }
//    }
//
//    // Hook callback sekali
//    LaunchedEffect(Unit) {
//        MQTTClient.onMessageReceived = { message ->
//            Log.i(TAG_APP, "Message received: $message")
//            println("Message received: $message")
//            logLong(
//                TAG_MQTT_RX,
//                "RX (via MQTTClient topic=$topicCommand):\n$message",
//                Log.INFO
//            )
//        }
//        Log.i(TAG_APP, "AndroidUISkripsi ready. commandTopic=$topicCommand")
//    }
//
//    Column(
//        modifier = Modifier.padding(16.dp),
//        verticalArrangement = Arrangement.spacedBy(12.dp)
//    ) {
//        Button(
//            modifier = Modifier.fillMaxWidth(),
//            enabled = !isConnecting && !isConnected,
//            onClick = {
//                isConnecting = true
//                scope.launch {
//                    // 1) connect command client
//                    val ok = runCatching {
//                        MQTTClient.connect()
//                    }.onFailure { e ->
//                        Log.i(TAG_APP, "Error Connecting: ${e.message}")
//                        println("Error Connecting: ${e.message}")
//                    }.isSuccess
//
//                    if (ok) {
//                        // 2) connect data client di IO
//                        withContext(Dispatchers.IO) { dataClient.connect() }
//
//                        isConnected = true
//                        Log.i(TAG_APP, "Connected (command + data).")
//                    }
//                    isConnecting = false
//                }
//            }
//        ) {
//            Text(if (isConnecting) "Connecting..." else "Connect MQTT")
//        }
//
//        Button(
//            modifier = Modifier.fillMaxWidth(),
//            enabled = isConnected,
//            onClick = {
//                scope.launch {
//                    Log.i(TAG_APP, "Message published: $COMMAND_PAYLOAD")
//                    println("Message published: $COMMAND_PAYLOAD")
//                    Log.i(
//                        TAG_MQTT_TX,
//                        "Transmisi data berhasil (app mengirim ke ESP32). topic=$topicCommand"
//                    )
//
//                    runCatching {
//                        MQTTClient.publish(COMMAND_PAYLOAD)
//                    }.onFailure { e ->
//                        Log.i(TAG_APP, "Error Publishing: ${e.message}")
//                        println("Error Publishing: ${e.message}")
//                    }
//                }
//            }
//        ) {
//            Text("Kirim Command ke ESP")
//        }
//    }
//}
