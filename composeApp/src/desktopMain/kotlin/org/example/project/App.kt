package org.example.project

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset

@Composable
actual fun App() {
    DesktopUI()
}

@Composable
fun DesktopUI() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF3E64FF),
            onPrimary = Color.White,
            secondary = Color(0xFF03A9F4),
            onSecondary = Color.White,
            background = Color(0xFFF3F6FD),
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            primaryContainer = Color(0xFF3E64FF),
            onPrimaryContainer = Color.White
        )
    ) {
        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()

        val channelMap = remember { mutableStateMapOf<Int, MutableList<Int>>() }
        val sensorMessages = remember { mutableStateListOf<String>() }

        var mode by remember { mutableStateOf("Rotasi") }
        var angleOrDistance by remember { mutableStateOf("") }
        var speed by remember { mutableStateOf("") }
        var repetitions by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Disconnected") }

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

        Surface(
            modifier = Modifier.fillMaxSize().padding(32.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                verticalArrangement = Arrangement.Top
            ) {
                OutlinedTextField(
                    value = sensorMessages.joinToString("\n"),
                    onValueChange = { },
                    label = { Text("MQTT Messages") },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    readOnly = true
                )

                Spacer(Modifier.height(16.dp))

                Button(onClick = {
                    coroutineScope.launch {
                        sensorMessages.clear()
                        channelMap.clear()
                        val dataMessage = buildString {
                            append("Mode:$mode;")
                            append("${if (mode == "Linear") "Distance" else "Angle"}:$angleOrDistance;")
                            append("Speed:$speed;")
                            append("Repetitions:$repetitions;")
                            append("START")
                        }
                        MQTTClient.publish(dataMessage)
                    }
                }) {
                    Text("START")
                }

                Spacer(Modifier.height(16.dp))

                Text("Status: $status")
                Button(onClick = {
                    coroutineScope.launch {
                        if (status == "Connected") {
                            MQTTClient.disconnect()
                            status = "Disconnected"
                        } else {
                            MQTTClient.connect()
                            status = "Connected"
                        }
                    }
                }) {
                    Text(if (status == "Connected") "Disconnect" else "Connect")
                }

                Spacer(Modifier.height(16.dp))

                Text("Mode")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Linear", "Rotasi").forEach {
                        Button(onClick = {
                            mode = it
                            angleOrDistance = ""
                            speed = ""
                            repetitions = ""
                        }) {
                            Text(it)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = angleOrDistance,
                    onValueChange = { angleOrDistance = it },
                    label = { Text(if (mode == "Linear") "Distance" else "Angle") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = speed,
                    onValueChange = { speed = it },
                    label = { Text("Speed") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = repetitions,
                    onValueChange = { repetitions = it },
                    label = { Text("Repetitions") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(32.dp))
                Text("Visualization")
                channelMap.forEach { (channel, values) ->
                    Text("Repetition $channel")
                    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        val padding = 32f
                        val maxData = (values.maxOrNull() ?: 0).coerceAtLeast(1)
                        val xStep = if (values.size > 1) (size.width - 2 * padding) / (values.size - 1) else 1f
                        val yStep = (size.height - 2 * padding) / maxData
                        for (i in 0 until values.size - 1) {
                            val x1 = padding + i * xStep
                            val y1 = size.height - padding - (values[i] * yStep)
                            val x2 = padding + (i + 1) * xStep
                            val y2 = size.height - padding - (values[i + 1] * yStep)
                            drawLine(
                                color = Color.Blue,
                                start = Offset(x1, y1),
                                end = Offset(x2, y2),
                                strokeWidth = 4f
                            )
                        }
                    }
                }
            }
        }
    }
}