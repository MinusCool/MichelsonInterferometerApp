package org.example.project

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import javax.swing.JFileChooser
import kotlin.math.*

/* ---------- Helpers ---------- */
fun clamp(v: Int, lo: Int, hi: Int) = max(lo, min(hi, v))

/* ============================== UI ROOT ============================== */

@Composable
fun DesktopUI() {
    var channelMap by remember { mutableStateOf<Map<Int, List<Int>>>(emptyMap()) }
    var selectedFilter by remember { mutableStateOf("Savitzky-Golay") }
    var fileName by remember { mutableStateOf("No file selected") }

    // Toggles
    var showRaw by remember { mutableStateOf(true) }
    var showFiltered by remember { mutableStateOf(true) }

    // Savitzky–Golay params
    var sgWindow by remember { mutableStateOf(7) }
    var sgOrder by remember { mutableStateOf(2) }

    // Kalman params (Q,R langsung)
    var kalmanQ by remember { mutableStateOf(0.01f) }
    var kalmanR by remember { mutableStateOf(1.0f) }

    // Plot options
    var gridH by remember { mutableStateOf(5) }
    var gridV by remember { mutableStateOf(10) }
    var xUnit by remember { mutableStateOf("Index") }
    var yUnit by remember { mutableStateOf("Amplitude") }

    val filters = listOf("Savitzky-Golay", "Kalman")

    fun enforceOdd(n: Int) = if (n % 2 == 0) n + 1 else n

    // Keep params valid
    LaunchedEffect(selectedFilter, sgWindow, sgOrder) {
        sgOrder = clamp(sgOrder, 2, 10)
        val minWinForOrder = max(3, sgOrder + 3)
        sgWindow = enforceOdd(clamp(max(sgWindow, minWinForOrder), 3, 301))
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101010))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // File chooser
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    val chooser = JFileChooser().apply { dialogTitle = "Pilih file sample data" }
                    val result = chooser.showOpenDialog(null)
                    if (result == JFileChooser.APPROVE_OPTION) {
                        val file = chooser.selectedFile
                        fileName = file.name
                        channelMap = readSampleFileGrouped(file)
                    }
                }) { Text("Choose File") }

                Spacer(Modifier.width(12.dp))
                Text(fileName, color = Color.White)
            }

            Spacer(Modifier.height(16.dp))

            // Filter chips
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = showRaw, onCheckedChange = { showRaw = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Show Raw", color = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = showFiltered, onCheckedChange = { showFiltered = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Show Filtered", color = Color.White)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Parameter panel
            when (selectedFilter) {
                "Savitzky-Golay" -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Savitzky–Golay Parameters", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Window: $sgWindow", color = Color.Gray)
                            Slider(
                                value = sgWindow.toFloat(),
                                onValueChange = { v ->
                                    val vv = enforceOdd(clamp(v.toInt(), 3, 301))
                                    sgWindow = if (vv <= sgOrder + 1) enforceOdd(sgOrder + 3) else vv
                                },
                                valueRange = 3f..301f,
                                steps = (301 - 3) / 2,
                                modifier = Modifier.weight(1f).padding(start = 12.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Poly Order: $sgOrder", color = Color.Gray)
                            Slider(
                                value = sgOrder.toFloat(),
                                onValueChange = { v ->
                                    sgOrder = clamp(v.toInt(), 2, 10)
                                    if (sgWindow <= sgOrder + 1) sgWindow = enforceOdd(sgOrder + 3)
                                },
                                valueRange = 2f..10f,
                                steps = 8,
                                modifier = Modifier.weight(1f).padding(start = 12.dp)
                            )
                        }
                    }
                }
                "Kalman" -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Kalman Parameters", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Q (Process): ${"%.4f".format(kalmanQ)}", color = Color.Gray)
                            Slider(
                                value = kalmanQ,
                                onValueChange = { kalmanQ = it.coerceIn(1e-5f, 10f) },
                                valueRange = 1e-5f..10f,
                                modifier = Modifier.weight(1f).padding(start = 12.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("R (Measurement): ${"%.4f".format(kalmanR)}", color = Color.Gray)
                            Slider(
                                value = kalmanR,
                                onValueChange = { kalmanR = it.coerceIn(1e-4f, 20f) },
                                valueRange = 1e-4f..20f,
                                modifier = Modifier.weight(1f).padding(start = 12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Plot options
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Plot Options", color = Color.White, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Grid H: $gridH", color = Color.Gray)
                    Slider(
                        value = gridH.toFloat(),
                        onValueChange = { gridH = clamp(it.toInt(), 2, 12) },
                        valueRange = 2f..12f,
                        steps = 10,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Grid V: $gridV", color = Color.Gray)
                    Slider(
                        value = gridV.toFloat(),
                        onValueChange = { gridV = clamp(it.toInt(), 2, 20) },
                        valueRange = 2f..20f,
                        steps = 18,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = xUnit,
                        onValueChange = { xUnit = it },
                        label = { Text("X unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = yUnit,
                        onValueChange = { yUnit = it },
                        label = { Text("Y unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val textMeasurer = rememberTextMeasurer()
            DataPlotSectionWithFilter(
                channelMap = channelMap,
                selectedFilter = selectedFilter,
                showRaw = showRaw,
                showFiltered = showFiltered,
                sgWindow = sgWindow,
                sgOrder = sgOrder,
                kalmanQ = kalmanQ.toDouble(),
                kalmanR = kalmanR.toDouble(),
                gridH = gridH,
                gridV = gridV,
                xUnit = xUnit,
                yUnit = yUnit,
                textMeasurer = textMeasurer
            )
        }
    }
}

/* ============================== PLOT SECTION ============================== */

@Composable
fun DataPlotSectionWithFilter(
    channelMap: Map<Int, List<Int>>,
    selectedFilter: String,
    showRaw: Boolean,
    showFiltered: Boolean,
    sgWindow: Int,
    sgOrder: Int,
    kalmanQ: Double,
    kalmanR: Double,
    gridH: Int,
    gridV: Int,
    xUnit: String,
    yUnit: String,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.height(460.dp).verticalScroll(scrollState)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val sortedKeys = channelMap.keys.sorted()
            if (sortedKeys.isEmpty()) {
                Text("No data to display.", color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                for (key in sortedKeys) {
                    val values = channelMap[key] ?: emptyList()
                    val filteredValues =
                        if (values.isNotEmpty() && showFiltered)
                            applyFilter(values, selectedFilter, sgWindow, sgOrder, kalmanQ, kalmanR)
                        else null

                    // Title
                    Text("Repetition $key", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium)

                    Canvas(
                        modifier = Modifier.fillMaxWidth().height(200.dp).padding(bottom = 12.dp)
                    ) {
                        if (values.isNotEmpty()) {
                            val plotLeft = 60f
                            val plotRight = size.width - 60f
                            val plotTop = 20f
                            val plotBottom = size.height - 30f
                            val plotWidth = plotRight - plotLeft
                            val plotHeight = plotBottom - plotTop

                            val maxVal = values.maxOrNull()?.toFloat() ?: 1f
                            val minVal = values.minOrNull()?.toFloat() ?: 0f
                            val range = max(1e-6f, maxVal - minVal)
                            val scaleY = plotHeight / range

                            fun xAt(i: Int, last: Int) = plotLeft + i * (plotWidth / last.coerceAtLeast(1))
                            fun yAt(v: Float) = plotTop + (plotHeight - (v - minVal) * scaleY)

                            // Grid
                            for (i in 0 until gridH) {
                                val y = plotTop + (i * plotHeight / (gridH - 1))
                                drawLine(Color.Gray.copy(alpha = 0.3f), Offset(plotLeft, y), Offset(plotRight, y), 1f)
                            }
                            for (i in 0 until gridV) {
                                val x = plotLeft + (i * plotWidth / (gridV - 1))
                                drawLine(Color.Gray.copy(alpha = 0.3f), Offset(x, plotTop), Offset(x, plotBottom), 1f)
                            }

                            // Raw data
                            if (showRaw)
                                for (i in 0 until values.size - 1)
                                    drawLine(Color.Cyan, Offset(xAt(i, values.lastIndex), yAt(values[i].toFloat())),
                                        Offset(xAt(i + 1, values.lastIndex), yAt(values[i + 1].toFloat())), 2f)

                            // Filtered data
                            if (filteredValues != null)
                                for (i in 0 until filteredValues.size - 1)
                                    drawLine(Color(0xFFFFA500), Offset(xAt(i, filteredValues.lastIndex),
                                        yAt(filteredValues[i].toFloat())), Offset(xAt(i + 1, filteredValues.lastIndex),
                                        yAt(filteredValues[i + 1].toFloat())), 2f)
                        }
                    }
                }
            }
        }
    }
}

/* ============================== FILTERS ============================== */

fun applyFilter(
    values: List<Int>,
    filterType: String,
    sgWindow: Int,
    sgOrder: Int,
    kalmanQ: Double,
    kalmanR: Double
): List<Int> = when (filterType) {
    "Savitzky-Golay" -> savitzkyGolayFilterTrue(values, sgWindow, sgOrder)
    "Kalman" -> kalmanFilterBasic(values, processNoise = kalmanQ, measurementNoise = kalmanR)
    else -> values
}

/* ---------- Savitzky-Golay ---------- (unchanged) */
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
    val I = Array(n) { DoubleArray(n) { if (it == it) 0.0 else 0.0 } }
    for (i in 0 until n) I[i][i] = 1.0
    for (i in 0 until n) {
        var max = i
        for (k in i + 1 until n) if (abs(A[k][i]) > abs(A[max][i])) max = k
        val tmp = A[i]; A[i] = A[max]; A[max] = tmp
        val tmpI = I[i]; I[i] = I[max]; I[max] = tmpI
        val div = A[i][i]
        for (j in 0 until n) { A[i][j] /= div; I[i][j] /= div }
        for (k in 0 until n) if (k != i) {
            val factor = A[k][i]
            for (j in 0 until n) { A[k][j] -= factor * A[i][j]; I[k][j] -= factor * I[i][j] }
        }
    }
    return I
}

/* ---------- Simple 1D Kalman (F=H=1) ---------- */
fun kalmanFilterBasic(values: List<Int>, processNoise: Double, measurementNoise: Double): List<Int> {
    if (values.isEmpty()) return values
    val out = IntArray(values.size)
    var x = values.first().toDouble()
    var P = 1.0
    val Q = processNoise
    val R = measurementNoise

    for (i in values.indices) {
        // Predict
        val xPred = x
        val PPred = P + Q

        // Update
        val z = values[i].toDouble()
        val K = PPred / (PPred + R)
        x = xPred + K * (z - xPred)
        P = (1 - K) * PPred

        out[i] = x.roundToInt()
    }
    return out.toList()
}

/* ---------- IO ---------- */
fun readSampleFileGrouped(file: File): Map<Int, List<Int>> {
    val map = mutableMapOf<Int, MutableList<Int>>()
    file.readLines().forEach { line ->
        val parts = line.split(":")
        val rep = parts.getOrNull(0)?.trim()?.toIntOrNull()
        val value = parts.getOrNull(1)?.trim()?.toIntOrNull()
        if (rep != null && value != null)
            map.getOrPut(rep) { mutableListOf() }.add(value)
    }
    return map
}
