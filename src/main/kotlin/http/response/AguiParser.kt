package http.response

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

private val aguiJson = Json { ignoreUnknownKeys = true; isLenient = true }
private val timestampSep = " data:"

internal data class AguiEventArgs(
    val line: String,
    val json: JsonObject,
)

internal fun extractAguiEvents(lines: List<String>): List<AguiEventArgs> {
    val events = mutableListOf<AguiEventArgs>()
    for (line in lines) {
        val trimmed = line.trimStart()
        val dataStr = if (trimmed.startsWith("data:")) {
            trimmed.removePrefix("data:").trimStart()
        } else {
            val idx = trimmed.indexOf(timestampSep)
            if (idx < 0) continue
            trimmed.substring(idx + timestampSep.length).trimStart()
        }
        val json = runCatching { aguiJson.parseToJsonElement(dataStr).jsonObject }.getOrNull() ?: continue
        val type = json["type"]?.jsonPrimitive?.contentOrNull
        if (type != null && type in knownAguiEventTypes) {
            events.add(AguiEventArgs(line, json))
        }
    }
    return events
}

private val knownAguiEventTypes = setOf(
    "RUN_STARTED", "RUN_FINISHED", "RUN_ERROR",
    "TEXT_MESSAGE_START", "TEXT_MESSAGE_CONTENT", "TEXT_MESSAGE_END", "TEXT_MESSAGE_CHUNK",
    "TOOL_CALL_START", "TOOL_CALL_ARGS", "TOOL_CALL_END", "TOOL_CALL_RESULT", "TOOL_CALL_CHUNK",
    "REASONING_START", "REASONING_MESSAGE_START", "REASONING_MESSAGE_CONTENT",
    "REASONING_MESSAGE_END", "REASONING_END", "REASONING_MESSAGE_CHUNK",
    "ACTIVITY_SNAPSHOT", "ACTIVITY_DELTA",
    "STATE_SNAPSHOT", "STATE_DELTA", "MESSAGES_SNAPSHOT",
    "STEP_STARTED", "STEP_FINISHED",
    "RAW", "CUSTOM",
)

internal fun buildAguiRunState(events: List<AguiEventArgs>): AguiRunState {
    var runId: String? = null
    var threadId: String? = null
    var status = AguiRunStatus.Running
    var errorMessage: String? = null
    val messages = mutableListOf<AguiMessage>()

    var pendingTextMessage: MutableTextMessage? = null
    var pendingToolCall: MutableToolCall? = null
    var pendingReasoning: MutableReasoning? = null

    for (ev in events) {
        val obj = ev.json
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: continue

        when (type) {
            "RUN_STARTED" -> {
                runId = obj["runId"]?.jsonPrimitive?.contentOrNull
                threadId = obj["threadId"]?.jsonPrimitive?.contentOrNull
                status = AguiRunStatus.Running
            }
            "RUN_FINISHED" -> status = AguiRunStatus.Finished
            "RUN_ERROR" -> {
                status = AguiRunStatus.Error
                errorMessage = obj["message"]?.jsonPrimitive?.contentOrNull
            }
            "TEXT_MESSAGE_START" -> {
                flushPending(pendingTextMessage, pendingToolCall, pendingReasoning, messages)
                val msgId = obj["messageId"]?.jsonPrimitive?.contentOrNull
                val role = obj["role"]?.jsonPrimitive?.contentOrNull ?: "assistant"
                pendingTextMessage = MutableTextMessage(msgId, role)
            }
            "TEXT_MESSAGE_CONTENT" -> {
                val delta = obj["delta"]?.jsonPrimitive?.contentOrNull ?: ""
                if (pendingTextMessage != null) {
                    pendingTextMessage = pendingTextMessage.copy(text = pendingTextMessage.text + delta)
                } else {
                    val msgId = obj["messageId"]?.jsonPrimitive?.contentOrNull
                    pendingTextMessage = MutableTextMessage(msgId, "assistant", text = delta)
                }
            }
            "TEXT_MESSAGE_END" -> {
                pendingTextMessage = pendingTextMessage?.copy(isComplete = true)
                pendingTextMessage?.let { messages.add(it.toAguiTextMessage()) }
                pendingTextMessage = null
            }
            "TEXT_MESSAGE_CHUNK" -> {
                handleTextMessageChunk(obj, pendingTextMessage, pendingToolCall, pendingReasoning, messages)?.let { pendingTextMessage = it }
            }
            "TOOL_CALL_START" -> {
                flushPending(pendingTextMessage, pendingToolCall, pendingReasoning, messages)
                pendingToolCall = MutableToolCall(
                    toolCallId = obj["toolCallId"]?.jsonPrimitive?.contentOrNull,
                    toolName = obj["toolCallName"]?.jsonPrimitive?.contentOrNull,
                )
            }
            "TOOL_CALL_ARGS" -> {
                pendingToolCall = pendingToolCall?.copy(args = pendingToolCall.args + (obj["delta"]?.jsonPrimitive?.contentOrNull ?: ""))
            }
            "TOOL_CALL_END" -> {
                pendingToolCall = pendingToolCall?.copy(argsComplete = true)
            }
            "TOOL_CALL_RESULT" -> {
                pendingToolCall = pendingToolCall?.copy(result = obj["content"]?.jsonPrimitive?.contentOrNull, isComplete = true)
                pendingToolCall?.let { messages.add(it.toAguiToolCallMessage()) }
                pendingToolCall = null
            }
            "TOOL_CALL_CHUNK" -> {
                handleToolCallChunk(obj, pendingTextMessage, pendingToolCall, pendingReasoning, messages)?.let { pendingToolCall = it }
            }
            "REASONING_MESSAGE_START" -> {
                flushPending(pendingTextMessage, pendingToolCall, pendingReasoning, messages)
                val msgId = obj["messageId"]?.jsonPrimitive?.contentOrNull
                pendingReasoning = MutableReasoning(msgId)
            }
            "REASONING_START" -> { /* marker only, no message created */ }
            "REASONING_MESSAGE_CONTENT" -> {
                pendingReasoning = pendingReasoning?.copy(text = pendingReasoning.text + (obj["delta"]?.jsonPrimitive?.contentOrNull ?: ""))
            }
            "REASONING_MESSAGE_END" -> {
                pendingReasoning = pendingReasoning?.copy(isComplete = true)
                pendingReasoning?.let { messages.add(it.toAguiReasoningBlock()) }
                pendingReasoning = null
            }
            "REASONING_END" -> {
                pendingReasoning?.let { messages.add(it.toAguiReasoningBlock()) }
                pendingReasoning = null
            }
            "REASONING_MESSAGE_CHUNK" -> {
                pendingReasoning = pendingReasoning?.copy(text = pendingReasoning.text + (obj["delta"]?.jsonPrimitive?.contentOrNull ?: ""))
            }
        }
    }

    // Flush any pending items
    flushPending(pendingTextMessage, pendingToolCall, pendingReasoning, messages)

    return AguiRunState(
        runId = runId,
        threadId = threadId,
        messages = messages,
        status = status,
        errorMessage = errorMessage,
    )
}

