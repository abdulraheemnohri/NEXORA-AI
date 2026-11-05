package com.nexora.ai.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NexoraHome() {
    Surface(Modifier.fillMaxSize(), color = Color(0xFF0B0D16)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("NEXORA AI", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("Think locally. Learn continuously. Act intelligently.",
                    color = Color(0xFFB9B4CA), style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D30))) {
                    Column(Modifier.padding(18.dp)) {
                        Text("LOCAL INTELLIGENCE", color = Color(0xFF75D7E8), fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Gemma 4 E2B · LiteRT-LM", color = Color.White,
                            style = MaterialTheme.typography.titleLarge)
                        Text("Runtime integration is being brought online. No simulated AI responses are shown.",
                            color = Color(0xFFB9B4CA), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Text("WORKSPACE", color = Color(0xFFB9A2FF), style = MaterialTheme.typography.titleMedium) }
            item { FeatureCard("Chat & Thinking", "Local conversation, streaming generation and context control") }
            item { FeatureCard("Autonomous Agent", "Goal planning, permission-gated tools and execution history") }
            item { FeatureCard("Memory & Knowledge", "Private local memories, documents and searchable knowledge") }
            item { FeatureCard("Skills & Evolution", "Validated skills, evaluations, versioning and rollback") }
            item { FeatureCard("Model Laboratory", "Model installation, integrity checks and runtime diagnostics") }
        }
    }
}
@Composable
private fun FeatureCard(title: String, description: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF151827))) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(5.dp))
            Text(description, color = Color(0xFFB9B4CA), style = MaterialTheme.typography.bodySmall)
        }
    }
}
