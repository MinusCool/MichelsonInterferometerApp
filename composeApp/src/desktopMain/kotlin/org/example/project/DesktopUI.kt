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
import kotlin.random.Random
import androidx.compose.ui.geometry.Offset


@Composable
fun HeaderSection(logo: Painter) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .aspectRatio(16f/9f)
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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

@Composable
fun DataPlotSection(channelMap: Map<Int, List<Int>>) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .height(320.dp)
            .verticalScroll(scrollState)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val sortedKeys = channelMap.keys.sorted()
            if (sortedKeys.isEmpty()) { // Tambahkan kondisi untuk menampilkan pesan jika tidak ada data
                Text(
                    "No data to display. Start the process to receive MQTT data.",
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 80.dp),
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            } else {
                for (key in sortedKeys) {
                    val values = channelMap[key] ?: emptyList()
                    Text("Repetition $key", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        if (values.isNotEmpty()) {
                            val maxVal = values.maxOrNull()?.toFloat() ?: 100f
                            val minVal = values.minOrNull()?.toFloat() ?: 0f
                            val rangeVal = maxVal - minVal
                            val normalizedMaxVal = if (rangeVal == 0f) maxVal + 10 else maxVal
                            val scaleY = size.height / normalizedMaxVal

                            // Draw horizontal grid
                            val numHorizontalLines = 5
                            for (i in 0 until numHorizontalLines) {
                                val y = size.height - (i * (size.height / (numHorizontalLines - 1)))
                                drawLine(
                                    color = Color.Gray.copy(alpha = 0.3f),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1f
                                )
                            }

                            // Draw vertical grid
                            val numVerticalLines = 10
                            val stepXGrid = size.width / (numVerticalLines - 1).coerceAtLeast(1)
                            for (i in 0 until numVerticalLines) {
                                val x = i * stepXGrid
                                drawLine(
                                    color = Color.Gray.copy(alpha = 0.3f),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1f
                                )
                            }

                            // Draw data lines
                            val stepX = size.width / (values.size - 1).coerceAtLeast(1)
                            for (i in 0 until values.size - 1) {
                                val y1 = size.height - (values[i] - minVal) * scaleY
                                val y2 = size.height - (values[i + 1] - minVal) * scaleY
                                drawLine(
                                    color = Color.Blue,
                                    start = Offset(i * stepX, y1),
                                    end = Offset((i + 1) * stepX, y2),
                                    strokeWidth = 2f
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


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

    var showAlertDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        MQTTClient.onMessageReceived = { message ->
            coroutineScope.launch(Dispatchers.Main) {
                val isControlMessage = message.contains("Mode:") && message.contains("START")
                if (!isControlMessage) {
                    message.lines().forEach { line ->
                        val parts = line.split(":")
                        val channel = parts.getOrNull(0)?.toIntOrNull()
                        val value = parts.getOrNull(1)?.toIntOrNull()

                        if (channel != null && value != null) {
                            sensorMessagesList.add(line)
                            if (sensorMessagesList.size > 200) {
                                sensorMessagesList.removeFirst()
                            }

                            val list = channelMap.getOrPut(channel) { mutableStateListOf() }
                            list.add(value)
                            if (list.size > 100) {
                                list.removeFirst()
                            }
                            showPlot = true
                        }
                    }
                }
            }
        }
    }

    // AlertDialog Composable
    if (showAlertDialog) {
        AlertDialog(
            onDismissRequest = { showAlertDialog = false },
            title = {
                Text(
                    text = "Invalid Input",
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Input must be a number.",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = { showAlertDialog = false },

                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Red
                    )
                ) {
                    Text("OK")
                }
            },
            // --- BAGIAN UTAMA UNTUK MENGUBAH BACKGROUND DIALOG ---
            containerColor = Color(0xFFE53935),

        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEAF3FF))
    ) {
        HeaderSection(painterResource("interferometer_header.png"))

        Card(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = (-12).dp),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Card(modifier = Modifier.fillMaxWidth().height(195.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFD6EAF8))) {
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

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFD6EAF8))) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Status", fontWeight = FontWeight.SemiBold)
                                StatusIndicator(connected) {
                                    if (connected) {
                                        MQTTClient.disconnect()
                                    } else {
                                        MQTTClient.connect()
                                    }
                                    connected = !connected
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFD6EAF8))) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Mode", fontWeight = FontWeight.SemiBold)
                                ModeSelectionChipGroup(mode) { newMode ->
                                    if (mode != newMode) {
                                        mode = newMode
                                        angle = ""
                                        speed = ""
                                        repetitions = ""
                                    }
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Card(modifier = Modifier
                            .fillMaxWidth()
                            .height(408.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFD6EAF8))) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Data Visualization", fontWeight = FontWeight.SemiBold)
                                Button(
                                    onClick = {
                                        showPlot = !showPlot // Toggle visibilitas plot
                                        if (!showPlot) { // Jika plot disembunyikan, hapus datanya
                                            channelMap.clear()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (showPlot) "Hide Plot" else "Show Plot", color = Color.White)
                                }
                                if (showPlot) {
                                    Spacer(Modifier.height(2.dp))
                                    DataPlotSection(channelMap)
                                }
                            }
                        }
                    }
                }

                // Settings
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFD6EAF8))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Settings", fontWeight = FontWeight.SemiBold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                // Start
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFD6EAF8))) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                        Button(
                            onClick = {
                                if (angle.toIntOrNull() == null && angle.isNotEmpty() ||
                                    speed.toIntOrNull() == null && speed.isNotEmpty() ||
                                    repetitions.toIntOrNull() == null && repetitions.isNotEmpty()) {
                                    showAlertDialog = true
                                } else if (connected) {
                                    sensorMessagesList.clear()
                                    channelMap.clear() // Hapus data plot saat Start ditekan
                                    showPlot = true // Pastikan plot terlihat ketika proses dimulai

                                    val command = "Mode:$mode;${if (mode == "Rotasi") "Angle" else "Distance"}:$angle;Speed:$speed;Repetitions:$repetitions;START"
                                    MQTTClient.publish(command)
                                } else {
                                    println("Not connected to MQTT broker.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Start", color = Color.White)
                        }

                    }
                }
            }
        }
    }
}