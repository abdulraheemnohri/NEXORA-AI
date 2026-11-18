package com.nexora.ai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun KnowledgeVaultDialog(viewModel: NexoraViewModel, state: NexoraUiState) {
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importKnowledgeDocument(uri)
    }

    if (state.showKnowledgeVault) {
        AlertDialog(
            onDismissRequest = viewModel::dismissKnowledgeVault,
            title = { Text("Local knowledge vault") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        "Import TXT, Markdown, or JSON files. Each file is limited to 512 KiB; the vault is limited to 50 documents and 8 MiB of source-file bytes (SQLite overhead is additional).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Extracted text is stored in app-private SQLite. NEXORA reads the selected URI once, retains no URI permission, uploads no content, and does not automatically add documents to Gemma prompts.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            documentPicker.launch(
                                arrayOf("text/*", "application/json", "application/octet-stream")
                            )
                        },
                        enabled = !state.isImportingKnowledge && !state.isDeletingKnowledgeDocument && !state.isSearchingKnowledge,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.isImportingKnowledge) "Importing…" else "Choose a document")
                    }

                    state.knowledgeMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                    }
                    state.knowledgeError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Text("Search indexed text", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = state.knowledgeQuery,
                        onValueChange = viewModel::updateKnowledgeQuery,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("2–128 characters") },
                        singleLine = true,
                        enabled = !state.isSearchingKnowledge && !state.isImportingKnowledge && !state.isDeletingKnowledgeDocument
                    )
                    Button(
                        onClick = viewModel::searchKnowledge,
                        enabled = !state.isSearchingKnowledge && !state.isImportingKnowledge && !state.isDeletingKnowledgeDocument &&
                            state.knowledgeQuery.trim().length in 2..128,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.isSearchingKnowledge) "Searching…" else "Search on device")
                    }

                    Text("Documents · " + state.knowledgeDocuments.size, style = MaterialTheme.typography.titleSmall)
                    if (state.knowledgeDocuments.isEmpty()) {
                        Text("No documents imported yet.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(state.knowledgeDocuments, key = { it.id }) { document ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                                        Text(document.displayName, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            document.format.name + " · " + formatBytes(document.byteSize) +
                                                " · " + document.chunkCount + " chunks",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        onClick = { viewModel.requestDeleteKnowledgeDocument(document.id) },
                                        enabled = !state.isImportingKnowledge && !state.isDeletingKnowledgeDocument && !state.isSearchingKnowledge
                                    ) {
                                        Text("Remove", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    Text("Search results · " + state.knowledgeResults.size, style = MaterialTheme.typography.titleSmall)
                    if (state.knowledgeResults.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(
                                state.knowledgeResults,
                                key = { it.documentId + ":" + it.chunkIndex }
                            ) { hit ->
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(
                                        hit.documentName + " · chunk " + (hit.chunkIndex + 1),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(hit.snippet, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else if (!state.isSearchingKnowledge && state.knowledgeQuery.trim().length >= 2 &&
                        state.knowledgeError == null
                    ) {
                        Text("No matching document text.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissKnowledgeVault) { Text("Done") }
            }
        )
    }

    state.pendingDeleteKnowledgeDocumentId?.let { documentId ->
        val name = state.knowledgeDocuments.firstOrNull { it.id == documentId }?.displayName ?: "this document"
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteKnowledgeDocument,
            title = { Text("Delete local document?") },
            text = {
                Text(
                    "“" + name + "” and all indexed chunks will be deleted from NEXORA's local knowledge database. The original file in its source location will not be changed."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDeleteKnowledgeDocument,
                    enabled = !state.isDeletingKnowledgeDocument
                ) {
                    Text(if (state.isDeletingKnowledgeDocument) "Deleting…" else "Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::cancelDeleteKnowledgeDocument,
                    enabled = !state.isDeletingKnowledgeDocument
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatBytes(bytes: Int): String =
    if (bytes < 1024) bytes.toString() + " B"
    else String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
