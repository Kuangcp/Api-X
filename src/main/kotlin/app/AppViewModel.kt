package app

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.isActive
import tree.PostmanAuth
import tree.UiCollection
import tree.TreeSelection
import tree.collectAllFolderIds
import tree.expandSetsForRequest
import tree.firstRequestSelection
import java.awt.EventQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

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

    val recentSwitcherActive: Boolean,
    val setRecentSwitcherActive: (Boolean) -> Unit,
    val recentSwitcherIds: List<String>,
    val setRecentSwitcherIds: (List<String>) -> Unit,
    val recentSwitcherIndex: Int,
    val setRecentSwitcherIndex: (Int) -> Unit,

    val onStartRequest: () -> Unit,
    val onCancelRequest: () -> Unit,
    val onRefreshTree: () -> Unit,
    val onApplyRequestToEditor: (String) -> Unit,
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
    var expandedCollectionIds by remember { mutableStateOf(expandLoaded.collectionIds) }
    var expandedFolderIds by remember { mutableStateOf(expandLoaded.folderIds) }
    var persistTreeExpand by remember { mutableStateOf(expandLoaded.fromSavedFile) }
    var didApplyDefaultTreeExpand by remember { mutableStateOf(false) }
    var treeSplitRatio by remember { mutableStateOf(0.2f) }
    var treeSidebarVisible by remember { mutableStateOf(true) }
    var didPickInitialRequest by remember { mutableStateOf(false) }
    var selectedHistoryEpochMs by remember { mutableStateOf<Long?>(null) }
    var responsePartialLine by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isSseResponse by remember { mutableStateOf(false) }
    var statusCodeText by remember { mutableStateOf("") }
    var responseTimeText by remember { mutableStateOf("") }
    var responseSizeText by remember { mutableStateOf("") }
    var activeRequestControl by remember { mutableStateOf<RequestControl?>(null) }
    var activeRequestThread by remember { mutableStateOf<Thread?>(null) }
    var splitRatio by remember { mutableStateOf(0.5f) }
    var contentRowWidthPx by remember { mutableStateOf(1f) }
    var leftTabIndex by remember { mutableStateOf(1) }
    var rightTabIndex by remember { mutableStateOf(0) }
    var exchangeRequestPlainText by remember { mutableStateOf("请先选择或创建一个请求") }
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
    var inflightBoundRequestId by remember { mutableStateOf<String?>(null) }
    val outboundRequestGeneration = remember { AtomicInteger(0) }

    val responseScopeKey = editorRequestId ?: ""
    val cachedResponse = remember(responseScopeKey) { editorRequestId?.let { RequestResponseStore.loadLatest(it) } }
    val historyEntries by remember(responseScopeKey) { mutableStateOf(editorRequestId?.let { RequestResponseStore.listHistory(it) } ?: emptyList()) }
    val responseLines = remember(responseScopeKey) { mutableStateListOf<String>().apply { if (editorRequestId == null) add("请先选择或创建一个请求") else cachedResponse?.responseBodyLines?.let { addAll(it) } ?: add("响应结果会显示在这里") } }
    val responseHeaderLines = remember(responseScopeKey) { mutableStateListOf<String>().apply { if (editorRequestId == null) add("(暂无响应头)") else cachedResponse?.responseHeaderLines?.let { addAll(it) } ?: add("(暂无响应头)") } }
    val responseListState = remember(responseScopeKey) { LazyListState() }
    val responseHeadersListState = remember(responseScopeKey) { LazyListState() }

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

    fun applyRequestToEditor(reqId: String) {
        val r = repository.getRequest(reqId) ?: return
        editorRequestId = reqId
        method = r.method
        url = r.url
        headersText = r.headersText
        paramsText = r.paramsText
        bodyText = migrateFormBodyToEditorLinesIfNeeded(r.bodyText, r.headersText)
        auth = r.auth
        RecentRequestUsageStore.touch(reqId)
    }

    fun selectTreeNode(sel: TreeSelection) {
        when (sel) {
            is TreeSelection.Request -> { saveEditorIfBound(); applyRequestToEditor(sel.id); treeSelection = sel }
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

    val loadingRef = rememberUpdatedState(isLoading)
    val liveTimerControlRef = rememberUpdatedState(activeRequestControl)
    LaunchedEffect(isLoading, activeRequestControl, editorRequestId, inflightBoundRequestId) {
        if (!isLoading) return@LaunchedEffect
        val control = activeRequestControl ?: return@LaunchedEffect
        while (isActive) {
            if (liveTimerControlRef.value !== control) break
            if (!loadingRef.value) break
            val elapsed = System.currentTimeMillis() - control.startTimeMs
            val sec = (elapsed / 1000L).toInt().coerceAtLeast(0)
            if (editorRequestId == inflightBoundRequestId) {
                responseTimeText = "${sec}S"
            }
            delay(1000)
        }
    }

    fun startRequest() {
        if (isLoading) return
        val boundRequestId = editorRequestId ?: return
        val requestGen = outboundRequestGeneration.incrementAndGet()
        saveEditorIfBound()
        RequestResponseStore.ensureLayout(boundRequestId)
        val tabAtStart = rightTabIndex
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
        exchangeRequestPlainText = formatActualRequestPlainText(reqMethodSnap, effectiveRequestUrl, finalHeaders, reqBodySnap)
        val control = RequestControl()
        control.startTimeMs = System.currentTimeMillis()
        activeRequestControl = control
        inflightBoundRequestId = boundRequestId
        isLoading = true
        responseLines.clear()
        responsePartialLine = null
        responseHeaderLines.clear()
        responseHeaderLines.add("等待响应…")
        statusCodeText = ""
        responseTimeText = ""
        responseSizeText = ""
        applyBufferUpdate(control.lineBuffer.drainUpdate(), responseLines) { responsePartialLine = it }
        val flusher = thread(isDaemon = true) {
            while (!control.finished && !control.cancelled) {
                try { Thread.sleep(UI_REFRESH_INTERVAL_MS) } catch (_: InterruptedException) { break }
                if (activeRequestControl !== control) break
                val update = control.lineBuffer.drainUpdate()
                if (!update.hasChanges()) continue
                EventQueue.invokeLater {
                    if (requestGen != outboundRequestGeneration.get()) return@invokeLater
                    if (editorRequestId != boundRequestId) return@invokeLater
                    applyBufferUpdate(update, responseLines) { responsePartialLine = it }
                }
            }
        }
        val worker = thread {
            sendRequestStreaming(
                method = method, url = effectiveRequestUrl, body = reqBodySnap, headersText = finalHeaders, control = control,
                onSseDetected = { isSse -> EventQueue.invokeLater { if (activeRequestControl === control && editorRequestId == boundRequestId) isSseResponse = isSse } },
                onStatusCode = { code -> EventQueue.invokeLater { if (activeRequestControl === control && editorRequestId == boundRequestId) statusCodeText = code.toString() } },
                onResponseTime = { },
                onProgress = { bytes -> EventQueue.invokeLater { if (activeRequestControl === control && editorRequestId == boundRequestId) responseSizeText = formatBytes(bytes) } },
                onResponseHeaders = { lines -> EventQueue.invokeLater { if (activeRequestControl === control && editorRequestId == boundRequestId) { responseHeaderLines.clear(); responseHeaderLines.addAll(lines) } } },
                onChunk = { chunk -> if (activeRequestControl === control && !control.cancelled) { control.lineBuffer.append(chunk); control.appendRawResponse(chunk) } }
            )
            EventQueue.invokeLater {
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
                }
                if (activeRequestControl === control) { isLoading = false; inflightBoundRequestId = null; activeRequestControl = null; activeRequestThread = null }
                if (editorRequestId == boundRequestId && requestGen == outboundRequestGeneration.get()) {
                    if (!control.cancelled) {
                        if (control.requestFailed) { statusCodeText = HttpExchangeErrorStatusMark; if (control.responseStatusCode < 0) { responseHeaderLines.clear(); responseHeaderLines.add("(无响应头 — 请求未成功)") } }
                        applyBufferUpdate(control.lineBuffer.drainUpdate(), responseLines) { responsePartialLine = it }
                    } else { control.lineBuffer.drainUpdate() }
                    responseTimeText = timeText; responseSizeText = formatBytes(control.totalBytes); isSseResponse = false
                }
                flusher.interrupt()
            }
        }
        activeRequestThread = worker
    }

    fun cancelActiveRequest() {
        if (!isLoading) return
        val boundId = inflightBoundRequestId ?: return
        val control = activeRequestControl ?: return
        control.cancelled = true
        closeQuietly(control.activeInput)
        val cancelMsg = "\n[请求已取消]\n"
        control.lineBuffer.append(cancelMsg)
        control.appendRawResponse(cancelMsg)
        if (editorRequestId == boundId) applyBufferUpdate(control.lineBuffer.drainUpdate(), responseLines) { responsePartialLine = it }
        else control.lineBuffer.drainUpdate()
        val timeText = formatDuration(System.currentTimeMillis() - control.startTimeMs)
        if (editorRequestId == boundId) responseTimeText = timeText
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
            rightTabIndex = if (editorRequestId == boundId) rightTabIndex.coerceIn(0, 2) else 0, isSseResponse = control.responseWasSse,
        ))
        activeRequestThread?.interrupt()
        isLoading = false; inflightBoundRequestId = null; isSseResponse = false; activeRequestControl = null; activeRequestThread = null
    }

    val exchangeMetrics = remember(appSettings.requestResponseFontSizeSp) { exchangeFontMetrics(appSettings.requestResponseFontSizeSp) }

    return AppViewModel(
        repository = repository, expandLoaded = expandLoaded, windowState = windowState, exchangeMetrics = exchangeMetrics,
        tree = tree, setTree = { tree = it },
        treeSelection = treeSelection, setTreeSelection = { treeSelection = it },
        expandedCollectionIds = expandedCollectionIds, setExpandedCollectionIds = { expandedCollectionIds = it },
        expandedFolderIds = expandedFolderIds, setExpandedFolderIds = { expandedFolderIds = it },
        treeSplitRatio = treeSplitRatio, setTreeSplitRatio = { treeSplitRatio = it },
        treeSidebarVisible = treeSidebarVisible, setTreeSidebarVisible = { treeSidebarVisible = it },
        treeScrollToRequestId = treeScrollToRequestId, setTreeScrollToRequestId = { treeScrollToRequestId = it },
        method = method, setMethod = { method = it },
        url = url, setUrl = { url = it },
        headersText = headersText, setHeadersText = { headersText = it },
        bodyText = bodyText, setBodyText = { bodyText = it },
        paramsText = paramsText, setParamsText = { paramsText = it },
        auth = auth, setAuth = { auth = it },
        editorRequestId = editorRequestId, setEditorRequestId = { editorRequestId = it },
        leftTabIndex = leftTabIndex, setLeftTabIndex = { leftTabIndex = it },
        methodMenuExpanded = methodMenuExpanded, setMethodMenuExpanded = { methodMenuExpanded = it },
        responseLines = responseLines, responseHeaderLines = responseHeaderLines,
        responseListState = responseListState, responseHeadersListState = responseHeadersListState,
        isLoading = isLoading, setIsLoading = { isLoading = it },
        isSseResponse = isSseResponse, setIsSseResponse = { isSseResponse = it },
        statusCodeText = statusCodeText, setStatusCodeText = { statusCodeText = it },
        responseTimeText = responseTimeText, setResponseTimeText = { responseTimeText = it },
        responseSizeText = responseSizeText, setResponseSizeText = { responseSizeText = it },
        responsePartialLine = responsePartialLine, setResponsePartialLine = { responsePartialLine = it },
        exchangeRequestPlainText = exchangeRequestPlainText, setExchangeRequestPlainText = { exchangeRequestPlainText = it },
        rightTabIndex = rightTabIndex, setRightTabIndex = { rightTabIndex = it },
        splitRatio = splitRatio, setSplitRatio = { splitRatio = it },
        contentRowWidthPx = contentRowWidthPx, setContentRowWidthPx = { contentRowWidthPx = it },
        historyEntries = historyEntries, selectedHistoryEpochMs = selectedHistoryEpochMs, setSelectedHistoryEpochMs = { selectedHistoryEpochMs = it },
        isDarkTheme = isDarkTheme, setIsDarkTheme = { isDarkTheme = it },
        jsonSyntaxHighlightEnabled = jsonSyntaxHighlightEnabled, setJsonSyntaxHighlightEnabled = { jsonSyntaxHighlightEnabled = it },
        showSettings = showSettings, setShowSettings = { showSettings = it },
        showEnvironmentManager = showEnvironmentManager, setShowEnvironmentManager = { showEnvironmentManager = it },
        showGlobalSearch = showGlobalSearch, setShowGlobalSearch = { showGlobalSearch = it },
        showCollectionSettings = showCollectionSettings, setShowCollectionSettings = { showCollectionSettings = it },
        collectionSettingsTarget = collectionSettingsTarget, setCollectionSettingsTarget = { collectionSettingsTarget = it },
        environmentsState = environmentsState, setEnvironmentsState = { environmentsState = it },
        appSettings = appSettings, setAppSettings = { appSettings = it },
        recentSwitcherActive = recentSwitcherActive, setRecentSwitcherActive = { recentSwitcherActive = it },
        recentSwitcherIds = recentSwitcherIds, setRecentSwitcherIds = { recentSwitcherIds = it },
        recentSwitcherIndex = recentSwitcherIndex, setRecentSwitcherIndex = { recentSwitcherIndex = it },
        onStartRequest = { startRequest() },
        onCancelRequest = { cancelActiveRequest() },
        onRefreshTree = { refreshTree() },
        onApplyRequestToEditor = { applyRequestToEditor(it) },
        onSelectTreeNode = { selectTreeNode(it) },
        onAddFolderAt = { addFolderAt(it) },
        onAddRequestAt = { addRequestAt(it) },
        onSaveEditor = { saveEditorIfBound() },
        getMruRequestIds = { mruRequestIdsForSwitcher() },
        onCommitEnvironments = { commitEnvironmentsState(it) },
    )
}