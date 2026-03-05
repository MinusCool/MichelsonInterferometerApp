package org.example.project

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

/* ======================== PREMIUM UI TOKENS ============================ */

private object PremiumTokens {
    val BgTop = Color(0xFFF7F9FF)
    val BgBottom = Color(0xFFEFF3FF)

    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFF4F7FF)

    val Primary = Color(0xFF1E40AF)
    val PrimarySoft = Color(0xFFE8EEFF)
    val Accent = Color(0xFF0EA5E9)
    val Danger = Color(0xFFDC2626)

    val Text = Color(0xFF0F172A)
    val TextMuted = Color(0xFF64748B)
    val Border = Color(0xFFE2E8F0)

    val CardShape = RoundedCornerShape(18.dp)
    val SheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    @Composable
    fun cardElevation() = CardDefaults.cardElevation(
        defaultElevation = 10.dp,
        pressedElevation = 12.dp,
        focusedElevation = 12.dp,
        hoveredElevation = 12.dp,
        draggedElevation = 14.dp,
        disabledElevation = 0.dp
    )

    @Composable
    fun buttonElevation() = ButtonDefaults.buttonElevation(
        defaultElevation = 8.dp,
        pressedElevation = 10.dp,
        focusedElevation = 10.dp,
        hoveredElevation = 10.dp,
        disabledElevation = 0.dp
    )
}

/* ======================== MQTT + UI ============================ */

@Composable
fun HeaderSection(logo: Painter) {
    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp).aspectRatio(16f / 9f)
    ) {
        Image(
            painter = logo,
            contentDescription = "Header Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
fun StatusIndicator(connected: Boolean, onToggle: () -> Unit) {
    val text = if (connected) "Connected" else "Disconnected"
    val color = if (connected) PremiumTokens.Primary else PremiumTokens.Danger
    Button(
        onClick = onToggle,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = color.copy(alpha = 0.45f),
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        ),
        elevation = PremiumTokens.buttonElevation(),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
    }
}

@Composable
fun ModeSelectionChipGroup(mode: String, onModeChange: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onModeChange("Linear") },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (mode == "Linear") PremiumTokens.Primary else PremiumTokens.Surface,
                contentColor = if (mode == "Linear") Color.White else PremiumTokens.Text
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = if (mode == "Linear") 8.dp else 2.dp,
                pressedElevation = 10.dp
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.linearGradient(listOf(PremiumTokens.Border, PremiumTokens.Border)),
                width = 1.dp
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (mode == "Linear") {
                Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text("Linear", fontWeight = FontWeight.SemiBold)
        }

        Button(
            onClick = { onModeChange("Rotasi") },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (mode == "Rotasi") PremiumTokens.Primary else PremiumTokens.Surface,
                contentColor = if (mode == "Rotasi") Color.White else PremiumTokens.Text
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = if (mode == "Rotasi") 8.dp else 2.dp,
                pressedElevation = 10.dp
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.linearGradient(listOf(PremiumTokens.Border, PremiumTokens.Border)),
                width = 1.dp
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (mode == "Rotasi") {
                Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text("Rotasi", fontWeight = FontWeight.SemiBold)
        }
    }
}

/* ======================== PLOT SECTION ============================ */

