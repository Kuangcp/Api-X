package app.state

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import db.RequestResponseStore
import java.awt.EventQueue
import kotlin.concurrent.thread

class ResponseState {
    private val requestSessions = mutableMapOf<String, RequestSession>()
    var runningRequestIds by mutableStateOf<Set<String>>(emptySet())
        private set

    val placeholderResponseLines = mutableStateListOf("请先选择或创建一个请求")
    val placeholderResponseHeaders = mutableStateListOf("(暂无响应头)")
    val placeholderListState = LazyListState()

    fun getOrCreateSession(reqId: String): RequestSession {
        return requestSessions.getOrPut(reqId) {
            val session = RequestSession(reqId)
            session.isCacheLoading = true
            session.responseLines.add("加载中…")
            session.responseHeaderLines.add("(加载中…)")
            thread {
                val cached = RequestResponseStore.loadLatest(reqId)
                EventQueue.invokeLater {
                    if (session.requestGen != 0) {
                        session.isCacheLoading = false
                        return@invokeLater
                    }
                    if (cached != null) {
                        session.responseLines.clear()
                        session.responseLines.addAll(cached.responseBodyLines)
                        session.responseHeaderLines.clear()
                        session.responseHeaderLines.addAll(cached.responseHeaderLines)
                        session.statusCodeText = cached.statusCodeText
                        session.responseTimeText = cached.responseTimeText
                        session.responseSizeText = cached.responseSizeText
                        session.isSseResponse = cached.isSseResponse
                        session.responseSseEventCount = cached.responseSseEventCount
                        session.rightTabIndex = cached.rightTabIndex.coerceIn(0, 2)
                        session.exchangeRequestPlainText = cached.requestPlainText.ifBlank { "尚无已发送请求记录；发送后将显示实际发出的请求头与正文。" }
                    } else {
                        session.responseLines.clear()
                        session.responseLines.add("响应结果会显示在这里")
                        session.responseHeaderLines.clear()
                        session.responseHeaderLines.add("(暂无响应头)")
                    }
                    session.isCacheLoading = false
                }
            }
            thread {
                val history = RequestResponseStore.listHistory(reqId)
                EventQueue.invokeLater {
                    if (session.requestGen == 0) {
                        session.historyEntries = history
                    }
                }
            }
            session
        }
    }

    fun getSession(reqId: String): RequestSession? = requestSessions[reqId]

    fun addRunningRequest(id: String) {
        runningRequestIds = runningRequestIds + id
    }

    fun removeRunningRequest(id: String) {
        runningRequestIds = runningRequestIds - id
    }
}
