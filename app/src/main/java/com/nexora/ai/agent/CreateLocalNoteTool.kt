package com.nexora.ai.agent

import com.nexora.ai.data.LocalNoteStore

/** Creates one app-private note. The executor classifies it as LOCAL_WRITE and requires per-run approval. */
class CreateLocalNoteTool(
    private val noteStore: LocalNoteStore,
    enabledByUser: Boolean
) : AgentTool {
    override val descriptor = ToolDescriptor(
        id = TOOL_ID,
        description = "Save a note in the private on-device notes store.",
        risk = ToolRisk.LOCAL_WRITE,
        enabledByUser = enabledByUser
    )

    override suspend fun execute(input: String): ToolOutput {
        val content = input.trim()
        require(content.isNotEmpty()) { "Note content cannot be empty." }
        require(content.length <= MAX_NOTE_CHARACTERS) {
            "Note exceeds the 4,000 character limit."
        }
        val title = content.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(MAX_TITLE_CHARACTERS)
            ?: "Untitled note"
        val note = noteStore.createLocalNote(title, content)
        return ToolOutput.LocalNoteCreated(note)
    }

    companion object {
        const val TOOL_ID = "local.notes.create"
        const val MAX_NOTE_CHARACTERS = 4_000
        const val MAX_TITLE_CHARACTERS = 80
    }
}
