package com.nexora.ai.society

/** Stable identifiers persisted in the local MAS-X database. */
enum class MasxDepartment(val id: String, val title: String) {
    EXECUTIVE("executive", "Executive"),
    INTELLIGENCE("intelligence", "Intelligence"),
    RESEARCH("research", "Research"),
    ENGINEERING("engineering", "Engineering"),
    KNOWLEDGE("knowledge", "Knowledge"),
    EVOLUTION("evolution", "Evolution"),
    OPERATIONS("operations", "Operations"),
    SECURITY("security", "Security"),
    QUALITY("quality", "Quality"),
    PRODUCTIVITY("productivity", "Productivity"),
    CREATIVE("creative", "Creative"),
    INFRASTRUCTURE("infrastructure", "Infrastructure")
}

enum class AgentStatus { ACTIVE, IDLE, PAUSED, DISABLED, ARCHIVED }
enum class AgentLevel { SUPERVISOR, SPECIALIST, WORKER, TEMPORARY }
enum class TaskStatus {
    CREATED, PLANNING, READY, ASSIGNED, RUNNING, WAITING_APPROVAL,
    REVIEW, REWORK, COMPLETED, FAILED, PAUSED, CANCELLED, RECOVERING
}
enum class TaskPriority { LOW, NORMAL, HIGH, CRITICAL }
enum class LampType {
    TASK_CREATE, TASK_ASSIGN, TASK_ACCEPT, TASK_REJECT, TASK_PROGRESS,
    TASK_COMPLETE, TASK_FAILED, HELP_REQUEST, KNOWLEDGE_SHARE,
    REVIEW_REQUEST, REVIEW_RESULT, APPROVAL_REQUEST, APPROVAL_RESULT,
    AGENT_HEARTBEAT, AGENT_PAUSE, AGENT_RESUME
}
enum class MemoryScope { GLOBAL, DEPARTMENT, AGENT, TASK, USER_APPROVED }
enum class SkillStatus { DRAFT, VALIDATING, ACTIVE, DISABLED, REVOKED }
enum class ToolRisk { READ_ONLY, LOCAL_WRITE, EXTERNAL, DESTRUCTIVE }

data class AgentProfile(
    val id: String,
    val name: String,
    val department: MasxDepartment,
    val role: String,
    val level: AgentLevel,
    val instructions: String,
    val capabilities: Set<String>,
    val allowedTools: Set<String> = emptySet(),
    val memoryScopes: Set<MemoryScope> = setOf(MemoryScope.AGENT, MemoryScope.TASK),
    val status: AgentStatus = AgentStatus.ACTIVE,
    val version: Int = 1
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]{2,63}")))
        require(name.isNotBlank() && name.length <= 80)
        require(role.isNotBlank() && role.length <= 120)
        require(instructions.length <= 8_000)
        require(version > 0)
    }
}

data class MasxTask(
    val id: String,
    val title: String,
    val input: String,
    val status: TaskStatus,
    val priority: TaskPriority,
    val createdAt: Long,
    val updatedAt: Long,
    val assignedAgentId: String? = null,
    val parentTaskId: String? = null,
    val attempt: Int = 0,
    val maxAttempts: Int = 2,
    val result: String? = null,
    val error: String? = null
) {
    init {
        require(id.isNotBlank() && title.isNotBlank())
        require(input.isNotBlank() && input.length <= 40_000)
        require(attempt >= 0 && maxAttempts in 0..5)
    }
}

data class LampMessage(
    val id: String,
    val taskId: String?,
    val correlationId: String?,
    val senderAgentId: String,
    val recipientAgentId: String?,
    val type: LampType,
    val priority: TaskPriority,
    val createdAt: Long,
    val expiresAt: Long?,
    val payload: String,
    val attempt: Int = 0
) {
    init {
        require(id.isNotBlank() && senderAgentId.isNotBlank())
        require(payload.length <= 32_000)
        require(attempt >= 0)
        require(expiresAt == null || expiresAt >= createdAt)
    }
}

data class AgentMemory(
    val id: String,
    val scope: MemoryScope,
    val ownerId: String?,
    val key: String,
    val value: String,
    val provenance: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    init {
        require(key.isNotBlank() && key.length <= 160)
        require(value.length <= 20_000)
        require(provenance.length <= 500)
    }
}

data class SkillManifest(
    val id: String,
    val name: String,
    val version: Int,
    val description: String,
    val instructions: String,
    val requiredTools: Set<String>,
    val status: SkillStatus,
    val checksum: String? = null
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]{2,63}")))
        require(name.isNotBlank() && version > 0)
        require(description.length <= 1_000 && instructions.length <= 12_000)
    }
}

enum class ModelInstallStatus { CANDIDATE, DOWNLOADING, INSTALLED, READY, FAILED, REMOVED }
enum class ModelRole { MAIN_ASSISTANT, REASONING, FAST_RESPONSE, CODING, RESEARCH, FALLBACK, AGENT_SOCIETY }

data class ModelProfile(
    val id: String, val name: String, val family: String, val runtimeId: String,
    val format: String, val source: String, val localPath: String?, val quantization: String?,
    val sizeBytes: Long?, val contextLength: Int?, val capabilities: Set<String>,
    val status: ModelInstallStatus, val checksum: String?, val license: String?, val updatedAt: Long
)

data class MasxAuditEvent(
    val id: String, val actorId: String?, val action: String, val subjectId: String?,
    val outcome: String, val details: String, val createdAt: Long
)

data class MasxMetricRecord(
    val id: String, val agentId: String, val taskId: String?, val metric: String,
    val value: Double, val recordedAt: Long
)

data class EvolutionProposal(
    val id: String, val title: String, val description: String, val status: String,
    val createdAt: Long, val reviewedAt: Long?
)

data class ToolPermissionRecord(\n    val toolId: String, val risk: ToolRisk, val enabled: Boolean, val approvalRequired: Boolean\n)\n\ndata class AgentMetric(
    val agentId: String,
    val taskId: String?,
    val metric: String,
    val value: Double,
    val recordedAt: Long
)

data class SocietySnapshot(
    val agents: List<AgentProfile>,
    val tasks: List<MasxTask>,
    val queuedMessages: Int,
    val activeTasks: Int,
    val settings: Map<String, String>
)
