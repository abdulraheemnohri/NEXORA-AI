package com.nexora.ai.society

/** Creates declarative temporary identities; it never creates OS processes or executable code. */
class MasxAgentFactory(private val database: MasxDatabase) {
    fun propose(
        name: String, department: MasxDepartment, role: String, instructions: String
    ): AgentProfile {
        require(name.isNotBlank() && name.length <= 80)
        require(role.isNotBlank() && role.length <= 120)
        require(instructions.isNotBlank() && instructions.length <= 8_000)
        val maxAgents=database.setting("society.max_agents","24").toIntOrNull()?.coerceIn(1,100) ?: 24
        check(database.agents().size < maxAgents) { "Society agent limit reached." }
        val slug=name.lowercase().replace(Regex("[^a-z0-9]+"),"-").trim('-').take(40)
        val id="temporary.$slug"
        check(database.agents().none { it.id==id }) { "An agent with this generated identity already exists." }
        val agent=AgentProfile(
            id=id,name=name.trim(),department=department,role=role.trim(),
            level=AgentLevel.TEMPORARY,instructions=instructions.trim(),
            capabilities=setOf("user-defined"),allowedTools=emptySet(),
            memoryScopes=setOf(MemoryScope.AGENT,MemoryScope.TASK),
            status=AgentStatus.PAUSED
        )
        database.upsertAgent(agent)
        database.appendAudit("user","AGENT_PROPOSED",id,"PENDING_APPROVAL","Temporary agent created disabled.")
        return agent
    }

    fun approveAndActivate(agentId: String, explicitlyApproved: Boolean): AgentProfile {
        require(explicitlyApproved) { "Activating a temporary agent requires explicit user approval." }
        val agent=database.agents().firstOrNull { it.id==agentId } ?: error("Agent not found.")
        require(agent.level==AgentLevel.TEMPORARY) { "Only temporary agents use this approval path." }
        val active=agent.copy(status=AgentStatus.ACTIVE,version=agent.version+1)
        database.upsertAgent(active)
        database.appendAudit("user","AGENT_ACTIVATED",agentId,"USER_APPROVED","Temporary agent explicitly activated.")
        return active
    }
}
