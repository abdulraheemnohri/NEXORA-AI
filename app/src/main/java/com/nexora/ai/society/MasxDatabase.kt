package com.nexora.ai.society

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import java.util.UUID

/**
 * MAS-X durable store. Kept separate from the existing chat database so the society
 * can be introduced without risking conversation migrations. All calls are synchronous:
 * invoke them from Dispatchers.IO.
 */
class MasxDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DB_NAME, null, DB_VERSION
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE agents(
            id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, department TEXT NOT NULL,
            role TEXT NOT NULL, level TEXT NOT NULL, instructions TEXT NOT NULL,
            capabilities TEXT NOT NULL, allowed_tools TEXT NOT NULL, memory_scopes TEXT NOT NULL,
            status TEXT NOT NULL, version INTEGER NOT NULL, updated_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE tasks(
            id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, input TEXT NOT NULL,
            status TEXT NOT NULL, priority TEXT NOT NULL, created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL, assigned_agent_id TEXT, parent_task_id TEXT,
            attempt INTEGER NOT NULL DEFAULT 0, max_attempts INTEGER NOT NULL DEFAULT 2,
            result TEXT, error TEXT,
            FOREIGN KEY(assigned_agent_id) REFERENCES agents(id) ON DELETE SET NULL,
            FOREIGN KEY(parent_task_id) REFERENCES tasks(id) ON DELETE SET NULL)""")
        db.execSQL("CREATE INDEX index_tasks_status_updated ON tasks(status, updated_at)")
        db.execSQL("""CREATE TABLE task_dependencies(
            task_id TEXT NOT NULL, depends_on_task_id TEXT NOT NULL,
            PRIMARY KEY(task_id, depends_on_task_id),
            FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE,
            FOREIGN KEY(depends_on_task_id) REFERENCES tasks(id) ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE messages(
            id TEXT PRIMARY KEY NOT NULL, task_id TEXT, correlation_id TEXT,
            sender TEXT NOT NULL, recipient TEXT, type TEXT NOT NULL, priority TEXT NOT NULL,
            created_at INTEGER NOT NULL, expires_at INTEGER, payload TEXT NOT NULL,
            attempt INTEGER NOT NULL DEFAULT 0, delivery_state TEXT NOT NULL DEFAULT 'PENDING',
            last_error TEXT,
            FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE SET NULL)""")
        db.execSQL("CREATE INDEX index_messages_delivery ON messages(delivery_state, priority, created_at)")
        db.execSQL("""CREATE TABLE memories(
            id TEXT PRIMARY KEY NOT NULL, scope TEXT NOT NULL, owner_id TEXT, memory_key TEXT NOT NULL,
            value TEXT NOT NULL, provenance TEXT NOT NULL, created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL, UNIQUE(scope, owner_id, memory_key))""")
        db.execSQL("CREATE INDEX index_memories_scope_owner ON memories(scope, owner_id)")
        db.execSQL("""CREATE TABLE skills(
            id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, version INTEGER NOT NULL,
            description TEXT NOT NULL, instructions TEXT NOT NULL, required_tools TEXT NOT NULL,
            status TEXT NOT NULL, checksum TEXT, updated_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE agent_skills(
            agent_id TEXT NOT NULL, skill_id TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1,
            PRIMARY KEY(agent_id, skill_id),
            FOREIGN KEY(agent_id) REFERENCES agents(id) ON DELETE CASCADE,
            FOREIGN KEY(skill_id) REFERENCES skills(id) ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE tool_permissions(
            tool_id TEXT PRIMARY KEY NOT NULL, risk TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 0,
            approval_required INTEGER NOT NULL DEFAULT 1, updated_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE checkpoints(
            task_id TEXT PRIMARY KEY NOT NULL, checkpoint TEXT NOT NULL, updated_at INTEGER NOT NULL,
            FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE settings(
            setting_key TEXT PRIMARY KEY NOT NULL, setting_value TEXT NOT NULL, updated_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE audit(
            id TEXT PRIMARY KEY NOT NULL, actor_id TEXT, action TEXT NOT NULL,
            subject_id TEXT, outcome TEXT NOT NULL, details TEXT NOT NULL, created_at INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX index_audit_created ON audit(created_at DESC)")
        db.execSQL("""CREATE TABLE metrics(
            id TEXT PRIMARY KEY NOT NULL, agent_id TEXT NOT NULL, task_id TEXT,
            metric TEXT NOT NULL, value REAL NOT NULL, recorded_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE evolution_proposals(
            id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL,
            status TEXT NOT NULL, created_at INTEGER NOT NULL, reviewed_at INTEGER)""")
        db.execSQL("""CREATE TABLE inference_queue(
            id TEXT PRIMARY KEY NOT NULL, task_id TEXT, agent_id TEXT NOT NULL,
            priority TEXT NOT NULL, state TEXT NOT NULL, created_at INTEGER NOT NULL,
            started_at INTEGER, finished_at INTEGER)""")
        db.execSQL("CREATE INDEX index_inference_queue_state ON inference_queue(state, priority, created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Additive-only. This database is independent of the chat database.
        if (oldVersion < 2) db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_agent ON tasks(assigned_agent_id)")
    }

    fun seedBuiltIns(now: Long = System.currentTimeMillis()) {
        writableDatabase.beginTransaction()
        try {
            MasxAgentCatalog.builtIns.forEach { upsertAgent(it, now) }
            setSetting("society.enabled", "true", now)
            setSetting("society.mode", "SUPERVISOR", now)
            setSetting("society.max_agents", "24", now)
            setSetting("inference.max_concurrent", "1", now)
            setSetting("tasks.max_attempts", "2", now)
            setSetting("security.require_approval_for_writes", "true", now)
            setSetting("memory.allow_user_approved", "false", now)
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    fun upsertAgent(agent: AgentProfile, now: Long = System.currentTimeMillis()) {
        val values = ContentValues().apply {
            put("id", agent.id); put("name", agent.name); put("department", agent.department.id)
            put("role", agent.role); put("level", agent.level.name); put("instructions", agent.instructions)
            put("capabilities", json(agent.capabilities)); put("allowed_tools", json(agent.allowedTools))
            put("memory_scopes", json(agent.memoryScopes.map { it.name }.toSet()))
            put("status", agent.status.name); put("version", agent.version); put("updated_at", now)
        }
        writableDatabase.insertWithOnConflict("agents", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun agents(): List<AgentProfile> = readableDatabase.rawQuery(
        "SELECT id,name,department,role,level,instructions,capabilities,allowed_tools,memory_scopes,status,version FROM agents ORDER BY department,name",
        null
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val department = MasxDepartment.entries.firstOrNull { it.id == c.getString(2) } ?: continue
                add(AgentProfile(
                    id=c.getString(0), name=c.getString(1), department=department, role=c.getString(3),
                    level=AgentLevel.valueOf(c.getString(4)), instructions=c.getString(5),
                    capabilities=parseSet(c.getString(6)), allowedTools=parseSet(c.getString(7)),
                    memoryScopes=parseSet(c.getString(8)).mapNotNull { runCatching { MemoryScope.valueOf(it) }.getOrNull() }.toSet(),
                    status=AgentStatus.valueOf(c.getString(9)), version=c.getInt(10)
                ))
            }
        }
    }

    fun createTask(task: MasxTask) {
        val values = ContentValues().apply {
            put("id", task.id); put("title", task.title); put("input", task.input)
            put("status", task.status.name); put("priority", task.priority.name)
            put("created_at", task.createdAt); put("updated_at", task.updatedAt)
            put("assigned_agent_id", task.assignedAgentId); put("parent_task_id", task.parentTaskId)
            put("attempt", task.attempt); put("max_attempts", task.maxAttempts)
            put("result", task.result); put("error", task.error)
        }
        writableDatabase.insertOrThrow("tasks", null, values)
        appendAudit(null, "TASK_CREATED", task.id, "SUCCESS", "Task created")
    }

    fun task(id: String): MasxTask? = readableDatabase.rawQuery(
        "SELECT id,title,input,status,priority,created_at,updated_at,assigned_agent_id,parent_task_id,attempt,max_attempts,result,error FROM tasks WHERE id=?",
        arrayOf(id)
    ).use { c -> if (c.moveToFirst()) c.toTask() else null }

    fun tasks(limit: Int = 100): List<MasxTask> = readableDatabase.rawQuery(
        "SELECT id,title,input,status,priority,created_at,updated_at,assigned_agent_id,parent_task_id,attempt,max_attempts,result,error FROM tasks ORDER BY updated_at DESC LIMIT ?",
        arrayOf(limit.coerceIn(1, 500).toString())
    ).use { c -> buildList { while (c.moveToNext()) add(c.toTask()) } }

    fun recoverableTasks(): List<MasxTask> = readableDatabase.rawQuery(
        "SELECT id,title,input,status,priority,created_at,updated_at,assigned_agent_id,parent_task_id,attempt,max_attempts,result,error FROM tasks WHERE status IN ('CREATED','PLANNING','READY','ASSIGNED','RUNNING','RECOVERING','REWORK') ORDER BY priority DESC,created_at ASC",
        null
    ).use { c -> buildList { while (c.moveToNext()) add(c.toTask()) } }

    fun transitionTask(
        id: String, next: TaskStatus, agentId: String? = null,
        result: String? = null, error: String? = null,
        attempt: Int? = null, now: Long = System.currentTimeMillis()
    ): Boolean {
        val current = task(id) ?: return false
        require(next in allowedTransitions.getValue(current.status)) {
            "Invalid task transition: ${current.status} -> $next"
        }
        val values = ContentValues().apply {
            put("status", next.name); put("updated_at", now)
            if (agentId != null) put("assigned_agent_id", agentId)
            if (result != null) put("result", result)
            if (error != null) put("error", error)
            if (attempt != null) put("attempt", attempt)
        }
        val changed = writableDatabase.update("tasks", values, "id=?", arrayOf(id)) > 0
        if (changed) appendAudit(agentId, "TASK_${next.name}", id, "SUCCESS", error ?: "")
        return changed
    }

    fun addDependency(taskId: String, dependsOn: String) {
        require(taskId != dependsOn) { "A task cannot depend on itself." }
        writableDatabase.execSQL(
            "INSERT OR IGNORE INTO task_dependencies(task_id,depends_on_task_id) VALUES(?,?)",
            arrayOf(taskId, dependsOn)
        )
    }

    fun dependenciesComplete(taskId: String): Boolean = readableDatabase.rawQuery(
        """SELECT COUNT(*) FROM task_dependencies d JOIN tasks t ON t.id=d.depends_on_task_id
           WHERE d.task_id=? AND t.status!='COMPLETED'""".trimIndent(), arrayOf(taskId)
    ).use { it.moveToFirst() && it.getInt(0) == 0 }

    fun saveCheckpoint(taskId: String, payload: String, now: Long = System.currentTimeMillis()) {
        require(payload.length <= 32_000)
        val values = ContentValues().apply { put("task_id",taskId); put("checkpoint",payload); put("updated_at",now) }
        writableDatabase.insertWithOnConflict("checkpoints", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun checkpoint(taskId: String): String? = readableDatabase.rawQuery(
        "SELECT checkpoint FROM checkpoints WHERE task_id=?", arrayOf(taskId)
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    fun enqueue(message: LampMessage) {
        val values = ContentValues().apply {
            put("id",message.id); put("task_id",message.taskId); put("correlation_id",message.correlationId)
            put("sender",message.senderAgentId); put("recipient",message.recipientAgentId)
            put("type",message.type.name); put("priority",message.priority.name)
            put("created_at",message.createdAt); put("expires_at",message.expiresAt)
            put("payload",message.payload); put("attempt",message.attempt); put("delivery_state","PENDING")
        }
        writableDatabase.insertOrThrow("messages", null, values)
    }

    fun pendingMessages(now: Long = System.currentTimeMillis(), limit: Int = 100): List<LampMessage> =
        readableDatabase.rawQuery(
            """SELECT id,task_id,correlation_id,sender,recipient,type,priority,created_at,expires_at,payload,attempt
               FROM messages WHERE delivery_state='PENDING' AND (expires_at IS NULL OR expires_at>?)
               ORDER BY CASE priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'NORMAL' THEN 2 ELSE 3 END,created_at
               LIMIT ?""".trimIndent(), arrayOf(now.toString(),limit.coerceIn(1,500).toString())
        ).use { c -> buildList { while(c.moveToNext()) add(c.toLamp()) } }

    fun acknowledgeMessage(id: String): Boolean {
        val v = ContentValues().apply { put("delivery_state","DELIVERED") }
        return writableDatabase.update("messages",v,"id=? AND delivery_state='PENDING'",arrayOf(id)) > 0
    }

    fun failMessage(id: String, error: String, maxAttempts: Int = 3): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val attempt = db.rawQuery("SELECT attempt FROM messages WHERE id=? AND delivery_state='PENDING'", arrayOf(id))
                .use { if (it.moveToFirst()) it.getInt(0)+1 else return false }
            val values = ContentValues().apply {
                put("attempt",attempt); put("last_error",error.take(500))
                put("delivery_state",if(attempt >= maxAttempts.coerceIn(1,5)) "DEAD" else "PENDING")
            }
            val updated = db.update("messages",values,"id=?",arrayOf(id)) > 0
            db.setTransactionSuccessful()
            return updated
        } finally { db.endTransaction() }
    }

    /** Agent-visible memory is filtered by scope and exact owner; no cross-agent reads. */
    fun memoriesForAgent(agent: AgentProfile, taskId: String?, includeUserApproved: Boolean = false): List<AgentMemory> {
        val scopes = mutableListOf<Pair<String,String?>>()
        scopes += MemoryScope.GLOBAL.name to null
        scopes += MemoryScope.DEPARTMENT.name to agent.department.id
        scopes += MemoryScope.AGENT.name to agent.id
        if (!taskId.isNullOrBlank()) scopes += MemoryScope.TASK.name to taskId
        if (includeUserApproved) scopes += MemoryScope.USER_APPROVED.name to null
        return scopes.flatMap { (scope,owner) ->
            val sql = if (owner == null) "SELECT id,scope,owner_id,memory_key,value,provenance,created_at,updated_at FROM memories WHERE scope=? AND owner_id IS NULL ORDER BY updated_at DESC LIMIT 100"
                else "SELECT id,scope,owner_id,memory_key,value,provenance,created_at,updated_at FROM memories WHERE scope=? AND owner_id=? ORDER BY updated_at DESC LIMIT 100"
            readableDatabase.rawQuery(sql, if(owner==null) arrayOf(scope) else arrayOf(scope,owner)).use { c ->
                buildList { while(c.moveToNext()) add(c.toMemory()) }
            }
        }
    }

    fun putMemory(memory: AgentMemory, explicitlyUserApproved: Boolean = false) {
        require(memory.scope != MemoryScope.USER_APPROVED || explicitlyUserApproved) {
            "User-approved memory can only be written through an explicit approval path."
        }
        require(memory.scope != MemoryScope.GLOBAL || explicitlyUserApproved) {
            "Global memory requires explicit approval."
        }
        val values = ContentValues().apply {
            put("id",memory.id); put("scope",memory.scope.name); put("owner_id",memory.ownerId)
            put("memory_key",memory.key); put("value",memory.value); put("provenance",memory.provenance)
            put("created_at",memory.createdAt); put("updated_at",memory.updatedAt)
        }
        writableDatabase.insertWithOnConflict("memories",null,values,SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun upsertSkill(skill: SkillManifest, now: Long = System.currentTimeMillis()) {
        val values = ContentValues().apply {
            put("id",skill.id); put("name",skill.name); put("version",skill.version)
            put("description",skill.description); put("instructions",skill.instructions)
            put("required_tools",json(skill.requiredTools)); put("status",skill.status.name)
            put("checksum",skill.checksum); put("updated_at",now)
        }
        writableDatabase.insertWithOnConflict("skills",null,values,SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun setSkillStatus(skillId: String, status: SkillStatus, approvedByUser: Boolean): Boolean {
        require(status != SkillStatus.ACTIVE || approvedByUser) { "Activating a skill requires explicit user approval." }
        val v = ContentValues().apply { put("status",status.name); put("updated_at",System.currentTimeMillis()) }
        return writableDatabase.update("skills",v,"id=?",arrayOf(skillId)) > 0
    }

    fun skills(): List<SkillManifest> = readableDatabase.rawQuery(
        "SELECT id,name,version,description,instructions,required_tools,status,checksum FROM skills ORDER BY name",null
    ).use { c -> buildList {
        while(c.moveToNext()) add(SkillManifest(c.getString(0),c.getString(1),c.getInt(2),c.getString(3),c.getString(4),
            parseSet(c.getString(5)),SkillStatus.valueOf(c.getString(6)),c.getString(7)))
    } }

    fun assignSkill(agentId: String, skillId: String, enabled: Boolean) {
        val values = ContentValues().apply { put("agent_id",agentId); put("skill_id",skillId); put("enabled",if(enabled)1 else 0) }
        writableDatabase.insertWithOnConflict("agent_skills",null,values,SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun setToolPermission(toolId: String, risk: ToolRisk, enabled: Boolean, approvalRequired: Boolean) {
        val values = ContentValues().apply {
            put("tool_id",toolId); put("risk",risk.name); put("enabled",if(enabled)1 else 0)
            put("approval_required",if(approvalRequired)1 else 0); put("updated_at",System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("tool_permissions",null,values,SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun toolAllowed(toolId: String, risk: ToolRisk): Boolean = readableDatabase.rawQuery(
        "SELECT enabled,risk,approval_required FROM tool_permissions WHERE tool_id=?",arrayOf(toolId)
    ).use { c -> c.moveToFirst() && c.getInt(0)==1 && c.getString(1)==risk.name }

    fun setSetting(key: String, value: String, now: Long = System.currentTimeMillis()) {
        require(key.matches(Regex("[a-zA-Z0-9._-]{2,100}")) && value.length <= 2_000)
        val v=ContentValues().apply { put("setting_key",key); put("setting_value",value); put("updated_at",now) }
        writableDatabase.insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun setting(key: String, fallback: String): String = readableDatabase.rawQuery(
        "SELECT setting_value FROM settings WHERE setting_key=?",arrayOf(key)
    ).use { if(it.moveToFirst()) it.getString(0) else fallback }

    fun settings(): Map<String,String> = readableDatabase.rawQuery("SELECT setting_key,setting_value FROM settings",null).use { c ->
        buildMap { while(c.moveToNext()) put(c.getString(0),c.getString(1)) }
    }

    fun appendAudit(actor: String?, action: String, subject: String?, outcome: String, details: String) {
        val v=ContentValues().apply {
            put("id",UUID.randomUUID().toString()); put("actor_id",actor); put("action",action)
            put("subject_id",subject); put("outcome",outcome); put("details",details.take(2_000))
            put("created_at",System.currentTimeMillis())
        }
        writableDatabase.insertOrThrow("audit",null,v)
    }

    fun recordMetric(metric: AgentMetric) {
        val v=ContentValues().apply {
            put("id",UUID.randomUUID().toString()); put("agent_id",metric.agentId); put("task_id",metric.taskId)
            put("metric",metric.metric.take(80)); put("value",metric.value); put("recorded_at",metric.recordedAt)
        }
        writableDatabase.insertOrThrow("metrics",null,v)
    }

    fun snapshot(): SocietySnapshot {
        val allTasks=tasks()
        val queued=pendingMessages().size
        return SocietySnapshot(agents(),allTasks,queued,allTasks.count { it.status==TaskStatus.RUNNING },settings())
    }

    private fun android.database.Cursor.toTask() = MasxTask(
        getString(0),getString(1),getString(2),TaskStatus.valueOf(getString(3)),TaskPriority.valueOf(getString(4)),
        getLong(5),getLong(6),getString(7),getString(8),getInt(9),getInt(10),getString(11),getString(12)
    )
    private fun android.database.Cursor.toLamp() = LampMessage(
        getString(0),getString(1),getString(2),getString(3),getString(4),LampType.valueOf(getString(5)),
        TaskPriority.valueOf(getString(6)),getLong(7),if(isNull(8)) null else getLong(8),getString(9),getInt(10)
    )
    private fun android.database.Cursor.toMemory() = AgentMemory(
        getString(0),MemoryScope.valueOf(getString(1)),getString(2),getString(3),getString(4),
        getString(5),getLong(6),getLong(7)
    )

    private fun json(values: Set<String>) = JSONArray(values.sorted()).toString()
    private fun parseSet(raw: String): Set<String> {
        val array=JSONArray(raw)
        return buildSet { for(i in 0 until array.length()) add(array.getString(i)) }
    }

    companion object {
        private const val DB_NAME = "nexora_masx.db"
        private const val DB_VERSION = 1
        private val allowedTransitions = mapOf(
            TaskStatus.CREATED to setOf(TaskStatus.PLANNING,TaskStatus.READY,TaskStatus.CANCELLED),
            TaskStatus.PLANNING to setOf(TaskStatus.READY,TaskStatus.FAILED,TaskStatus.CANCELLED,TaskStatus.PAUSED),
            TaskStatus.READY to setOf(TaskStatus.ASSIGNED,TaskStatus.CANCELLED,TaskStatus.PAUSED),
            TaskStatus.ASSIGNED to setOf(TaskStatus.RUNNING,TaskStatus.WAITING_APPROVAL,TaskStatus.FAILED,TaskStatus.CANCELLED,TaskStatus.PAUSED),
            TaskStatus.RUNNING to setOf(TaskStatus.REVIEW,TaskStatus.WAITING_APPROVAL,TaskStatus.FAILED,TaskStatus.PAUSED,TaskStatus.CANCELLED,TaskStatus.RECOVERING),
            TaskStatus.WAITING_APPROVAL to setOf(TaskStatus.ASSIGNED,TaskStatus.RUNNING,TaskStatus.CANCELLED,TaskStatus.FAILED),
            TaskStatus.REVIEW to setOf(TaskStatus.COMPLETED,TaskStatus.REWORK,TaskStatus.FAILED),
            TaskStatus.REWORK to setOf(TaskStatus.ASSIGNED,TaskStatus.FAILED,TaskStatus.CANCELLED),
            TaskStatus.RECOVERING to setOf(TaskStatus.READY,TaskStatus.ASSIGNED,TaskStatus.FAILED,TaskStatus.CANCELLED),
            TaskStatus.PAUSED to setOf(TaskStatus.READY,TaskStatus.CANCELLED),
            TaskStatus.FAILED to setOf(TaskStatus.RECOVERING),
            TaskStatus.COMPLETED to emptySet(),
            TaskStatus.CANCELLED to emptySet()
        )
    }
}
