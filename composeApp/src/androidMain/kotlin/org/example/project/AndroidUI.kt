package org.example.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import interferometerapp.composeapp.generated.resources.Res
import interferometerapp.composeapp.generated.resources.interferometer_header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/* ======================== PREMIUM UI TOKENS (SAMA SEPERTI DESKTOP) ============================ */

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

/* ======================== ANDROID UI ============================ */

@Composable
fun AndroidUI() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PremiumTokens.Primary,
            onPrimary = Color.White,
            secondary = PremiumTokens.Accent,
            onSecondary = Color.White,
            background = PremiumTokens.BgTop,
            onBackground = PremiumTokens.Text,
            surface = PremiumTokens.Surface,
            onSurface = PremiumTokens.Text,
            primaryContainer = PremiumTokens.Primary,
            onPrimaryContainer = Color.White
        )
    ) {
        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        val keyboardController = LocalSoftwareKeyboardController.current

        var receivedMessage by remember { mutableStateOf("") } // keep
        val channelMap = remember { mutableStateMapOf<Int, MutableList<Int>>() } // keep type
        var showNavigationPopup by remember { mutableStateOf(false) }

        var mode by remember { mutableStateOf("Rotasi") }
        var angleOrDistance by remember { mutableStateOf("") }
        var speed by remember { mutableStateOf("") }
        var repetitions by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Disconnected") }

        var isFocused1 by remember { mutableStateOf(false) }
        var isFocused2 by remember { mutableStateOf(false) }
        var isFocused3 by remember { mutableStateOf(false) }

        val borderColor1 by androidx.compose.animation.animateColorAsState(
            if (isFocused1) PremiumTokens.Primary else PremiumTokens.Border, label = "Focus1"
        )
        val borderColor2 by androidx.compose.animation.animateColorAsState(
            if (isFocused2) PremiumTokens.Primary else PremiumTokens.Border, label = "Focus2"
        )
        val borderColor3 by androidx.compose.animation.animateColorAsState(
            if (isFocused3) PremiumTokens.Primary else PremiumTokens.Border, label = "Focus3"
        )

        val sensorMessages = remember { mutableStateListOf<String>() }
        var droppedFirstSampleRep1 by remember { mutableStateOf(false) } // sama seperti desktop

        // MQTT parsing: sama konsep desktop (update di Main)
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

                            // tampilkan SEMUA (tidak dipotong window); scroll yang menangani
                            sensorMessages.add(line)

                            // drop first sample rep1 (persis desktop)
                            if (channel == 1 && !droppedFirstSampleRep1) {
                                droppedFirstSampleRep1 = true
                                return@forEach
                            }

                            // simpan SEMUA sampel per repetition (persis desktop: cap besar 2000)
                            val list = channelMap.getOrPut(channel) { mutableStateListOf() }
                            list.add(value)
                            if (list.size > 2000) list.removeFirst()
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
            HeaderWithMicroscope()

            // sheet overlap sedikit ke header (offset negatif)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .offset(y = (-48).dp),
                shape = PremiumTokens.SheetShape,
                colors = CardDefaults.cardColors(containerColor = PremiumTokens.Surface),
                elevation = PremiumTokens.cardElevation()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(scrollState)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    // ===== MQTT Messages Card (scroll untuk lihat semua) =====
                    Card(
                        modifier = Modifier.fillMaxWidth(),
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

                            val msgScroll = rememberScrollState()
                            OutlinedTextField(
                                value = sensorMessages.joinToString("\n"),
                                onValueChange = { },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .verticalScroll(msgScroll),
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

                    // ===== Data Visualization Card (button) =====
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = PremiumTokens.CardShape,
                        colors = CardDefaults.cardColors(containerColor = PremiumTokens.SurfaceAlt),
                        elevation = PremiumTokens.cardElevation()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Data Visualization", fontWeight = FontWeight.SemiBold, color = PremiumTokens.Text)
                            Button(
                                onClick = { showNavigationPopup = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PremiumTokens.Primary,
                                    contentColor = Color.White
                                ),
                                elevation = PremiumTokens.buttonElevation(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Generate Data Visualization", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // ===== Status Card =====
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = PremiumTokens.CardShape,
                        colors = CardDefaults.cardColors(containerColor = PremiumTokens.SurfaceAlt),
                        elevation = PremiumTokens.cardElevation()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Status", fontWeight = FontWeight.SemiBold, color = PremiumTokens.Text)
                            StatusIndicatorPremium(status == "Connected") {
                                coroutineScope.launch {
                                    val newConnected = status != "Connected"
                                    if (newConnected) {
                                        MQTTClient.connect()
                                        status = "Connected"
                                    } else {
                                        MQTTClient.disconnect()
                                        angleOrDistance = ""
                                        speed = ""
                                        repetitions = ""
                                        status = "Disconnected"
                                    }
                                }
                            }
                        }
                    }

                    // ===== Mode Card =====
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = PremiumTokens.CardShape,
                        colors = CardDefaults.cardColors(containerColor = PremiumTokens.SurfaceAlt),
                        elevation = PremiumTokens.cardElevation()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Mode", fontWeight = FontWeight.SemiBold, color = PremiumTokens.Text)
                            ModeSelectionChipGroupPremium(mode = mode) { newMode ->
                                mode = newMode
                                angleOrDistance = ""
                                speed = ""
                                repetitions = ""
                            }
                        }
                    }

                    // ===== Settings + START Card =====
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = PremiumTokens.CardShape,
                        colors = CardDefaults.cardColors(containerColor = PremiumTokens.SurfaceAlt),
                        elevation = PremiumTokens.cardElevation()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Settings", fontWeight = FontWeight.SemiBold, color = PremiumTokens.Text)

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = angleOrDistance,
                                    onValueChange = { angleOrDistance = it },
                                    label = { Text(if (mode == "Linear") "Distance" else "Angle") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .onFocusChanged { isFocused1 = it.isFocused },
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Next,
                                        keyboardType = KeyboardType.Number
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = borderColor1,
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
                                    onValueChange = { speed = it },
                                    label = { Text("Speed") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .onFocusChanged { isFocused2 = it.isFocused },
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Next,
                                        keyboardType = KeyboardType.Number
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = borderColor2,
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

                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = repetitions,
                                onValueChange = { repetitions = it },
                                label = { Text("Repetitions") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isFocused3 = it.isFocused },
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done,
                                    keyboardType = KeyboardType.Number
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    coroutineScope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                                    keyboardController?.hide()
                                }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = borderColor3,
                                    unfocusedBorderColor = PremiumTokens.Border,
                                    focusedLabelColor = PremiumTokens.Primary,
                                    unfocusedLabelColor = PremiumTokens.TextMuted,
                                    cursorColor = PremiumTokens.Primary,
                                    focusedTextColor = PremiumTokens.Text,
                                    unfocusedTextColor = PremiumTokens.Text
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )

                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        sensorMessages.clear()
                                        channelMap.clear()
                                        droppedFirstSampleRep1 = false

                                        val dataMessage = buildString {
                                            append("Mode:$mode;")
                                            append("${if (mode == "Linear") "Distance" else "Angle"}:$angleOrDistance;")
                                            append("Speed:$speed;")
                                            append("Repetitions:$repetitions;")
                                            append("START")
                                        }
                                        MQTTClient.publish(dataMessage)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PremiumTokens.Primary,
                                    contentColor = Color.White
                                ),
                                elevation = PremiumTokens.buttonElevation(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("START", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = showNavigationPopup,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            NavigationOverlayPagePremium(
                channelMap = channelMap,
                onBack = { showNavigationPopup = false }
            )
        }
    }
}

