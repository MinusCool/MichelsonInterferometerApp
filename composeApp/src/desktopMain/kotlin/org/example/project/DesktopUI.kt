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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.*
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar

import java.io.File

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
    selectedFilter: String,
    sgWindow: Int,
    sgOrder: Int,
    kalmanQ: Double,
    kalmanR: Double
) {
    // Apple-to-apple target: berapa sampel yang "muat" di 1 layar (mirip Code 1 yang sering terlihat seperti 100 titik terakhir)
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

                    // IMPORTANT: snapshot harus dibuat dari isi list (bukan remember(values)),
                    // supaya update in-place pada SnapshotStateList tetap kebaca dan plot ikut berubah.
                    val rawSnapshot = values.toList()

                    val filteredValues = remember(
                        rawSnapshot, selectedFilter, sgWindow, sgOrder, kalmanQ, kalmanR
                    ) {
                        if (rawSnapshot.isNotEmpty())
                            applyFilter(rawSnapshot, selectedFilter, sgWindow, sgOrder, kalmanQ, kalmanR)
                        else emptyList()
                    }

                    Text(
                        "Repetition $keyVal",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PremiumTokens.Text
                    )

                    key(keyVal) {
                        val hScroll = rememberScrollState()

                        // Total points untuk panjang plot (gabungan raw vs filtered)
                        val totalPoints = max(rawSnapshot.size, filteredValues.size).coerceAtLeast(2)

                        // Ambil lebar viewport (yang terlihat) supaya kita bisa bikin "fit-to-width" ala Code 1.
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        ) {
                            val viewportWidthDp = this.maxWidth

                            // dp per sample ditentukan dari "berapa sampel per layar" (N_VISIBLE)
                            val dpPerSample = (viewportWidthDp / (N_VISIBLE - 1).coerceAtLeast(1))

                            // Panjang total plot (biar bisa scroll sepanjang data)
                            val plotWidthDp = (dpPerSample * (totalPoints - 1))
                                .coerceAtLeast(viewportWidthDp)

                            // Auto-scroll ke kanan (data terbaru), biar perilaku lebih mirip Code 1 (yang biasanya nunjukin data terbaru)
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
                                        // X scaling (apple-to-apple): stepX berbasis "samples-per-screen", bukan 10.dp fixed
                                        val stepX = dpPerSample.toPx()

                                        // Y scaling: pakai range (benar) dan share untuk raw+filtered
                                        val dataForScale =
                                            if (filteredValues.isNotEmpty()) (rawSnapshot + filteredValues) else rawSnapshot

                                        if (dataForScale.size >= 2) {
                                            val maxVal = dataForScale.maxOrNull()?.toFloat() ?: 100f
                                            val minVal = dataForScale.minOrNull()?.toFloat() ?: 0f
                                            val rangeVal = max(1e-6f, maxVal - minVal)
                                            val scaleY = size.height / rangeVal

                                            // Grid
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

                                            // Raw line
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

                                            // Filtered line
                                            if (filteredValues.size >= 2) {
                                                val gold = Color(0xFFF59E0B)
                                                for (i in 0 until filteredValues.size - 1) {
                                                    val y1 = size.height - ((filteredValues[i].toFloat() - minVal) * scaleY)
                                                    val y2 = size.height - ((filteredValues[i + 1].toFloat() - minVal) * scaleY)
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

    // ===== Fringe Count PER repetition (ambil dari filteredMap) =====
    // ===== Fringe Count ADAPTIF: dihitung dari FILTERED values (per repetition) =====
    val filteredSeriesByRep by remember {
        derivedStateOf {
            channelMap.keys.sorted().associateWith { rep ->
                val raw = channelMap[rep]?.toList().orEmpty()
                if (raw.isNotEmpty()) {
                    applyFilter(
                        raw,
                        selectedFilter,
                        sgWindow,
                        sgOrder,
                        kalmanQ.toDouble(),
                        kalmanR.toDouble()
                    )
                } else emptyList()
            }
        }
    }

    val fringeCountByRep by remember {
        derivedStateOf {
            filteredSeriesByRep.mapValues { (_, filteredSeries) ->
                countFringesFromPeaks(filteredSeries)
            }
        }
    }

    val latestRep by remember { derivedStateOf { fringeCountByRep.keys.maxOrNull() } }
    val latestFringeCount by remember {
        derivedStateOf { latestRep?.let { fringeCountByRep[it] } ?: 0 }
    }

    var droppedFirstSampleRep1 by remember { mutableStateOf(false) }


    LaunchedEffect(Unit) {

        MQTTClient.onMessageReceived = { message ->
            coroutineScope.launch(Dispatchers.Main) {

                val isControlMessage = message.contains("Mode:") && message.contains("START")
                if (isControlMessage) return@launch

                message.lines().forEach { line ->
                    val parts = line.split(":")
                    val channel = parts.getOrNull(0)?.toIntOrNull()
                    val value = parts.getOrNull(1)?.toIntOrNull()

                    if (channel != null && value != null) {

                        sensorMessagesList.add(line)
                        if (sensorMessagesList.size > 200) sensorMessagesList.removeFirst()

                        if (channel == 1 && !droppedFirstSampleRep1) {
                            droppedFirstSampleRep1 = true
                            return@forEach
                        }

                        val list = channelMap.getOrPut(channel) { mutableStateListOf() }
                        list.add(value)
                        if (list.size > 2000) list.removeFirst()

                        val filtered = applyFilter(
                            list.toList(),
                            selectedFilter,
                            sgWindow,
                            sgOrder,
                            kalmanQ.toDouble(),
                            kalmanR.toDouble()
                        )
                        filteredMap[channel] = filtered.toMutableList()

                        showPlot = true
                    }
                }
            }
        }
    }





    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PremiumTokens.BgTop, PremiumTokens.BgBottom)))
    ) {
        HeaderSection(painterResource("interferometer_header.png"))

        // ✅ Box ini yang mengisi sisa tinggi, agar sheet putih full sampai bawah & bounded
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
                                        if (connected) MQTTClient.disconnect() else MQTTClient.connect()
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
                                                channelMap = channelMap,
                                                selectedFilter = selectedFilter,
                                                sgWindow = sgWindow,
                                                sgOrder = sgOrder,
                                                kalmanQ = kalmanQ.toDouble(),
                                                kalmanR = kalmanR.toDouble()
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

                                // ===== Fringe Count component (per repetition) =====
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
                                    // Container yang punya scroll + scrollbar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 80.dp, max = 140.dp) // tweak sesuai kebutuhan
                                    ) {
                                        // Area konten yang di-scroll
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(end = 10.dp) // space biar tidak ketutup scrollbar
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

                                        // Scrollbar Desktop
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
                                        droppedFirstSampleRep1 = false
                                        showPlot = true
                                        val cmd =
                                            "Mode:$mode;${if (mode == "Rotasi") "Angle" else "Distance"}:$angle;" +
                                                    "Speed:$speed;Repetitions:$repetitions;START"
                                        MQTTClient.publish(cmd)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PremiumTokens.Primary,
                                    contentColor = Color.White
                                ),
                                elevation = PremiumTokens.buttonElevation(),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Start", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
    }
}

/* ======================== FRINGE COUNT LOGIC ============================ */
/* Peak-to-peak: peak1=0, peak2=1 => fringe = peaks - 1 */

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

    // Untuk sinus halus, cara paling stabil:
    // - local maxima
    // - puncak harus berada di "bagian atas" amplitude (threshold)
    // - minimal distance antar puncak untuk hindari double count karena noise
    val minPeakDistance = 6
    val peakThreshold = minV + (0.70 * range)   // top 30% amplitude

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
