package com.nexora.ai.society

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.ai.NexoraApplication
import com.nexora.ai.ai.Gemma4E2BModelDownloader
import com.nexora.ai.ai.RuntimeState
import kotlinx.coroutines.Dispatchers
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
    val memories: List<AgentMemory> = emptyList(),
    val auditEvents: List<MasxAuditEvent> = emptyList(),
    val metrics: List<MasxMetricRecord> = emptyList(),
    val evolutionProposals: List<EvolutionProposal> = emptyList(),
    val queuedMessages: Int = 0,
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
    val memories: List<AgentMemory>,
    val auditEvents: List<MasxAuditEvent>,
    val metrics: List<MasxMetricRecord>,
    val proposals: List<EvolutionProposal>,
    val queuedMessages: Int
)

/** Society state holder. Durable operations run away from Android's main thread. */
class MasxViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NexoraApplication
    private val database = app.masxDatabase
    private val governor = MasxResourceGovernor(application)
    private val messageBus = MasxMessageBus(database)
    private val agentFactory = MasxAgentFactory(database)
    private val orchestrator = MasxOrchestrator(
        database,
        LiteRtMasxInferenceGateway(app.localModelRuntime, app.inferenceScheduler)
    )
    private val mutableState = MutableStateFlow(MasxUiState())
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
                                    localPath = application.filesDir.resolve(
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
                    memories = database.allMemories(),
                    auditEvents = database.auditEvents(),
                    metrics = database.metrics(),
                    proposals = database.evolutionProposals(),
                    queuedMessages = database.pendingMessages().size
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
                    memories = loaded.memories,
                    auditEvents = loaded.auditEvents,
                    metrics = loaded.metrics,
                    evolutionProposals = loaded.proposals,
                    queuedMessages = loaded.queuedMessages,
                    settings = loaded.snapshot.settings,
                    resources = governor.snapshot()
                )
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
        if (mutableState.value.isBusy) return
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, error = null, result = null) }
            try {
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
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.message ?: "Task execution failed.") }
            } finally {
                mutableState.update { it.copy(isBusy = false) }
                refresh()
            }
        }
    }

    fun cancelSelectedTask() {
        val id = mutableState.value.selectedTaskId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    database.transitionTask(id, TaskStatus.CANCELLED, error = "Cancelled by user.")
                }
            }
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
        mutableState.update { it.copy(resources = governor.snapshot()) }
    }
}
