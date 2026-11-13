package com.nexora.ai.society

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.ai.NexoraApplication
import com.nexora.ai.ai.RuntimeState
import com.nexora.ai.ai.Gemma4E2BModelDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class MasxUiState(
    val agents: List<AgentProfile> = emptyList(),
    val tasks: List<MasxTask> = emptyList(),
    val skills: List<SkillManifest> = emptyList(),
    val toolPermissions: List<ToolPermissionRecord> = emptyList(),
    val models: List<ModelProfile> = emptyList(),
    val modelRoles: Map<ModelRole,String> = emptyMap(),
    val memories: List<AgentMemory> = emptyList(),
    val queuedMessages: Int = 0,
    val settings: Map<String,String> = emptyMap(),
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

/** Society UI state; all durable work is delegated to MasxDatabase and MasxOrchestrator. */
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
            database.seedBuiltIns()
            database.agents().forEach { agent ->
                messageBus.register(agent.id) { message ->
                    database.appendAudit(agent.id,"LAMP_RECEIVED_${'
        }
        viewModelScope.launch {
            app.localModelRuntime.state.collect { state ->
                val ready = state is RuntimeState.Ready
                if (ready) {
                    val model = database.models().firstOrNull { it.id == "gemma-4-e2b-it-litertlm" }
                    if (model != null) {
                        database.upsertModel(model.copy(
                            status=ModelInstallStatus.READY,
                            localPath=application.filesDir.resolve("models/${Gemma4E2BModelDownloader.MODEL_FILE_NAME}").absolutePath,
                            updatedAt=System.currentTimeMillis()
                        ))
                        database.assignModelRole(ModelRole.MAIN_ASSISTANT,model.id)
                        database.assignModelRole(ModelRole.AGENT_SOCIETY,model.id)
                    }
                }
                mutableState.update { it.copy(modelReady = ready) }
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val snapshot = database.snapshot()
            mutableState.update {
                it.copy(
                    agents = snapshot.agents,
                    tasks = snapshot.tasks,
                    skills = database.skills(),
                    toolPermissions = database.toolPermissions(),
                    models = database.models(),
                    modelRoles = database.modelRoleAssignments(),
                    memories = database.allMemories(),
                    queuedMessages = database.pendingMessages().size,
                    settings = snapshot.settings,
                    resources = governor.snapshot()
                )
            }
        }
    }

    fun updateTaskTitle(value: String) { mutableState.update { it.copy(taskTitle=value.take(120),error=null) } }
    fun updateTaskInput(value: String) { mutableState.update { it.copy(taskInput=value.take(40_000),error=null) } }
    fun selectDepartment(value: MasxDepartment?) { mutableState.update { it.copy(selectedDepartment=value) } }
    fun selectTask(id: String?) { mutableState.update { it.copy(selectedTaskId=id,result=null,error=null) } }

    fun createTask() {
        val state = mutableState.value
        if (state.isBusy) return
        val title = state.taskTitle.trim().ifBlank { state.taskInput.trim().take(60) }
        val input = state.taskInput.trim()
        if (title.isBlank() || input.isBlank()) {
            mutableState.update { it.copy(error="Enter a task title and task instructions.") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy=true,error=null) }
            try {
                val id = orchestrator.submit(title,input)
                messageBus.dispatchBatch()
                mutableState.update { it.copy(taskTitle="",taskInput="",selectedTaskId=id) }
                refresh()
            } catch (error: Exception) {
                mutableState.update { it.copy(error=error.message ?: "Unable to create task.") }
            } finally {
                mutableState.update { it.copy(isBusy=false) }
            }
        }
    }

    fun runSelectedTask() {
        val id = mutableState.value.selectedTaskId ?: return
        if (mutableState.value.isBusy) return
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy=true,error=null,result=null) }
            try {
                val resources = governor.snapshot()
                mutableState.update { it.copy(resources=resources) }
                val (allowed,reason) = governor.canStartInference(resources)
                check(allowed) { reason }
                check(mutableState.value.modelReady) { "Load the local LiteRT-LM model in the chat screen before running society tasks." }
                val result = orchestrator.runTask(id).getOrThrow()
                messageBus.dispatchBatch()
                mutableState.update { it.copy(result=result) }
            } catch (error: Exception) {
                mutableState.update { it.copy(error=error.message ?: "Task execution failed.") }
            } finally {
                mutableState.update { it.copy(isBusy=false) }
                refresh()
            }
        }
    }

    fun cancelSelectedTask() {
        val id = mutableState.value.selectedTaskId ?: return
        viewModelScope.launch {
            runCatching { database.transitionTask(id,TaskStatus.CANCELLED,error="Cancelled by user.") }
            refresh()
        }
    }

    fun setSetting(key: String, value: String) {
        viewModelScope.launch {
            runCatching { database.setSetting(key,value) }
                .onFailure { error -> mutableState.update { it.copy(error=error.message) } }
            refresh()
        }
    }

    fun setAgentStatus(agentId: String, status: AgentStatus) {
        viewModelScope.launch {
            val current = database.agents().firstOrNull { it.id==agentId } ?: return@launch
            database.upsertAgent(current.copy(status=status,version=current.version+1))
            database.appendAudit("user","AGENT_STATUS_CHANGED",agentId,"SUCCESS",status.name)
            refresh()
        }
    }

    fun installSkillDraft(id: String, name: String, description: String, instructions: String) {
        viewModelScope.launch {
            try {
                database.upsertSkill(
                    SkillManifest(id,name,1,description,instructions, emptySet(),SkillStatus.DRAFT)
                )
                database.appendAudit("user","SKILL_DRAFT_CREATED",id,"SUCCESS","Awaiting validation and approval.")
                refresh()
            } catch (error: Exception) {
                mutableState.update { it.copy(error=error.message ?: "Unable to save skill draft.") }
            }
        }
    }

    fun approveSkill(skillId: String) {
        viewModelScope.launch {
            runCatching {
                val skill = database.skills().firstOrNull { it.id == skillId } ?: error("Skill not found.")
                val validation = MasxSkillValidator.validate(skill, database.toolPermissions().map { it.toolId }.toSet())
                check(validation.valid) { validation.reason }
                check(database.setSkillStatus(skillId,SkillStatus.ACTIVE,approvedByUser=true))
            }.onSuccess {
                database.appendAudit("user","SKILL_ACTIVATED",skillId,"USER_APPROVED","Static validation passed; user approved activation.")
            }.onFailure { error ->
                mutableState.update { it.copy(error=error.message ?: "Skill activation failed.") }
            }
            refresh()
        }
    }

    fun saveApprovedMemory(key: String, value: String) {
        viewModelScope.launch {
            runCatching {
                val now=System.currentTimeMillis()
                database.putMemory(
                    AgentMemory(UUID.randomUUID().toString(),MemoryScope.USER_APPROVED,null,key.trim(),value.trim(),
                        "Explicitly saved by user in MAS-X memory center",now,now),
                    explicitlyUserApproved=true
                )
                database.appendAudit("user","USER_APPROVED_MEMORY_SAVED",key,"SUCCESS","User explicitly saved shared memory.")
            }.onFailure { error ->
                mutableState.update { it.copy(error=error.message ?: "Unable to save memory.") }
            }
            refresh()
        }
    }

    fun proposeTemporaryAgent(name: String, department: MasxDepartment, role: String, instructions: String) {
        viewModelScope.launch {
            runCatching { agentFactory.propose(name,department,role,instructions) }
                .onFailure { error -> mutableState.update { it.copy(error=error.message ?: "Agent proposal failed.") } }
            refresh()
        }
    }

    fun approveTemporaryAgent(agentId: String) {
        viewModelScope.launch {
            runCatching { agentFactory.approveAndActivate(agentId,explicitlyApproved=true) }
                .onFailure { error -> mutableState.update { it.copy(error=error.message ?: "Agent activation failed.") } }
            refresh()
        }
    }

    fun setToolEnabled(toolId: String, enabled: Boolean) {
        viewModelScope.launch {
            val record = database.toolPermissions().firstOrNull { it.toolId == toolId } ?: return@launch
            database.setToolPermission(record.toolId,record.risk,enabled,record.approvalRequired)
            database.appendAudit("user","TOOL_PERMISSION_CHANGED",toolId,"SUCCESS","enabled=$enabled")
            refresh()
        }
    }

    fun assignModelRole(role: ModelRole, modelId: String) {
        viewModelScope.launch {
            runCatching { database.assignModelRole(role,modelId) }
                .onFailure { error -> mutableState.update { it.copy(error=error.message) } }
            refresh()
        }
    }

    fun refreshResources() {
        mutableState.update { it.copy(resources=governor.snapshot()) }
    }
}
}{message.type}",message.id,"DELIVERED",message.payload.take(200))
                }
            }
            orchestrator.recoverInterruptedTasks()
            messageBus.dispatchBatch()
            refresh()
        }
        viewModelScope.launch {
            app.localModelRuntime.state.collect { state ->
                mutableState.update { it.copy(modelReady = state is RuntimeState.Ready) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val snapshot = database.snapshot()
            mutableState.update {
                it.copy(
                    agents = snapshot.agents,
                    tasks = snapshot.tasks,
                    skills = database.skills(),
                    toolPermissions = database.toolPermissions(),
                    settings = snapshot.settings,
                    resources = governor.snapshot()
                )
            }
        }
    }

    fun updateTaskTitle(value: String) { mutableState.update { it.copy(taskTitle=value.take(120),error=null) } }
    fun updateTaskInput(value: String) { mutableState.update { it.copy(taskInput=value.take(40_000),error=null) } }
    fun selectDepartment(value: MasxDepartment?) { mutableState.update { it.copy(selectedDepartment=value) } }
    fun selectTask(id: String?) { mutableState.update { it.copy(selectedTaskId=id,result=null,error=null) } }

    fun createTask() {
        val state = mutableState.value
        if (state.isBusy) return
        val title = state.taskTitle.trim().ifBlank { state.taskInput.trim().take(60) }
        val input = state.taskInput.trim()
        if (title.isBlank() || input.isBlank()) {
            mutableState.update { it.copy(error="Enter a task title and task instructions.") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy=true,error=null) }
            try {
                val id = orchestrator.submit(title,input)
                mutableState.update { it.copy(taskTitle="",taskInput="",selectedTaskId=id) }
                refresh()
            } catch (error: Exception) {
                mutableState.update { it.copy(error=error.message ?: "Unable to create task.") }
            } finally {
                mutableState.update { it.copy(isBusy=false) }
            }
        }
    }

    fun runSelectedTask() {
        val id = mutableState.value.selectedTaskId ?: return
        if (mutableState.value.isBusy) return
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy=true,error=null,result=null) }
            try {
                val resources = governor.snapshot()
                mutableState.update { it.copy(resources=resources) }
                val (allowed,reason) = governor.canStartInference(resources)
                check(allowed) { reason }
                check(mutableState.value.modelReady) { "Load the local LiteRT-LM model in the chat screen before running society tasks." }
                val result = orchestrator.runTask(id).getOrThrow()
                mutableState.update { it.copy(result=result) }
            } catch (error: Exception) {
                mutableState.update { it.copy(error=error.message ?: "Task execution failed.") }
            } finally {
                mutableState.update { it.copy(isBusy=false) }
                refresh()
            }
        }
    }

    fun cancelSelectedTask() {
        val id = mutableState.value.selectedTaskId ?: return
        viewModelScope.launch {
            runCatching { database.transitionTask(id,TaskStatus.CANCELLED,error="Cancelled by user.") }
            refresh()
        }
    }

    fun setSetting(key: String, value: String) {
        viewModelScope.launch {
            runCatching { database.setSetting(key,value) }
                .onFailure { error -> mutableState.update { it.copy(error=error.message) } }
            refresh()
        }
    }

    fun setAgentStatus(agentId: String, status: AgentStatus) {
        viewModelScope.launch {
            val current = database.agents().firstOrNull { it.id==agentId } ?: return@launch
            database.upsertAgent(current.copy(status=status,version=current.version+1))
            database.appendAudit("user","AGENT_STATUS_CHANGED",agentId,"SUCCESS",status.name)
            refresh()
        }
    }

    fun installSkillDraft(id: String, name: String, description: String, instructions: String) {
        viewModelScope.launch {
            try {
                database.upsertSkill(
                    SkillManifest(id,name,1,description,instructions, emptySet(),SkillStatus.DRAFT)
                )
                database.appendAudit("user","SKILL_DRAFT_CREATED",id,"SUCCESS","Awaiting validation and approval.")
                refresh()
            } catch (error: Exception) {
                mutableState.update { it.copy(error=error.message ?: "Unable to save skill draft.") }
            }
        }
    }

    fun approveSkill(skillId: String) {
        viewModelScope.launch {
            runCatching {
                val skill = database.skills().firstOrNull { it.id == skillId } ?: error("Skill not found.")
                val validation = MasxSkillValidator.validate(skill, database.toolPermissions().map { it.toolId }.toSet())
                check(validation.valid) { validation.reason }
                database.setSkillStatus(skillId,SkillStatus.ACTIVE,approvedByUser=true)
            }
                .onFailure { error -> mutableState.update { it.copy(error=error.message) } }
            database.appendAudit("user","SKILL_ACTIVATED",skillId,"USER_APPROVED","Explicit approval from society screen.")
            refresh()
        }
    }

    fun setToolEnabled(toolId: String, enabled: Boolean) {
        viewModelScope.launch {
            val record = database.toolPermissions().firstOrNull { it.toolId == toolId } ?: return@launch
            database.setToolPermission(record.toolId,record.risk,enabled,record.approvalRequired)
            database.appendAudit("user","TOOL_PERMISSION_CHANGED",toolId,"SUCCESS","enabled=$enabled")
            refresh()
        }
    }

    fun refreshResources() {
        mutableState.update { it.copy(resources=governor.snapshot()) }
    }
}
