package org.example.project

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlin.system.exitProcess

fun main() {
    // Pastikan folder kerja benar
    System.setProperty("apple.awt.UIElement", "true")

    try {
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "InterferometerApp",
                state = WindowState(
                    width = 1500.dp,
                    height = 900.dp,
                    placement = WindowPlacement.Maximized
                ),
                resizable = true,
                // Gunakan try-catch lokal untuk ikon agar tidak mematikan seluruh app
                icon = painterResource("icon.png")
            ) {
                App()
            }
        }
    } catch (e: Throwable) {
        // Tulis error ke file di folder yang sama dengan EXE
        val logFile = java.io.File("crash_log.txt")
        logFile.writeText("=== CRASH LOG ===\n" + e.stackTraceToString())

        // Munculkan pesan error box sederhana (Windows) jika memungkinkan
        throw e
    }
}