/* ======================== OVERLAY PAGE (PREMIUM, SAMA SEPERTI DESKTOP) ============================ */

@Composable
fun NavigationOverlayPagePremium(
    channelMap: Map<Int, List<Int>>,
    onBack: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("SG") }
    var sgWindow by remember { mutableStateOf(7) }
    var sgOrder by remember { mutableStateOf(2) }
    var kalmanQ by remember { mutableStateOf(0.01f) }
    var kalmanR by remember { mutableStateOf(1.0f) }
    val filters = listOf("SG", "Kalman")

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
    val latestFringeCount by remember { derivedStateOf { latestRep?.let { fringeCountByRep[it] } ?: 0 } }

    val rootScroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PremiumTokens.BgTop, PremiumTokens.BgBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
                .padding(bottom = 88.dp)
                .verticalScroll(rootScroll),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ===== Data Visualization Card (plot ada di card yang sama; VERTICAL SCROLL untuk semua repetition) =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = PremiumTokens.CardShape,
                colors = CardDefaults.cardColors(containerColor = PremiumTokens.SurfaceAlt),
                elevation = PremiumTokens.cardElevation()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Data Visualization", fontWeight = FontWeight.SemiBold, color = PremiumTokens.Text)
                    Spacer(Modifier.height(10.dp))

                    val plotScroll = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp) // ~2 plot terlihat, sisanya scroll
                            .verticalScroll(plotScroll)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val sortedKeys = channelMap.keys.sorted()
                            if (sortedKeys.isEmpty()) {
                                Text(
                                    "No data to display. Start the process to receive MQTT data.",
                                    color = PremiumTokens.TextMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            } else {
                                sortedKeys.forEach { rep ->
                                    val raw = channelMap[rep]?.toList().orEmpty()
                                    val filtered = filteredSeriesByRep[rep].orEmpty()
                                    PlotCardPerRepetition(
                                        repetition = rep,
                                        rawValues = raw,
                                        filteredValues = filtered
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ===== Filter Settings + Fringe list =====
            Card(
                modifier = Modifier.fillMaxWidth(),
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

                    if (fringeCountByRep.isEmpty()) {
                        Text("No repetition data yet.", color = PremiumTokens.TextMuted, fontSize = 12.sp)
                    } else {
                        val listScroll = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp, max = 160.dp)
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
                    }
                }
            }
        }

        // Fixed Back button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PremiumTokens.Primary,
                    contentColor = Color.White
                ),
                elevation = PremiumTokens.buttonElevation(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = "")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/* ======================== PLOT CARD PER REP (RAW + FILTERED, H-SCROLL) ============================ */

