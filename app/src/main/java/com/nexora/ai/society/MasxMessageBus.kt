package com.nexora.ai.society

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface LampHandler {
    suspend fun handle(message: LampMessage)
}

/** Durable inbox/outbox dispatcher with expiry, acknowledgement and bounded dead-lettering. */
class MasxMessageBus(private val database: MasxDatabase) {
    private val handlers = mutableMapOf<String, LampHandler>()

    fun register(agentId: String, handler: LampHandler) {
        require(agentId.isNotBlank())
        check(agentId !in handlers) { "A handler is already registered for this agent." }
        handlers[agentId] = handler
    }

    fun unregister(agentId: String) { handlers.remove(agentId) }

    suspend fun publish(message: LampMessage) = withContext(Dispatchers.IO) {
        database.enqueue(message)
    }

    suspend fun dispatchBatch(limit: Int = 32): Int = withContext(Dispatchers.IO) {
        var delivered = 0
        database.pendingMessages(limit = limit).forEach { message ->
            val recipients = message.recipientAgentId?.let { listOfNotNull(it to handlers[it]) }
                ?: handlers.entries.map { it.key to it.value }
            if (recipients.isEmpty()) {
                database.failMessage(message.id, "No active recipient handler is registered.")
            } else {
                try {
                    recipients.forEach { (recipient, handler) ->
                        if (message.recipientAgentId == null || recipient == message.recipientAgentId) handler.handle(message)
                    }
                    database.acknowledgeMessage(message.id)
                    delivered++
                } catch (error: Exception) {
                    database.failMessage(message.id, error.message ?: "Message delivery failed.")
                }
            }
        }
        delivered
    }
}
