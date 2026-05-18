package app

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import db.HarLogCodec
import db.HarSnapshot
import db.RequestResponseStore
import http.BufferUpdate
import http.HttpExchangeErrorStatusMark
import http.RequestControl
import http.applyEnvironmentVariables
import http.bodyWirePayloadForHttp
import http.closeQuietly
import http.ensureDefaultHttpScheme
import http.exchangeFontMetrics
import http.formatActualRequestPlainText
import http.mergeUrlWithParams
import http.migrateFormBodyToEditorLinesIfNeeded
import http.parseHeadersForSend
import http.requestToCurlCommand
import http.resolveAuthToHeaders
import http.responseHeaderLinesForHar
import http.sendRequestStreaming
import http.substitutionMapForActive
import kotlinx.coroutines.delay
import tree.PostmanAuth
import tree.UiCollection
import tree.TreeSelection
import tree.collectAllFolderIds
import tree.expandSetsForRequest
import tree.firstRequestSelection
import java.awt.EventQueue
import kotlin.concurrent.thread

data class TabSession(
    val method: String = "GET",
    val url: String = "",
    val headersText: String = "",
    val bodyText: String = "",
    val paramsText: String = "",
    val auth: PostmanAuth? = null,
    val leftTabIndex: Int = 0,
)

data class AppViewModel(
    val repository: db.CollectionRepository,
    val expandLoaded: TreeExpandPrefs.Loaded,
    val windowState: androidx.compose.ui.window.WindowState,
    val exchangeMetrics: http.ExchangeFontMetrics,

    val tree: List<UiCollection>,
    val setTree: (List<UiCollection>) -> Unit,
    val treeSelection: TreeSelection?,
    val setTreeSelection: (TreeSelection?) -> Unit,
    val expandedCollectionIds: Set<String>,
    val setExpandedCollectionIds: (Set<String>) -> Unit,
    val expandedFolderIds: Set<String>,
    val setExpandedFolderIds: (Set<String>) -> Unit,
    val treeSplitRatio: Float,
    val setTreeSplitRatio: (Float) -> Unit,
    val treeSidebarVisible: Boolean,
    val setTreeSidebarVisible: (Boolean) -> Unit,
    val treeScrollToRequestId: String?,
    val setTreeScrollToRequestId: (String?) -> Unit,

    val method: String,
    val setMethod: (String) -> Unit,
    val url: String,
    val setUrl: (String) -> Unit,
    val headersText: String,
    val setHeadersText: (String) -> Unit,
    val bodyText: String,
    val setBodyText: (String) -> Unit,
    val paramsText: String,
    val setParamsText: (String) -> Unit,
    val auth: PostmanAuth?,
    val setAuth: (PostmanAuth?) -> Unit,
    val editorRequestId: String?,
    val setEditorRequestId: (String?) -> Unit,
    val openTabIds: List<String>,
    val activeTabId: String?,
    val onTabSelected: (String) -> Unit,
    val onTabCloseRequest: (String) -> Unit,
    val leftTabIndex: Int,
    val setLeftTabIndex: (Int) -> Unit,
    val methodMenuExpanded: Boolean,
    val setMethodMenuExpanded: (Boolean) -> Unit,

    val responseLines: MutableList<String>,
    val responseHeaderLines: MutableList<String>,
    val responseListState: LazyListState,
    val responseHeadersListState: LazyListState,
    val isLoading: Boolean,
    val setIsLoading: (Boolean) -> Unit,
    val isSseResponse: Boolean,
    val setIsSseResponse: (Boolean) -> Unit,
    val statusCodeText: String,
    val setStatusCodeText: (String) -> Unit,
    val responseTimeText: String,
    val setResponseTimeText: (String) -> Unit,
    val responseSizeText: String,
    val setResponseSizeText: (String) -> Unit,
    val responsePartialLine: String?,
    val setResponsePartialLine: (String?) -> Unit,
    val exchangeRequestPlainText: String,
    val setExchangeRequestPlainText: (String) -> Unit,
    val rightTabIndex: Int,
    val setRightTabIndex: (Int) -> Unit,
    val splitRatio: Float,
    val setSplitRatio: (Float) -> Unit,
    val contentRowWidthPx: Float,
    val setContentRowWidthPx: (Float) -> Unit,
    val historyEntries: List<db.HistoryEntry>,
    val selectedHistoryEpochMs: Long?,
    val setSelectedHistoryEpochMs: (Long?) -> Unit,

    val isDarkTheme: Boolean,
    val setIsDarkTheme: (Boolean) -> Unit,
    val jsonSyntaxHighlightEnabled: Boolean,
    val setJsonSyntaxHighlightEnabled: (Boolean) -> Unit,
    val showSettings: Boolean,
    val setShowSettings: (Boolean) -> Unit,
    val showEnvironmentManager: Boolean,
    val setShowEnvironmentManager: (Boolean) -> Unit,
    val showGlobalSearch: Boolean,
    val setShowGlobalSearch: (Boolean) -> Unit,
    val showCollectionSettings: Boolean,
    val setShowCollectionSettings: (Boolean) -> Unit,
    val collectionSettingsTarget: TreeSelection?,
    val setCollectionSettingsTarget: (TreeSelection?) -> Unit,
    val environmentsState: EnvironmentsState,
    val setEnvironmentsState: (EnvironmentsState) -> Unit,
    val appSettings: AppSettings,
    val setAppSettings: (AppSettings) -> Unit,

    val toastMessage: String?,
    val showToast: (String) -> Unit,
    val clearToast: () -> Unit,

    val recentSwitcherActive: Boolean,
    val setRecentSwitcherActive: (Boolean) -> Unit,
    val recentSwitcherIds: List<String>,
    val setRecentSwitcherIds: (List<String>) -> Unit,
    val recentSwitcherIndex: Int,
    val setRecentSwitcherIndex: (Int) -> Unit,

    val runningRequestIds: Set<String>,

    val onStartRequest: () -> Unit,
    val onCancelRequest: () -> Unit,
    val onRefreshTree: () -> Unit,
    val onApplyRequestToEditor: (String) -> Unit,
    /** 一次性将编辑器切到新请求并写入 cURL 解析结果，避免多轮 set* 出现中间态导致 Body/表单行不同步。 */
    val applyCurlToEditor: (String, String, String, String, String, String) -> Unit,
    val onSelectTreeNode: (TreeSelection) -> Unit,
    val onAddFolderAt: (TreeSelection) -> Unit,
    val onAddRequestAt: (TreeSelection) -> Unit,
    val onSaveEditor: () -> Unit,
    val getMruRequestIds: () -> List<String>,
    val onCommitEnvironments: (EnvironmentsState) -> Unit,
)

