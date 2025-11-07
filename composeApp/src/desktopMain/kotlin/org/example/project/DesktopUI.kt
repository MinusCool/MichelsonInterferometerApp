package org.example.project

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.*
import java.io.File

/* ======================== MQTT + UI ============================ */

@Composable
fun HeaderSection(logo: Painter) {
    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp).aspectRatio(16f / 9f)
    ) {
        Image(
            painter = logo,
            contentDescription = "Header Image",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun StatusIndicator(connected: Boolean, onToggle: () -> Unit) {
    val text = if (connected) "Connected" else "Disconnected"
    val color = if (connected) Color(0xFF1565C0) else Color(0xFFE53935)
    Button(
        onClick = onToggle,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, color = Color.White)
    }
}

@Composable
fun ModeSelectionChipGroup(mode: String, onModeChange: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onModeChange("Linear") },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (mode == "Linear") Color(0xFF1565C0) else Color.White,
                contentColor = if (mode == "Linear") Color.White else Color.Black
            ),
            border = ButtonDefaults.outlinedButtonBorder,
            shape = RoundedCornerShape(16.dp)
        ) {
            if (mode == "Linear") {
                Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text("Linear")
        }
        Button(
            onClick = { onModeChange("Rotasi") },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (mode == "Rotasi") Color(0xFF1565C0) else Color.White,
                contentColor = if (mode == "Rotasi") Color.White else Color.Black
            ),
            border = ButtonDefaults.outlinedButtonBorder,
            shape = RoundedCornerShape(16.dp)
        ) {
            if (mode == "Rotasi") {
                Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text("Rotasi")
        }
    }
}

/* ======================== PLOT SECTION ============================ */

