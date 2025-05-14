package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable
fun App() {
    MaterialTheme {
        var mode by remember { mutableStateOf("Rotasi") }
        var angleOrDistance by remember { mutableStateOf("") }
        var speed by remember { mutableStateOf("") }
        var repetitions by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Disconnected") }

        val coroutineScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            // Status Section
            Spacer(Modifier.weight(1f))
            Text("Status", style = MaterialTheme.typography.h6)
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

            // Mode Section
            Text("Mode", style = MaterialTheme.typography.h6)
            Spacer(Modifier.height(8.dp))
            DropdownMenuButton(mode, listOf("Linear", "Rotasi")) { selected ->
                mode = selected
                angleOrDistance = ""
                speed = ""
                repetitions = ""
            }

            Spacer(Modifier.height(24.dp))
            // Settings Section
            Text("Settings", style = MaterialTheme.typography.h6)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextField(
                    value = angleOrDistance,
                    onValueChange = { angleOrDistance = it },
                    label = { Text(if (mode == "Linear") "Distance" else "Angle") },
                    modifier = Modifier.weight(1f)
                )
                TextField(
                    value = speed,
                    onValueChange = { speed = it },
                    label = { Text("Speed") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))
            TextField(
                value = repetitions,
                onValueChange = { repetitions = it },
                label = { Text("Repetitions") },
                modifier = Modifier.fillMaxWidth()
            )

            // Push Start Button to bottom
            //Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(24.dp))
            // Start Button at the bottom
            Button(
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("START")
            }
        }
    }
}

@Composable
fun StatusIndicator(status: String, onToggle: (String) -> Unit) {
    val isConnected = status == "Connected"
    val color = if (isConnected) Color.Green else Color.Red
    val displayText = if (isConnected) "Connected" else "Disconnected"

    Button(
        onClick = {
            val newStatus = if (isConnected) "Disconnected" else "Connected"
            onToggle(newStatus)
        },
        colors = ButtonDefaults.buttonColors(backgroundColor = color),
        modifier = Modifier.fillMaxWidth()  // Membuat tombol selebar layar
    ) {
        Text(displayText, color = Color.White)
    }
}


@Composable
fun DropdownMenuButton(currentSelection: String, options: List<String>, onSelectionChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth(0.5f)) {  // Box selebar setengah layar
        Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {  // Button isi penuh Box
            Text(currentSelection)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(onClick = {
                    onSelectionChange(option)
                    expanded = false
                }) {
                    Text(option)
                }
            }
        }
    }
}
