package com.nexora.ai.society

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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
    @Test fun cancellingRunningTaskMarksItCancelledWithoutRetry() = runBlocking {
        val inferenceStarted = CompletableDeferred<Unit>()
        val orchestrator = MasxOrchestrator(database, MasxInferenceGateway { _, _, _, _ ->
            inferenceStarted.complete(Unit)
            awaitCancellation()
        })
        val taskId = orchestrator.submit("Cancelable task", "Wait for a cancellation test.")
        val execution = launch { orchestrator.runTask(taskId) }

        inferenceStarted.await()
        execution.cancelAndJoin()

        val cancelled = database.task(taskId)!!
        assertEquals(TaskStatus.CANCELLED, cancelled.status)
        assertEquals(0, cancelled.attempt)
    }

    @Test fun readyQueueRunsPrerequisitesBeforeTheirDependents() = runBlocking {
        val now = System.currentTimeMillis()
        listOf(
            MasxTask("queue-a", "A", "Task A", TaskStatus.CREATED, TaskPriority.NORMAL, now, now),
            MasxTask("queue-b", "B", "Task B", TaskStatus.CREATED, TaskPriority.NORMAL, now + 1, now + 1),
            MasxTask("queue-c", "C", "Task C", TaskStatus.CREATED, TaskPriority.NORMAL, now + 2, now + 2)
        ).forEach(database::createTask)
        database.addDependency("queue-a", "queue-b")
        database.addDependency("queue-b", "queue-c")
        val executionOrder = mutableListOf<String>()
        val orchestrator = MasxOrchestrator(database, MasxInferenceGateway { _, taskId, _, _ ->
            executionOrder += taskId
            "Completed $taskId"
        })

        val report = orchestrator.runReadyTasks()

        assertEquals(listOf("queue-c", "queue-b", "queue-a"), executionOrder)
        assertEquals(executionOrder, report.completedTaskIds)
        assertEquals(3, report.attemptedTaskIds.size)
        assertEquals(0, report.remainingRunnableCount)
    }

    @Test fun readyQueueUsesPriorityAndHonorsThePerRunLimit() = runBlocking {
        val now = System.currentTimeMillis()
        listOf(
            MasxTask("queue-low", "Low", "Low priority", TaskStatus.CREATED, TaskPriority.LOW, now, now),
            MasxTask("queue-normal", "Normal", "Normal priority", TaskStatus.CREATED, TaskPriority.NORMAL, now + 1, now + 1),
            MasxTask("queue-high", "High", "High priority", TaskStatus.CREATED, TaskPriority.HIGH, now + 2, now + 2)
        ).forEach(database::createTask)
        val executionOrder = mutableListOf<String>()
        val orchestrator = MasxOrchestrator(database, MasxInferenceGateway { _, taskId, _, _ ->
            executionOrder += taskId
            "Completed $taskId"
        })

        val report = orchestrator.runReadyTasks(maxTasks = 2)

        assertEquals(listOf("queue-high", "queue-normal"), executionOrder)
        assertEquals(1, report.remainingRunnableCount)
        assertEquals(true, report.limitReached)
    }

    @Test fun readyQueueAttemptsAFailedTaskOnlyOncePerBatch() = runBlocking {
        val now = System.currentTimeMillis()
        database.createTask(
            MasxTask(
                "queue-failing", "Failing", "Failure fixture",
                TaskStatus.CREATED, TaskPriority.NORMAL, now, now, maxAttempts = 2
            )
        )
        var inferenceCalls = 0
        val orchestrator = MasxOrchestrator(database, MasxInferenceGateway { _, _, _, _ ->
            inferenceCalls += 1
            error("Expected deterministic test failure")
        })

        val report = orchestrator.runReadyTasks()

        assertEquals(1, inferenceCalls)
        assertEquals(listOf("queue-failing"), report.failedTaskIds)
        assertEquals(1, report.remainingRunnableCount)
    }

    @Test fun dependencyCannotBeChangedAfterTaskIsAssigned() = runBlocking {
        val now = System.currentTimeMillis()
        database.createTask(MasxTask("locked-task", "Locked", "Task", TaskStatus.CREATED, TaskPriority.NORMAL, now, now))
        database.createTask(MasxTask("prerequisite", "Prerequisite", "Task", TaskStatus.CREATED, TaskPriority.NORMAL, now, now))
        database.transitionTask("locked-task", TaskStatus.PLANNING)
        database.transitionTask("locked-task", TaskStatus.READY)
        database.transitionTask("locked-task", TaskStatus.ASSIGNED, agentId = "engineering.developer")

        val result = runCatching { database.addDependency("locked-task", "prerequisite") }

        assertEquals(true, result.isFailure)
        assertEquals(emptyList<String>(), database.dependencyIds("locked-task"))
    }

}