@Composable
private fun PlotCardPerRepetition(
    repetition: Int,
    rawValues: List<Int>,
    filteredValues: List<Int>
) {
    val N_VISIBLE = 100

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PremiumTokens.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Repetition $repetition",
                fontWeight = FontWeight.SemiBold,
                color = PremiumTokens.Text,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(8.dp))

            val rawSnapshot = rawValues.toList()
            val filteredSnapshot = filteredValues.toList()
            val totalPoints = max(rawSnapshot.size, filteredSnapshot.size).coerceAtLeast(2)

            if (totalPoints < 2) {
                Text("No data.", color = PremiumTokens.TextMuted, fontSize = 12.sp)
                return@Column
            }

            val hScroll = rememberScrollState()
            LaunchedEffect(totalPoints) { hScroll.scrollTo(hScroll.maxValue) }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val viewportWidthDp = this.maxWidth
                val dpPerSample = (viewportWidthDp / (N_VISIBLE - 1).coerceAtLeast(1))
                val plotWidthDp = (dpPerSample * (totalPoints - 1)).coerceAtLeast(viewportWidthDp)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(hScroll)
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(plotWidthDp)
                            .fillMaxHeight()
                            .padding(bottom = 8.dp)
                    ) {
                        val stepX = dpPerSample.toPx()

                        val dataForScale =
                            if (filteredSnapshot.isNotEmpty()) (rawSnapshot + filteredSnapshot) else rawSnapshot

                        if (dataForScale.size >= 2) {
                            val maxVal = (dataForScale.maxOrNull() ?: 1).toFloat()
                            val minVal = (dataForScale.minOrNull() ?: 0).toFloat()
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
            }
        }
    }
}

/* ======================== PREMIUM STATUS + MODE ============================ */

@Composable
private fun StatusIndicatorPremium(connected: Boolean, onToggle: () -> Unit) {
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
private fun ModeSelectionChipGroupPremium(
    mode: String,
    onModeChange: (String) -> Unit
) {
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

/* ======================== HEADER ============================ */

@Composable
fun HeaderWithMicroscope() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .statusBarsPadding()
    ) {
        Image(
            painter = painterResource(Res.drawable.interferometer_header),
            contentDescription = "Header Illustration",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.14f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.06f)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        radius = 900f
                    )
                )
        )
    }
}

/* ======================== FRINGE COUNT LOGIC (SAMA PERSIS DESKTOP) ============================ */

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

/* ======================== FILTER LOGIC (SAMA PERSIS DESKTOP) ============================ */

private fun applyFilter(
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

private fun savitzkyGolayFilterTrue(values: List<Int>, window: Int, polyOrder: Int): List<Int> {
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
        var maxRow = i
        for (k in i + 1 until n) if (abs(A[k][i]) > abs(A[maxRow][i])) maxRow = k
        val tmp = A[i]; A[i] = A[maxRow]; A[maxRow] = tmp
        val tmpI = I[i]; I[i] = I[maxRow]; I[maxRow] = tmpI
        val div = A[i][i]
        for (j in 0 until n) { A[i][j] /= div; I[i][j] /= div }
        for (k in 0 until n) if (k != i) {
            val f = A[k][i]
            for (j in 0 until n) { A[k][j] -= f * A[i][j]; I[k][j] -= f * I[i][j] }
        }
    }
    return I
}

private fun kalmanFilterBasic(values: List<Int>, processNoise: Double, measurementNoise: Double): List<Int> {
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
