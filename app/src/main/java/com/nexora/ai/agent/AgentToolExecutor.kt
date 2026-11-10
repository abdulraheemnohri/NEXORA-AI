package com.nexora.ai.agent

import com.nexora.ai.data.ConversationSearchHit
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

/** Immutable, user-originated invocation. Model output is never converted into this type implicitly. */
class ToolInvocation private constructor(
    val requestId: String,
    val toolId: String,
    val input: String
) {
    companion object {
        fun userInitiated(toolId: String, input: String): ToolInvocation {
            require(toolId.isNotBlank()) { "Tool id cannot be empty." }
            require(input.length <= MAX_INPUT_CHARACTERS) { "Tool input is too long." }
            return ToolInvocation(UUID.randomUUID().toString(), toolId, input)
        }

        private const val MAX_INPUT_CHARACTERS = 4_096
    }
}

/** A one-use grant tied to the exact invocation object reviewed by the user. */
internal class ToolApprovalGrant internal constructor(
    private val approvedInvocation: ToolInvocation
) {
    private val consumed = AtomicBoolean(false)

    internal fun consume(candidate: ToolInvocation): Boolean =
        candidate === approvedInvocation && consumed.compareAndSet(false, true)
}

/** Only UI code reached from an explicit confirmation control should call this issuer. */
internal class ToolApprovalIssuer {
    internal fun approveAfterExplicitUserConfirmation(invocation: ToolInvocation): ToolApprovalGrant =
        ToolApprovalGrant(invocation)
}

sealed interface ToolOutput {
    data class Text(val value: String) : ToolOutput
    data class ConversationMatches(val matches: List<ConversationSearchHit>) : ToolOutput
}

interface AgentTool {
    val descriptor: ToolDescriptor
    suspend fun execute(input: String): ToolOutput
}

sealed interface ToolExecutionOutcome {
    data class Completed(val output: ToolOutput) : ToolExecutionOutcome
    data class NeedsApproval(val reason: String) : ToolExecutionOutcome
    data class Denied(val reason: String) : ToolExecutionOutcome
    data class Failed(val reason: String) : ToolExecutionOutcome
}

/**
 * Central execution boundary. The policy is evaluated immediately before the handler is invoked.
 * A grant is one-shot and cannot authorize a copied or modified invocation.
 */
class AgentToolExecutor(
    tools: List<AgentTool>,
    private val policy: ToolPermissionPolicy = ToolPermissionPolicy()
) {
    private val toolsById = tools.associateBy { it.descriptor.id }

    init {
        require(toolsById.size == tools.size) { "Tool ids must be unique." }
    }

    internal suspend fun execute(
        invocation: ToolInvocation,
        approval: ToolApprovalGrant? = null
    ): ToolExecutionOutcome {
        val tool = toolsById[invocation.toolId]
            ?: return ToolExecutionOutcome.Denied("Unknown tool.")

        val approvedThisInvocation = approval?.consume(invocation) == true
        return when (val decision = policy.evaluate(tool.descriptor, approvedThisInvocation)) {
            ToolDecision.Allow -> try {
                ToolExecutionOutcome.Completed(tool.execute(invocation.input))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ToolExecutionOutcome.Failed(error.message ?: "Tool execution failed.")
            }
            is ToolDecision.NeedsApproval -> ToolExecutionOutcome.NeedsApproval(decision.reason)
            is ToolDecision.Deny -> ToolExecutionOutcome.Denied(decision.reason)
        }
    }
}