@Composable
fun rememberAppViewModel(
    repository: db.CollectionRepository,
    expandLoaded: TreeExpandPrefs.Loaded,
    windowState: androidx.compose.ui.window.WindowState,
): AppViewModel {
    var method by remember { mutableStateOf("GET") }
    var methodMenuExpanded by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("https://httpbin.org/get") }
    var headersText by remember { mutableStateOf("Content-Type: application/x-www-form-urlencoded\nAccept: */*") }
    var bodyText by remember { mutableStateOf("key: value") }
    var paramsText by remember { mutableStateOf("") }
    var auth by remember { mutableStateOf<PostmanAuth?>(null) }
    var tree by remember { mutableStateOf(repository.loadTree()) }
    var treeSelection by remember { mutableStateOf<TreeSelection?>(null) }
    var editorRequestId by remember { mutableStateOf<String?>(null) }
    val openTabIds = remember { mutableStateListOf<String>() }
    var activeTabId by remember { mutableStateOf<String?>(null) }
    val tabSessions = remember { mutableMapOf<String, TabSession>() }
    var expandedCollectionIds by remember { mutableStateOf(expandLoaded.collectionIds) }
    var expandedFolderIds by remember { mutableStateOf(expandLoaded.folderIds) }
    var persistTreeExpand by remember { mutableStateOf(expandLoaded.fromSavedFile) }
    var didApplyDefaultTreeExpand by remember { mutableStateOf(false) }
    var treeSplitRatio by remember { mutableStateOf(0.2f) }
    var treeSidebarVisible by remember { mutableStateOf(true) }
    var didPickInitialRequest by remember { mutableStateOf(false) }
    var selectedHistoryEpochMs by remember { mutableStateOf<Long?>(null) }
    var splitRatio by remember { mutableStateOf(0.5f) }
    var contentRowWidthPx by remember { mutableStateOf(1f) }
    var leftTabIndex by remember { mutableStateOf(0) }
    var isDarkTheme by remember { mutableStateOf(true) }
    var jsonSyntaxHighlightEnabled by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showEnvironmentManager by remember { mutableStateOf(false) }
    var showGlobalSearch by remember { mutableStateOf(false) }
    var treeScrollToRequestId by remember { mutableStateOf<String?>(null) }
    var recentSwitcherActive by remember { mutableStateOf(false) }
    var recentSwitcherIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var recentSwitcherIndex by remember { mutableStateOf(0) }
    var showCollectionSettings by remember { mutableStateOf(false) }
    var collectionSettingsTarget by remember { mutableStateOf<TreeSelection?>(null) }
    var environmentsState by remember { mutableStateOf(withDefaultActiveWhenSingle(EnvironmentStore.snapshot())) }
    var appSettings by remember { mutableStateOf(AppSettingsStore.snapshot()) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val requestSessions = remember { mutableMapOf<String, RequestSession>() }
    val runningRequestIds = remember { mutableSetOf<String>() }

    val placeholderResponseLines = remember { mutableStateListOf("请先选择或创建一个请求") }
    val placeholderResponseHeaders = remember { mutableStateListOf("(暂无响应头)") }
    val placeholderListState = remember { LazyListState() }

    fun getOrCreateSession(reqId: String): RequestSession {
        return requestSessions.getOrPut(reqId) {
            val cached = RequestResponseStore.loadLatest(reqId)
            val session = RequestSession(reqId)
            if (cached != null) {
                session.responseLines.addAll(cached.responseBodyLines)
                session.responseHeaderLines.addAll(cached.responseHeaderLines)
                session.statusCodeText = cached.statusCodeText
                session.responseTimeText = cached.responseTimeText
                session.responseSizeText = cached.responseSizeText
                session.isSseResponse = cached.isSseResponse
                session.rightTabIndex = cached.rightTabIndex.coerceIn(0, 2)
                session.exchangeRequestPlainText = cached.requestPlainText.ifBlank { "尚无已发送请求记录；发送后将显示实际发出的请求头与正文。" }
            } else {
                session.responseLines.add("响应结果会显示在这里")
                session.responseHeaderLines.add("(暂无响应头)")
            }
            session.historyEntries = RequestResponseStore.listHistory(reqId)
            session
        }
    }

    val currentResponseLines: MutableList<String> = editorRequestId?.let { getOrCreateSession(it).responseLines } ?: placeholderResponseLines
    val currentResponseHeaderLines: MutableList<String> = editorRequestId?.let { getOrCreateSession(it).responseHeaderLines } ?: placeholderResponseHeaders
    val currentResponseListState: LazyListState = editorRequestId?.let { getOrCreateSession(it).responseListState } ?: placeholderListState
    val currentResponseHeadersListState: LazyListState = editorRequestId?.let { getOrCreateSession(it).responseHeadersListState } ?: placeholderListState
    val currentIsLoading: Boolean = requestSessions[editorRequestId]?.isLoading ?: false
    val currentIsSseResponse: Boolean = requestSessions[editorRequestId]?.isSseResponse ?: false
    val currentStatusCodeText: String = requestSessions[editorRequestId]?.statusCodeText ?: ""
    val currentResponseTimeText: String = requestSessions[editorRequestId]?.responseTimeText ?: ""
    val currentResponseSizeText: String = requestSessions[editorRequestId]?.responseSizeText ?: ""
    val currentResponsePartialLine: String? = requestSessions[editorRequestId]?.responsePartialLine
    val currentExchangeRequestPlainText: String = requestSessions[editorRequestId]?.exchangeRequestPlainText ?: "请先选择或创建一个请求"
    val currentRightTabIndex: Int = requestSessions[editorRequestId]?.rightTabIndex ?: 0
    val currentHistoryEntries: List<db.HistoryEntry> = requestSessions[editorRequestId]?.historyEntries ?: emptyList()
    val currentSelectedHistoryEpochMs: Long? = requestSessions[editorRequestId]?.selectedHistoryEpochMs

    DisposableEffect(Unit) {
        onDispose {
            val id = editorRequestId
            if (id != null) {
                repository.saveRequestEditorFields(id, method, url, headersText, paramsText, bodyText, auth)
            }
            repository.close()
        }
    }

    fun refreshTree() { tree = repository.loadTree() }

    fun saveEditorIfBound() {
        val id = editorRequestId ?: return
        repository.saveRequestEditorFields(id, method, url, headersText, paramsText, bodyText, auth)
    }

    fun saveCurrentTabSession(id: String?) {
        if (id == null) return
        tabSessions[id] = TabSession(
            method = method, url = url, headersText = headersText,
            bodyText = bodyText, paramsText = paramsText, auth = auth,
            leftTabIndex = leftTabIndex,
        )
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
                treeSelection = null
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

    fun selectTreeNode(sel: TreeSelection) {
        when (sel) {
            is TreeSelection.Request -> {
                if (sel.id in openTabIds) {
                    selectTab(sel.id)
                    treeSelection = sel
                } else {
                    saveEditorIfBound()
                    applyRequestToEditor(sel.id)
                    treeSelection = sel
                }
            }
            else -> treeSelection = sel
        }
    }

    fun addFolderAt(at: TreeSelection) {
        val target = repository.newFolderTarget(at) ?: return
        val (cid, pid) = target
        repository.createFolder(cid, pid, "新文件夹").let { fid ->
            refreshTree()
            expandedCollectionIds = expandedCollectionIds + cid
            if (pid != null) expandedFolderIds = expandedFolderIds + pid
            treeSelection = TreeSelection.Folder(fid)
        }
    }

    fun addRequestAt(at: TreeSelection) {
        val target = repository.newRequestTarget(at) ?: return
        val (cid, fid) = target
        saveEditorIfBound()
        val rid = repository.createRequest(cid, fid, "新请求")
        refreshTree()
        expandedCollectionIds = expandedCollectionIds + cid
        if (fid != null) expandedFolderIds = expandedFolderIds + fid
        applyRequestToEditor(rid)
        treeSelection = TreeSelection.Request(rid)
    }

    fun mruRequestIdsForSwitcher(): List<String> {
        val ordered = RecentRequestUsageStore.orderedIdsNewestFirst { repository.getRequest(it) != null }
        val cur = editorRequestId ?: return ordered
        val without = ordered.filter { it != cur }
        return (listOf(cur) + without).distinct().take(30)
    }

    fun commitEnvironmentsState(newState: EnvironmentsState) {
        EnvironmentStore.replace(newState)
        environmentsState = EnvironmentStore.snapshot()
    }

    LaunchedEffect(method, url, headersText, paramsText, bodyText, auth, editorRequestId) {
        val id = editorRequestId ?: return@LaunchedEffect
        delay(450)
        repository.saveRequestEditorFields(id, method, url, headersText, paramsText, bodyText, auth)
        refreshTree()
    }

    LaunchedEffect(editorRequestId) {
        val id = editorRequestId ?: return@LaunchedEffect
        LastRequestPrefs.save(id)
    }

    LaunchedEffect(tree) {
        if (tree.isEmpty()) return@LaunchedEffect
        if (!didApplyDefaultTreeExpand) {
            didApplyDefaultTreeExpand = true
            if (!expandLoaded.fromSavedFile && expandedCollectionIds.isEmpty() && expandedFolderIds.isEmpty()) {
                expandedCollectionIds = tree.map { it.id }.toSet()
                expandedFolderIds = collectAllFolderIds(tree)
            }
        }
        persistTreeExpand = true
        if (!didPickInitialRequest) {
            didPickInitialRequest = true
            val savedId = LastRequestPrefs.load()
            if (savedId.isNotEmpty() && repository.getRequest(savedId) != null) {
                expandSetsForRequest(tree, savedId)?.let { (cols, folders) ->
                    expandedCollectionIds = expandedCollectionIds + cols
                    expandedFolderIds = expandedFolderIds + folders
                }
                applyRequestToEditor(savedId)
                treeSelection = TreeSelection.Request(savedId)
            } else {
                firstRequestSelection(tree)?.let { sel ->
                    applyRequestToEditor(sel.id)
                    treeSelection = sel
                }
            }
        }
    }

    LaunchedEffect(expandedCollectionIds, expandedFolderIds, persistTreeExpand) {
        if (!persistTreeExpand) return@LaunchedEffect
        TreeExpandPrefs.save(expandedCollectionIds, expandedFolderIds)
    }

    fun startRequest() {
        val boundRequestId = editorRequestId ?: return
        val session = getOrCreateSession(boundRequestId)
        if (session.isLoading) return
        saveEditorIfBound()
        RequestResponseStore.ensureLayout(boundRequestId)
        val tabAtStart = currentRightTabIndex
        val reqMethodSnap = method
        val varMap = environmentsState.substitutionMapForActive()
        val reqUrlSnap = applyEnvironmentVariables(url, varMap)
        val reqParamsSnap = applyEnvironmentVariables(paramsText, varMap)
        val effectiveRequestUrl = ensureDefaultHttpScheme(mergeUrlWithParams(reqUrlSnap, parseHeadersForSend(reqParamsSnap)))
        val reqHeadersFullSnap = applyEnvironmentVariables(headersText, varMap)
        val effectiveAuth = if (auth?.type == "inherit") repository.resolveEffectiveAuth(boundRequestId) else auth
        val authHeaders = resolveAuthToHeaders(effectiveAuth, varMap)
        val finalHeaders = if (authHeaders.isEmpty()) reqHeadersFullSnap
            else if (reqHeadersFullSnap.isEmpty()) authHeaders.joinToString("\n") { "${it.first}: ${it.second}" }
            else reqHeadersFullSnap + "\n" + authHeaders.joinToString("\n") { "${it.first}: ${it.second}" }
        val reqBodySnap = bodyWirePayloadForHttp(applyEnvironmentVariables(bodyText, varMap), finalHeaders)
        session.exchangeRequestPlainText = formatActualRequestPlainText(reqMethodSnap, effectiveRequestUrl, finalHeaders, reqBodySnap)
        val control = RequestControl()
        control.startTimeMs = System.currentTimeMillis()
        session.control = control
        session.requestGen++
        val gen = session.requestGen
        session.isLoading = true
        runningRequestIds.add(boundRequestId)
        session.responseLines.clear()
        session.responsePartialLine = null
        session.responseHeaderLines.clear()
        session.responseHeaderLines.add("等待响应…")
        session.statusCodeText = ""
        session.responseTimeText = ""
        session.responseSizeText = ""
        applyBufferUpdate(control.lineBuffer.drainUpdate(), session.responseLines) { session.responsePartialLine = it }
        val flusher = thread(isDaemon = true) {
            var lastTimerSec = -1L
            while (!control.finished && !control.cancelled) {
                try { Thread.sleep(UI_REFRESH_INTERVAL_MS) } catch (_: InterruptedException) { break }
                if (session.control !== control) break
                val elapsed = System.currentTimeMillis() - control.startTimeMs
                val sec = (elapsed / 1000L).toInt().coerceAtLeast(0)
                val update = control.lineBuffer.drainUpdate()
                EventQueue.invokeLater {
                    if (session.requestGen != gen) return@invokeLater
                    if (sec.toLong() != lastTimerSec) {
                        session.responseTimeText = "${sec}S"
                        lastTimerSec = sec.toLong()
                    }
                    if (update.hasChanges()) {
                        applyBufferUpdate(update, session.responseLines) { session.responsePartialLine = it }
                    }
                }
            }
        }
        val worker = thread {
            sendRequestStreaming(
                method = method, url = effectiveRequestUrl, body = reqBodySnap, headersText = finalHeaders, control = control,
                onSseDetected = { isSse -> EventQueue.invokeLater { if (session.control === control) session.isSseResponse = isSse } },
                onStatusCode = { code -> EventQueue.invokeLater { if (session.control === control) session.statusCodeText = code.toString() } },
                onResponseTime = { },
                onProgress = { bytes -> EventQueue.invokeLater { if (session.control === control) session.responseSizeText = formatBytes(bytes) } },
                onResponseHeaders = { lines -> EventQueue.invokeLater { if (session.control === control) { session.responseHeaderLines.clear(); session.responseHeaderLines.addAll(lines) } } },
                onChunk = { chunk -> if (session.control === control && !control.cancelled) { control.lineBuffer.append(chunk); control.appendRawResponse(chunk) } }
            )
            EventQueue.invokeLater {
                if (session.requestGen != gen) return@invokeLater
                control.finished = true
                val elapsed = System.currentTimeMillis() - control.startTimeMs
                val timeText = formatDuration(elapsed)
                if (!control.cancelled && !control.requestFailed) {
                    val code = control.responseStatusCode
                    RequestResponseStore.save(boundRequestId, HarSnapshot(
                        savedAtEpochMs = System.currentTimeMillis(), requestMethod = reqMethodSnap, requestUrl = effectiveRequestUrl,
                        requestHeadersFullText = reqHeadersFullSnap, requestBody = reqBodySnap, requestHeadersSent = parseHeadersForSend(reqHeadersFullSnap),
                        responseStatus = if (code >= 0) code else 0,
                        responseStatusText = if (code >= 0) HarLogCodec.responseStatusPhrase(code) else "",
                        responseHeaderLines = responseHeaderLinesForHar(control.responseHeaderSnapshot, control.responseBodyDecodedForHar),
                        responseBodyLines = control.snapshotRawBodyLines(), responseTimeMs = elapsed, responseSizeBytes = control.totalBytes,
                        responseTimeLabel = timeText, responseSizeLabel = formatBytes(control.totalBytes),
                        rightTabIndex = tabAtStart.coerceIn(0, 2), isSseResponse = control.responseWasSse,
                    ))
                    if (editorRequestId == boundRequestId) {
                        session.historyEntries = RequestResponseStore.listHistory(boundRequestId)
                    }
                }
                session.isLoading = false
                runningRequestIds.remove(boundRequestId)
                if (session.control === control) { session.control = null; session.workerThread = null; session.flusherThread = null }
                if (!control.cancelled) {
                    if (control.requestFailed) { session.statusCodeText = HttpExchangeErrorStatusMark; if (control.responseStatusCode < 0) { session.responseHeaderLines.clear(); session.responseHeaderLines.add("(无响应头 — 请求未成功)") } }
                    applyBufferUpdate(control.lineBuffer.drainUpdate(), session.responseLines) { session.responsePartialLine = it }
                } else { control.lineBuffer.drainUpdate() }
                session.responseTimeText = timeText; session.responseSizeText = formatBytes(control.totalBytes); session.isSseResponse = false
                flusher.interrupt()
            }
        }
        session.workerThread = worker
        session.flusherThread = flusher
    }

    fun cancelActiveRequest() {
        val boundId = editorRequestId ?: return
        val session = requestSessions[boundId] ?: return
        if (!session.isLoading) return
        val control = session.control ?: return
        control.cancelled = true
        closeQuietly(control.activeInput)
        val cancelMsg = "\n[请求已取消]\n"
        control.lineBuffer.append(cancelMsg)
        control.appendRawResponse(cancelMsg)
        applyBufferUpdate(control.lineBuffer.drainUpdate(), session.responseLines) { session.responsePartialLine = it }
        val timeText = formatDuration(System.currentTimeMillis() - control.startTimeMs)
        session.responseTimeText = timeText
        val sc = control.responseStatusCode
        val varMap = environmentsState.substitutionMapForActive()
        val resolvedUrl = applyEnvironmentVariables(url, varMap)
        val resolvedParams = applyEnvironmentVariables(paramsText, varMap)
        val cancelUrl = mergeUrlWithParams(resolvedUrl, parseHeadersForSend(resolvedParams))
        val resolvedHeaders = applyEnvironmentVariables(headersText, varMap)
        val resolvedBody = bodyWirePayloadForHttp(applyEnvironmentVariables(bodyText, varMap), resolvedHeaders)
        RequestResponseStore.save(boundId, HarSnapshot(
            savedAtEpochMs = System.currentTimeMillis(), requestMethod = method, requestUrl = cancelUrl,
            requestHeadersFullText = resolvedHeaders, requestBody = resolvedBody, requestHeadersSent = parseHeadersForSend(resolvedHeaders),
            responseStatus = if (sc >= 0) sc else 0,
            responseStatusText = if (sc >= 0) HarLogCodec.responseStatusPhrase(sc) else "",
            responseHeaderLines = responseHeaderLinesForHar(control.responseHeaderSnapshot, control.responseBodyDecodedForHar),
            responseBodyLines = control.snapshotRawBodyLines(), responseTimeMs = System.currentTimeMillis() - control.startTimeMs,
            responseSizeBytes = control.totalBytes, responseTimeLabel = timeText, responseSizeLabel = formatBytes(control.totalBytes),
            rightTabIndex = session.rightTabIndex.coerceIn(0, 2), isSseResponse = control.responseWasSse,
        ))
        session.workerThread?.interrupt()
        session.flusherThread?.interrupt()
        session.isLoading = false; session.isSseResponse = false; runningRequestIds.remove(boundId); session.control = null; session.workerThread = null; session.flusherThread = null
    }

    val exchangeMetrics = remember(appSettings.requestResponseFontSizeSp) { exchangeFontMetrics(appSettings.requestResponseFontSizeSp) }

    return AppViewModel(
        repository = repository, expandLoaded = expandLoaded, windowState = windowState, exchangeMetrics = exchangeMetrics,
        tree = tree, setTree = { tree = it },
        treeSelection = treeSelection, setTreeSelection = { treeSelection = it },
        expandedCollectionIds = expandedCollectionIds, setExpandedCollectionIds = { expandedCollectionIds = it },
        expandedFolderIds = expandedFolderIds, setExpandedFolderIds = { expandedFolderIds = it },
        treeSplitRatio = treeSplitRatio, setTreeSplitRatio = { treeSplitRatio = it.coerceIn(0.02f, 0.98f) },
        treeSidebarVisible = treeSidebarVisible, setTreeSidebarVisible = { treeSidebarVisible = it },
        treeScrollToRequestId = treeScrollToRequestId, setTreeScrollToRequestId = { treeScrollToRequestId = it },
        method = method, setMethod = { method = it },
        url = url, setUrl = { url = it },
        headersText = headersText, setHeadersText = { headersText = it },
        bodyText = bodyText, setBodyText = { bodyText = it },
        paramsText = paramsText, setParamsText = { paramsText = it },
        auth = auth, setAuth = { auth = it },
        editorRequestId = editorRequestId, setEditorRequestId = { editorRequestId = it },
        openTabIds = openTabIds, activeTabId = activeTabId,
        onTabSelected = { selectTab(it) },
        onTabCloseRequest = { closeTab(it) },
        leftTabIndex = leftTabIndex, setLeftTabIndex = { leftTabIndex = it },
        methodMenuExpanded = methodMenuExpanded, setMethodMenuExpanded = { methodMenuExpanded = it },
        responseLines = currentResponseLines, responseHeaderLines = currentResponseHeaderLines,
        responseListState = currentResponseListState, responseHeadersListState = currentResponseHeadersListState,
        isLoading = currentIsLoading, setIsLoading = { requestSessions[editorRequestId]?.isLoading = it },
        isSseResponse = currentIsSseResponse, setIsSseResponse = { requestSessions[editorRequestId]?.isSseResponse = it },
        statusCodeText = currentStatusCodeText, setStatusCodeText = { requestSessions[editorRequestId]?.statusCodeText = it },
        responseTimeText = currentResponseTimeText, setResponseTimeText = { requestSessions[editorRequestId]?.responseTimeText = it },
        responseSizeText = currentResponseSizeText, setResponseSizeText = { requestSessions[editorRequestId]?.responseSizeText = it },
        responsePartialLine = currentResponsePartialLine, setResponsePartialLine = { requestSessions[editorRequestId]?.responsePartialLine = it },
        exchangeRequestPlainText = currentExchangeRequestPlainText, setExchangeRequestPlainText = { requestSessions[editorRequestId]?.exchangeRequestPlainText = it },
        rightTabIndex = currentRightTabIndex, setRightTabIndex = { requestSessions[editorRequestId]?.rightTabIndex = it.coerceIn(0, 2) },
        splitRatio = splitRatio, setSplitRatio = { splitRatio = it.coerceIn(0.02f, 0.98f) },
        contentRowWidthPx = contentRowWidthPx, setContentRowWidthPx = { contentRowWidthPx = it },
        historyEntries = currentHistoryEntries, selectedHistoryEpochMs = currentSelectedHistoryEpochMs, setSelectedHistoryEpochMs = { requestSessions[editorRequestId]?.selectedHistoryEpochMs = it },
        isDarkTheme = isDarkTheme, setIsDarkTheme = { isDarkTheme = it },
        jsonSyntaxHighlightEnabled = jsonSyntaxHighlightEnabled, setJsonSyntaxHighlightEnabled = { jsonSyntaxHighlightEnabled = it },
        showSettings = showSettings, setShowSettings = { showSettings = it },
        showEnvironmentManager = showEnvironmentManager, setShowEnvironmentManager = { showEnvironmentManager = it },
        showGlobalSearch = showGlobalSearch, setShowGlobalSearch = { showGlobalSearch = it },
        showCollectionSettings = showCollectionSettings, setShowCollectionSettings = { showCollectionSettings = it },
        collectionSettingsTarget = collectionSettingsTarget, setCollectionSettingsTarget = { collectionSettingsTarget = it },
        environmentsState = environmentsState, setEnvironmentsState = { environmentsState = it },
        appSettings = appSettings, setAppSettings = { appSettings = it },
        toastMessage = toastMessage,
        showToast = { toastMessage = it },
        clearToast = { toastMessage = null },
        recentSwitcherActive = recentSwitcherActive, setRecentSwitcherActive = { recentSwitcherActive = it },
        recentSwitcherIds = recentSwitcherIds, setRecentSwitcherIds = { recentSwitcherIds = it },
        recentSwitcherIndex = recentSwitcherIndex, setRecentSwitcherIndex = { recentSwitcherIndex = it },
        runningRequestIds = runningRequestIds,
        onStartRequest = { startRequest() },
        onCancelRequest = { cancelActiveRequest() },
        onRefreshTree = { refreshTree() },
        onApplyRequestToEditor = { applyRequestToEditor(it) },
        applyCurlToEditor = { a, b, c, d, e, f -> doApplyCurlToEditor(a, b, c, d, e, f) },
        onSelectTreeNode = { selectTreeNode(it) },
        onAddFolderAt = { addFolderAt(it) },
        onAddRequestAt = { addRequestAt(it) },
        onSaveEditor = { saveEditorIfBound() },
        getMruRequestIds = { mruRequestIdsForSwitcher() },
        onCommitEnvironments = { commitEnvironmentsState(it) },
    )
}