package com.nexora.ai.society

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LampProtocolInstrumentedTest {
    @Test fun versionedMessageRoundTrips() {
        val message = LampProtocol.newMessage(
            sender="executive.commander",recipient="engineering.developer",
            type=LampType.TASK_ASSIGN,payload="""{"task":"compile"}""",
            taskId="task-1",correlationId="corr-1",priority=TaskPriority.HIGH,now=1000L
        )
        val decoded=LampProtocol.decode(LampProtocol.encode(message))
        assertEquals(message.id,decoded.id)
        assertEquals(message.senderAgentId,decoded.senderAgentId)
        assertEquals(message.recipientAgentId,decoded.recipientAgentId)
        assertEquals(message.type,decoded.type)
        assertEquals(message.payload,decoded.payload)
        assertEquals(LampProtocol.VERSION,1)
    }
}