@Composable
fun DataPlotSection(channelMap: Map<Int, List<Int>>, filteredMap: Map<Int, List<Int>>) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.height(320.dp).verticalScroll(scrollState)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val sortedKeys = channelMap.keys.sorted()
            if (sortedKeys.isEmpty()) {
                Text(
                    "No data to display. Start the process to receive MQTT data.",
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 80.dp),
                    color = Color.Gray, fontSize = 14.sp
                )
            } else {
                for (key in sortedKeys) {
                    val values = channelMap[key] ?: emptyList()
                    val filteredValues = filteredMap[key] ?: emptyList()
                    Text("Repetition $key", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Canvas(
                        modifier = Modifier.fillMaxWidth().height(100.dp).padding(bottom = 12.dp)
                    ) {
                        if (values.isNotEmpty()) {
                            val maxVal = (values + filteredValues).maxOrNull()?.toFloat() ?: 100f
                            val minVal = (values + filteredValues).minOrNull()?.toFloat() ?: 0f
                            val rangeVal = max(1e-6f, maxVal - minVal)
                            val scaleY = size.height / rangeVal
                            val stepX = size.width / (values.size - 1).coerceAtLeast(1)

                            // Grid
                            val numHorizontalLines = 5
                            repeat(numHorizontalLines) {
                                val y = it * (size.height / (numHorizontalLines - 1))
                                drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, y), Offset(size.width, y), 1f)
                            }
                            val numVerticalLines = 10
                            val stepXGrid = size.width / (numVerticalLines - 1).coerceAtLeast(1)
                            repeat(numVerticalLines) {
                                val x = it * stepXGrid
                                drawLine(Color.Gray.copy(alpha = 0.3f), Offset(x, 0f), Offset(x, size.height), 1f)
                            }

                            // Raw line (cyan)
                            for (i in 0 until values.size - 1) {
                                val y1 = size.height - (values[i] - minVal) * scaleY
                                val y2 = size.height - (values[i + 1] - minVal) * scaleY
                                drawLine(Color.Cyan, Offset(i * stepX, y1), Offset((i + 1) * stepX, y2), 2f)
                            }

                            // Filtered line (orange)
                            for (i in 0 until filteredValues.size - 1) {
                                val y1 = size.height - (filteredValues[i] - minVal) * scaleY
                                val y2 = size.height - (filteredValues[i + 1] - minVal) * scaleY
                                drawLine(Color(0xFFFFA500), Offset(i * stepX, y1), Offset((i + 1) * stepX, y2), 2f)
                            }
                        }
                    }
                }
            }
        }
    }
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
    var channelMap by remember { mutableStateOf<MutableMap<Int, MutableList<Int>>>(mutableStateMapOf()) }
    var filteredMap by remember { mutableStateOf<MutableMap<Int, MutableList<Int>>>(mutableStateMapOf()) }
    var showAlertDialog by remember { mutableStateOf(false) }
    // Filter states
    var selectedFilter by remember { mutableStateOf("SG") }
    var sgWindow by remember { mutableStateOf(7) }
    var sgOrder by remember { mutableStateOf(2) }
    var kalmanQ by remember { mutableStateOf(0.01f) }
    var kalmanR by remember { mutableStateOf(1.0f) }

    val filters = listOf("SG", "Kalman")

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        MQTTClient.onMessageReceived = { message ->
            coroutineScope.launch(Dispatchers.Main) {
                if (!message.contains("Mode:") && !message.contains("START")) {
                    message.lines().forEach { line ->
                        val parts = line.split(":")
                        val channel = parts.getOrNull(0)?.toIntOrNull()
                        val value = parts.getOrNull(1)?.toIntOrNull()
                        if (channel != null && value != null) {
                            val list = channelMap.getOrPut(channel) { mutableStateListOf() }
                            list.add(value)
                            if (list.size > 100) list.removeFirst()
                            val filtered = applyFilter(
                                list, selectedFilter, sgWindow, sgOrder,
                                kalmanQ.toDouble(), kalmanR.toDouble()
                            )
                            filteredMap[channel] = filtered.toMutableList()
                            showPlot = true
                        }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFEAF3FF))) {
        HeaderSection(painterResource("interferometer_header.png"))

        Card(
            modifier = Modifier.fillMaxSize().offset(y = (-12).dp),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Card(
                            modifier = Modifier.fillMaxWidth().height(195.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFD6EAF8))
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("MQTT Messages", fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = sensorMessagesList.joinToString("\n"),
                                    onValueChange = {},
                                    modifier = Modifier.fillMaxWidth().height(190.dp),
                                    readOnly = true,
                                    singleLine = false
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFD6EAF8))
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Status", fontWeight = FontWeight.SemiBold)
                                StatusIndicator(connected) {
                                    if (connected) MQTTClient.disconnect() else MQTTClient.connect()
                                    connected = !connected
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFD6EAF8))
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Mode", fontWeight = FontWeight.SemiBold)
                                ModeSelectionChipGroup(mode) { newMode -> mode = newMode }
                            }
                        }
                    }
                    Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(16.dp))
                    {
                        Column(modifier = Modifier.weight(1f)) {
                            Card(
                                modifier = Modifier.fillMaxWidth().height(408.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFD6EAF8))
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Data Visualization", fontWeight = FontWeight.SemiBold)
                                    Button(
                                        onClick = { showPlot = !showPlot },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(if (showPlot) "Hide Plot" else "Show Plot", color = Color.White)
                                    }
                                    if (showPlot) {
                                        Spacer(Modifier.height(2.dp))
                                        DataPlotSection(channelMap, filteredMap)
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
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFD6EAF8))
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
                                color = Color.Black
                            )

                            Spacer(Modifier.height(8.dp))

                            // --- Tombol Pilihan Filter (gaya sama dengan Mode) ---
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                filters.forEach { filter ->
                                    Button(
                                        onClick = { selectedFilter = filter },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (selectedFilter == filter) Color(0xFF1565C0) else Color.White,
                                            contentColor = if (selectedFilter == filter) Color.White else Color(0xFF1565C0)
                                        ),
                                        border = ButtonDefaults.outlinedButtonBorder,
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        if (selectedFilter == filter) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Selected",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(filter)
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // --- Parameter Dinamis Berdasarkan Filter yang Dipilih ---
                            when (selectedFilter) {

                                /* Savitzky–Golay Filter */
                                "SG" -> {
                                    Text(
                                        "Savitzky–Golay Parameters",
                                        color = Color.DarkGray,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Spacer(Modifier.height(6.dp))
                                    Text("Window: $sgWindow", color = Color.Gray, fontSize = 13.sp)
                                    Slider(
                                        value = sgWindow.toFloat(),
                                        onValueChange = { sgWindow = it.toInt().coerceIn(3, 301) },
                                        valueRange = 3f..301f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF1565C0),
                                            activeTrackColor = Color(0xFF1565C0),
                                            inactiveTrackColor = Color(0xFF90CAF9)
                                        )
                                    )

                                    Spacer(Modifier.height(6.dp))
                                    Text("Order: $sgOrder", color = Color.Gray, fontSize = 13.sp)
                                    Slider(
                                        value = sgOrder.toFloat(),
                                        onValueChange = { sgOrder = it.toInt().coerceIn(2, 10) },
                                        valueRange = 2f..10f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF1565C0),
                                            activeTrackColor = Color(0xFF1565C0),
                                            inactiveTrackColor = Color(0xFF90CAF9)
                                        )
                                    )
                                }

                                /* Kalman Filter */
                                "Kalman" -> {
                                    Text(
                                        "Kalman Filter Parameters",
                                        color = Color.DarkGray,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Spacer(Modifier.height(6.dp))
                                    Text("Q (Process Noise): ${"%.4f".format(kalmanQ)}", color = Color.Gray, fontSize = 13.sp)
                                    Slider(
                                        value = kalmanQ,
                                        onValueChange = { kalmanQ = it.coerceIn(1e-5f, 10f) },
                                        valueRange = 1e-5f..10f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF1565C0),
                                            activeTrackColor = Color(0xFF1565C0),
                                            inactiveTrackColor = Color(0xFF90CAF9)
                                        )
                                    )

                                    Spacer(Modifier.height(6.dp))
                                    Text("R (Measurement Noise): ${"%.4f".format(kalmanR)}", color = Color.Gray, fontSize = 13.sp)
                                    Slider(
                                        value = kalmanR,
                                        onValueChange = { kalmanR = it.coerceIn(1e-4f, 20f) },
                                        valueRange = 1e-4f..20f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF1565C0),
                                            activeTrackColor = Color(0xFF1565C0),
                                            inactiveTrackColor = Color(0xFF90CAF9)
                                        )
                                    )
                                }
                            }
                        }
                    }
                    }


                /* =============== Settings & Start Cards =============== */

                // === Pop-up Warning Dialog ===
                if (showAlertDialog) {
                    AlertDialog(
                        onDismissRequest = { showAlertDialog = false },
                        title = { Text("Invalid Input", color = Color.White) },
                        text = { Text("Input must be a number.", color = Color.White) },
                        confirmButton = {
                            Button(
                                onClick = { showAlertDialog = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFFE53935)
                                )
                            ) { Text("OK") }
                        },
                        containerColor = Color(0xFFE53935)
                    )
                }

