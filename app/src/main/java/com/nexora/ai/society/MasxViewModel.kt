package com.nexora.ai.society

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.ai.NexoraApplication
import com.nexora.ai.ai.Gemma4E2BModelDownloader
import com.nexora.ai.ai.RuntimeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class MasxUiState(
    val agents: List<AgentProfile> = emptyList(),
    val tasks: List<MasxTask> = emptyList(),
    val skills: List<SkillManifest> = emptyList(),
    val toolPermissions: List<ToolPermissionRecord> = emptyList(),
    val models: List<ModelProfile> = emptyList(),
    val modelRoles: Map<ModelRole, String> = emptyMap(),
    val compatibility: Map<String, ModelCompatibilityResult> = emptyMap(),
    val memories: List<AgentMemory> = emptyList(),
    val auditEvents: List<MasxAuditEvent> = emptyList(),
    val metrics: List<MasxMetricRecord> = emptyList(),
    val evolutionProposals: List<EvolutionProposal> = emptyList(),
    val queuedMessages: Int = 0,
    val readyQueueCount: Int = 0,
    val queueRunSummary: String? = null,
    val isQueueRunning: Boolean = false,
    val planGoal: String = "",
    val pendingPlans: List<StoredTaskPlan> = emptyList(),
    val isPlanning: Boolean = false,
    val settings: Map<String, String> = emptyMap(),
    val modelReady: Boolean = false,
    val isBusy: Boolean = false,
    val taskTitle: String = "",
    val taskInput: String = "",
    val selectedDepartment: MasxDepartment? = null,
    val selectedTaskId: String? = null,
    val result: String? = null,
    val error: String? = null,
    val resources: DeviceResourceSnapshot? = null
)

private data class MasxLoadedData(
    val snapshot: SocietySnapshot,
    val skills: List<SkillManifest>,
    val tools: List<ToolPermissionRecord>,
    val models: List<ModelProfile>,
    val roles: Map<ModelRole, String>,
    val compatibility: Map<String, ModelCompatibilityResult>,
    val memories: List<AgentMemory>,
    val auditEvents: List<MasxAuditEvent>,
    val metrics: List<MasxMetricRecord>,
    val proposals: List<EvolutionProposal>,
    val plans: List<StoredTaskPlan>,
    val resources: DeviceResourceSnapshot,
    val queuedMessages: Int,
    val readyQueueCount: Int
)

