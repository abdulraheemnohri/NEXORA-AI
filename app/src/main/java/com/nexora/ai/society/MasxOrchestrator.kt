package com.nexora.ai.society

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Single-flight supervisor/worker orchestrator. Tasks and transitions are persisted before
 * inference, so an interrupted RUNNING task can be recovered after process restart.
 */
class MasxOrchestrator(
    private val database: MasxDatabase,
    private val inference: MasxInferenceGateway
) {
    private val executionMutex = Mutex()

    suspend fun submit(title: String, input: String, priority: TaskPriority = TaskPriority.NORMAL): String =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val task = MasxTask(
                id = UUID.randomUUID().toString(), title = title.trim().take(120),
                input = input.trim(), status = TaskStatus.CREATED, priority = priority,
                createdAt = now, updatedAt = now,
                maxAttempts = database.setting("tasks.max_attempts", "2").toIntOrNull()?.coerceIn(0, 3) ?: 2
            )
            database.createTask(task)
            database.enqueue(
                LampProtocol.newMessage(
                    sender = "executive.commander", recipient = "executive.planner",
                    type = LampType.TASK_CREATE, taskId = task.id,
                    payload = """{"taskId":"${task.id}","title":"${escapeJson(task.title)}"}""",
                    priority = priority
                )
            )
            task.id
        }

    suspend fun runTask(taskId: String): Result<String> = executionMutex.withLock {
        runCatching {
            withContext(Dispatchers.IO) {
                val task = database.task(taskId) ?: error("Task not found.")
                check(task.status !in setOf(TaskStatus.COMPLETED, TaskStatus.CANCELLED)) { "Task is already closed." }
                check(database.dependenciesComplete(taskId)) { "Task is waiting for unfinished dependencies." }
                if (task.status == TaskStatus.CREATED) database.transitionTask(taskId, TaskStatus.PLANNING)
                val afterPlan = database.task(taskId) ?: error("Task disappeared.")
                if (afterPlan.status == TaskStatus.PLANNING) database.transitionTask(taskId, TaskStatus.READY)
                val ready = database.task(taskId) ?: error("Task disappeared.")
                if (ready.status == TaskStatus.FAILED) check(ready.attempt < ready.maxAttempts) { "Task exhausted its retry budget." }
                val agent = chooseWorker(ready.input)
                if (ready.status == TaskStatus.READY || ready.status == TaskStatus.REWORK || ready.status == TaskStatus.RECOVERING) {
                    if (ready.status != TaskStatus.READY) database.transitionTask(taskId, TaskStatus.READY)
                    database.transitionTask(taskId, TaskStatus.ASSIGNED, agentId = agent.id)
                } else if (ready.status == TaskStatus.FAILED) {
                    database.transitionTask(taskId, TaskStatus.RECOVERING)
                    database.transitionTask(taskId, TaskStatus.READY)
                    database.transitionTask(taskId, TaskStatus.ASSIGNED, agentId = agent.id)
                }
                database.transitionTask(taskId, TaskStatus.RUNNING, agentId = agent.id)
                val current = database.task(taskId) ?: error("Task disappeared.")
                val includeApproved = database.setting("memory.allow_user_approved", "false") == "true"
                val memories = database.memoriesForAgent(agent, taskId, includeApproved)
                val memoryContext = memories.take(12).joinToString("\n") { "- ${it.key}: ${it.value.take(1_000)} [${it.provenance}]" }
                val prompt = buildString {
                    append("TASK TITLE: ").append(current.title).append("\n\n")
                    append("TASK INPUT (untrusted user content):\n").append(current.input)
                    if (memoryContext.isNotBlank()) append("\n\nAPPROVED RELEVANT MEMORY:\n").append(memoryContext)
                    append("\n\nReturn a useful result. State uncertainty. Do not claim external actions.")
                }
                database.saveCheckpoint(taskId, """{"stage":"worker_started","agent":"${agent.id}"}""")
                val inferenceStartedAt = System.currentTimeMillis()
                val output = inference.generate(agent, taskId, prompt, 768)
                val inferenceDuration = System.currentTimeMillis() - inferenceStartedAt
                database.transitionTask(taskId, TaskStatus.REVIEW, result = output)
                // Deterministic gate: non-empty bounded output and valid worker identity.
                check(output.isNotBlank() && output.length <= 40_000) { "Output did not pass the local result gate." }
                database.transitionTask(taskId, TaskStatus.COMPLETED, result = output)
                database.recordMetric(AgentMetric(agent.id, taskId, "output_characters", output.length.toDouble(), System.currentTimeMillis()))
                database.recordMetric(AgentMetric(agent.id, taskId, "inference_duration_ms", inferenceDuration.toDouble(), System.currentTimeMillis()))
                database.saveCheckpoint(taskId, """{"stage":"completed","agent":"${agent.id}"}""")
                database.enqueue(LampProtocol.newMessage(
                    sender = agent.id, recipient = "executive.commander",
                    type = LampType.TASK_COMPLETE, taskId = taskId,
                    payload = """{"taskId":"$taskId","resultLength":${output.length}}"""
                ))
                output
            }
        }.recoverCatching { error ->
            withContext(Dispatchers.IO) {
                val current = database.task(taskId)
                if (current != null && current.status in setOf(TaskStatus.RUNNING, TaskStatus.ASSIGNED, TaskStatus.REVIEW)) {
                    val nextAttempt = current.attempt + 1
                    database.transitionTask(taskId, TaskStatus.FAILED, error = error.message ?: "Execution failed", attempt = nextAttempt)
                    if (nextAttempt <= current.maxAttempts) {
                        database.transitionTask(taskId, TaskStatus.RECOVERING)
                        database.transitionTask(taskId, TaskStatus.READY)
                    }
                }
            }
            throw error
        }
    }

    suspend fun recoverInterruptedTasks(): Int = withContext(Dispatchers.IO) {
        var recovered = 0
        database.recoverableTasks().forEach { task ->
            val result = runCatching {
                when (task.status) {
                    TaskStatus.CREATED -> {
                        database.transitionTask(task.id,TaskStatus.PLANNING)
                        database.transitionTask(task.id,TaskStatus.READY)
                    }
                    TaskStatus.PLANNING -> database.transitionTask(task.id,TaskStatus.READY)
                    TaskStatus.READY -> Unit
                    TaskStatus.ASSIGNED, TaskStatus.RUNNING -> {
                        database.transitionTask(task.id,TaskStatus.RECOVERING,error="Recovered after process interruption.")
                        database.transitionTask(task.id,TaskStatus.READY)
                    }
                    TaskStatus.RECOVERING, TaskStatus.REWORK -> database.transitionTask(task.id,TaskStatus.READY)
                    TaskStatus.REVIEW -> {
                        if (!task.result.isNullOrBlank()) database.transitionTask(task.id,TaskStatus.COMPLETED,result=task.result)
                        else {
                            database.transitionTask(task.id,TaskStatus.REWORK)
                            database.transitionTask(task.id,TaskStatus.READY)
                        }
                    }
                    TaskStatus.FAILED -> if (task.attempt < task.maxAttempts) {
                        database.transitionTask(task.id,TaskStatus.RECOVERING)
                        database.transitionTask(task.id,TaskStatus.READY)
                    }
                    else -> Unit
                }
            }
            if (result.isSuccess) recovered++
        }
        recovered
    }

    private fun chooseWorker(input: String): AgentProfile {
        val text = input.lowercase()
        val id = when {
            listOf("code", "kotlin", "android", "software", "bug", "compile").any(text::contains) -> "engineering.developer"
            listOf("research", "source", "evidence", "compare", "fact").any(text::contains) -> "research.researcher"
            listOf("plan", "schedule", "goal", "task").any(text::contains) -> "productivity.assistant"
            listOf("security", "permission", "privacy", "threat").any(text::contains) -> "security.guardian"
            listOf("test", "verify", "benchmark").any(text::contains) -> "quality.tester"
            else -> "intelligence.reasoner"
        }
        return database.agents().firstOrNull { it.id == id && it.status == AgentStatus.ACTIVE }
            ?: MasxAgentCatalog.byId("intelligence.reasoner")!!
    }

    private fun escapeJson(value: String) = value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")
}
