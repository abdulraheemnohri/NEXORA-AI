package com.nexora.ai
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.nexora.ai.ui.NexoraHome

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFB9A2FF), secondary = Color(0xFF75D7E8),
                background = Color(0xFF0B0D16), surface = Color(0xFF151827)
            )) { NexoraHome() }
        }
    }
}
