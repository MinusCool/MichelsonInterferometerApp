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
private fun log2(x: Double) = ln(x) / ln(2.0)

/* ============================== UI ROOT ============================== */

@Composable
fun DesktopUI() {
    var channelMap by remember { mutableStateOf<Map<Int, List<Int>>>(emptyMap()) }
    var selectedFilter by remember { mutableStateOf("FIR") }
    var fileName by remember { mutableStateOf("No file selected") }

    // Toggles
    var showRaw by remember { mutableStateOf(true) }
    var showFiltered by remember { mutableStateOf(true) }

    // FIR params
    var firWindow by remember { mutableStateOf(5) }

    // Savitzky–Golay params (true LS; order adjustable)
    var sgWindow by remember { mutableStateOf(7) }
    var sgOrder by remember { mutableStateOf(2) }

    // Kalman params
    var kalmanQ by remember { mutableStateOf(0.01f) }
    var kalmanR by remember { mutableStateOf(1.0f) }

    // Wavelet (Haar) params
    var wavLevel by remember { mutableStateOf(2) }
    var wavThreshold by remember { mutableStateOf(10f) }

    // ==== NEW: Plot options ====
    var gridH by remember { mutableStateOf(5) }   // horizontal grid lines
    var gridV by remember { mutableStateOf(10) }  // vertical grid lines
    var xUnit by remember { mutableStateOf("Index") }
    var yUnit by remember { mutableStateOf("Amplitude") }

    val filters = listOf("FIR", "Savitzky-Golay", "Wavelet", "Kalman")

    fun enforceOdd(n: Int) = if (n % 2 == 0) n + 1 else n

    // Keep params valid
    LaunchedEffect(selectedFilter, sgWindow, sgOrder, firWindow) {
        firWindow = enforceOdd(clamp(firWindow, 3, 301))
        sgOrder = clamp(sgOrder, 2, 10)
        val minWinForOrder = max(3, sgOrder + 3) // window must be > order and odd
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
                "FIR" -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("FIR Parameters", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Window: $firWindow", color = Color.Gray)
                            Slider(
                                value = firWindow.toFloat(),
                                onValueChange = { firWindow = enforceOdd(clamp(it.toInt(), 3, 301)) },
                                valueRange = 3f..301f,
                                steps = (301 - 3) / 2,
                                modifier = Modifier.weight(1f).padding(start = 12.dp)
                            )
                        }
                    }
                }
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
                "Wavelet" -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Wavelet (Haar) Parameters", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Level: $wavLevel", color = Color.Gray)
                            Slider(
                                value = wavLevel.toFloat(),
                                onValueChange = { wavLevel = clamp(it.toInt(), 1, 8) },
                                valueRange = 1f..8f,
                                steps = 7,
                                modifier = Modifier.weight(1f).padding(start = 12.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Threshold: ${"%.1f".format(wavThreshold)}", color = Color.Gray)
                            Slider(
                                value = wavThreshold,
                                onValueChange = { wavThreshold = it.coerceIn(0f, 500f) },
                                valueRange = 0f..500f,
                                modifier = Modifier.weight(1f).padding(start = 12.dp)
                            )
                        }
                    }
                }
                "Kalman" -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Kalman Parameters", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Q: ${"%.4f".format(kalmanQ)}", color = Color.Gray)
                            Slider(
                                value = kalmanQ,
                                onValueChange = { kalmanQ = it.coerceIn(1e-4f, 10f) },
                                valueRange = 1e-4f..10f,
                                modifier = Modifier.weight(1f).padding(start = 12.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("R: ${"%.4f".format(kalmanR)}", color = Color.Gray)
                            Slider(
                                value = kalmanR,
                                onValueChange = { kalmanR = it.coerceIn(1e-3f, 20f) },
                                valueRange = 1e-3f..20f,
                                modifier = Modifier.weight(1f).padding(start = 12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ==== NEW: Plot Options (grid & units) ====
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
                firWindow = firWindow,
                sgWindow = sgWindow,
                sgOrder = sgOrder,
                kalmanQ = kalmanQ.toDouble(),
                kalmanR = kalmanR.toDouble(),
                wavLevel = wavLevel,
                wavThreshold = wavThreshold.toDouble(),
                // NEW:
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
    firWindow: Int,
    sgWindow: Int,
    sgOrder: Int,
    kalmanQ: Double,
    kalmanR: Double,
    wavLevel: Int,
    wavThreshold: Double,
    gridH: Int,
    gridV: Int,
    xUnit: String,
    yUnit: String,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .height(460.dp)
            .verticalScroll(scrollState)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val sortedKeys = channelMap.keys.sorted()
            if (sortedKeys.isEmpty()) {
                Text(
                    "No data to display. Choose a sample file first.",
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 80.dp),
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            } else {
                for (key in sortedKeys) {
                    val values = channelMap[key] ?: emptyList()
                    val filteredValues =
                        if (values.isNotEmpty() && showFiltered) {
                            applyFilter(
                                values, selectedFilter,
                                firWindow, sgWindow, sgOrder,
                                kalmanQ, kalmanR,
                                wavLevel, wavThreshold
                            )
                        } else null

                    // Title + legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Repetition $key", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).background(Color.Cyan))
                                Spacer(Modifier.width(6.dp))
                                Text("Raw", color = Color.White, fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).background(Color(0xFFFFA500)))
                                Spacer(Modifier.width(6.dp))
                                Text("Filtered", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp) // lebih tinggi utk label luar + unit
                            .padding(bottom = 12.dp)
                    ) {
                        if (values.isNotEmpty()) {
                            // ====== 1) Plot area margins ======
                            val marginLeft = 68f
                            val marginRight = 68f
                            val marginTop = 16f
                            val marginBottom = 36f

                            val plotLeft = marginLeft
                            val plotRight = size.width - marginRight
                            val plotTop = marginTop
                            val plotBottom = size.height - marginBottom
                            val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
                            val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)

                            // ====== 2) Data range & scaling ======
                            val maxFiltered = filteredValues?.maxOrNull()?.toFloat() ?: Float.NEGATIVE_INFINITY
                            val minFiltered = filteredValues?.minOrNull()?.toFloat() ?: Float.POSITIVE_INFINITY
                            val maxVal = max(values.maxOrNull()?.toFloat() ?: 100f, maxFiltered)
                            val minVal = min(values.minOrNull()?.toFloat() ?: 0f, minFiltered)
                            val rangeVal = max(1e-6f, maxVal - minVal)
                            val scaleY = plotHeight / rangeVal

                            fun xAt(i: Int, last: Int): Float {
                                val stepX = plotWidth / last.coerceAtLeast(1)
                                return plotLeft + i * stepX
                            }
                            fun yAt(v: Float): Float {
                                return plotTop + (plotHeight - (v - minVal) * scaleY)
                            }

                            // ====== 3) Grid di dalam plotRect ======
                            val h = gridH.coerceAtLeast(2)
                            for (i in 0 until h) {
                                val y = plotTop + (i * (plotHeight / (h - 1)))
                                drawLine(
                                    color = Color.Gray.copy(alpha = 0.3f),
                                    start = Offset(plotLeft, y),
                                    end = Offset(plotRight, y),
                                    strokeWidth = 1f
                                )
                            }
                            val v = gridV.coerceAtLeast(2)
                            for (i in 0 until v) {
                                val x = plotLeft + (i * (plotWidth / (v - 1)))
                                drawLine(
                                    color = Color.Gray.copy(alpha = 0.3f),
                                    start = Offset(x, plotTop),
                                    end = Offset(x, plotBottom),
                                    strokeWidth = 1f
                                )
                            }

                            // ====== 3.5) Border kotak ======
                            drawRect(
                                color = Color.Gray.copy(alpha = 0.5f),
                                topLeft = Offset(plotLeft, plotTop),
                                size = Size(plotWidth, plotHeight),
                                style = Stroke(width = 1f)
                            )

                            // ====== 4) Axis labels (Y ticks 5 nilai rata) DI LUAR ======
                            val labelStyle = TextStyle(color = Color.White, fontSize = 12.sp)

                            val ticksY = 5
                            for (i in 0 until ticksY) {
                                val value = minVal + i * (rangeVal / (ticksY - 1))
                                val y = yAt(value)
                                val text = "%.0f".format(value)
                                // kanan
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = text,
                                    style = labelStyle,
                                    topLeft = Offset(size.width - marginRight + 8f, (y - 6f).coerceIn(0f, size.height - 12.sp.toPx()))
                                )
                            }

                            // X kiri & kanan (index)
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "0",
                                style = labelStyle,
                                topLeft = Offset(plotLeft, plotBottom + 6f)
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "${values.lastIndex}",
                                style = labelStyle,
                                topLeft = Offset(plotRight - 28f, plotBottom + 6f)
                            )

                            // ====== 4.5) Unit labels ======
                            val unitStyle = TextStyle(color = Color.LightGray, fontSize = 12.sp)
                            // X unit di tengah bawah
                            drawText(
                                textMeasurer = textMeasurer,
                                text = xUnit,
                                style = unitStyle,
                                topLeft = Offset(
                                    x = plotLeft + (plotWidth / 2f) - (xUnit.length * 3.2f),
                                    y = plotBottom + 20f
                                )
                            )
                            // Y unit di kiri atas (tanpa rotasi agar simpel)
                            drawText(
                                textMeasurer = textMeasurer,
                                text = yUnit,
                                style = unitStyle,
                                topLeft = Offset(8f, plotTop - 10f)
                            )

                            // ====== 5) Gambar data (di dalam plotRect) ======
                            if (showRaw) {
                                for (i in 0 until values.size - 1) {
                                    val x1 = xAt(i, values.size - 1)
                                    val x2 = xAt(i + 1, values.size - 1)
                                    val y1 = yAt(values[i].toFloat())
                                    val y2 = yAt(values[i + 1].toFloat())
                                    drawLine(Color.Cyan, Offset(x1, y1), Offset(x2, y2), strokeWidth = 2f)
                                }
                            }

                            if (!filteredValues.isNullOrEmpty() && showFiltered) {
                                for (i in 0 until filteredValues.size - 1) {
                                    val x1 = xAt(i, filteredValues.size - 1)
                                    val x2 = xAt(i + 1, filteredValues.size - 1)
                                    val y1 = yAt(filteredValues[i].toFloat())
                                    val y2 = yAt(filteredValues[i + 1].toFloat())
                                    drawLine(Color(0xFFFFA500), Offset(x1, y1), Offset(x2, y2), strokeWidth = 2f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ============================== FILTERS & IO ============================== */

fun applyFilter(
    values: List<Int>,
    filterType: String,
    firWindow: Int,
    sgWindow: Int,
    sgOrder: Int,
    kalmanQ: Double,
    kalmanR: Double,
    wavLevel: Int,
    wavThreshold: Double
): List<Int> {
    return when (filterType) {
        "FIR" -> firFilter(values, firWindow)
        "Savitzky-Golay" -> savitzkyGolayFilterTrue(values, window = sgWindow, polyOrder = sgOrder)
        "Wavelet" -> waveletDenoiseHaar(values, levels = wavLevel, threshold = wavThreshold)
        "Kalman" -> kalmanFilter(values, processNoise = kalmanQ, measurementNoise = kalmanR)
        else -> values
    }
}

/* ---------- FIR (moving average) ---------- */
fun firFilter(values: List<Int>, window: Int = 5): List<Int> {
    if (values.isEmpty()) return values
    val w = if (window < 3) 3 else if (window % 2 == 0) window + 1 else window
    val half = w / 2
    val out = IntArray(values.size)
    for (i in values.indices) {
        val s = max(0, i - half)
        val e = min(values.lastIndex, i + half)
        var sum = 0L
        var cnt = 0
        for (j in s..e) { sum += values[j]; cnt++ }
        out[i] = (sum / cnt).toInt()
    }
    return out.toList()
}

/* ---------- Savitzky–Golay (true LS coefficients) ---------- */
fun savitzkyGolayFilterTrue(values: List<Int>, window: Int, polyOrder: Int): List<Int> {
    if (values.isEmpty()) return values
    var w = if (window % 2 == 0) window + 1 else window
    var p = max(0, polyOrder)
    val minWin = max(3, p + 3) // sederhana agar stabil
    if (w < minWin) w = if (minWin % 2 == 0) minWin + 1 else minWin

    val half = w / 2
    val coeffs = sgCoefficients(w, p) // koef smoothing pada pusat
    val out = DoubleArray(values.size)

    for (i in values.indices) {
        var acc = 0.0
        var kIndex = 0
        for (k in -half..half) {
            val idx = (i + k).coerceIn(0, values.lastIndex)
            acc += coeffs[kIndex++] * values[idx]
        }
        out[i] = acc
    }
    return out.map { it.roundToInt() }
}

// Hitung koefisien SG untuk smoothing (deriv=0) via pinv(A)
private fun sgCoefficients(window: Int, polyOrder: Int): DoubleArray {
    val m = window / 2
    val cols = polyOrder + 1
    // A: window x cols, A[r,c] = k^c, k = -m..m
    val A = Array(window) { r ->
        val k = r - m
        DoubleArray(cols) { c -> k.toDouble().pow(c.toDouble()) }
    }

    // ATA = A^T A
    val ATA = Array(cols) { DoubleArray(cols) { 0.0 } }
    for (i in 0 until cols) {
        for (j in 0 until cols) {
            var s = 0.0
            for (r in 0 until window) s += A[r][i] * A[r][j]
            ATA[i][j] = s
        }
    }

    // Invers ATA (Gauss-Jordan)
    val ATAinv = invertMatrix(ATA)

    // pinv = (A^T A)^(-1) A^T
    val AT = Array(cols) { c -> DoubleArray(window) { r -> A[r][c] } }
    val pinv = Array(cols) { DoubleArray(window) { 0.0 } }
    for (i in 0 until cols) {
        for (j in 0 until window) {
            var s = 0.0
            for (k in 0 until cols) s += ATAinv[i][k] * AT[k][j]
            pinv[i][j] = s
        }
    }

    // Koef smoothing = baris orde 0
    return DoubleArray(window) { j -> pinv[0][j] }
}

private fun invertMatrix(M: Array<DoubleArray>): Array<DoubleArray> {
    val n = M.size
    val A = Array(n) { i -> DoubleArray(n) { j -> M[i][j] } }
    val I = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } }

    for (col in 0 until n) {
        // pivot
        var pivot = col
        var maxAbs = abs(A[pivot][col])
        for (r in col + 1 until n) {
            val v = abs(A[r][col])
            if (v > maxAbs) { maxAbs = v; pivot = r }
        }
        require(maxAbs >= 1e-12) { "Matrix is singular" }

        // swap
        if (pivot != col) {
            val tmp = A[col]; A[col] = A[pivot]; A[pivot] = tmp
            val tmpI = I[col]; I[col] = I[pivot]; I[pivot] = tmpI
        }

        // normalize
        val diag = A[col][col]
        for (j in 0 until n) {
            A[col][j] /= diag
            I[col][j] /= diag
        }

        // eliminate
        for (r in 0 until n) if (r != col) {
            val factor = A[r][col]
            for (j in 0 until n) {
                A[r][j] -= factor * A[col][j]
                I[r][j] -= factor * I[col][j]
            }
        }
    }
    return I
}

/* ---------- Wavelet Haar DWT denoising (level + soft threshold) ---------- */
fun waveletDenoiseHaar(values: List<Int>, levels: Int, threshold: Double): List<Int> {
    if (values.isEmpty()) return values
    val x = values.map { it.toDouble() }.toMutableList()

    // pad ke power-of-two via edge padding
    val n = x.size
    val nPow2 = 1 shl (ceil(log2(n.toDouble())).toInt())
    if (nPow2 > n) {
        val last = x.last()
        repeat(nPow2 - n) { x.add(last) }
    }

    val maxLevels = floor(log2(x.size.toDouble())).toInt()
    val L = clamp(levels, 1, maxLevels)

    val sqrt2 = sqrt(2.0)
    var current = x.toDoubleArray()
    val detailsList = mutableListOf<DoubleArray>()
    var currLen = current.size

    repeat(L) {
        val nextLen = currLen / 2
        val avg = DoubleArray(nextLen)
        val det = DoubleArray(nextLen)
        var i = 0
        var j = 0
        while (i < currLen) {
            val s0 = current[i]
            val s1 = current[i + 1]
            avg[j] = (s0 + s1) / sqrt2
            det[j] = (s0 - s1) / sqrt2
            i += 2; j += 1
        }
        // soft threshold
        for (k in 0 until det.size) {
            val d = det[k]
            val mag = abs(d) - threshold
            det[k] = if (mag > 0) kotlin.math.sign(d) * mag else 0.0
        }
        detailsList.add(det)
        current = avg
        currLen = nextLen
    }

    // inverse
    var rec = current
    for (lvl in L - 1 downTo 0) {
        val det = detailsList[lvl]
        val out = DoubleArray(rec.size * 2)
        var j = 0
        for (k in rec.indices) {
            val a = rec[k]
            val d = det[k]
            out[j] = (a + d) / sqrt2
            out[j + 1] = (a - d) / sqrt2
            j += 2
        }
        rec = out
    }

    // crop to original length
    val result = rec.copyOf(values.size)
    return result.map { it.roundToInt() }
}

/* ---------- Kalman 1D ---------- */
fun kalmanFilter(values: List<Int>, processNoise: Double = 0.01, measurementNoise: Double = 1.0): List<Int> {
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

/* ---------- IO ---------- */
fun readSampleFileGrouped(file: File): Map<Int, List<Int>> {
    val map = mutableMapOf<Int, MutableList<Int>>()
    file.readLines().forEach { line ->
        val parts = line.split(":")
        val rep = parts.getOrNull(0)?.trim()?.toIntOrNull()
        val value = parts.getOrNull(1)?.trim()?.toIntOrNull()
        if (rep != null && value != null) {
            map.getOrPut(rep) { mutableListOf() }.add(value)
        }
    }
    return map
}
