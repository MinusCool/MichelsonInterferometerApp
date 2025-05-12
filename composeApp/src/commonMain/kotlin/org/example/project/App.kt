package org.example.project

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Status: $status")

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                coroutineScope.launch {
                    if (status == "Disconnected") {
                        MQTTClient.connect()
                        status = "Connected"
                    } else {
                        MQTTClient.disconnect()
                        status = "Disconnected"
                    }
                }
            }) {
                Text(if (status == "Disconnected") "Connect" else "Disconnect")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Select Box Mode
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mode: ")
                Spacer(modifier = Modifier.width(8.dp))
                DropdownMenuButton(mode, listOf("Linear", "Rotasi")) { selected ->
                    mode = selected
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Input for Angle or Distance
            TextField(
                value = angleOrDistance,
                onValueChange = { angleOrDistance = it },
                label = { Text(if (mode == "Linear") "Distance" else "Angle") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Input for Speed
            TextField(
                value = speed,
                onValueChange = { speed = it },
                label = { Text("Speed") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Input for Repetitions
            TextField(
                value = repetitions,
                onValueChange = { repetitions = it },
                label = { Text("Repetitions") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Start Button (Kirim semua data sekaligus)
            Button(onClick = {
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
            }) { Text("Start") }
        }
    }
}

@Composable
fun DropdownMenuButton(currentSelection: String, options: List<String>, onSelectionChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Button(onClick = { expanded = true }) {
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
