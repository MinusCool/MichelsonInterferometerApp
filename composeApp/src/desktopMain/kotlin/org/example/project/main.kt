package org.example.project

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlin.system.exitProcess

fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "InterferometerApp",
            state = WindowState(width = 1500.dp, height = 900.dp, placement = WindowPlacement.Maximized),
            resizable = true,
            icon = painterResource("icon.png")
        ) {
            App()
        }
    }
    exitProcess(0)
}