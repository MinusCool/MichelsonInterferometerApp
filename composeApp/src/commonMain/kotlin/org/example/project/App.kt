package org.example.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable
fun App() {
    MaterialTheme(
        colorScheme = lightColorScheme()
    ) {
        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        val keyboardController = LocalSoftwareKeyboardController.current

        var receivedMessage by remember { mutableStateOf("") }
        val channelMap = remember { mutableStateMapOf<Int, MutableList<Int>>() }
        var showNavigationPopup by remember { mutableStateOf(false) }

        var mode by remember { mutableStateOf("Rotasi") }
        var angleOrDistance by remember { mutableStateOf("") }
        var speed by remember { mutableStateOf("") }
        var repetitions by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Disconnected") }

        // Fokus state untuk animasi border
        var isFocused1 by remember { mutableStateOf(false) }
        var isFocused2 by remember { mutableStateOf(false) }
        var isFocused3 by remember { mutableStateOf(false) }

        val borderColor1 by animateColorAsState(if (isFocused1) Color(0xFF1976D2) else Color.Gray, label = "Focus1")
        val borderColor2 by animateColorAsState(if (isFocused2) Color(0xFF1976D2) else Color.Gray, label = "Focus2")
        val borderColor3 by animateColorAsState(if (isFocused3) Color(0xFF1976D2) else Color.Gray, label = "Focus3")
        val sensorMessages = remember { mutableStateListOf<String>() }

        LaunchedEffect(Unit) {
            MQTTClient.onMessageReceived = { message ->
                val isControlMessage = message.contains("Mode:") && message.contains("START")

                if (!isControlMessage) {
                    message.lines().forEach { line ->
                        val parts = line.split(":")
                        val channel = parts.getOrNull(0)?.toIntOrNull()
                        val value = parts.getOrNull(1)?.toIntOrNull()
                        if (channel != null && value != null) {
                            sensorMessages.add(line)
                            if (sensorMessages.size > 200) sensorMessages.removeFirst()

                            val list = channelMap.getOrPut(channel) { mutableListOf() }
                            list.add(value)
                            if (list.size > 100) list.removeFirst()
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.Start
            ) {
                val sensorLogText = sensorMessages.joinToString("\n")
                OutlinedTextField(
                    value = sensorLogText,
                    onValueChange = { },
                    label = { Text("MQTT Messages") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .verticalScroll(rememberScrollState()),
                    readOnly = true
                )

                Spacer(Modifier.height(16.dp))

                ElevatedButton(
                    onClick = { showNavigationPopup = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF03A9F4),
                        contentColor = Color.White
                    )
                ) {
                    Text("Buka Pop-up Navigasi")
                }

                Spacer(Modifier.height(24.dp))

                Spacer(Modifier.weight(1f))
                Text("Status", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                StatusIndicator(status) { newStatus ->
                    coroutineScope.launch {
                        if (newStatus == "Connected") {
                            MQTTClient.connect()
                        } else {
                            MQTTClient.disconnect()
                            angleOrDistance = ""
                            speed = ""
                            repetitions = ""
                        }
                        status = newStatus
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text("Mode", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ModeSelectionChipGroup(selectedMode = mode) { selected ->
                    mode = selected
                    angleOrDistance = ""
                    speed = ""
                    repetitions = ""
                }

                Spacer(Modifier.height(24.dp))

                Text("Settings", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                            unfocusedBorderColor = borderColor1
                        )
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
                            unfocusedBorderColor = borderColor2
                        )
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
                        coroutineScope.launch {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                        keyboardController?.hide()
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = borderColor3,
                        unfocusedBorderColor = borderColor3
                    )
                )

                Spacer(Modifier.height(24.dp))
                ElevatedButton(
                    onClick = {
                        coroutineScope.launch {
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
                        containerColor = Color(0x88fcba03),
                        contentColor = Color.White
                    )
                ) {
                    Text("START")
                }
            }

            // Animated pop-up overlay
            AnimatedVisibility(
                visible = showNavigationPopup,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NavigationOverlayPage(channelMap = channelMap, onBack = { showNavigationPopup = false })
            }
        }
    }
}

// Pop Up Page berisi line chart dari setiap repetitions yang diterima oleh aplikasi
@Composable
fun NavigationOverlayPage(
    channelMap: Map<Int, List<Int>>,
    onBack: () -> Unit
) {
    // Warna default per channel
    val channelColors = listOf(
        Color.Blue, Color.Red, Color.Green, Color.Magenta,
        Color.Cyan, Color.Yellow, Color.Gray, Color.Black,
        Color(0xFF8BC34A), Color(0xFFFF9800), Color(0xFF9C27B0)
    )

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.Start
        ) {
            Text("Halaman Navigasi", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            // Untuk setiap channel (repetitions), buat 1 chart
            channelMap.forEach { (channel, values) ->
                if (values.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Judul dan legend
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(channelColors[channel % channelColors.size])
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Channel $channel")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Canvas line chart
                    Canvas(modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)) {

                        val padding = 32f
                        val maxData = (values.maxOrNull() ?: 0).coerceAtLeast(1)
                        val xStep = if (values.size > 1) {
                            (size.width - 2 * padding) / (values.size - 1)
                        } else 1f
                        val yStep = (size.height - 2 * padding) / maxData

                        // Grid horizontal
                        val gridLines = 5
                        val yInterval = maxData / gridLines.toFloat()
                        for (i in 0..gridLines) {
                            val y = size.height - padding - (i * yInterval * yStep)
                            drawLine(
                                color = Color.LightGray,
                                start = Offset(padding, y),
                                end = Offset(size.width - padding, y),
                                strokeWidth = 2f
                            )
                        }

                        // Grid vertikal
                        for (i in values.indices) {
                            val x = padding + i * xStep
                            drawLine(
                                color = Color.LightGray,
                                start = Offset(x, padding),
                                end = Offset(x, size.height - padding),
                                strokeWidth = 2f
                            )
                        }

                        // Line Chart
                        val lineColor = channelColors[channel % channelColors.size]
                        for (i in 0 until values.size - 1) {
                            val x1 = padding + i * xStep
                            val y1 = size.height - padding - (values[i] * yStep)
                            val x2 = padding + (i + 1) * xStep
                            val y2 = size.height - padding - (values[i + 1] * yStep)
                            drawLine(
                                color = lineColor,
                                start = Offset(x1, y1),
                                end = Offset(x2, y2),
                                strokeWidth = 4f
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back")
            }
        }
    }
}


// Status Indicator berupa button
@Composable
fun StatusIndicator(status: String, onToggle: (String) -> Unit) {
    val isConnected = status == "Connected"
    val displayText = if (isConnected) "Connected" else "Disconnected"
    val baseColor = if (isConnected) Color.Green else Color.Red
    val semiTransparentColor = baseColor.copy(alpha = 0.5f)

    Button(
        onClick = {
            val newStatus = if (isConnected) "Disconnected" else "Connected"
            onToggle(newStatus)
        },
        colors = ButtonDefaults.buttonColors(containerColor = semiTransparentColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(displayText, color = Color.White)
    }
}

// Selection Chip Linear / Rotation
@Composable
fun ModeSelectionChipGroup(
    selectedMode: String,
    onModeChange: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Linear", "Rotasi").forEach { mode ->
            val isSelected = selectedMode == mode
            FilterChip(
                selected = isSelected,
                onClick = { onModeChange(mode) },
                label = { Text(mode) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Done,
                            contentDescription = "Selected",
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}
