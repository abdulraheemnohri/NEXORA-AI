package com.nexora.ai.agent

import com.nexora.ai.data.LocalNote
import com.nexora.ai.data.LocalNoteStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolExecutorTest {
    private class CountingTool(
        risk: ToolRisk,
        enabled: Boolean = true
    ) : AgentTool {
        var calls = 0
        override val descriptor = ToolDescriptor(
            id = "test.action",
            description = "Test action",
            risk = risk,
            enabledByUser = enabled
        )

        override suspend fun execute(input: String): ToolOutput {
            calls += 1
            return ToolOutput.Text(input)
        }
    }

    @Test
    fun disabledToolNeverRunsEvenWhenAnApprovalGrantIsSupplied() = runBlocking {
        val tool = CountingTool(ToolRisk.READ_ONLY, enabled = false)
        val invocation = ToolInvocation.userInitiated(tool.descriptor.id, "read")
        val grant = ToolApprovalIssuer().approveAfterExplicitUserConfirmation(invocation)

        val outcome = AgentToolExecutor(listOf(tool)).execute(invocation, grant)

        assertTrue(outcome is ToolExecutionOutcome.Denied)
        assertEquals(0, tool.calls)
    }

    @Test
    fun enabledReadOnlyToolRunsWithoutAnApprovalPrompt() = runBlocking {
        val tool = CountingTool(ToolRisk.READ_ONLY)
        val invocation = ToolInvocation.userInitiated(tool.descriptor.id, "query")

        val outcome = AgentToolExecutor(listOf(tool)).execute(invocation)

        assertEquals(ToolExecutionOutcome.Completed(ToolOutput.Text("query")), outcome)
        assertEquals(1, tool.calls)
    }

    @Test
    fun destructiveToolRequiresAnExactOneShotApprovalGrant() = runBlocking {
        val tool = CountingTool(ToolRisk.DESTRUCTIVE)
        val invocation = ToolInvocation.userInitiated(tool.descriptor.id, "record-7")
        val executor = AgentToolExecutor(listOf(tool))

        assertTrue(executor.execute(invocation) is ToolExecutionOutcome.NeedsApproval)
        assertEquals(0, tool.calls)

        val grant = ToolApprovalIssuer().approveAfterExplicitUserConfirmation(invocation)
        assertEquals(
            ToolExecutionOutcome.Completed(ToolOutput.Text("record-7")),
            executor.execute(invocation, grant)
        )
        assertTrue(executor.execute(invocation, grant) is ToolExecutionOutcome.NeedsApproval)
        assertEquals(1, tool.calls)
    }

    @Test
    fun approvalForOneInvocationCannotAuthorizeAnotherInvocation() = runBlocking {
        val tool = CountingTool(ToolRisk.EXTERNAL)
        val approved = ToolInvocation.userInitiated(tool.descriptor.id, "approved payload")
        val different = ToolInvocation.userInitiated(tool.descriptor.id, "different payload")
        val grant = ToolApprovalIssuer().approveAfterExplicitUserConfirmation(approved)

        val outcome = AgentToolExecutor(listOf(tool)).execute(different, grant)

        assertTrue(outcome is ToolExecutionOutcome.NeedsApproval)
        assertEquals(0, tool.calls)
    }



    private class FakeLocalNoteStore : LocalNoteStore {
        val notes = mutableListOf<LocalNote>()

        override suspend fun createLocalNote(title: String, content: String): LocalNote {
            val note = LocalNote(
                id = "note-\${notes.size + 1}",
                title = title,
                content = content,
                createdAt = 1L,
                updatedAt = 1L
            )
            notes += note
            return note
        }

        override suspend fun localNotes(): List<LocalNote> = notes.toList()

        override suspend fun deleteLocalNote(id: String): Boolean =
            notes.removeAll { it.id == id }
    }

    @Test
    fun localNoteWriteRequiresExactOneShotApprovalBeforeSaving() = runBlocking {
        val store = FakeLocalNoteStore()
        val tool = CreateLocalNoteTool(store, enabledByUser = true)
        val invocation = ToolInvocation.userInitiated(
            CreateLocalNoteTool.TOOL_ID,
            "\\nSolar log\\nR phase = 230 V"
        )
        val executor = AgentToolExecutor(
            listOf(tool),
            ToolPermissionPolicy(allowLocalWritesWithoutPrompt = false)
        )

        assertTrue(executor.execute(invocation) is ToolExecutionOutcome.NeedsApproval)
        assertTrue(store.notes.isEmpty())

        val grant = ToolApprovalIssuer().approveAfterExplicitUserConfirmation(invocation)
        val approved = executor.execute(invocation, grant)

        assertTrue(approved is ToolExecutionOutcome.Completed)
        assertEquals("Solar log", store.notes.single().title)
        assertEquals("Solar log\\nR phase = 230 V", store.notes.single().content)
        assertTrue(executor.execute(invocation, grant) is ToolExecutionOutcome.NeedsApproval)
        assertEquals(1, store.notes.size)
    }

    @Test
    fun disabledLocalNoteToolCannotWriteEvenWithApproval() = runBlocking {
        val store = FakeLocalNoteStore()
        val tool = CreateLocalNoteTool(store, enabledByUser = false)
        val invocation = ToolInvocation.userInitiated(CreateLocalNoteTool.TOOL_ID, "Private note")
        val grant = ToolApprovalIssuer().approveAfterExplicitUserConfirmation(invocation)

        val outcome = AgentToolExecutor(listOf(tool)).execute(invocation, grant)

        assertTrue(outcome is ToolExecutionOutcome.Denied)
        assertTrue(store.notes.isEmpty())
    }

    @Test
    fun localNoteToolRejectsBlankAndOversizedContent() = runBlocking {
        val tool = CreateLocalNoteTool(FakeLocalNoteStore(), enabledByUser = true)

        try {
            tool.execute("   ")
            throw AssertionError("Blank notes must be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Note content cannot be empty.", expected.message)
        }

        try {
            tool.execute("x".repeat(CreateLocalNoteTool.MAX_NOTE_CHARACTERS + 1))
            throw AssertionError("Oversized notes must be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Note exceeds the 4,000 character limit.", expected.message)
        }
    }

    @Test
    fun duplicateToolIdsAreRejectedAtRegistration() {
        val first = CountingTool(ToolRisk.READ_ONLY)
        val duplicate = object : AgentTool {
            override val descriptor = first.descriptor
            override suspend fun execute(input: String) = ToolOutput.Text(input)
        }

        try {
            AgentToolExecutor(listOf(first, duplicate))
            throw AssertionError("Duplicate tool ids must be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Tool ids must be unique.", expected.message)
        }
    }
}
