package com.nexora.ai.society

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MasxPlanParserInstrumentedTest {
    @Test fun acceptsBoundedPlanWithEarlierDependencies() {
        val draft = MasxPlanParser.parse(
            """{"summary":"Inspect, then verify.","steps":[
                {"id":"step-1","title":"Inspect source","instructions":"Read the local Kotlin files.","priority":"HIGH","depends_on":[]},
                {"id":"step-2","title":"Verify behavior","instructions":"Run focused local tests.","priority":"NORMAL","depends_on":["step-1"]}
            ]}"""
        )
        assertEquals(2, draft.steps.size)
        assertEquals(listOf("step-1"), draft.steps[1].dependsOn)
        assertEquals(TaskPriority.HIGH, draft.steps[0].priority)
    }

    @Test fun rejectsUnknownToolOrExecutionFields() {
        val raw = """{"summary":"Unsafe","steps":[],"tool_call":{"name":"shell"}}"""
        assertTrue(runCatching { MasxPlanParser.parse(raw) }.isFailure)
    }

    @Test fun rejectsForwardDependencies() {
        val raw = """{"summary":"Invalid dependency","steps":[
            {"id":"step-1","title":"First","instructions":"Do first.","priority":"NORMAL","depends_on":["step-2"]},
            {"id":"step-2","title":"Second","instructions":"Do second.","priority":"NORMAL","depends_on":[]}
        ]}"""
        assertTrue(runCatching { MasxPlanParser.parse(raw) }.isFailure)
    }

    @Test fun rejectsMoreThanEightSteps() {
        val steps = (1..9).joinToString(",") { index ->
            """{"id":"step-$index","title":"Step $index","instructions":"Do step $index.","priority":"NORMAL","depends_on":[]}"""
        }
        assertTrue(runCatching { MasxPlanParser.parse("""{"summary":"Too many","steps":[$steps]}""") }.isFailure)
    }

    @Test fun rejectsOversizedInstructions() {
        val longText = "x".repeat(MasxPlanParser.MAX_INSTRUCTION_CHARACTERS + 1)
        val raw = org.json.JSONObject()
            .put("summary", "Oversized")
            .put("steps", org.json.JSONArray().put(
                org.json.JSONObject()
                    .put("id", "step-1")
                    .put("title", "Long task")
                    .put("instructions", longText)
                    .put("priority", "NORMAL")
                    .put("depends_on", org.json.JSONArray())
            )).toString()
        assertTrue(runCatching { MasxPlanParser.parse(raw) }.isFailure)
    }
}
