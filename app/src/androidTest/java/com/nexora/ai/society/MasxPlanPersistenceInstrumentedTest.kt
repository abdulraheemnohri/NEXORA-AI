package com.nexora.ai.society

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MasxPlanPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: MasxDatabase

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("nexora_masx.db")
        database = MasxDatabase(context)
        database.seedBuiltIns()
    }

    @After fun tearDown() {
        database.close()
        context.deleteDatabase("nexora_masx.db")
    }

    @Test fun approvalAtomicallyCreatesTasksAndPrerequisitesWithoutExecutingThem() {
        val draft = MasxPlanParser.parse(
            """{"summary":"Two local tasks.","steps":[
                {"id":"step-1","title":"Prepare","instructions":"Prepare the local workspace.","priority":"HIGH","depends_on":[]},
                {"id":"step-2","title":"Verify","instructions":"Verify the prepared workspace.","priority":"NORMAL","depends_on":["step-1"]}
            ]}"""
        )
        val planId = database.savePlanProposal("Prepare and verify a local workspace.", draft)
        assertEquals(TaskPlanStatus.PENDING_REVIEW, database.pendingPlans().single().status)

        val taskIds = database.approvePlanAndCreateTasks(planId)

        assertEquals(2, taskIds.size)
        assertEquals(TaskStatus.CREATED, database.task(taskIds[0])?.status)
        assertEquals(TaskStatus.CREATED, database.task(taskIds[1])?.status)
        assertEquals(listOf(taskIds[0]), database.dependencyIds(taskIds[1]))
        assertTrue(database.pendingPlans().isEmpty())
        assertTrue(runCatching { database.approvePlanAndCreateTasks(planId) }.isFailure)
    }

    @Test fun rejectedProposalCannotCreateTasks() {
        val draft = MasxPlanParser.parse(
            """{"summary":"One task.","steps":[
                {"id":"step-1","title":"Inspect","instructions":"Inspect local source.","priority":"NORMAL","depends_on":[]}
            ]}"""
        )
        val planId = database.savePlanProposal("Inspect the local source tree.", draft)

        assertTrue(database.rejectPlan(planId))
        assertTrue(runCatching { database.approvePlanAndCreateTasks(planId) }.isFailure)
        assertTrue(database.tasks().isEmpty())
    }

    @Test fun disabledSocietyCannotSaveOrApprovePlans() {
        val draft = MasxPlanParser.parse(
            """{"summary":"One task.","steps":[
                {"id":"step-1","title":"Inspect","instructions":"Inspect local source.","priority":"NORMAL","depends_on":[]}
            ]}"""
        )
        database.setSetting("society.enabled", "false")
        assertTrue(runCatching {
            database.savePlanProposal("Inspect the local source tree.", draft)
        }.isFailure)
        assertTrue(database.pendingPlans().isEmpty())

        database.setSetting("society.enabled", "true")
        val planId = database.savePlanProposal("Inspect the local source tree.", draft)
        database.setSetting("society.enabled", "false")
        assertTrue(runCatching { database.approvePlanAndCreateTasks(planId) }.isFailure)
        assertTrue(database.tasks().isEmpty())
        assertEquals(TaskPlanStatus.PENDING_REVIEW, database.pendingPlans().single().status)
    }

    @Test fun pendingProposalCountIsBounded() {
        val draft = MasxPlanParser.parse(
            """{"summary":"One task.","steps":[
                {"id":"step-1","title":"Inspect","instructions":"Inspect local source.","priority":"NORMAL","depends_on":[]}
            ]}"""
        )
        repeat(20) { index ->
            database.savePlanProposal("Review local planning item number $index.", draft, now = index.toLong())
        }
        assertEquals(20, database.pendingPlans(limit = 20).size)
        assertTrue(runCatching {
            database.savePlanProposal("This twenty-first proposal must be rejected.", draft)
        }.isFailure)
        assertEquals(20, database.pendingPlans(limit = 20).size)
    }

    @Test fun versionOneDatabaseReceivesTheAdditivePlanTableMigration() {
        database.close()
        val legacy = context.openOrCreateDatabase("nexora_masx.db", Context.MODE_PRIVATE, null)
        legacy.execSQL("DROP TABLE IF EXISTS task_plans")
        legacy.execSQL("DROP INDEX IF EXISTS index_task_plans_status_created")
        legacy.version = 1
        legacy.close()

        database = MasxDatabase(context)
        database.writableDatabase
        database.seedBuiltIns()
        val draft = MasxPlanParser.parse(
            """{"summary":"Migration works.","steps":[
                {"id":"step-1","title":"Check migration","instructions":"Save a proposal after upgrade.","priority":"NORMAL","depends_on":[]}
            ]}"""
        )
        database.savePlanProposal("Check additive migration works.", draft)
        assertEquals(TaskPlanStatus.PENDING_REVIEW, database.pendingPlans().single().status)
    }
}
