package com.nexora.ai.society

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MasxOrchestratorInstrumentedTest {
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

    @Test fun supervisorDelegatesAndPersistsCompletedWorkerTask() = runBlocking {
        val orchestrator=MasxOrchestrator(database,MasxInferenceGateway { agent,_,_,_ ->
            assertEquals("engineering.developer",agent.id)
            "Implementation analysis completed."
        })
        val taskId=orchestrator.submit("Review Kotlin implementation","Review this Android Kotlin code.")
        val result=orchestrator.runTask(taskId).getOrThrow()
        assertEquals("Implementation analysis completed.",result)
        assertEquals(TaskStatus.COMPLETED,database.task(taskId)?.status)
        assertEquals("engineering.developer",database.task(taskId)?.assignedAgentId)
    }
}