private fun flushPending(
    textMsg: MutableTextMessage?,
    toolCall: MutableToolCall?,
    reasoning: MutableReasoning?,
    messages: MutableList<AguiMessage>,
) {
    textMsg?.let { messages.add(it.toAguiTextMessage()) }
    toolCall?.let { messages.add(it.toAguiToolCallMessage()) }
    reasoning?.let { messages.add(it.toAguiReasoningBlock()) }
}

private fun handleTextMessageChunk(
    obj: JsonObject,
    current: MutableTextMessage?,
    pendingToolCall: MutableToolCall?,
    pendingReasoning: MutableReasoning?,
    messages: MutableList<AguiMessage>,
): MutableTextMessage? {
    flushPending(null, pendingToolCall, pendingReasoning, messages)
    val delta = obj["delta"]?.jsonPrimitive?.contentOrNull ?: ""
    val msgId = obj["messageId"]?.jsonPrimitive?.contentOrNull
    val role = obj["role"]?.jsonPrimitive?.contentOrNull ?: "assistant"
    return when {
        msgId != null && current?.messageId != msgId -> MutableTextMessage(msgId, role, text = delta, isComplete = false)
        msgId != null && current != null -> current.copy(text = current.text + delta)
        msgId != null -> MutableTextMessage(msgId, role, text = delta, isComplete = false)
        current != null -> current.copy(text = current.text + delta)
        else -> MutableTextMessage(null, role, text = delta, isComplete = false)
    }
}

private fun handleToolCallChunk(
    obj: JsonObject,
    textMsg: MutableTextMessage?,
    current: MutableToolCall?,
    reasoning: MutableReasoning?,
    messages: MutableList<AguiMessage>,
): MutableToolCall? {
    flushPending(textMsg, null, reasoning, messages)
    val tcId = obj["toolCallId"]?.jsonPrimitive?.contentOrNull
    val name = obj["toolCallName"]?.jsonPrimitive?.contentOrNull
    val delta = obj["delta"]?.jsonPrimitive?.contentOrNull ?: ""
    return if (current?.toolCallId == tcId && current != null) {
        current.copy(args = current.args + delta)
    } else {
        MutableToolCall(toolCallId = tcId, toolName = name, args = delta)
    }
}

private data class MutableTextMessage(
    val messageId: String?,
    val role: String,
    val text: String = "",
    val isComplete: Boolean = false,
) {
    fun toAguiTextMessage() = AguiTextMessage(messageId, role, text, isComplete)
}

private data class MutableToolCall(
    val toolCallId: String?,
    val toolName: String?,
    val args: String = "",
    val argsComplete: Boolean = false,
    val result: String? = null,
    val isComplete: Boolean = false,
) {
    fun toAguiToolCallMessage() = AguiToolCallMessage(
        messageId = null, toolCallId, toolName, args, argsComplete, result, isComplete,
    )
}

private data class MutableReasoning(
    val messageId: String?,
    val text: String = "",
    val isComplete: Boolean = false,
) {
    fun toAguiReasoningBlock() = AguiReasoningBlock(messageId, text, isComplete)
}
