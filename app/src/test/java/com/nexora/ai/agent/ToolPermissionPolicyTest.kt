package com.nexora.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolPermissionPolicyTest {
    private val policy = ToolPermissionPolicy()

    @Test fun disabledToolIsAlwaysDenied() {
        val tool = ToolDescriptor("web", "Browse", ToolRisk.READ_ONLY, enabledByUser = false)
        assertEquals(ToolDecision.Deny("Tool is disabled in settings."), policy.evaluate(tool, true))
    }

    @Test fun enabledReadOnlyToolCanRun() {
        val tool = ToolDescriptor("search", "Search local index", ToolRisk.READ_ONLY, enabledByUser = true)
        assertEquals(ToolDecision.Allow, policy.evaluate(tool, false))
    }

    @Test fun externalToolRequiresApproval() {
        val tool = ToolDescriptor("share", "Send content", ToolRisk.EXTERNAL, enabledByUser = true)
        assertEquals(true, policy.evaluate(tool, false) is ToolDecision.NeedsApproval)
        assertEquals(ToolDecision.Allow, policy.evaluate(tool, true))
    }

    @Test fun destructiveToolRequiresApproval() {
        val tool = ToolDescriptor("delete", "Delete files", ToolRisk.DESTRUCTIVE, enabledByUser = true)
        assertEquals(true, policy.evaluate(tool, false) is ToolDecision.NeedsApproval)
    }
}
