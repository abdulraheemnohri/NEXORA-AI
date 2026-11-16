package com.nexora.ai.society

import org.json.JSONArray
import org.json.JSONObject

enum class TaskPlanStatus { PENDING_REVIEW, APPROVED, REJECTED }

data class MasxPlanStep(
    val id: String,
    val title: String,
    val instructions: String,
    val priority: TaskPriority,
    val dependsOn: List<String>
)

data class MasxPlanDraft(
    val summary: String,
    val steps: List<MasxPlanStep>
)

data class StoredTaskPlan(
    val id: String,
    val goal: String,
    val summary: String,
    val steps: List<MasxPlanStep>,
    val status: TaskPlanStatus,
    val createdAt: Long,
    val reviewedAt: Long?
)

/**
 * Strict parser for untrusted Gemma output. Only a small declarative task schema is accepted.
 * No executable-action fields exist; all accepted text remains untrusted and is shown for review.
 */
object MasxPlanParser {
    const val MAX_OUTPUT_CHARACTERS = 12_000
    const val MAX_STEPS = 8
    const val MAX_SUMMARY_CHARACTERS = 500
    const val MAX_TITLE_CHARACTERS = 120
    const val MAX_INSTRUCTION_CHARACTERS = 2_000

    fun parse(raw: String): MasxPlanDraft {
        require(raw.length <= MAX_OUTPUT_CHARACTERS) { "Planner output exceeds the 12,000-character limit." }
        val candidate = raw.trim()
        val json = when {
            candidate.startsWith("```json", ignoreCase = true) && candidate.endsWith("```") ->
                candidate.substring(7, candidate.length - 3).trim()
            candidate.startsWith("```") && candidate.endsWith("```") ->
                candidate.substring(3, candidate.length - 3).trim()
            else -> candidate
        }
        require(json.isNotBlank() && json.length <= MAX_OUTPUT_CHARACTERS) { "Planner returned an empty or oversized response." }

        val root = JSONObject(json)
        requireKeys(root, setOf("summary", "steps"))
        val summary = requiredString(root, "summary", MAX_SUMMARY_CHARACTERS)
        val array = root.optJSONArray("steps")
            ?: throw IllegalArgumentException("Planner steps must be a JSON array.")
        require(array.length() in 1..MAX_STEPS) { "A plan must contain between 1 and 8 steps." }

        val priorStepIds = linkedSetOf<String>()
        val steps = (0 until array.length()).map { index ->
            val item = array.optJSONObject(index)
                ?: throw IllegalArgumentException("Every plan step must be a JSON object.")
            requireKeys(item, setOf("id", "title", "instructions", "priority", "depends_on"))
            val expectedId = "step-${index + 1}"
            val id = requiredString(item, "id", 16)
            require(id == expectedId) { "Plan step IDs must be sequential (expected $expectedId)." }
            val title = requiredString(item, "title", MAX_TITLE_CHARACTERS)
            val instructions = requiredString(item, "instructions", MAX_INSTRUCTION_CHARACTERS)
            val priorityName = requiredString(item, "priority", 16)
            val priority = runCatching { TaskPriority.valueOf(priorityName) }
                .getOrElse { throw IllegalArgumentException("Unsupported task priority.") }
            val dependencies = item.optJSONArray("depends_on")
                ?: throw IllegalArgumentException("Every step must include a depends_on array.")
            require(dependencies.length() <= index) { "A step can depend only on earlier steps." }
            val dependsOn = (0 until dependencies.length()).map { dependencyIndex ->
                val dependency = dependencies.opt(dependencyIndex)
                require(dependency is String && dependency in priorStepIds) {
                    "Dependencies must reference earlier step IDs only."
                }
                dependency
            }
            require(dependsOn.distinct().size == dependsOn.size) { "Duplicate dependencies are not allowed." }
            priorStepIds += id
            MasxPlanStep(id, title, instructions, priority, dependsOn)
        }
        return MasxPlanDraft(summary, steps)
    }

    fun encode(draft: MasxPlanDraft): String {
        val steps = JSONArray()
        draft.steps.forEach { step ->
            val dependencies = JSONArray()
            step.dependsOn.forEach { dependency -> dependencies.put(dependency) }
            steps.put(
                JSONObject()
                    .put("id", step.id)
                    .put("title", step.title)
                    .put("instructions", step.instructions)
                    .put("priority", step.priority.name)
                    .put("depends_on", dependencies)
            )
        }
        val encoded = JSONObject().put("summary", draft.summary).put("steps", steps).toString()
        parse(encoded)
        return encoded
    }

    fun decode(encoded: String): MasxPlanDraft = parse(encoded)

    private fun requiredString(json: JSONObject, key: String, maxLength: Int): String {
        val value = json.opt(key)
        require(value is String && value.isNotBlank() && value.length <= maxLength) {
            "Field '$key' must be a non-empty string of at most $maxLength characters."
        }
        return value.trim()
    }

    private fun requireKeys(json: JSONObject, expected: Set<String>) {
        val actual = json.keys().asSequence().toSet()
        require(actual == expected) { "Planner JSON contains missing or unsupported fields." }
    }
}
