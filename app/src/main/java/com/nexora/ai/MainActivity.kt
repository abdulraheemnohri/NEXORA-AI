package com.nexora.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.nexora.ai.ui.NexoraHome
import com.nexora.ai.ui.NexoraViewModel
import com.nexora.ai.society.MasxViewModel
import com.nexora.ai.society.MasxHome
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private val nexoraViewModel: NexoraViewModel by viewModels()
    private val masxViewModel: MasxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFB9A2FF),
                secondary = Color(0xFF75D7E8),
                background = Color(0xFF0B0D16),
                surface = Color(0xFF151827)
            )) {
                var showSociety by rememberSaveable { mutableStateOf(false) }
                if (showSociety) {
                    MasxHome(masxViewModel, onBack = { showSociety = false })
                } else {
                    NexoraHome(nexoraViewModel, onOpenSociety = { showSociety = true })
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) nexoraViewModel.shutdown()
        super.onDestroy()
    }
}