@Composable
fun DataPlotSection(
    channelMap: Map<Int, List<Int>>,
    filteredMap: Map<Int, List<Double>>
) {
    val N_VISIBLE = 100
    val vScroll = rememberScrollState()

    Box(modifier = Modifier.height(320.dp).verticalScroll(vScroll)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val sortedKeys = channelMap.keys.sorted()

            if (sortedKeys.isEmpty()) {
                Text(
                    "No data to display. Start the process to receive MQTT data.",
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 80.dp),
                    color = PremiumTokens.TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                for (keyVal in sortedKeys) {
                    val values = channelMap[keyVal] ?: emptyList()
                    val rawSnapshot = values.toList()
                    val filteredSnapshot = (filteredMap[keyVal] ?: emptyList()).toList()

                    Text(
                        "samples=${rawSnapshot.size}  min=${rawSnapshot.minOrNull()}  max=${rawSnapshot.maxOrNull()}",
                        fontSize = 12.sp,
                        color = PremiumTokens.TextMuted
                    )
                    Text(
                        "Repetition $keyVal",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PremiumTokens.Text
                    )

                    key(keyVal) {
                        val hScroll = rememberScrollState()
                        val totalPoints = max(rawSnapshot.size, filteredSnapshot.size).coerceAtLeast(2)

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        ) {
                            val viewportWidthDp = this.maxWidth
                            val dpPerSample = (viewportWidthDp / (N_VISIBLE - 1).coerceAtLeast(1))
                            val plotWidthDp = (dpPerSample * (totalPoints - 1)).coerceAtLeast(viewportWidthDp)

                            LaunchedEffect(totalPoints, plotWidthDp) {
                                hScroll.scrollTo(hScroll.maxValue)
                            }

                            Box(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .horizontalScroll(hScroll)
                                        .draggable(
                                            orientation = Orientation.Horizontal,
                                            state = rememberDraggableState { delta ->
                                                hScroll.dispatchRawDelta(-delta)
                                            }
                                        )
                                ) {
                                    Canvas(
                                        modifier = Modifier
                                            .width(plotWidthDp)
                                            .fillMaxHeight()
                                            .padding(bottom = 12.dp)
                                    ) {
                                        val stepX = dpPerSample.toPx()

                                        val dataForScale =
                                            if (filteredSnapshot.isNotEmpty())
                                                (rawSnapshot.map { it.toDouble() } + filteredSnapshot)
                                            else rawSnapshot.map { it.toDouble() }

                                        if (dataForScale.size >= 2) {
                                            val maxVal = dataForScale.maxOrNull()?.toFloat() ?: 100f
                                            val minVal = dataForScale.minOrNull()?.toFloat() ?: 0f
                                            val rangeVal = max(1e-6f, maxVal - minVal)
                                            val scaleY = size.height / rangeVal

                                            val grid = PremiumTokens.Border.copy(alpha = 0.55f)
                                            val numHorizontalLines = 5
                                            repeat(numHorizontalLines) {
                                                val y = it * (size.height / (numHorizontalLines - 1))
                                                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                                            }
                                            val numVerticalLines = 12
                                            val stepXGrid = size.width / (numVerticalLines - 1).coerceAtLeast(1)
                                            repeat(numVerticalLines) {
                                                val x = it * stepXGrid
                                                drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
                                            }

                                            if (rawSnapshot.size >= 2) {
                                                for (i in 0 until rawSnapshot.size - 1) {
                                                    val y1 = size.height - ((rawSnapshot[i].toFloat() - minVal) * scaleY)
                                                    val y2 = size.height - ((rawSnapshot[i + 1].toFloat() - minVal) * scaleY)
                                                    drawLine(
                                                        PremiumTokens.Accent,
                                                        Offset(i * stepX, y1),
                                                        Offset((i + 1) * stepX, y2),
                                                        2f
                                                    )
                                                }
                                            }

                                            if (filteredSnapshot.size >= 2) {
                                                val gold = Color(0xFFF59E0B)
                                                for (i in 0 until filteredSnapshot.size - 1) {
                                                    val y1 = size.height - ((filteredSnapshot[i].toFloat() - minVal) * scaleY)
                                                    val y2 = size.height - ((filteredSnapshot[i + 1].toFloat() - minVal) * scaleY)
                                                    drawLine(
                                                        gold,
                                                        Offset(i * stepX, y1),
                                                        Offset((i + 1) * stepX, y2),
                                                        2f
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalScrollbar(
                                    adapter = rememberScrollbarAdapter(hScroll),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ======================== STREAMING BUFFERS + PROCESSOR API ============================ */

private fun Int.floorMod(m: Int): Int = ((this % m) + m) % m

private class IntRingBuffer(private val capacity: Int) {
    private val data = IntArray(capacity)
    private var head = 0
    private var size = 0
    private var startSeq = 0L // absolute seq number of the oldest element

    fun append(v: Int) {
        data[head] = v
        head = (head + 1) % capacity
        if (size < capacity) {
            size++
        } else {
            startSeq++ // overwrite oldest
        }
    }

    fun oldestSeq(): Long = startSeq
    fun newestSeqExclusive(): Long = startSeq + size
    fun currentSize(): Int = size

    private fun getBySeq(seq: Long): Int {
        val idx = (seq - startSeq).toInt()
        require(idx in 0 until size) { "seq out of range" }
        val physical = (head - size + idx).floorMod(capacity)
        return data[physical]
    }

    fun readChunk(fromSeq: Long, maxCount: Int): IntArray {
        val from = max(fromSeq, oldestSeq())
        val to = min(from + maxCount, newestSeqExclusive())
        val n = (to - from).toInt().coerceAtLeast(0)
        val out = IntArray(n)
        for (i in 0 until n) out[i] = getBySeq(from + i)
        return out
    }

    fun snapshotLast(maxPoints: Int): List<Int> {
        val n = min(size, maxPoints)
        val start = newestSeqExclusive() - n
        return List(n) { i -> getBySeq(start + i) }
    }
}

private class DoubleRingBuffer(private val capacity: Int) {
    private val data = DoubleArray(capacity)
    private var head = 0
    private var size = 0
    private var startSeq = 0L

    fun appendAll(values: DoubleArray) {
        for (v in values) append(v)
    }

    private fun append(v: Double) {
        data[head] = v
        head = (head + 1) % capacity
        if (size < capacity) size++ else startSeq++
    }

    private fun getBySeq(seq: Long): Double {
        val idx = (seq - startSeq).toInt()
        require(idx in 0 until size) { "seq out of range" }
        val physical = (head - size + idx).floorMod(capacity)
        return data[physical]
    }

    fun snapshotLast(maxPoints: Int): List<Double> {
        val n = min(size, maxPoints)
        val start = startSeq + size - n
        return List(n) { i -> getBySeq(start + i) }
    }

    fun clear() {
        head = 0
        size = 0
        startSeq = 0L
    }
}

data class ProcParams(
    val version: Long,
    val type: String,
    val sgWindow: Int,
    val sgOrder: Int,
    val kalmanQ: Double,
    val kalmanR: Double
)

data class ProcResponse(
    val channel: Int,
    val seqStart: Long,
    val paramsVersion: Long,
    val filtered: DoubleArray
)

interface ProcessorClient {
    fun process(
        channel: Int,
        seqStart: Long,
        rawTail: IntArray,
        rawChunk: IntArray,
        params: ProcParams
    ): ProcResponse
}

private class LocalProcessorClient(
    private val kalmanX: MutableMap<Int, Double>,
    private val kalmanP: MutableMap<Int, Double>
) : ProcessorClient {

    override fun process(
        channel: Int,
        seqStart: Long,
        rawTail: IntArray,
        rawChunk: IntArray,
        params: ProcParams
    ): ProcResponse {
        val out: DoubleArray = when (params.type) {
            "SG" -> {
                val combined = ArrayList<Int>(rawTail.size + rawChunk.size)
                for (v in rawTail) combined.add(v)
                for (v in rawChunk) combined.add(v)

                val yInt = savitzkyGolayFilterTrue(combined, params.sgWindow, params.sgOrder)
                val dropped = rawTail.size
                DoubleArray(rawChunk.size) { i -> yInt[i + dropped].toDouble() }
            }

            "Kalman" -> {
                val Q = params.kalmanQ
                val R = params.kalmanR

                var x: Double = kalmanX[channel] ?: (rawChunk.firstOrNull()?.toDouble() ?: 0.0)
                var P: Double = kalmanP[channel] ?: 1.0

                val y = DoubleArray(rawChunk.size)
                for (i in rawChunk.indices) {
                    val z = rawChunk[i].toDouble()
                    val xPred = x
                    val PPred = P + Q
                    val K = PPred / (PPred + R)
                    x = xPred + K * (z - xPred)
                    P = (1.0 - K) * PPred
                    y[i] = x
                }
                kalmanX[channel] = x
                kalmanP[channel] = P
                y
            }

            else -> DoubleArray(rawChunk.size) { i -> rawChunk[i].toDouble() }
        }

        return ProcResponse(
            channel = channel,
            seqStart = seqStart,
            paramsVersion = params.version,
            filtered = out
        )
    }
}

/* ======================== CSV EXPORT (MANUAL ON SAVE) ============================ */

private fun exportCsvSnapshotToExperiments(
    rawBufByChannel: Map<Int, IntRingBuffer>,
    filtBufByChannel: Map<Int, DoubleRingBuffer>,
    rawMax: Int,
    filtMax: Int
): File {
    val dir = File("newest_experiments")
    dir.mkdirs()

    val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val outFile = File(dir, "experiment_$ts.csv")

    val sb = StringBuilder()
    sb.appendLine("channel,index,raw,filtered")

    val channels = (rawBufByChannel.keys + filtBufByChannel.keys).toSortedSet()
    for (ch in channels) {
        val raw = rawBufByChannel[ch]?.snapshotLast(rawMax).orEmpty()
        val filt = filtBufByChannel[ch]?.snapshotLast(filtMax).orEmpty()
        val n = max(raw.size, filt.size)
        for (i in 0 until n) {
            val r = raw.getOrNull(i)
            val f = filt.getOrNull(i)
            sb.append(ch).append(',')
                .append(i).append(',')
                .append(r?.toString() ?: "").append(',')
                .append(f?.let { "%.6f".format(it) } ?: "")
                .appendLine()
        }
    }

    outFile.writeText(sb.toString())
    return outFile
}

private suspend fun awaitFilterCatchUp(
    rawBufByChannel: Map<Int, IntRingBuffer>,
    nextSeqToProcess: Map<Int, Long>,
    timeoutMs: Long = 3000L,
    pollMs: Long = 25L
): Boolean {
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start <= timeoutMs) {
        var allDone = true
        for (ch in rawBufByChannel.keys) {
            val raw = rawBufByChannel[ch] ?: continue
            val need = raw.newestSeqExclusive()
            val cur = nextSeqToProcess[ch] ?: raw.oldestSeq()
            if (cur < need) {
                allDone = false
                break
            }
        }
        if (allDone) return true
        delay(pollMs)
    }
    return false
}

/* ======================== MAIN UI ============================ */

@Composable
fun DesktopUI() {
    var connected by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("Rotasi") }
    var angle by remember { mutableStateOf("") }
    var speed by remember { mutableStateOf("") }
    var repetitions by remember { mutableStateOf("") }
    var showPlot by remember { mutableStateOf(false) }

    val sensorMessagesList = remember { mutableStateListOf<String>() }
    var showAlertDialog by remember { mutableStateOf(false) }

    // NEW: scope untuk call suspend dari onClick
    val scope = rememberCoroutineScope()

    // ===== Filter UI states (PENDING / preview) =====
    var selectedFilter by remember { mutableStateOf("SG") }
    var sgWindow by remember { mutableStateOf(7) }
    var sgOrder by remember { mutableStateOf(2) }
    var kalmanQ by remember { mutableStateOf(0.01f) }
    var kalmanR by remember { mutableStateOf(1.0f) }

    // ===== Filter APPLIED states (yang dipakai pipeline) =====
    var appliedFilter by remember { mutableStateOf(selectedFilter) }
    var appliedSgWindow by remember { mutableStateOf(sgWindow) }
    var appliedSgOrder by remember { mutableStateOf(sgOrder) }
    var appliedKalmanQ by remember { mutableStateOf(kalmanQ.toDouble()) }
    var appliedKalmanR by remember { mutableStateOf(kalmanR.toDouble()) }

    var paramsVersion by remember { mutableStateOf(0L) }

    val filters = listOf("SG", "Kalman")

    val runEpoch = remember { AtomicLong(0L) }
    val mqttQueue = remember { Channel<Pair<Long, String>>(capacity = Channel.BUFFERED) }

    val RAW_CAP = 200_000
    val FILT_CAP = 200_000
    val PLOT_MAX_POINTS = 5_000

    val rawBufByChannel = remember { mutableStateMapOf<Int, IntRingBuffer>() }
    val filtBufByChannel = remember { mutableStateMapOf<Int, DoubleRingBuffer>() }
    val nextSeqToProcess = remember { mutableStateMapOf<Int, Long>() }

    val kalmanXByChannel = remember { mutableStateMapOf<Int, Double>() }
    val kalmanPByChannel = remember { mutableStateMapOf<Int, Double>() }

    val usePython = true
    val pythonClient = remember { PythonProcessorClient(resourcePath = "worker/python_worker.exe") }
    val processor: ProcessorClient = remember {
        if (usePython) pythonClient else LocalProcessorClient(kalmanXByChannel, kalmanPByChannel)
    }

    var uiTick by remember { mutableStateOf(0L) }
    var procTick by remember { mutableStateOf(0L) }

    var droppedFirstSampleRep1 by remember { mutableStateOf(false) }

    var savedForThisRun by remember { mutableStateOf(false) }
    var runDone by remember { mutableStateOf(false) }

    // NEW: biar tombol Save disable saat proses save berlangsung
    var savingCsv by remember { mutableStateOf(false) }

    LaunchedEffect(selectedFilter, sgWindow, sgOrder, kalmanQ, kalmanR) {
        delay(350)

        val w = (if (sgWindow % 2 == 0) sgWindow + 1 else sgWindow).coerceIn(3, 301)
        val o = sgOrder.coerceIn(2, 10).coerceAtMost((w - 1).coerceAtLeast(2))

        val nextFilter = selectedFilter
        val nextQ = kalmanQ.toDouble().coerceIn(1e-5, 10.0)
        val nextR = kalmanR.toDouble().coerceIn(1e-4, 20.0)

        val changed =
            nextFilter != appliedFilter ||
                    w != appliedSgWindow ||
                    o != appliedSgOrder ||
                    abs(nextQ - appliedKalmanQ) > 1e-12 ||
                    abs(nextR - appliedKalmanR) > 1e-12

        if (changed) {
            appliedFilter = nextFilter
            appliedSgWindow = w
            appliedSgOrder = o
            appliedKalmanQ = nextQ
            appliedKalmanR = nextR
            paramsVersion++

            for ((_, fbuf) in filtBufByChannel) {
                fbuf.clear()
            }
            kalmanXByChannel.clear()
            kalmanPByChannel.clear()

            val lookback = max(2048, appliedSgWindow * 8)
            for ((ch, rbuf) in rawBufByChannel) {
                val newest = rbuf.newestSeqExclusive()
                val oldest = rbuf.oldestSeq()
                val start = max(oldest, newest - lookback)
                nextSeqToProcess[ch] = start
            }

            procTick++
        }
    }

    LaunchedEffect(Unit) {
        MQTTClient.onMessageReceived = { message ->
            mqttQueue.trySend(runEpoch.get() to message)
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            val (epoch, message) = mqttQueue.receive()
            if (epoch != runEpoch.get()) continue

            val lines = message.lines()

            var doneSeen = false
            var appendedAny = false

            for (rawLine in lines) {
                val lineNorm = rawLine.trim().removeSurrounding("\"").trim()
                if (lineNorm.isEmpty()) continue

                if (lineNorm == "DONE") {
                    doneSeen = true
                    continue
                }

                if (lineNorm.contains("Mode:") && lineNorm.contains("START")) {
                    continue
                }

                val parts = lineNorm.split(":", limit = 2)
                val channel = parts.getOrNull(0)?.trim()?.toIntOrNull()
                val value = parts.getOrNull(1)?.trim()?.toIntOrNull()

                if (channel != null && value != null) {
                    sensorMessagesList.add("$channel:$value")
                    if (sensorMessagesList.size > 200) sensorMessagesList.removeFirst()

                    if (channel == 1 && !droppedFirstSampleRep1) {
                        droppedFirstSampleRep1 = true
                        continue
                    }

                    val buf = rawBufByChannel.getOrPut(channel) { IntRingBuffer(RAW_CAP) }
                    buf.append(value)

                    val cur = nextSeqToProcess[channel]
                    if (cur == null) nextSeqToProcess[channel] = buf.oldestSeq()
                    else if (cur < buf.oldestSeq()) nextSeqToProcess[channel] = buf.oldestSeq()

                    filtBufByChannel.getOrPut(channel) { DoubleRingBuffer(FILT_CAP) }

                    appendedAny = true
                    showPlot = true
                }
            }

            if (appendedAny) uiTick++

            if (doneSeen) {
                runDone = true
                sensorMessagesList.add("DONE received → ready to save CSV")
                if (sensorMessagesList.size > 200) sensorMessagesList.removeFirst()
            }
        }
    }

    LaunchedEffect(connected, paramsVersion) {
        if (!connected) return@LaunchedEffect

        val tickMs = 25L
        val chunkSize = 256
        val maxChunksPerTickPerChannel = 4

        while (isActive && connected) {
            delay(tickMs)

            val params = ProcParams(
                version = paramsVersion,
                type = appliedFilter,
                sgWindow = appliedSgWindow,
                sgOrder = appliedSgOrder,
                kalmanQ = appliedKalmanQ,
                kalmanR = appliedKalmanR
            )

            var processedAny = false

            for (ch in rawBufByChannel.keys.sorted()) {
                val rawBuf = rawBufByChannel[ch] ?: continue
                val filtBuf = filtBufByChannel.getOrPut(ch) { DoubleRingBuffer(FILT_CAP) }

                var seq = nextSeqToProcess[ch] ?: rawBuf.oldestSeq()
                if (seq < rawBuf.oldestSeq()) seq = rawBuf.oldestSeq()

                var processed = 0
                while (seq < rawBuf.newestSeqExclusive() && processed < maxChunksPerTickPerChannel) {
                    val chunk = rawBuf.readChunk(seq, chunkSize)
                    if (chunk.isEmpty()) break

                    val tailLen = if (params.type == "SG") (params.sgWindow - 1).coerceAtLeast(0) else 0
                    val tail = if (tailLen > 0) rawBuf.readChunk(seq - tailLen, tailLen) else IntArray(0)

                    val resp = processor.process(
                        channel = ch,
                        seqStart = seq,
                        rawTail = tail,
                        rawChunk = chunk,
                        params = params
                    )

                    if (resp.paramsVersion != paramsVersion) break

                    filtBuf.appendAll(resp.filtered)

                    seq += chunk.size
                    processed++
                    processedAny = true
                }

                nextSeqToProcess[ch] = seq
            }

            if (processedAny) {
                procTick++
            }
        }
    }

    val rawMapForPlot by remember(uiTick) {
        derivedStateOf {
            rawBufByChannel.mapValues { (_, buf) -> buf.snapshotLast(PLOT_MAX_POINTS) }
        }
    }
    val filteredMapForPlot by remember(procTick) {
        derivedStateOf {
            filtBufByChannel.mapValues { (_, buf) -> buf.snapshotLast(PLOT_MAX_POINTS) }
        }
    }

    val fringeCountByRep by remember(procTick) {
        derivedStateOf {
            filteredMapForPlot.keys.sorted().associateWith { rep ->
                val filtered = filteredMapForPlot[rep].orEmpty()
                countFringesFromPeaksDouble(filtered)
            }
        }
    }

    val latestRep by remember { derivedStateOf { fringeCountByRep.keys.maxOrNull() } }
    val latestFringeCount by remember { derivedStateOf { latestRep?.let { fringeCountByRep[it] } ?: 0 } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PremiumTokens.BgTop, PremiumTokens.BgBottom)))
    ) {
        HeaderSection(painterResource("interferometer_header.png"))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Card(
                modifier = Modifier.fillMaxSize().offset(y = (-2).dp),
                shape = PremiumTokens.SheetShape,
                colors = CardDefaults.cardColors(containerColor = PremiumTokens.Surface),
                elevation = PremiumTokens.cardElevation()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {

                        Column(modifier = Modifier.weight(1f)) {
                            Card(
                                modifier = Modifier.fillMaxWidth().height(195.dp),
                                shape = PremiumTokens.CardShape,
                                colors = CardDefaults.cardColors(containerColor = PremiumTokens.SurfaceAlt),
                                elevation = PremiumTokens.cardElevation()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "MQTT Messages",
                                        fontWeight = FontWeight.SemiBold,
                                        color = PremiumTokens.Text,
                                        letterSpacing = 0.2.sp
                                    )
                                    OutlinedTextField(
                                        value = sensorMessagesList.joinToString("\n"),
                                        onValueChange = {},
                                        modifier = Modifier.fillMaxWidth().height(190.dp),
                                        readOnly = true,
                                        singleLine = false,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = PremiumTokens.Primary.copy(alpha = 0.55f),
                                            unfocusedBorderColor = PremiumTokens.Border,
                                            focusedLabelColor = PremiumTokens.Primary,
                                            cursorColor = PremiumTokens.Primary,
                                            focusedTextColor = PremiumTokens.Text,
                                            unfocusedTextColor = PremiumTokens.Text,
                                            disabledBorderColor = PremiumTokens.Border,
                                            disabledTextColor = PremiumTokens.TextMuted
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = PremiumTokens.CardShape,
                                colors = CardDefaults.cardColors(containerColor = PremiumTokens.SurfaceAlt),
                                elevation = PremiumTokens.cardElevation()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Status", fontWeight = FontWeight.SemiBold, color = PremiumTokens.Text)
                                    StatusIndicator(connected) {
                                        if (connected) {
                                            MQTTClient.disconnect()
                                            pythonClient.stop()
                                        } else {
                                            MQTTClient.connect()
                                        }
                                        connected = !connected
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = PremiumTokens.CardShape,
                                colors = CardDefaults.cardColors(containerColor = PremiumTokens.SurfaceAlt),
                                elevation = PremiumTokens.cardElevation()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Mode", fontWeight = FontWeight.SemiBold, color = PremiumTokens.Text)
                                    ModeSelectionChipGroup(mode) { newMode -> mode = newMode }
                                }
                            }
                        }

                        Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().height(408.dp),
                                    shape = PremiumTokens.CardShape,
                                    colors = CardDefaults.cardColors(containerColor = PremiumTokens.SurfaceAlt),
                                    elevation = PremiumTokens.cardElevation()
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text("Data Visualization", fontWeight = FontWeight.SemiBold, color = PremiumTokens.Text)
                                        Button(
                                            onClick = { showPlot = !showPlot },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = PremiumTokens.Primary,
                                                contentColor = Color.White
                                            ),
                                            elevation = PremiumTokens.buttonElevation(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(if (showPlot) "Hide Plot" else "Show Plot", fontWeight = FontWeight.SemiBold)
                                        }
                                        if (showPlot) {
                                            Spacer(Modifier.height(2.dp))
                                            DataPlotSection(
                                                channelMap = rawMapForPlot,
                                                filteredMap = filteredMapForPlot
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        /* =============== Filter Settings Card =============== */
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(400.dp),
                            shape = PremiumTokens.CardShape,
                            colors = CardDefaults.cardColors(containerColor = PremiumTokens.SurfaceAlt),
                            elevation = PremiumTokens.cardElevation()
                        ) {
                            Column(
                                Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    "Filter Settings",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    color = PremiumTokens.Text
                                )

                                Spacer(Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    filters.forEach { filter ->
                                        Button(
                                            onClick = { selectedFilter = filter },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (selectedFilter == filter) PremiumTokens.Primary else PremiumTokens.Surface,
                                                contentColor = if (selectedFilter == filter) Color.White else PremiumTokens.Text
                                            ),
                                            elevation = ButtonDefaults.buttonElevation(
                                                defaultElevation = if (selectedFilter == filter) 8.dp else 2.dp,
                                                pressedElevation = 10.dp
                                            ),
                                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                                brush = Brush.linearGradient(listOf(PremiumTokens.Border, PremiumTokens.Border)),
                                                width = 1.dp
                                            ),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            if (selectedFilter == filter) {
                                                Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(filter, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                when (selectedFilter) {
                                    "SG" -> {
                                        Text("Savitzky–Golay Parameters", color = PremiumTokens.TextMuted, fontWeight = FontWeight.Medium)

                                        Spacer(Modifier.height(6.dp))
                                        Text("Window: $sgWindow", color = PremiumTokens.TextMuted, fontSize = 13.sp)
                                        Slider(
                                            value = sgWindow.toFloat(),
                                            onValueChange = { sgWindow = it.toInt().coerceIn(3, 301) },
                                            valueRange = 3f..301f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = PremiumTokens.Primary,
                                                activeTrackColor = PremiumTokens.Primary,
                                                inactiveTrackColor = PremiumTokens.PrimarySoft
                                            )
                                        )

                                        Spacer(Modifier.height(6.dp))
                                        Text("Order: $sgOrder", color = PremiumTokens.TextMuted, fontSize = 13.sp)
                                        Slider(
                                            value = sgOrder.toFloat(),
                                            onValueChange = { sgOrder = it.toInt().coerceIn(2, 10) },
                                            valueRange = 2f..10f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = PremiumTokens.Primary,
                                                activeTrackColor = PremiumTokens.Primary,
                                                inactiveTrackColor = PremiumTokens.PrimarySoft
                                            )
                                        )
                                    }

                                    "Kalman" -> {
                                        Text("Kalman Filter Parameters", color = PremiumTokens.TextMuted, fontWeight = FontWeight.Medium)

                                        Spacer(Modifier.height(6.dp))
                                        Text("Q (Process Noise): ${"%.4f".format(kalmanQ)}", color = PremiumTokens.TextMuted, fontSize = 13.sp)
                                        Slider(
                                            value = kalmanQ,
                                            onValueChange = { kalmanQ = it.coerceIn(1e-5f, 10f) },
                                            valueRange = 1e-5f..10f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = PremiumTokens.Primary,
                                                activeTrackColor = PremiumTokens.Primary,
                                                inactiveTrackColor = PremiumTokens.PrimarySoft
                                            )
                                        )

                                        Spacer(Modifier.height(6.dp))
                                        Text("R (Measurement Noise): ${"%.4f".format(kalmanR)}", color = PremiumTokens.TextMuted, fontSize = 13.sp)
                                        Slider(
                                            value = kalmanR,
                                            onValueChange = { kalmanR = it.coerceIn(1e-4f, 20f) },
                                            valueRange = 1e-4f..20f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = PremiumTokens.Primary,
                                                activeTrackColor = PremiumTokens.Primary,
                                                inactiveTrackColor = PremiumTokens.PrimarySoft
                                            )
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                Divider(color = PremiumTokens.Border.copy(alpha = 0.85f))
                                Spacer(Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Fringe Count (per repetition)",
                                        fontWeight = FontWeight.SemiBold,
                                        color = PremiumTokens.Text
                                    )

                                    Surface(
                                        color = PremiumTokens.PrimarySoft,
                                        contentColor = PremiumTokens.Primary,
                                        shape = RoundedCornerShape(999.dp),
                                        tonalElevation = 2.dp
                                    ) {
                                        Text(
                                            text = "latest n = $latestFringeCount",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                val listScroll = rememberScrollState()

                                if (fringeCountByRep.isEmpty()) {
                                    Text(
                                        "No repetition data yet.",
                                        color = PremiumTokens.TextMuted,
                                        fontSize = 12.sp
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 80.dp, max = 140.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(end = 10.dp)
                                                .verticalScroll(listScroll)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                fringeCountByRep.forEach { (rep, n) ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            "Repetition $rep",
                                                            color = PremiumTokens.TextMuted,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Surface(
                                                            color = PremiumTokens.Surface,
                                                            contentColor = PremiumTokens.Text,
                                                            shape = RoundedCornerShape(999.dp),
                                                            tonalElevation = 1.dp
                                                        ) {
                                                            Text(
                                                                text = "n = $n",
                                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        VerticalScrollbar(
                                            adapter = rememberScrollbarAdapter(listScroll),
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .fillMaxHeight()
                                                .width(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    /* =============== Settings & Start Cards =============== */

                    if (showAlertDialog) {
                        AlertDialog(
                            onDismissRequest = { showAlertDialog = false },
                            title = { Text("Invalid Input", color = PremiumTokens.Text) },
                            text = { Text("Input must be a number.", color = PremiumTokens.TextMuted) },
                            confirmButton = {
                                Button(
                                    onClick = { showAlertDialog = false },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PremiumTokens.Primary,
                                        contentColor = Color.White
                                    ),
                                    elevation = PremiumTokens.buttonElevation()
                                ) { Text("OK", fontWeight = FontWeight.SemiBold) }
                            },
                            containerColor = PremiumTokens.Surface,
                            tonalElevation = 10.dp
                        )
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = PremiumTokens.CardShape,
                        colors = CardDefaults.cardColors(containerColor = PremiumTokens.SurfaceAlt),
                        elevation = PremiumTokens.cardElevation()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Settings", fontWeight = FontWeight.SemiBold, color = PremiumTokens.Text)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = angle,
                                    onValueChange = { newValue ->
                                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d+$"))) {
                                            angle = newValue
                                        } else {
                                            showAlertDialog = true
                                        }
                                    },
                                    label = { Text(if (mode == "Rotasi") "Angle" else "Distance") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PremiumTokens.Primary,
                                        unfocusedBorderColor = PremiumTokens.Border,
                                        focusedLabelColor = PremiumTokens.Primary,
                                        unfocusedLabelColor = PremiumTokens.TextMuted,
                                        cursorColor = PremiumTokens.Primary,
                                        focusedTextColor = PremiumTokens.Text,
                                        unfocusedTextColor = PremiumTokens.Text
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                OutlinedTextField(
                                    value = speed,
                                    onValueChange = { newValue ->
                                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d+$"))) {
                                            speed = newValue
                                        } else {
                                            showAlertDialog = true
                                        }
                                    },
                                    label = { Text("Speed") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PremiumTokens.Primary,
                                        unfocusedBorderColor = PremiumTokens.Border,
                                        focusedLabelColor = PremiumTokens.Primary,
                                        unfocusedLabelColor = PremiumTokens.TextMuted,
                                        cursorColor = PremiumTokens.Primary,
                                        focusedTextColor = PremiumTokens.Text,
                                        unfocusedTextColor = PremiumTokens.Text
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                OutlinedTextField(
                                    value = repetitions,
                                    onValueChange = { newValue ->
                                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d+$"))) {
                                            repetitions = newValue
                                        } else {
                                            showAlertDialog = true
                                        }
                                    },
                                    label = { Text("Repetitions") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PremiumTokens.Primary,
                                        unfocusedBorderColor = PremiumTokens.Border,
                                        focusedLabelColor = PremiumTokens.Primary,
                                        unfocusedLabelColor = PremiumTokens.TextMuted,
                                        cursorColor = PremiumTokens.Primary,
                                        focusedTextColor = PremiumTokens.Text,
                                        unfocusedTextColor = PremiumTokens.Text
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = PremiumTokens.CardShape,
                        colors = CardDefaults.cardColors(containerColor = PremiumTokens.SurfaceAlt),
                        elevation = PremiumTokens.cardElevation()
                    ) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {

                                Button(
                                    onClick = {
                                        if (angle.toIntOrNull() == null && angle.isNotEmpty() ||
                                            speed.toIntOrNull() == null && speed.isNotEmpty() ||
                                            repetitions.toIntOrNull() == null && repetitions.isNotEmpty()
                                        ) {
                                            showAlertDialog = true
                                        } else if (connected) {
                                            runEpoch.incrementAndGet()
                                            sensorMessagesList.clear()

                                            rawBufByChannel.clear()
                                            filtBufByChannel.clear()
                                            nextSeqToProcess.clear()
                                            kalmanXByChannel.clear()
                                            kalmanPByChannel.clear()
                                            droppedFirstSampleRep1 = false

                                            savedForThisRun = false
                                            runDone = false
                                            savingCsv = false

                                            uiTick++
                                            procTick++

                                            showPlot = true
                                            val cmd =
                                                "Mode:$mode;${if (mode == "Rotasi") "Angle" else "Distance"}:$angle;" +
                                                        "Speed:$speed;Repetitions:$repetitions;START"
                                            MQTTClient.publish(cmd)
                                        }
                                    },
                                    modifier = Modifier.weight(2f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PremiumTokens.Primary,
                                        contentColor = Color.White
                                    ),
                                    elevation = PremiumTokens.buttonElevation(),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Start", fontWeight = FontWeight.SemiBold) }

                                val saveGreen = Color(0xFF16A34A)          // green-600
                                val saveGreenDisabled = Color(0xFF16A34A).copy(alpha = 0.45f)

                                Button(
                                    onClick = {
                                        if (!runDone || savedForThisRun || savingCsv) return@Button

                                        scope.launch {
                                            savingCsv = true
                                            try {
                                                val ok = runCatching {
                                                    awaitFilterCatchUp(
                                                        rawBufByChannel = rawBufByChannel,
                                                        nextSeqToProcess = nextSeqToProcess,
                                                        timeoutMs = 3000L,
                                                        pollMs = 25L
                                                    )
                                                }.getOrDefault(false)

                                                val result = runCatching {
                                                    exportCsvSnapshotToExperiments(
                                                        rawBufByChannel = rawBufByChannel,
                                                        filtBufByChannel = filtBufByChannel,
                                                        rawMax = RAW_CAP,
                                                        filtMax = FILT_CAP
                                                    )
                                                }

                                                result.onSuccess { file ->
                                                    sensorMessagesList.add(
                                                        if (ok) "CSV saved: ${file.absolutePath}"
                                                        else "CSV saved (catch-up timeout): ${file.absolutePath}"
                                                    )
                                                    if (sensorMessagesList.size > 200) sensorMessagesList.removeFirst()
                                                    savedForThisRun = true
                                                }.onFailure { e ->
                                                    sensorMessagesList.add("CSV FAILED: ${e::class.simpleName}: ${e.message}")
                                                    if (sensorMessagesList.size > 200) sensorMessagesList.removeFirst()
                                                }
                                            } finally {
                                                savingCsv = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = runDone && !savedForThisRun && !savingCsv,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = saveGreen,
                                        contentColor = Color.White,
                                        disabledContainerColor = saveGreenDisabled,
                                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                                    ),
                                    elevation = PremiumTokens.buttonElevation(),   // biar “tone” sama seperti Start
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (savingCsv) "Saving..." else "Save CSV", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ======================== FRINGE COUNT LOGIC ============================ */

private fun countFringesFromPeaks(values: List<Int>): Int {
    val peaks = detectPeaks(values)
    return (peaks.size - 1).coerceAtLeast(0)
}

private fun detectPeaks(values: List<Int>): List<Int> {
    if (values.size < 3) return emptyList()

    val minV = values.minOrNull() ?: return emptyList()
    val maxV = values.maxOrNull() ?: return emptyList()
    val range = (maxV - minV).toDouble()
    if (range < 1e-9) return emptyList()

    val minPeakDistance = 6
    val peakThreshold = minV + (0.70 * range)

    val peaks = ArrayList<Int>()
    var lastPeak = -10_000

    for (i in 1 until values.lastIndex) {
        val prev = values[i - 1].toDouble()
        val curr = values[i].toDouble()
        val next = values[i + 1].toDouble()

        val isLocalMax = (curr > prev && curr >= next) || (curr >= prev && curr > next)
        if (!isLocalMax) continue

        val heightOk = curr >= peakThreshold
        val distOk = (i - lastPeak) >= minPeakDistance

        if (heightOk && distOk) {
            peaks.add(i)
            lastPeak = i
        }
    }
    return peaks
}

private fun countFringesFromPeaksDouble(values: List<Double>): Int {
    val peaks = detectPeaksDouble(values)
    return (peaks.size - 1).coerceAtLeast(0)
}

private fun detectPeaksDouble(values: List<Double>): List<Int> {
    if (values.size < 3) return emptyList()

    val minV = values.minOrNull() ?: return emptyList()
    val maxV = values.maxOrNull() ?: return emptyList()
    val range = (maxV - minV)
    if (range < 1e-12) return emptyList()

    val minPeakDistance = 6
    val peakThreshold = minV + (0.70 * range)

    val peaks = ArrayList<Int>()
    var lastPeak = -10_000

    for (i in 1 until values.lastIndex) {
        val prev = values[i - 1]
        val curr = values[i]
        val next = values[i + 1]

        val isLocalMax = (curr > prev && curr >= next) || (curr >= prev && curr > next)
        if (!isLocalMax) continue

        val heightOk = curr >= peakThreshold
        val distOk = (i - lastPeak) >= minPeakDistance

        if (heightOk && distOk) {
            peaks.add(i)
            lastPeak = i
        }
    }
    return peaks
}

/* ======================== FILTER LOGIC (KEPT AS-IS) ============================ */

fun applyFilter(
    values: List<Int>,
    filterType: String,
    sgWindow: Int,
    sgOrder: Int,
    kalmanQ: Double,
    kalmanR: Double
): List<Int> =
    when (filterType) {
        "SG" -> savitzkyGolayFilterTrue(values, sgWindow, sgOrder)
        "Kalman" -> kalmanFilterBasic(values, kalmanQ, kalmanR)
        else -> values
    }

fun savitzkyGolayFilterTrue(values: List<Int>, window: Int, polyOrder: Int): List<Int> {
    if (values.isEmpty()) return values
    val w = if (window % 2 == 0) window + 1 else window
    val p = polyOrder
    val half = w / 2
    val coeffs = sgCoefficients(w, p)
    val out = DoubleArray(values.size)
    for (i in values.indices) {
        var acc = 0.0
        var idx = 0
        for (k in -half..half) {
            val j = (i + k).coerceIn(0, values.lastIndex)
            acc += coeffs[idx++] * values[j]
        }
        out[i] = acc
    }
    return out.map { it.roundToInt() }
}

private fun sgCoefficients(window: Int, order: Int): DoubleArray {
    val m = window / 2
    val cols = order + 1
    val A = Array(window) { r ->
        val k = r - m
        DoubleArray(cols) { c -> k.toDouble().pow(c) }
    }
    val ATA = Array(cols) { DoubleArray(cols) }
    for (i in 0 until cols) for (j in 0 until cols)
        ATA[i][j] = (0 until window).sumOf { A[it][i] * A[it][j] }
    val inv = invertMatrix(ATA)
    val AT = Array(cols) { c -> DoubleArray(window) { r -> A[r][c] } }
    val pinv = Array(cols) { DoubleArray(window) }
    for (i in 0 until cols) for (j in 0 until window)
        pinv[i][j] = (0 until cols).sumOf { inv[i][it] * AT[it][j] }
    return DoubleArray(window) { pinv[0][it] }
}

private fun invertMatrix(M: Array<DoubleArray>): Array<DoubleArray> {
    val n = M.size
    val A = Array(n) { M[it].clone() }
    val I = Array(n) { DoubleArray(n) { 0.0 } }
    for (i in 0 until n) I[i][i] = 1.0
    for (i in 0 until n) {
        var max = i
        for (k in i + 1 until n) if (abs(A[k][i]) > abs(A[max][i])) max = k
        val tmp = A[i]; A[i] = A[max]; A[max] = tmp
        val tmpI = I[i]; I[i] = I[max]; I[max] = tmpI
        val div = A[i][i]
        for (j in 0 until n) { A[i][j] /= div; I[i][j] /= div }
        for (k in 0 until n) if (k != i) {
            val f = A[k][i]
            for (j in 0 until n) { A[k][j] -= f * A[i][j]; I[k][j] -= f * I[i][j] }
        }
    }
    return I
}

fun kalmanFilterBasic(values: List<Int>, processNoise: Double, measurementNoise: Double): List<Int> {
    if (values.isEmpty()) return values
    val out = IntArray(values.size)
    var x = values.first().toDouble()
    var P = 1.0
    val Q = processNoise
    val R = measurementNoise
    for (i in values.indices) {
        val z = values[i].toDouble()
        val xPred = x
        val PPred = P + Q
        val K = PPred / (PPred + R)
        x = xPred + K * (z - xPred)
        P = (1 - K) * PPred
        out[i] = x.roundToInt()
    }
    return out.toList()
}