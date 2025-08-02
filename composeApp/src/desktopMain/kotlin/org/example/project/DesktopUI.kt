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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import javax.swing.JFileChooser

@Composable
fun DesktopUI() {
    var channelMap by remember { mutableStateOf<Map<Int, List<Int>>>(emptyMap()) }
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf("No file selected") }

    val filters = listOf("FIR", "Savitzky-Golay", "Wavelet", "Kalman")

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101010))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tombol Pilih File
            Button(onClick = {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Pilih file sample data"
                }
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile
                    fileName = file.name
                    channelMap = readSampleFileGrouped(file)
                    println("DEBUG: ${channelMap.size} channels loaded")
                }
            }) {
                Text("Choose File")
            }

            Spacer(Modifier.height(8.dp))
            Text(fileName, color = Color.White)

            Spacer(Modifier.height(16.dp))

            // Plot Section
            DataPlotSection(channelMap)

            Spacer(Modifier.height(24.dp))

            // ChipGroup Filter
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }
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
            if (sortedKeys.isEmpty()) {
                Text(
                    "No data to display. Choose a sample file first.",
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 80.dp),
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            } else {
                for (key in sortedKeys) {
                    val values = channelMap[key] ?: emptyList()
                    Text(
                        "Repetition $key",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
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

                            // Horizontal grid
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

                            // Vertical grid
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

                            // Data line
                            val stepX = size.width / (values.size - 1).coerceAtLeast(1)
                            for (i in 0 until values.size - 1) {
                                val y1 = size.height - (values[i] - minVal) * scaleY
                                val y2 = size.height - (values[i + 1] - minVal) * scaleY
                                drawLine(
                                    color = Color.Cyan,
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

// Fungsi baca file dan kelompokkan berdasarkan repetition
fun readSampleFileGrouped(file: File): Map<Int, List<Int>> {
    val map = mutableMapOf<Int, MutableList<Int>>()
    file.readLines().forEach { line ->
        val parts = line.split(":")
        val rep = parts.getOrNull(0)?.trim()?.toIntOrNull()
        val value = parts.getOrNull(1)?.trim()?.toIntOrNull()
        if (rep != null && value != null) {
            val list = map.getOrPut(rep) { mutableListOf() }
            list.add(value)
        }
    }
    return map
}
