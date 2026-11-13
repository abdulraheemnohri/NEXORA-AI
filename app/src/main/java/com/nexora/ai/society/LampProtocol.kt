package com.nexora.ai.society

import org.json.JSONObject
import java.util.UUID

/** Versioned local Agent Message Protocol (LAMP). Payload is data, never executable code. */
object LampProtocol {
    const val VERSION = 1
    const val MAX_PAYLOAD_CHARS = 32_000

    fun newMessage(
        sender: String,
        recipient: String?,
        type: LampType,
        payload: String,
        taskId: String? = null,
        correlationId: String? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
        ttlMillis: Long = 24 * 60 * 60 * 1000L,
        now: Long = System.currentTimeMillis()
    ): LampMessage {
        require(sender.isNotBlank())
        require(payload.length <= MAX_PAYLOAD_CHARS)
        require(ttlMillis in 1_000L..7L * 24 * 60 * 60 * 1000)
        return LampMessage(
            id = UUID.randomUUID().toString(), taskId = taskId,
            correlationId = correlationId, senderAgentId = sender,
            recipientAgentId = recipient, type = type, priority = priority,
            createdAt = now, expiresAt = now + ttlMillis, payload = payload
        )
    }

    fun encode(message: LampMessage): String = JSONObject()
        .put("version", VERSION)
        .put("id", message.id)
        .put("task_id", message.taskId)
        .put("correlation_id", message.correlationId)
        .put("sender", message.senderAgentId)
        .put("recipient", message.recipientAgentId)
        .put("type", message.type.name)
        .put("priority", message.priority.name)
        .put("created_at", message.createdAt)
        .put("expires_at", message.expiresAt)
        .put("payload", message.payload)
        .put("attempt", message.attempt)
        .toString()

    fun decode(raw: String): LampMessage {
        require(raw.length <= MAX_PAYLOAD_CHARS + 4_096) { "LAMP envelope is too large." }
        val json = JSONObject(raw)
        require(json.getInt("version") == VERSION) { "Unsupported LAMP protocol version." }
        return LampMessage(
            id = json.getString("id"),
            taskId = json.optString("task_id").takeUnless { it == "null" || it.isBlank() },
            correlationId = json.optString("correlation_id").takeUnless { it == "null" || it.isBlank() },
            senderAgentId = json.getString("sender"),
            recipientAgentId = json.optString("recipient").takeUnless { it == "null" || it.isBlank() },
            type = LampType.valueOf(json.getString("type")),
            priority = TaskPriority.valueOf(json.getString("priority")),
            createdAt = json.getLong("created_at"),
            expiresAt = if (json.isNull("expires_at")) null else json.getLong("expires_at"),
            payload = json.getString("payload"),
            attempt = json.optInt("attempt", 0)
        )
    }
}
