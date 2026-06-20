package app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mcp.McpLiveConnection

class McpConnectionSession(
    val requestId: String,
) {
    var connection: McpLiveConnection? = null
    var isConnected by mutableStateOf(false)
    var isConnecting by mutableStateOf(false)
    var target by mutableStateOf("")
    var envText by mutableStateOf("")
}

class McpConnectionState {
    private val sessions = mutableMapOf<String, McpConnectionSession>()

    fun getOrCreate(requestId: String): McpConnectionSession {
        return sessions.getOrPut(requestId) { McpConnectionSession(requestId) }
    }

    fun get(requestId: String): McpConnectionSession? = sessions[requestId]

    fun close(requestId: String, onChunk: (String) -> Unit = {}) {
        val session = sessions[requestId] ?: return
        runCatching { session.connection?.close(onChunk) }
        session.connection = null
        session.isConnected = false
        session.isConnecting = false
    }

    fun closeAll() {
        sessions.keys.toList().forEach { close(it) }
    }
}
