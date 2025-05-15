package org.example.project

//import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
//import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable
fun App() {
    MaterialTheme(
        colorScheme = lightColorScheme() // Material 3 Color Scheme
    ) {
        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        val keyboardController = LocalSoftwareKeyboardController.current

        var receivedMessage by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            MQTTClient.onMessageReceived = { message ->
                receivedMessage = message
            }
        }

        var mode by remember { mutableStateOf("Rotasi") }
        var angleOrDistance by remember { mutableStateOf("") }
        var speed by remember { mutableStateOf("") }
        var repetitions by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Disconnected") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.Start
        ) {
            // MQTT Messages Display
            Text("Received Data", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = receivedMessage,
                onValueChange = { },
                label = { Text("MQTT Messages") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .verticalScroll(rememberScrollState()),
                readOnly = true
            )
            Spacer(Modifier.height(24.dp))

            Spacer(Modifier.weight(1f))
            // Status Section
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

            // Mode Section
            Text("Mode", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ModeSelectionChipGroup(selectedMode = mode) { selected ->
                mode = selected
                angleOrDistance = ""
                speed = ""
                repetitions = ""
            }
//            DropdownMenuButton(mode, listOf("Linear", "Rotasi")) { selected ->
//                mode = selected
//                angleOrDistance = ""
//                speed = ""
//                repetitions = ""
//            }

            Spacer(Modifier.height(24.dp))

            // Settings Section
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
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = speed,
                    onValueChange = { speed = it },
                    label = { Text("Speed") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = repetitions,
                onValueChange = { repetitions = it },
                label = { Text("Repetitions") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                    keyboardController?.hide()
                })
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
                    contentColor = Color.White,
//                    disabledContainerColor = Color.White,
//                    disabledContentColor = Color.White
                    )
            ) {
                Text("START")
            }
        }
    }
}

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

@Composable
fun DropdownMenuButton(currentSelection: String, options: List<String>, onSelectionChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth(0.5f)) {
        Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(currentSelection)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelectionChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ModeSelectionChipGroup(
    selectedMode: String,
    onModeChange: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    .weight(1f)  // Membagi ruang menjadi 50%
                    .fillMaxWidth()
            )
        }
    }
}
