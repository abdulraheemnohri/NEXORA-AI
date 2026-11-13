package com.nexora.ai.society

import java.util.concurrent.atomic.AtomicBoolean

sealed interface MasxToolDecision {
    data object Allow : MasxToolDecision
    data class NeedsApproval(val reason: String) : MasxToolDecision
    data class Deny(val reason: String) : MasxToolDecision
}

class MasxToolApprovalGrant internal constructor(private val invocationId: String) {
    private val used = AtomicBoolean(false)
    internal fun consume(candidateId: String): Boolean =
        candidateId == invocationId && used.compareAndSet(false,true)
}

class MasxToolApprovalIssuer {
    /** Call only after a user has reviewed the exact action and explicitly confirmed it. */
    fun approveAfterExplicitUserConfirmation(invocationId: String): MasxToolApprovalGrant {
        require(invocationId.isNotBlank())
        return MasxToolApprovalGrant(invocationId)
    }
}

/** MAS-X policy evaluation; this class authorizes metadata only and does not execute handlers. */
class MasxToolPolicy(private val database: MasxDatabase) {
    fun evaluate(
        toolId: String,
        invocationId: String,
        grant: MasxToolApprovalGrant? = null
    ): MasxToolDecision {
        val record=database.toolPermissions().firstOrNull { it.toolId==toolId }
            ?: return MasxToolDecision.Deny("Tool is not registered.")
        if (!record.enabled) return MasxToolDecision.Deny("Tool is disabled by the user.")
        val approvalRequired = when(record.risk) {
            ToolRisk.READ_ONLY -> record.approvalRequired
            ToolRisk.LOCAL_WRITE -> record.approvalRequired ||
                database.setting("security.require_approval_for_writes","true")=="true"
            ToolRisk.EXTERNAL, ToolRisk.DESTRUCTIVE -> true
        }
        if (!approvalRequired) return MasxToolDecision.Allow
        if (grant?.consume(invocationId)==true) return MasxToolDecision.Allow
        return MasxToolDecision.NeedsApproval("This exact invocation requires explicit one-use user approval.")
    }
}
