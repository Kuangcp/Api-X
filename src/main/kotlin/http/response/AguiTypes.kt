package http.response

sealed interface AguiMessage {
    val messageId: String?
}

data class AguiTextMessage(
    override val messageId: String?,
    val role: String,
    val text: String,
    val isComplete: Boolean,
) : AguiMessage

data class AguiToolCallMessage(
    override val messageId: String?,
    val toolCallId: String?,
    val toolName: String?,
    val args: String,
    val argsComplete: Boolean,
    val result: String?,
    val isComplete: Boolean,
) : AguiMessage

data class AguiReasoningBlock(
    override val messageId: String?,
    val text: String,
    val isComplete: Boolean,
) : AguiMessage

enum class AguiRunStatus {
    Running,
    Finished,
    Error,
}

data class AguiRunState(
    val runId: String?,
    val threadId: String?,
    val messages: List<AguiMessage>,
    val status: AguiRunStatus,
    val errorMessage: String?,
)
