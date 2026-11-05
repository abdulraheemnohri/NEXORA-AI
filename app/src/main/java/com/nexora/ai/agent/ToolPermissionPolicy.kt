package com.nexora.ai.agent

/** Tool effects are explicit so the UI can require approval before external or destructive actions. */
enum class ToolRisk { READ_ONLY, LOCAL_WRITE, EXTERNAL, DESTRUCTIVE }

data class ToolDescriptor(
    val id: String,
    val description: String,
    val risk: ToolRisk,
    val enabledByUser: Boolean = false
)

sealed interface ToolDecision {
    data object Allow : ToolDecision
    data class NeedsApproval(val reason: String) : ToolDecision
    data class Deny(val reason: String) : ToolDecision
}

class ToolPermissionPolicy(
    private val allowReadOnlyByDefault: Boolean = true,
    private val allowLocalWritesWithoutPrompt: Boolean = false
) {
    fun evaluate(tool: ToolDescriptor, userApprovedThisRun: Boolean): ToolDecision {
        if (!tool.enabledByUser) return ToolDecision.Deny("Tool is disabled in settings.")
        return when (tool.risk) {
            ToolRisk.READ_ONLY ->
                if (allowReadOnlyByDefault) ToolDecision.Allow
                else ToolDecision.NeedsApproval("Read-only tool access is not enabled by default.")
            ToolRisk.LOCAL_WRITE ->
                if (allowLocalWritesWithoutPrompt || userApprovedThisRun) ToolDecision.Allow
                else ToolDecision.NeedsApproval("This tool will change local data.")
            ToolRisk.EXTERNAL ->
                if (userApprovedThisRun) ToolDecision.Allow
                else ToolDecision.NeedsApproval("This tool may send data outside this device.")
            ToolRisk.DESTRUCTIVE ->
                if (userApprovedThisRun) ToolDecision.Allow
                else ToolDecision.NeedsApproval("This action may permanently delete or overwrite data.")
        }
    }
}