// === Settings Card ===
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD6EAF8))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Settings", fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = angle,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.matches(Regex("^\\d+\$"))) {
                                        angle = newValue
                                    } else {
                                        showAlertDialog = true
                                    }
                                },
                                label = { Text(if (mode == "Rotasi") "Angle" else "Distance") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = speed,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.matches(Regex("^\\d+\$"))) {
                                        speed = newValue
                                    } else {
                                        showAlertDialog = true
                                    }
                                },
                                label = { Text("Speed") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = repetitions,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.matches(Regex("^\\d+\$"))) {
                                        repetitions = newValue
                                    } else {
                                        showAlertDialog = true
                                    }
                                },
                                label = { Text("Repetitions") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }
                }

                // === Start Card ===
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD6EAF8))
                ) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                        Button(
                            onClick = {
                                if (angle.toIntOrNull() == null && angle.isNotEmpty() ||
                                    speed.toIntOrNull() == null && speed.isNotEmpty() ||
                                    repetitions.toIntOrNull() == null && repetitions.isNotEmpty()
                                ) {
                                    showAlertDialog = true
                                } else if (connected) {
                                    channelMap.clear()
                                    filteredMap.clear()
                                    showPlot = true
                                    val cmd = "Mode:$mode;${if (mode == "Rotasi") "Angle" else "Distance"}:$angle;" +
                                            "Speed:$speed;Repetitions:$repetitions;START"
                                    MQTTClient.publish(cmd)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Start", color = Color.White) }
                    }
                }

            }
        }
    }
}



/* ======================== FILTER LOGIC ============================ */

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
