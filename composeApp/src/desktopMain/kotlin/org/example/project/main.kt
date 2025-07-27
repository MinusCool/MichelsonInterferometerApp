package org.example.project

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.res.painterResource


fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "InterferometerApp" ,
        state = WindowState(width = 800.dp, height = 850.dp),
        resizable = false,
        icon = painterResource("icon.png")
    ) {
        App()
    }
}