/** Society state holder. Durable operations run away from Android's main thread. */
class MasxViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NexoraApplication
    private val appFilesDir = application.filesDir
    private val database = app.masxDatabase
    private val governor = MasxResourceGovernor(application)
    private val messageBus = MasxMessageBus(database)
    private val agentFactory = MasxAgentFactory(database)
    private val toolPolicy = MasxToolPolicy(database)
    private val inferenceGateway = LiteRtMasxInferenceGateway(
        app.localModelRuntime, app.inferenceScheduler, governor
    )
    private val planner = MasxPlanner(database, inferenceGateway)
    private val orchestrator = MasxOrchestrator(database, inferenceGateway)
    private val mutableState = MutableStateFlow(MasxUiState())
    private var activeTaskId: String? = null
    private var activeTaskJob: Job? = null
    val uiState = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val seededAgents = withContext(Dispatchers.IO) {
                database.seedBuiltIns()
                database.agents()
            }
            seededAgents.forEach { agent ->
                messageBus.register(agent.id) { message ->
                    withContext(Dispatchers.IO) {
                        database.appendAudit(
                            agent.id,
                            "LAMP_RECEIVED_" + message.type.name,
                            message.id,
                            "DELIVERED",
                            message.payload.take(200)
                        )
                    }
                }
            }
            orchestrator.recoverInterruptedTasks()
            messageBus.dispatchBatch()
            refresh()
        }
        viewModelScope.launch {
            app.localModelRuntime.state.collect { runtimeState ->
                val ready = runtimeState is RuntimeState.Ready
                if (ready) {
                    withContext(Dispatchers.IO) {
                        val model = database.models().firstOrNull { it.id == "gemma-4-e2b-it-litertlm" }
                        if (model != null) {
                            database.upsertModel(
                                model.copy(
                                    status = ModelInstallStatus.READY,
                                    localPath = appFilesDir.resolve(
                                        "models/" + Gemma4E2BModelDownloader.MODEL_FILE_NAME
                                    ).absolutePath,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            database.assignModelRole(ModelRole.MAIN_ASSISTANT, model.id)
                            database.assignModelRole(ModelRole.AGENT_SOCIETY, model.id)
                        }
                    }
                }
                mutableState.update { it.copy(modelReady = ready) }
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val snapshot = database.snapshot()
                MasxLoadedData(
                    snapshot = snapshot,
                    skills = database.skills(),
                    tools = database.toolPermissions(),
                    models = database.models(),
                    roles = database.modelRoleAssignments(),
                    compatibility = database.models().associate { model ->
                        model.id to MasxModelCompatibility.assess(
                            model, governor.snapshot(), "litertlm-android-v0.18.1",
                            model.localPath?.let { java.io.File(it).isFile } == true,
                            appFilesDir.usableSpace
                        )
                    },
                    memories = database.allMemories(),
                    auditEvents = database.auditEvents(),
                    metrics = database.metrics(),
                    proposals = database.evolutionProposals(),
                    plans = database.pendingPlans(limit = 3),
                    resources = governor.snapshot(),
                    queuedMessages = database.pendingMessages().size,
                    readyQueueCount = database.runnableTasks(limit = 11).size
                )
            }
            mutableState.update {
                it.copy(
                    agents = loaded.snapshot.agents,
                    tasks = loaded.snapshot.tasks,
                    skills = loaded.skills,
                    toolPermissions = loaded.tools,
                    models = loaded.models,
                    modelRoles = loaded.roles,
                    compatibility = loaded.compatibility,
                    memories = loaded.memories,
                    auditEvents = loaded.auditEvents,
                    metrics = loaded.metrics,
                    evolutionProposals = loaded.proposals,
                    pendingPlans = loaded.plans,
                    queuedMessages = loaded.queuedMessages,
                    readyQueueCount = loaded.readyQueueCount,
                    settings = loaded.snapshot.settings,
                    resources = loaded.resources
                )
            }
        }
    }


    fun updatePlanGoal(value: String) {
        mutableState.update { it.copy(planGoal = value.take(2_000), error = null) }
    }

    /** Generates a proposal through the installed local Gemma/LiteRT-LM gateway only. */
    fun generatePlan() {
        val current = mutableState.value
        if (current.isBusy || current.isPlanning) return
        if (!current.modelReady) {
            mutableState.update { it.copy(error = "Load the local Gemma/LiteRT-LM model before generating a plan.") }
            return
        }
        val goal = current.planGoal.trim()
        if (goal.length !in 8..2_000) {
            mutableState.update { it.copy(error = "Plan goal must contain 8–2,000 characters.") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, isPlanning = true, error = null, result = null) }
            try {
                check(withContext(Dispatchers.IO) {
                    database.setting("society.enabled", "true") == "true"
                }) { "Micro-Agent Society is disabled in settings." }
                val draft = planner.propose(goal)
                withContext(Dispatchers.IO) { database.savePlanProposal(goal, draft) }
                mutableState.update {
                    it.copy(
                        planGoal = "",
                        result = "Plan saved for review. No tasks have been created or executed."
                    )
                }
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.message ?: "Local plan generation failed.") }
            } finally {
                mutableState.update { it.copy(isBusy = false, isPlanning = false) }
            }
        }
    }

    /** Explicit review approval creates local CREATED tasks only; it never starts inference. */
    fun approvePlan(planId: String) {
        if (mutableState.value.isBusy) return
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, error = null) }
            try {
                check(withContext(Dispatchers.IO) {
                    database.setting("society.enabled", "true") == "true"
                }) { "Micro-Agent Society is disabled in settings." }
                val taskIds = withContext(Dispatchers.IO) {
                    database.approvePlanAndCreateTasks(planId)
                }
                mutableState.update {
                    it.copy(
                        selectedTaskId = taskIds.firstOrNull(),
                        result = "Approved plan created ${taskIds.size} local task(s). Nothing was executed."
                    )
                }
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.message ?: "Unable to approve plan.") }
            } finally {
                mutableState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun rejectPlan(planId: String) {
        if (mutableState.value.isBusy) return
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, error = null) }
            try {
                val rejected = withContext(Dispatchers.IO) { database.rejectPlan(planId) }
                check(rejected) { "Plan proposal is no longer awaiting review." }
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.message ?: "Unable to reject plan.") }
            } finally {
                mutableState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun updateTaskTitle(value: String) {
        mutableState.update { it.copy(taskTitle = value.take(120), error = null) }
    }

    fun updateTaskInput(value: String) {
        mutableState.update { it.copy(taskInput = value.take(40_000), error = null) }
    }

    fun selectDepartment(value: MasxDepartment?) {
        mutableState.update { it.copy(selectedDepartment = value) }
    }

    fun selectTask(id: String?) {
        mutableState.update { it.copy(selectedTaskId = id, result = null, error = null) }
    }

    fun createTask() {
        val current = mutableState.value
        if (current.isBusy) return
        val title = current.taskTitle.trim().ifBlank { current.taskInput.trim().take(60) }
        val input = current.taskInput.trim()
        if (title.isBlank() || input.isBlank()) {
            mutableState.update { it.copy(error = "Enter a task title and task instructions.") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, error = null) }
            try {
                check(withContext(Dispatchers.IO) { database.setting("society.enabled","true")=="true" }) { "Micro-Agent Society is disabled in settings." }
                val id = orchestrator.submit(title, input)
                messageBus.dispatchBatch()
                mutableState.update { it.copy(taskTitle = "", taskInput = "", selectedTaskId = id) }
                refresh()
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.message ?: "Unable to create task.") }
            } finally {
                mutableState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun runSelectedTask() {
        val id = mutableState.value.selectedTaskId ?: return
        if (mutableState.value.isBusy || activeTaskJob?.isActive == true) return
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            mutableState.update { it.copy(isBusy = true, error = null, result = null) }
            try {
                check(withContext(Dispatchers.IO) { database.setting("society.enabled","true")=="true" }) {
                    "Micro-Agent Society is disabled in settings."
                }
                val resources = withContext(Dispatchers.IO) { governor.snapshot() }
                mutableState.update { it.copy(resources = resources) }
                val (allowed, reason) = governor.canStartInference(resources)
                check(allowed) { reason }
                check(mutableState.value.modelReady) {
                    "Load the local LiteRT-LM model in the chat screen before running society tasks."
                }
                val result = orchestrator.runTask(id).getOrThrow()
                messageBus.dispatchBatch()
                mutableState.update { it.copy(result = result) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.message ?: "Task execution failed.") }
            } finally {
                if (activeTaskId == id) {
                    activeTaskId = null
                    activeTaskJob = null
                }
                mutableState.update { it.copy(isBusy = false) }
                refresh()
            }
        }
        activeTaskId = id
        activeTaskJob = job
        job.start()
    }

    fun runReadyQueue() {
        val current = mutableState.value
        if (current.isBusy || activeTaskJob?.isActive == true) return
        if (current.readyQueueCount == 0) {
            mutableState.update { it.copy(error = "No dependency-ready tasks are available.") }
            return
        }
        if (!current.modelReady) {
            mutableState.update { it.copy(error = "Load the local LiteRT-LM model before running the ready queue.") }
            return
        }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            mutableState.update {
                it.copy(isBusy = true, isQueueRunning = true, error = null, queueRunSummary = null)
            }
            try {
                check(withContext(Dispatchers.IO) {
                    database.setting("society.enabled", "true") == "true"
                }) { "Micro-Agent Society is disabled in settings." }
                check(mutableState.value.modelReady) {
                    "The local LiteRT-LM model is no longer ready."
                }
                val resources = withContext(Dispatchers.IO) { governor.snapshot() }
                mutableState.update { it.copy(resources = resources) }
                val (allowed, reason) = governor.canStartInference(resources)
                check(allowed) { reason }

                val report = orchestrator.runReadyTasks(maxTasks = 10)
                messageBus.dispatchBatch()
                val summary = if (report.attemptedTaskIds.isEmpty()) {
                    "No tasks are ready. Complete prerequisites or resume paused tasks first."
                } else buildString {
                    append("Processed ").append(report.attemptedTaskIds.size).append(" task(s): ")
                    append(report.completedTaskIds.size).append(" completed, ")
                    append(report.failedTaskIds.size).append(" failed.")
                    if (report.remainingRunnableCount > 0) {
                        append(" ").append(report.remainingRunnableCount)
                            .append(" task(s) remain eligible for a later run.")
                    }
                    if (report.limitReached) append(" The 10-task batch limit was reached.")
                }
                mutableState.update { it.copy(queueRunSummary = summary) }
            } catch (cancelled: CancellationException) {
                mutableState.update {
                    it.copy(queueRunSummary = "Queue cancelled. The active task was stopped through the runtime cancellation path.")
                }
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.message ?: "Ready queue execution failed.") }
            } finally {
                activeTaskId = null
                activeTaskJob = null
                mutableState.update { it.copy(isBusy = false, isQueueRunning = false) }
                refresh()
            }
        }
        activeTaskId = null
        activeTaskJob = job
        job.start()
    }

    fun cancelReadyQueue() {
        if (!mutableState.value.isQueueRunning) return
        activeTaskJob?.takeIf { it.isActive }?.cancel(
            CancellationException("Ready queue cancelled by user.")
        )
    }

    fun cancelSelectedTask() {
        if (mutableState.value.isQueueRunning) {
            cancelReadyQueue()
            return
        }
        val id = mutableState.value.selectedTaskId ?: return
        val runningJob = activeTaskJob?.takeIf { activeTaskId == id && it.isActive }
        viewModelScope.launch {
            if (runningJob != null) {
                runningJob.cancel(CancellationException("Cancelled by user."))
                runningJob.join()
            }
            withContext(Dispatchers.IO) {
                val current = database.task(id)
                if (current != null && current.status !in setOf(TaskStatus.COMPLETED, TaskStatus.CANCELLED)) {
                    database.transitionTask(id, TaskStatus.CANCELLED, error = "Cancelled by user.")
                }
            }
            refresh()
        }
    }

    fun pauseSelectedTask() {
        val id = mutableState.value.selectedTaskId ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { check(database.transitionTask(id,TaskStatus.PAUSED,error="Paused by user.")) }
            }
            result.onFailure { error -> mutableState.update { it.copy(error=error.message) } }
            refresh()
        }
    }

    fun resumeSelectedTask() {
        val id = mutableState.value.selectedTaskId ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { check(database.transitionTask(id,TaskStatus.READY,error="Resumed by user.")) }
            }
            result.onFailure { error -> mutableState.update { it.copy(error=error.message) } }
            refresh()
        }
    }

    fun addTaskDependency(taskId: String, dependsOnTaskId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { database.addDependency(taskId,dependsOnTaskId) }
            }
            result.onFailure { error -> mutableState.update { it.copy(error=error.message) } }
            refresh()
        }
    }

    fun setSetting(key: String, value: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { database.setSetting(key, value) }
            }
            result.onFailure { error ->
                mutableState.update { it.copy(error = error.message) }
            }
            refresh()
        }
    }

    fun setAgentStatus(agentId: String, status: AgentStatus) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val current = database.agents().firstOrNull { it.id == agentId } ?: return@withContext
                database.upsertAgent(current.copy(status = status, version = current.version + 1))
                database.appendAudit("user", "AGENT_STATUS_CHANGED", agentId, "SUCCESS", status.name)
            }
            refresh()
        }
    }

    fun installSkillDraft(id: String, name: String, description: String, instructions: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.upsertSkill(
                        SkillManifest(id, name, 1, description, instructions, emptySet(), SkillStatus.DRAFT)
                    )
                    database.appendAudit("user", "SKILL_DRAFT_CREATED", id, "SUCCESS", "Awaiting validation and approval.")
                }
                refresh()
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.message ?: "Unable to save skill draft.") }
            }
        }
    }

    fun approveSkill(skillId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val skill = database.skills().firstOrNull { it.id == skillId } ?: error("Skill not found.")
                    val registeredTools = database.toolPermissions().map { it.toolId }.toSet()
                    val validation = MasxSkillValidator.validate(skill, registeredTools)
                    check(validation.valid) { validation.reason }
                    check(database.setSkillStatus(skillId, SkillStatus.ACTIVE, approvedByUser = true))
                    database.appendAudit(
                        "user", "SKILL_ACTIVATED", skillId, "USER_APPROVED",
                        "Static validation passed; user approved activation."
                    )
                }
            }
            result.onFailure { error ->
                mutableState.update { it.copy(error = error.message ?: "Skill activation failed.") }
            }
            refresh()
        }
    }

    fun assignSkillToAgent(skillId: String, agentId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { database.assignSkill(agentId,skillId,true) }
            }
            result.onFailure { error -> mutableState.update { it.copy(error=error.message ?: "Skill assignment failed.") } }
            refresh()
        }
    }

    fun deleteMemory(memoryId: String) {
        if (memoryId.isBlank()) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    check(database.deleteMemory(memoryId)) { "This memory record is no longer available." }
                }
            }
            result.onFailure { error ->
                mutableState.update { it.copy(error = error.message ?: "Unable to delete memory.") }
            }
            refresh()
        }
    }

    fun saveApprovedMemory(key: String, value: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val now = System.currentTimeMillis()
                    database.putMemory(
                        AgentMemory(
                            UUID.randomUUID().toString(), MemoryScope.USER_APPROVED, null,
                            key.trim(), value.trim(),
                            "Explicitly saved by user in MAS-X memory center", now, now
                        ),
                        explicitlyUserApproved = true
                    )
                    database.appendAudit(
                        "user", "USER_APPROVED_MEMORY_SAVED", key, "SUCCESS",
                        "User explicitly saved shared memory."
                    )
                }
            }
            result.onFailure { error ->
                mutableState.update { it.copy(error = error.message ?: "Unable to save memory.") }
            }
            refresh()
        }
    }

    fun proposeTemporaryAgent(
        name: String, department: MasxDepartment, role: String, instructions: String
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { agentFactory.propose(name, department, role, instructions) }
            }
            result.onFailure { error ->
                mutableState.update { it.copy(error = error.message ?: "Agent proposal failed.") }
            }
            refresh()
        }
    }

    fun approveTemporaryAgent(agentId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { agentFactory.approveAndActivate(agentId, explicitlyApproved = true) }
            }
            result.onFailure { error ->
                mutableState.update { it.copy(error = error.message ?: "Agent activation failed.") }
            }
            refresh()
        }
    }

    fun setToolEnabled(toolId: String, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val record = database.toolPermissions().firstOrNull { it.toolId == toolId } ?: return@withContext
                database.setToolPermission(record.toolId, record.risk, enabled, record.approvalRequired)
                database.appendAudit("user", "TOOL_PERMISSION_CHANGED", toolId, "SUCCESS", "enabled=$enabled")
            }
            refresh()
        }
    }

    suspend fun evaluateToolPermission(toolId: String, invocationId: String, grant: MasxToolApprovalGrant? = null): MasxToolDecision =
        withContext(Dispatchers.IO) { toolPolicy.evaluate(toolId,invocationId,grant) }

    fun assignModelRole(role: ModelRole, modelId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { database.assignModelRole(role, modelId) }
            }
            result.onFailure { error ->
                mutableState.update { it.copy(error = error.message) }
            }
            refresh()
        }
    }

    fun createEvolutionProposal(title: String, description: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { database.createEvolutionProposal(title,description) }
            }
            result.onFailure { error -> mutableState.update { it.copy(error=error.message ?: "Unable to save evolution proposal.") } }
            refresh()
        }
    }

    fun reviewEvolutionProposal(id: String, approved: Boolean) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { check(database.reviewEvolutionProposal(id,approved,explicitlyReviewed=true)) { "Proposal was already reviewed or no longer exists." } }
            }
            result.onFailure { error -> mutableState.update { it.copy(error=error.message ?: "Unable to review proposal.") } }
            refresh()
        }
    }

    fun refreshResources() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { governor.snapshot() }
            mutableState.update { it.copy(resources = snapshot) }
        }
    }
}
