package com.nexora.ai.ui

import com.nexora.ai.ai.GenerationSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun NexoraHome(viewModel: NexoraViewModel) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = Color(0xFF0B0D16),
        contentWindowInsets = WindowInsets.ime
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0B0D16)).padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("NEXORA AI", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("THINK LOCALLY · ACT INTELLIGENTLY", color = Color(0xFF9B9CB2), style = MaterialTheme.typography.labelSmall)
                }
                Box {
                    TextButton(
                        onClick = viewModel::toggleHistoryMenu,
                        enabled = state.conversationReady && !state.isGenerating && !state.isDeletingConversation
                    ) {
                        Text("Chats · ${state.conversations.size}", color = Color(0xFF75D7E8))
                    }
                    DropdownMenu(
                        expanded = state.historyMenuExpanded,
                        onDismissRequest = viewModel::dismissHistoryMenu
                    ) {
                        if (state.conversations.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No saved conversations") },
                                onClick = viewModel::dismissHistoryMenu
                            )
                        } else {
                            state.conversations.forEach { conversation ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                conversation.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (conversation.id == state.selectedConversationId) {
                                                Text(
                                                    "CURRENT CHAT",
                                                    color = Color(0xFF75D7E8),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    },
                                    onClick = { viewModel.openConversation(conversation.id) },
                                    trailingIcon = {
                                        TextButton(
                                            onClick = { viewModel.requestDeleteConversation(conversation.id) }
                                        ) {
                                            Text("Delete", color = Color(0xFFFF9EAD))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = viewModel::newConversation, enabled = state.conversationReady && !state.isGenerating && !state.isDeletingConversation) {
                    Text("New chat", color = Color(0xFFB9A2FF))
                }
                TextButton(onClick = viewModel::openSettings) {
                    Text("Settings", color = Color(0xFFD4C8FF))
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171B2B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = if (state.modelReady) Color(0xFF164A43) else Color(0xFF39314F), shape = MaterialTheme.shapes.small) {
                            Text(
                                if (state.modelReady) "LOCAL MODEL READY" else "LOCAL MODEL",
                                color = if (state.modelReady) Color(0xFF9CF0D8) else Color(0xFFD4C8FF),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(state.modelStatus, color = Color(0xFFB9B4CA), style = MaterialTheme.typography.labelSmall)
                    }
                    if (!state.modelReady && !state.isDownloading) {
                        Text("Install Gemma 4 E2B", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text("Downloads about 2.58 GB once. The model stays in private app storage.", color = Color(0xFFB9B4CA), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = viewModel::installModel, enabled = state.conversationReady) {
                            Text("Download and install model")
                        }
                    }
                    if (state.isDownloading) {
                        LinearProgressIndicator(progress = { state.downloadProgress }, modifier = Modifier.fillMaxWidth())
                        Text("${(state.downloadProgress * 100).toInt()}% · Keep the app open and use a stable connection.", color = Color(0xFFB9B4CA), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            state.error?.let { message ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3B202A))) {
                    Text(message, color = Color(0xFFFFC5CF), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            if (state.messages.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Your private AI workspace", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (state.modelReady) "Ask a question. Your prompt stays on this device."
                        else "Install the local model to start an offline conversation.",
                        color = Color(0xFF9B9CB2), style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.messages, key = { it.id }) { bubble ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (bubble.role == "user") Arrangement.End else Arrangement.Start
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(0.88f),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (bubble.role == "user") Color(0xFF292344) else Color(0xFF171B2B)
                                )
                            ) {
                                Column(Modifier.padding(13.dp)) {
                                    Text(
                                        if (bubble.role == "user") "YOU" else "NEXORA",
                                        color = if (bubble.role == "user") Color(0xFFC5B6FF) else Color(0xFF75D7E8),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(bubble.content.ifBlank { "Thinking…" }, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::updateInput,
                    modifier = Modifier.weight(1f),
                    enabled = state.modelReady && !state.isGenerating && !state.isDeletingConversation,
                    placeholder = { Text("Message NEXORA…") },
                    maxLines = 4
                )
                Button(
                    onClick = viewModel::sendMessage,
                    enabled = state.modelReady && !state.isGenerating && !state.isDeletingConversation && state.input.isNotBlank()
                ) {
                    Text(if (state.isGenerating) "…" else "Send")
                }
            }
            Text("ON-DEVICE INFERENCE · NO CLOUD AI API", color = Color(0xFF70748C), style = MaterialTheme.typography.labelSmall)
        }
    }

    if (state.showSettings) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSettings,
            title = { Text("Generation settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Output token limit", style = MaterialTheme.typography.titleSmall)
                    GenerationSettings.OUTPUT_TOKEN_LIMITS.forEach { limit ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.maxOutputTokens == limit,
                                onClick = { viewModel.setMaxOutputTokens(limit) }
                            )
                            Text("$limit tokens", color = Color.White)
                        }
                    }
                    Text(
                        "Saved only on this device and passed to LiteRT-LM as its per-decode output limit. Model and context limits still apply.",
                        color = Color(0xFF9B9CB2),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissSettings) {
                    Text("Done")
                }
            }
        )
    }

    state.pendingDeleteConversationId?.let { pendingId ->
        val title = state.conversations.firstOrNull { it.id == pendingId }?.title ?: "this conversation"
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteConversation,
            title = { Text("Delete conversation?") },
            text = {
                Text(
                    "“$title” and its locally stored messages will be permanently deleted from this device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDeleteConversation,
                    enabled = !state.isDeletingConversation
                ) {
                    Text(if (state.isDeletingConversation) "Deleting…" else "Delete", color = Color(0xFFFF9EAD))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::cancelDeleteConversation,
                    enabled = !state.isDeletingConversation
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
