package com.nexora.ai.society

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.ai.NexoraApplication
import com.nexora.ai.ai.RuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class MasxUiState(
    val agents: List<AgentProfile> = emptyList(),
    val tasks: List<MasxTask> = emptyList(),
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
    private val orchestrator = MasxOrchestrator(
        database,
        LiteRtMasxInferenceGateway(app.localModelRuntime, app.inferenceScheduler)
    )
    private val mutableState = MutableStateFlow(MasxUiState())
    val uiState = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            database.seedBuiltIns()
            orchestrator.recoverInterruptedTasks()
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
            runCatching { database.setSkillStatus(skillId,SkillStatus.ACTIVE,approvedByUser=true) }
                .onFailure { error -> mutableState.update { it.copy(error=error.message) } }
            database.appendAudit("user","SKILL_ACTIVATED",skillId,"USER_APPROVED","Explicit approval from society screen.")
            refresh()
        }
    }

    fun refreshResources() {
        mutableState.update { it.copy(resources=governor.snapshot()) }
    }
}
