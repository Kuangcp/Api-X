package app.state

import app.settings.RecentRequestUsageStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import db.CollectionRepository
import http.migrateFormBodyToEditorLinesIfNeeded
import tree.PostmanAuth
import tree.TreeSelection

data class TabSession(
    val method: String = "GET",
    val url: String = "",
    val headersText: String = "",
    val bodyText: String = "",
    val paramsText: String = "",
    val auth: PostmanAuth? = null,
    val leftTabIndex: Int = 0,
)

class RequestEditorState(private val repository: CollectionRepository) {
    var method by mutableStateOf("GET")
    var methodMenuExpanded by mutableStateOf(false)
    var url by mutableStateOf("https://httpbin.org/get")
    var headersText by mutableStateOf("Content-Type: application/x-www-form-urlencoded\nAccept: */*")
    var bodyText by mutableStateOf("key: value")
    var paramsText by mutableStateOf("")
    var auth by mutableStateOf<PostmanAuth?>(null)
    var editorRequestId by mutableStateOf<String?>(null)
    val openTabIds = mutableStateListOf<String>()
    var activeTabId by mutableStateOf<String?>(null)
    var leftTabIndex by mutableStateOf(0)

    private val tabSessions = mutableMapOf<String, TabSession>()

    fun saveCurrentTabSession(id: String?) {
        if (id == null) return
        tabSessions[id] = TabSession(
            method = method, url = url, headersText = headersText,
            bodyText = bodyText, paramsText = paramsText, auth = auth,
            leftTabIndex = leftTabIndex,
        )
    }

    fun saveEditorIfBound() {
        val id = editorRequestId ?: return
        repository.saveRequestEditorFields(id, method, url, headersText, paramsText, bodyText, auth)
    }

    fun selectTab(requestId: String) {
        if (requestId == activeTabId) return
        saveEditorIfBound()
        saveCurrentTabSession(editorRequestId)
        activeTabId = requestId
        editorRequestId = requestId
        val session = tabSessions[requestId]
        if (session != null) {
            method = session.method
            url = session.url
            headersText = session.headersText
            bodyText = session.bodyText
            paramsText = session.paramsText
            auth = session.auth
            leftTabIndex = session.leftTabIndex
        } else {
            val r = repository.getRequest(requestId) ?: return
            method = r.method
            url = r.url
            headersText = r.headersText
            paramsText = r.paramsText
            bodyText = migrateFormBodyToEditorLinesIfNeeded(r.bodyText, r.headersText)
            auth = r.auth
            leftTabIndex = 0
            tabSessions[requestId] = TabSession(
                method = method, url = url, headersText = headersText,
                bodyText = bodyText, paramsText = paramsText, auth = auth,
            )
        }
        RecentRequestUsageStore.touch(requestId)
    }

    fun closeTab(requestId: String) {
        val idx = openTabIds.indexOf(requestId)
        if (idx < 0) return
        if (requestId == activeTabId) saveCurrentTabSession(requestId)
        tabSessions.remove(requestId)
        openTabIds.removeAt(idx)
        if (requestId == activeTabId) {
            val newIdx = idx.coerceAtMost(openTabIds.lastIndex)
            if (newIdx >= 0 && openTabIds.isNotEmpty()) {
                selectTab(openTabIds[newIdx])
            } else {
                activeTabId = null
                editorRequestId = null
                method = "GET"
                url = ""
                headersText = ""
                bodyText = ""
                paramsText = ""
                auth = null
                leftTabIndex = 0
            }
        }
    }

    fun applyRequestToEditor(reqId: String) {
        val r = repository.getRequest(reqId) ?: return
        saveCurrentTabSession(editorRequestId)
        if (reqId !in openTabIds) {
            openTabIds.add(reqId)
        }
        activeTabId = reqId
        editorRequestId = reqId
        method = r.method
        url = r.url
        headersText = r.headersText
        paramsText = r.paramsText
        bodyText = migrateFormBodyToEditorLinesIfNeeded(r.bodyText, r.headersText)
        auth = r.auth
        leftTabIndex = 0
        tabSessions[reqId] = TabSession(
            method = method, url = url, headersText = headersText,
            bodyText = bodyText, paramsText = paramsText, auth = auth,
        )
        RecentRequestUsageStore.touch(reqId)
    }

    fun doApplyCurlToEditor(
        newId: String,
        m: String,
        u: String,
        p: String,
        h: String,
        b: String,
    ) {
        val r = repository.getRequest(newId) ?: return
        saveCurrentTabSession(editorRequestId)
        if (newId !in openTabIds) {
            openTabIds.add(newId)
        }
        activeTabId = newId
        editorRequestId = newId
        method = m
        url = u
        paramsText = p
        headersText = h
        bodyText = b
        auth = r.auth
        leftTabIndex = 0
        tabSessions[newId] = TabSession(
            method = method, url = url, headersText = headersText,
            bodyText = bodyText, paramsText = paramsText, auth = auth,
        )
        RecentRequestUsageStore.touch(newId)
    }

    fun mruRequestIdsForSwitcher(): List<String> {
        val ordered = RecentRequestUsageStore.orderedIdsNewestFirst { repository.getRequest(it) != null }
        val cur = editorRequestId ?: return ordered
        val without = ordered.filter { it != cur }
        return (listOf(cur) + without).distinct().take(30)
    }
}
