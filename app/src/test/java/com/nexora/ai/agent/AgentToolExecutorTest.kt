package com.nexora.ai.agent

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
