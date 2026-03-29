package org.example.project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import android.graphics.Color
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalView // Tambahkan import ini jika belum ada
import androidx.core.view.WindowCompat // Tambahkan import ini

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Baris penting untuk mengatur agar konten mengisi seluruh layar (termasuk di bawah Status Bar)
        // Ini memungkinkan gradasi header Anda terlihat penuh.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val view = LocalView.current
            if (!view.isInEditMode) {
                // Opsional: Untuk membuat Status Bar transparan agar gradasi terlihat lebih baik
                window.statusBarColor = Color.TRANSPARENT
                // Opsional: Untuk mengatur ikon Status Bar agar terlihat di atas warna terang/gelap header
                // WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true // Atau false
            }
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}