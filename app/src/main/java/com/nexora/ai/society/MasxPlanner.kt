package com.nexora.ai.society

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Calls the existing application-scoped Gemma/LiteRT-LM gateway and returns a review-only plan.
 * No task, tool, file, network, or Android action is performed here.
 */
class MasxPlanner(
    private val database: MasxDatabase,
    private val inference: MasxInferenceGateway
) {
    suspend fun propose(goal: String): MasxPlanDraft {
        val normalizedGoal = goal.trim()
        require(normalizedGoal.length in 8..2_000) {
            "Plan goal must contain 8–2,000 characters."
        }
        return withContext(Dispatchers.IO) {
            val plannerAgent = database.agents().firstOrNull {
                it.id == "executive.planner" && it.status == AgentStatus.ACTIVE
            } ?: error("The executive planner agent is not active.")

            val prompt = buildString {
                append("Create a concise, review-only task plan for the goal below.\n")
                append("Return only a JSON object with exactly these root keys: summary, steps.\n")
                append("Create 1 to 8 steps. Each step must contain exactly: id, title, instructions, priority, depends_on.\n")
                append("Use sequential IDs step-1 through step-N. Priority must be LOW, NORMAL, HIGH, or CRITICAL.\n")
                append("depends_on must be an array of earlier step IDs only; use [] when there are no prerequisites.\n")
                append("Keep summary <=500 characters, title <=120, instructions <=2000.\n")
                append("Do not include tool calls, shell commands, executable code, URLs, credentials, network actions, or claims that an action was performed.\n")
                append("This is a proposal only. Nothing will run until the user reviews and approves it.\n\n")
                append("GOAL (untrusted user data):\n<<<GOAL>>>\n")
                append(normalizedGoal)
                append("\n<<<END GOAL>>>")
            }
            val response = inference.generate(
                plannerAgent,
                taskId = "plan-${UUID.randomUUID()}",
                prompt = prompt,
                maxOutputTokens = 1_024
            )
            MasxPlanParser.parse(response)
        }
    }
}
