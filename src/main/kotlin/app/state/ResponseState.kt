package app.state

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.log.Logger
import db.RequestResponseStore
import mcp.McpCatalogSummary
import mcp.extractMcpCatalogFromLog
import java.awt.EventQueue
import kotlin.concurrent.thread

class ResponseState {
    private val requestSessions = mutableMapOf<String, RequestSession>()
    private val accessQueue = ArrayDeque<String>()
    var runningRequestIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var cacheRefreshVersion by mutableStateOf(0)

    val placeholderResponseLines = mutableStateListOf("请先选择或创建一个请求")
    val placeholderResponseHeaders = mutableStateListOf("(暂无响应头)")
    val placeholderListState = LazyListState()

    companion object {
        private const val MAX_SESSIONS = 20
    }

    fun getOrCreateSession(reqId: String): RequestSession {
        requestSessions[reqId]?.let { session ->
            // Logger.debug("SESSION") { "getOrCreateSession: found existing $reqId, isCacheLoading=${session.isCacheLoading}, isLoading=${session.isLoading}, lines=${session.responseLines.size}, firstLine=${session.responseLines.firstOrNull()}" }
            touch(reqId)
            return session
        }
        evictIfNeeded()
        val session = RequestSession(reqId)
        session.isCacheLoading = true
        session.responseLines.add("加载中…")
        session.responseHeaderLines.add("(加载中…)")
        Logger.info("SESSION") { "getOrCreateSession: created new $reqId, isCacheLoading=true, sessions=${requestSessions.size + 1}" }
        thread {
            val history = runCatching {
                RequestResponseStore.listHistory(reqId)
            }.getOrElse { e ->
                Logger.error("SESSION", e) { "listHistory failed for $reqId: ${e.message}" }
                emptyList()
            }
            EventQueue.invokeLater {
                if (session.requestGen == 0) {
                    session.historyEntries = history
                    Logger.info("SESSION") { "listHistory loaded for $reqId: ${history.size} entries" }
                }
            }
        }
        requestSessions[reqId] = session
        accessQueue.addLast(reqId)
        return session
    }

    fun getSession(reqId: String): RequestSession? {
        val session = requestSessions[reqId] ?: return null
        touch(reqId)
        return session
    }

    fun mcpCatalogByRequestId(): Map<String, McpCatalogSummary> {
        return requestSessions.mapNotNull { (reqId, session) ->
            val catalog = extractMcpCatalogFromLog(session.responseLines, session.responsePartialLine)
            if (catalog.isEmpty) null else reqId to catalog
        }.toMap()
    }
    fun removeSession(reqId: String) {
        Logger.info("SESSION") { "removeSession: $reqId" }
        requestSessions.remove(reqId)?.dispose()
        accessQueue.remove(reqId)
    }

    fun addRunningRequest(id: String) {
        Logger.info("SESSION") { "addRunningRequest: $id" }
        runningRequestIds = runningRequestIds + id
    }

    fun removeRunningRequest(id: String) {
        Logger.info("SESSION") { "removeRunningRequest: $id" }
        runningRequestIds = runningRequestIds - id
    }

    private fun touch(reqId: String) {
        accessQueue.remove(reqId)
        accessQueue.addLast(reqId)
    }

    private fun evictIfNeeded() {
        while (requestSessions.size >= MAX_SESSIONS && accessQueue.isNotEmpty()) {
            val eldest = accessQueue.first()
            val session = requestSessions[eldest]
            if (session != null && (session.isLoading || session.isCacheLoading)) {
                Logger.info("SESSION") { "evictIfNeeded: skip $eldest, isLoading=${session.isLoading}, isCacheLoading=${session.isCacheLoading}" }
                touch(eldest)
                continue
            }
            Logger.info("SESSION") { "evictIfNeeded: evict $eldest, sessions=${requestSessions.size - 1}" }
            removeSession(eldest)
        }
    }
}
