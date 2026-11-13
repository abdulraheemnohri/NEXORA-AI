package com.nexora.ai.society

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MasxGovernanceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: MasxDatabase

    @Before fun setUp() {
        context=ApplicationProvider.getApplicationContext()
        context.deleteDatabase("nexora_masx.db")
        database=MasxDatabase(context)
        database.seedBuiltIns()
    }

    @After fun tearDown() {
        database.close()
        context.deleteDatabase("nexora_masx.db")
    }

    @Test fun seedingDoesNotResetUserAgentOrSettingChanges() {
        val agent=database.agents().first { it.id=="engineering.developer" }
        database.upsertAgent(agent.copy(status=AgentStatus.PAUSED,version=agent.version+1))
        database.setSetting("society.mode","TEAM")
        database.setToolPermission("local.conversation.search",ToolRisk.READ_ONLY,true,false)
        database.seedBuiltIns()
        assertEquals(AgentStatus.PAUSED,database.agents().first { it.id==agent.id }.status)
        assertEquals("TEAM",database.setting("society.mode","SUPERVISOR"))
        assertTrue(database.toolAllowed("local.conversation.search",ToolRisk.READ_ONLY))
    }

    @Test fun modelRegistryAndRolesPersist() {
        val model=database.models().single()
        database.assignModelRole(ModelRole.AGENT_SOCIETY,model.id)
        assertEquals(model.id,database.modelRoleAssignments()[ModelRole.AGENT_SOCIETY])
    }

    @Test fun temporaryAgentsStartPausedAndNeedExplicitActivation() {
        val factory=MasxAgentFactory(database)
        val agent=factory.propose("My helper",MasxDepartment.PRODUCTIVITY,"Personal helper","Help organize approved tasks.")
        assertEquals(AgentStatus.PAUSED,agent.status)
        assertEquals(AgentStatus.ACTIVE,factory.approveAndActivate(agent.id,true).status)
    }

    @Test fun updatingAgentAndModelPreservesForeignKeyRoleAssignments() {
        val agent=database.agents().first { it.id=="engineering.developer" }
        val now=System.currentTimeMillis()
        val task=MasxTask("assigned-task","Assigned","Keep assignment",TaskStatus.CREATED,TaskPriority.NORMAL,now,now,assignedAgentId=agent.id)
        database.createTask(task)
        database.upsertAgent(agent.copy(status=AgentStatus.PAUSED,version=agent.version+1))
        assertEquals(agent.id,database.task(task.id)?.assignedAgentId)

        val model=database.models().single()
        database.assignModelRole(ModelRole.AGENT_SOCIETY,model.id)
        database.upsertModel(model.copy(status=ModelInstallStatus.READY,localPath="/private/model.litertlm",updatedAt=now))
        assertEquals(model.id,database.modelRoleAssignments()[ModelRole.AGENT_SOCIETY])
    }

    @Test fun evolutionReviewIsExplicitAndDoesNotExecuteChanges() {
        val id=database.createEvolutionProposal("Optimize prompts","Compare prompt variants with a regression test set.")
        try {
            database.reviewEvolutionProposal(id,true,explicitlyReviewed=false)
            throw AssertionError("Explicit review is required.")
        } catch (_: IllegalArgumentException) { }
        assertEquals("PROPOSED",database.evolutionProposals().single().status)
        assertTrue(database.reviewEvolutionProposal(id,true,explicitlyReviewed=true))
        assertEquals("APPROVED_FOR_IMPLEMENTATION",database.evolutionProposals().single().status)
    }
}
