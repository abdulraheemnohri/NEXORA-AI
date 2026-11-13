package com.nexora.ai.society

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MasxDatabaseInstrumentedTest {
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

    @Test fun seedsUniqueAgentRegistryAcrossDepartments() {
        val agents=database.agents()
        assertEquals(MasxAgentCatalog.builtIns.size,agents.size)
        assertEquals(agents.size,agents.map { it.id }.toSet().size)
        assertEquals(MasxDepartment.entries.toSet(),agents.map { it.department }.toSet())
    }

    @Test fun agentMemoryIsIsolatedByOwner() {
        val first=database.agents().first { it.id=="engineering.developer" }
        val second=database.agents().first { it.id=="research.researcher" }
        val now=System.currentTimeMillis()
        database.putMemory(AgentMemory(UUID.randomUUID().toString(),MemoryScope.AGENT,first.id,"private-note","Only for engineering","test",now,now))
        assertTrue(database.memoriesForAgent(first,null).any { it.key=="private-note" })
        assertFalse(database.memoriesForAgent(second,null).any { it.key=="private-note" })
    }

    @Test fun taskTransitionsAreValidatedAndPersisted() {
        val now=System.currentTimeMillis()
        val task=MasxTask("task-test","Test task","Verify task transitions",TaskStatus.CREATED,TaskPriority.NORMAL,now,now)
        database.createTask(task)
        database.transitionTask(task.id,TaskStatus.PLANNING)
        database.transitionTask(task.id,TaskStatus.READY)
        database.transitionTask(task.id,TaskStatus.ASSIGNED,agentId="engineering.developer")
        database.transitionTask(task.id,TaskStatus.RUNNING)
        database.transitionTask(task.id,TaskStatus.REVIEW,result="reviewable")
        database.transitionTask(task.id,TaskStatus.COMPLETED,result="done")
        assertEquals(TaskStatus.COMPLETED,database.task(task.id)?.status)
        try {
            database.transitionTask(task.id,TaskStatus.RUNNING)
            throw AssertionError("Completed tasks must be terminal.")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun taskDependenciesBlockUntilCompleteAndRejectCycles() {
        val now=System.currentTimeMillis()
        val a=MasxTask("task-a","A","Task A",TaskStatus.CREATED,TaskPriority.NORMAL,now,now)
        val b=MasxTask("task-b","B","Task B",TaskStatus.CREATED,TaskPriority.NORMAL,now,now)
        val d=MasxTask("task-c","C","Task C",TaskStatus.CREATED,TaskPriority.NORMAL,now,now)
        database.createTask(a);database.createTask(b);database.createTask(d)
        database.addDependency(a.id,b.id)
        database.addDependency(b.id,d.id)
        assertFalse(database.dependenciesComplete(a.id))
        try {
            database.addDependency(d.id,a.id)
            throw AssertionError("Dependency cycle must be rejected.")
        } catch (_: IllegalStateException) { }
        database.transitionTask(d.id,TaskStatus.PLANNING)
        database.transitionTask(d.id,TaskStatus.READY)
        database.transitionTask(d.id,TaskStatus.ASSIGNED,agentId="engineering.developer")
        database.transitionTask(d.id,TaskStatus.RUNNING)
        database.transitionTask(d.id,TaskStatus.REVIEW,result="done")
        database.transitionTask(d.id,TaskStatus.COMPLETED,result="done")
        assertTrue(database.dependenciesComplete(b.id))
    }

    @Test fun messagesAreDurableAndAcknowledgedExactlyOnce() {
        val message=LampProtocol.newMessage("executive.commander","engineering.developer",LampType.TASK_ASSIGN,"payload")
        database.enqueue(message)
        assertEquals(message.id,database.pendingMessages().single().id)
        assertTrue(database.acknowledgeMessage(message.id))
        assertFalse(database.acknowledgeMessage(message.id))
        assertTrue(database.pendingMessages().isEmpty())
    }

    @Test fun skillActivationRequiresExplicitApproval() {
        val skill=SkillManifest("notes.writer","Notes writer",1,"Draft notes","Write a draft only.",emptySet(),SkillStatus.DRAFT)
        database.upsertSkill(skill)
        try {
            database.setSkillStatus(skill.id,SkillStatus.ACTIVE,approvedByUser=false)
            throw AssertionError("Skill activation must require approval.")
        } catch (_: IllegalArgumentException) { }
        assertTrue(database.setSkillStatus(skill.id,SkillStatus.ACTIVE,approvedByUser=true))
        assertEquals(SkillStatus.ACTIVE,database.skills().single().status)
    }

    @Test fun deletingMemoryRemovesOnlySelectedRecordAndAuditsWithoutContent() {
        val now=System.currentTimeMillis()
        val privateMemory=AgentMemory(
            "memory-private",MemoryScope.AGENT,"engineering.developer",
            "private-key","private-value-must-not-enter-audit","test",now,now
        )
        val sharedMemory=AgentMemory(
            "memory-shared",MemoryScope.USER_APPROVED,null,
            "shared-key","shared-value","test",now,now
        )
        database.putMemory(privateMemory)
        database.putMemory(sharedMemory,explicitlyUserApproved=true)

        assertTrue(database.deleteMemory(privateMemory.id))
        assertEquals(listOf(sharedMemory.id),database.allMemories().map { it.id })
        val deletionAudit=database.auditEvents().first { it.action=="MEMORY_DELETED" }
        assertEquals(privateMemory.id,deletionAudit.subjectId)
        assertFalse(deletionAudit.details.contains("private-value-must-not-enter-audit"))
        assertFalse(database.deleteMemory(privateMemory.id))
    }
}
