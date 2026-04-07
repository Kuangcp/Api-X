package app

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import db.AppPaths
import db.CachedHttpResponse
import db.CollectionRepository
import db.HarLogCodec
import db.HarSnapshot
import db.RequestResponseStore
import http.applyEnvironmentVariables
import http.BufferUpdate
import http.RequestControl
import http.RequestSidePanel
import http.RequestTopBar
import http.ResponsePanel
import http.substitutionMapForActive
import http.HttpExchangeErrorStatusMark
import http.exchangeFontMetrics
import http.closeQuietly
import http.mergeUrlWithParams
import http.parseHeadersForSend
import http.parseCurlCommand
import http.requestToCurlCommand
import http.responseHeaderLinesForHar
import http.sendRequestStreaming
import http.splitUrlQueryForParamsEditor
import tree.CollectionTreeSidebar
import tree.TreeDropTarget
import tree.TreeSelection
import tree.collectAllFolderIds
import tree.expandSetsForRequest
import tree.firstRequestSelection

@Composable
@Preview
fun App() {
    val repository = remember { CollectionRepository(AppPaths.collectionDatabasePath()) }

    var method by remember { mutableStateOf("GET") }
    var methodMenuExpanded by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("https://httpbin.org/get") }
    var headersText by remember {
        mutableStateOf(
            "Content-Type: application/json\nAccept: application/json"
        )
    }
    var bodyText by remember { mutableStateOf("{\n  \"name\": \"api-x\"\n}") }
    var paramsText by remember { mutableStateOf("") }

    var tree by remember { mutableStateOf(repository.loadTree()) }
    var treeSelection by remember { mutableStateOf<TreeSelection?>(null) }
    var editorRequestId by remember { mutableStateOf<String?>(null) }
    val expandLoaded = remember { TreeExpandPrefs.load() }
    var expandedCollectionIds by remember { mutableStateOf(expandLoaded.collectionIds) }
    var expandedFolderIds by remember { mutableStateOf(expandLoaded.folderIds) }
    var persistTreeExpand by remember { mutableStateOf(expandLoaded.fromSavedFile) }
    var didApplyDefaultTreeExpand by remember { mutableStateOf(false) }
    var treeSplitRatio by remember { mutableStateOf(0.2f) }
    var didPickInitialRequest by remember { mutableStateOf(false) }

    val editorIdSnap by rememberUpdatedState(editorRequestId)
    val methodSnap by rememberUpdatedState(method)
    val urlSnap by rememberUpdatedState(url)
    val headersSnap by rememberUpdatedState(headersText)
    val paramsSnap by rememberUpdatedState(paramsText)
    val bodySnap by rememberUpdatedState(bodyText)

    DisposableEffect(repository) {
        onDispose {
            editorIdSnap?.let {
                repository.saveRequestEditorFields(
                    it,
                    methodSnap,
                    urlSnap,
                    headersSnap,
                    paramsSnap,
                    bodySnap,
                )
            }
            repository.close()
        }
    }

    fun refreshTree() {
        tree = repository.loadTree()
    }

    fun saveEditorIfBound() {
        val id = editorRequestId ?: return
        repository.saveRequestEditorFields(id, method, url, headersText, paramsText, bodyText)
    }

    fun applyRequestToEditor(reqId: String) {
        val r = repository.getRequest(reqId) ?: return
        editorRequestId = reqId
        method = r.method
        url = r.url
        headersText = r.headersText
        paramsText = r.paramsText
        bodyText = r.bodyText
    }

    fun selectTreeNode(sel: TreeSelection) {
        when (sel) {
            is TreeSelection.Request -> {
                saveEditorIfBound()
                applyRequestToEditor(sel.id)
                treeSelection = sel
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
    val responseScopeKey = editorRequestId ?: ""
    val cachedResponse = remember(responseScopeKey) {
        editorRequestId?.let { RequestResponseStore.loadLatest(it) }
    }
    val responseLines = remember(responseScopeKey) {
        mutableStateListOf<String>().apply {
            when {
                editorRequestId == null -> add("请先选择或创建一个请求")
                else -> {
                    val snap = cachedResponse
                    if (snap != null) addAll(snap.responseBodyLines)
                    else add("响应结果会显示在这里")
                }
            }
        }
    }
    var responsePartialLine by remember(responseScopeKey) { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isSseResponse by remember(responseScopeKey) { mutableStateOf(false) }
    var statusCodeText by remember(responseScopeKey) {
        mutableStateOf(cachedResponse?.statusCodeText ?: "")
    }
    var responseTimeText by remember(responseScopeKey) {
        mutableStateOf(cachedResponse?.responseTimeText ?: "")
    }
    var responseSizeText by remember(responseScopeKey) {
        mutableStateOf(cachedResponse?.responseSizeText ?: "")
    }
    var activeRequestControl by remember { mutableStateOf<RequestControl?>(null) }
    var activeRequestThread by remember { mutableStateOf<Thread?>(null) }
    var splitRatio by remember { mutableStateOf(0.5f) }
    var contentRowWidthPx by remember { mutableStateOf(1f) }
    var leftTabIndex by remember { mutableStateOf(0) }
    var rightTabIndex by remember(responseScopeKey) {
        mutableStateOf(cachedResponse?.rightTabIndex?.coerceIn(0, 1) ?: 0)
    }
    val responseHeaderLines = remember(responseScopeKey) {
        mutableStateListOf<String>().apply {
            when {
                editorRequestId == null -> add("(暂无响应头)")
                else -> {
                    val snap = cachedResponse
                    if (snap != null) addAll(snap.responseHeaderLines)
                    else add("(暂无响应头)")
                }
            }
        }
    }
    val responseListState = remember(responseScopeKey) { LazyListState() }
    val responseHeadersListState = remember(responseScopeKey) { LazyListState() }
    var isDarkTheme by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showEnvironmentManager by remember { mutableStateOf(false) }
    var environmentsState by remember {
        mutableStateOf<EnvironmentsState>(
            withDefaultActiveWhenSingle(EnvironmentStore.snapshot())
        )
    }
    var appSettings by remember { mutableStateOf(AppSettingsStore.snapshot()) }

    fun commitEnvironmentsState(newState: EnvironmentsState) {
        EnvironmentStore.replace(newState)
        environmentsState = EnvironmentStore.snapshot()
    }
    /** 当前进行中的 HTTP 请求对应的 request id（用于计时器不污染切换后的请求文案）。 */
    var inflightBoundRequestId by remember { mutableStateOf<String?>(null) }
    /** 每次发起请求递增；避免 flusher 已 drain 但 EDT 尚未应用时，完成回调先清空 active 导致正文丢失。 */
    val outboundRequestGeneration = remember { AtomicInteger(0) }

    LaunchedEffect(method, url, headersText, paramsText, bodyText, editorRequestId) {
        val id = editorRequestId ?: return@LaunchedEffect
        delay(450)
        repository.saveRequestEditorFields(id, method, url, headersText, paramsText, bodyText)
    }

    LaunchedEffect(editorRequestId) {
        val id = editorRequestId ?: return@LaunchedEffect
        LastRequestPrefs.save(id)
    }

    LaunchedEffect(tree) {
        if (tree.isEmpty()) return@LaunchedEffect
        if (!didApplyDefaultTreeExpand) {
            didApplyDefaultTreeExpand = true
            if (!expandLoaded.fromSavedFile &&
                expandedCollectionIds.isEmpty() &&
                expandedFolderIds.isEmpty()
            ) {
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
        val effectiveRequestUrl =
            mergeUrlWithParams(reqUrlSnap, parseHeadersForSend(reqParamsSnap))
        val reqHeadersFullSnap = applyEnvironmentVariables(headersText, varMap)
        val reqBodySnap = applyEnvironmentVariables(bodyText, varMap)
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
        applyBufferUpdate(control.lineBuffer.drainUpdate(), responseLines) {
            responsePartialLine = it
        }
        val flusher = thread(isDaemon = true) {
            while (!control.finished && !control.cancelled) {
                try {
                    Thread.sleep(UI_REFRESH_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
                if (activeRequestControl !== control) break
                val update = control.lineBuffer.drainUpdate()
                if (!update.hasChanges()) continue
                EventQueue.invokeLater {
                    if (requestGen != outboundRequestGeneration.get()) return@invokeLater
                    if (editorRequestId != boundRequestId) return@invokeLater
                    applyBufferUpdate(update, responseLines) { partial ->
                        responsePartialLine = partial
                    }
                }
            }
        }
        val worker = thread {
            sendRequestStreaming(
                method = method,
                url = effectiveRequestUrl,
                body = reqBodySnap,
                headersText = reqHeadersFullSnap,
                control = control,
                onSseDetected = { isSse ->
                    EventQueue.invokeLater {
                        if (activeRequestControl === control && editorRequestId == boundRequestId) {
                            isSseResponse = isSse
                        }
                    }
                },
                onStatusCode = { code ->
                    EventQueue.invokeLater {
                        if (activeRequestControl === control && editorRequestId == boundRequestId) {
                            statusCodeText = code.toString()
                        }
                    }
                },
                onResponseTime = { },
                onProgress = { bytes ->
                    EventQueue.invokeLater {
                        if (activeRequestControl === control && editorRequestId == boundRequestId) {
                            responseSizeText = formatBytes(bytes)
                        }
                    }
                },
                onResponseHeaders = { lines ->
                    EventQueue.invokeLater {
                        if (activeRequestControl === control && editorRequestId == boundRequestId) {
                            responseHeaderLines.clear()
                            responseHeaderLines.addAll(lines)
                        }
                    }
                },
                onChunk = { chunk ->
                    if (activeRequestControl === control && !control.cancelled) {
                        control.lineBuffer.append(chunk)
                        control.appendRawResponse(chunk)
                    }
                }
            )
            EventQueue.invokeLater {
                control.finished = true
                val elapsed = System.currentTimeMillis() - control.startTimeMs
                val timeText = formatDuration(elapsed)
                if (!control.cancelled && !control.requestFailed) {
                    val code = control.responseStatusCode
                    RequestResponseStore.save(
                        boundRequestId,
                        HarSnapshot(
                            savedAtEpochMs = System.currentTimeMillis(),
                            requestMethod = reqMethodSnap,
                            requestUrl = effectiveRequestUrl,
                            requestHeadersFullText = reqHeadersFullSnap,
                            requestBody = reqBodySnap,
                            requestHeadersSent = parseHeadersForSend(reqHeadersFullSnap),
                            responseStatus = if (code >= 0) code else 0,
                            responseStatusText = if (code >= 0) {
                                HarLogCodec.responseStatusPhrase(code)
                            } else {
                                ""
                            },
                            responseHeaderLines = responseHeaderLinesForHar(
                                control.responseHeaderSnapshot,
                                control.responseBodyDecodedForHar,
                            ),
                            responseBodyLines = control.snapshotRawBodyLines(),
                            responseTimeMs = elapsed,
                            responseSizeBytes = control.totalBytes,
                            responseTimeLabel = timeText,
                            responseSizeLabel = formatBytes(control.totalBytes),
                            rightTabIndex = tabAtStart.coerceIn(0, 1),
                            isSseResponse = control.responseWasSse,
                        ),
                    )
                }
                if (activeRequestControl === control) {
                    isLoading = false
                    inflightBoundRequestId = null
                    activeRequestControl = null
                    activeRequestThread = null
                }
                if (editorRequestId == boundRequestId && requestGen == outboundRequestGeneration.get()) {
                    if (!control.cancelled) {
                        if (control.requestFailed) {
                            statusCodeText = HttpExchangeErrorStatusMark
                            if (control.responseStatusCode < 0) {
                                responseHeaderLines.clear()
                                responseHeaderLines.add("(无响应头 — 请求未成功)")
                            }
                        }
                        applyBufferUpdate(control.lineBuffer.drainUpdate(), responseLines) { partial ->
                            responsePartialLine = partial
                        }
                    } else {
                        control.lineBuffer.drainUpdate()
                    }
                    responseTimeText = timeText
                    responseSizeText = formatBytes(control.totalBytes)
                    isSseResponse = false
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
        if (editorRequestId == boundId) {
            applyBufferUpdate(control.lineBuffer.drainUpdate(), responseLines) { partial ->
                responsePartialLine = partial
            }
        } else {
            control.lineBuffer.drainUpdate()
        }
        val timeText = formatDuration(System.currentTimeMillis() - control.startTimeMs)
        if (editorRequestId == boundId) {
            responseTimeText = timeText
        }
        val sc = control.responseStatusCode
        val varMap = environmentsState.substitutionMapForActive()
        val resolvedUrl = applyEnvironmentVariables(url, varMap)
        val resolvedParams = applyEnvironmentVariables(paramsText, varMap)
        val cancelUrl = mergeUrlWithParams(resolvedUrl, parseHeadersForSend(resolvedParams))
        val resolvedHeaders = applyEnvironmentVariables(headersText, varMap)
        val resolvedBody = applyEnvironmentVariables(bodyText, varMap)
        RequestResponseStore.save(
            boundId,
            HarSnapshot(
                savedAtEpochMs = System.currentTimeMillis(),
                requestMethod = method,
                requestUrl = cancelUrl,
                requestHeadersFullText = resolvedHeaders,
                requestBody = resolvedBody,
                requestHeadersSent = parseHeadersForSend(resolvedHeaders),
                responseStatus = if (sc >= 0) sc else 0,
                responseStatusText = if (sc >= 0) HarLogCodec.responseStatusPhrase(sc) else "",
                responseHeaderLines = responseHeaderLinesForHar(
                    control.responseHeaderSnapshot,
                    control.responseBodyDecodedForHar,
                ),
                responseBodyLines = control.snapshotRawBodyLines(),
                responseTimeMs = System.currentTimeMillis() - control.startTimeMs,
                responseSizeBytes = control.totalBytes,
                responseTimeLabel = timeText,
                responseSizeLabel = formatBytes(control.totalBytes),
                rightTabIndex = if (editorRequestId == boundId) {
                    rightTabIndex.coerceIn(0, 1)
                } else {
                    0
                },
                isSseResponse = control.responseWasSse,
            ),
        )
        activeRequestThread?.interrupt()
        isLoading = false
        inflightBoundRequestId = null
        isSseResponse = false
        activeRequestControl = null
        activeRequestThread = null
    }

    MaterialTheme(
        colors = appMaterialColors(isDarkTheme, appSettings.backgroundHex),
        typography = typographyFromSettings(appSettings),
    ) {
        val exchangeMetrics = remember(appSettings.requestResponseFontSizeSp) {
            exchangeFontMetrics(appSettings.requestResponseFontSizeSp)
        }
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(10.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.isCtrlPressed && event.key == Key.Enter -> {
                            startRequest()
                            true
                        }
                        event.key == Key.Escape -> {
                            if (isLoading) {
                                cancelActiveRequest()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RequestTopBar(
                isLoading = isLoading,
                isDarkTheme = isDarkTheme,
                environmentsState = environmentsState,
                onActiveEnvironmentChange = { id ->
                    commitEnvironmentsState(environmentsState.copy(activeEnvironmentId = id))
                },
                onManageEnvironmentsClick = { showEnvironmentManager = true },
                onThemeToggle = { isDarkTheme = !isDarkTheme },
                onSettingsClick = { showSettings = true },
                onImportCurlClick = {
                    try {
                        val clipboardText = readClipboardText()
                        val parsed = parseCurlCommand(clipboardText)
                        method = parsed.method
                        val rawUrl = parsed.url.ifBlank { url }
                        val (urlNoQuery, queryParamsText) = splitUrlQueryForParamsEditor(rawUrl)
                        url = urlNoQuery.ifBlank { rawUrl }
                        paramsText = queryParamsText
                        headersText = parsed.headers.joinToString("\n")
                        bodyText = parsed.body
                        editorRequestId?.let {
                            repository.saveRequestEditorFields(
                                it, method, url, headersText, paramsText, bodyText,
                            )
                        }
                        setSingleResponseMessage(responseLines, "已从剪贴板导入 cURL")
                        responsePartialLine = null
                        responseHeaderLines.clear()
                        responseHeaderLines.add("(暂无响应头)")
                        statusCodeText = "-"
                        responseTimeText = "-"
                        responseSizeText = "0 B"
                    } catch (e: Exception) {
                        setSingleResponseMessage(responseLines, "导入 cURL 失败: ${e.message}")
                        responsePartialLine = null
                        responseHeaderLines.clear()
                        responseHeaderLines.add("(暂无响应头)")
                    }
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { contentRowWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            ) {
                val middleFraction = 1f - treeSplitRatio
                CollectionTreeSidebar(
                    modifier = Modifier.weight(treeSplitRatio),
                    tree = tree,
                    selectedNode = treeSelection,
                    editorBoundRequestId = editorRequestId,
                    expandedCollectionIds = expandedCollectionIds,
                    expandedFolderIds = expandedFolderIds,
                    onToggleCollection = { id ->
                        expandedCollectionIds =
                            if (id in expandedCollectionIds) expandedCollectionIds - id
                            else expandedCollectionIds + id
                    },
                    onToggleFolder = { id ->
                        expandedFolderIds =
                            if (id in expandedFolderIds) expandedFolderIds - id
                            else expandedFolderIds + id
                    },
                    onSelectNode = { selectTreeNode(it) },
                    onAddCollection = {
                        val id = repository.createCollection("新集合")
                        refreshTree()
                        expandedCollectionIds = expandedCollectionIds + id
                        treeSelection = TreeSelection.Collection(id)
                    },
                    onAddFolder = { treeSelection?.let { addFolderAt(it) } },
                    onAddRequest = { treeSelection?.let { addRequestAt(it) } },
                    onContextAddFolder = { addFolderAt(it) },
                    onContextAddRequest = { addRequestAt(it) },
                    onRename = { sel, newName ->
                        when (sel) {
                            is TreeSelection.Collection -> repository.renameCollection(sel.id, newName)
                            is TreeSelection.Folder -> repository.renameFolder(sel.id, newName)
                            is TreeSelection.Request -> repository.renameRequest(sel.id, newName)
                        }
                        refreshTree()
                    },
                    onDelete = { sel ->
                        saveEditorIfBound()
                        when (sel) {
                            is TreeSelection.Collection -> repository.deleteCollection(sel.id)
                            is TreeSelection.Folder -> repository.deleteFolder(sel.id)
                            is TreeSelection.Request -> {
                                repository.deleteRequest(sel.id)
                                RequestResponseStore.deleteRequestArtifacts(sel.id)
                            }
                        }
                        val nextTree = repository.loadTree()
                        tree = nextTree
                        if (editorRequestId != null && repository.getRequest(editorRequestId!!) == null) {
                            editorRequestId = null
                            firstRequestSelection(nextTree)?.let { next ->
                                applyRequestToEditor(next.id)
                                treeSelection = next
                            } ?: run {
                                LastRequestPrefs.clear()
                                method = "GET"
                                url = ""
                                headersText = ""
                                paramsText = ""
                                bodyText = ""
                                treeSelection =
                                    nextTree.firstOrNull()?.let { TreeSelection.Collection(it.id) }
                            }
                        }
                    },
                    folderAddEnabled = repository.newFolderTarget(treeSelection) != null,
                    requestAddEnabled = repository.newRequestTarget(treeSelection) != null,
                    onExportRequestAsCurl = { rid ->
                        val r = repository.getRequest(rid) ?: return@CollectionTreeSidebar
                        val vm = environmentsState.substitutionMapForActive()
                        writeClipboardText(
                            requestToCurlCommand(
                                r.method,
                                mergeUrlWithParams(
                                    applyEnvironmentVariables(r.url, vm),
                                    parseHeadersForSend(applyEnvironmentVariables(r.paramsText, vm)),
                                ),
                                applyEnvironmentVariables(r.headersText, vm),
                                applyEnvironmentVariables(r.bodyText, vm),
                            ),
                        )
                        setSingleResponseMessage(responseLines, "已复制 cURL 到剪贴板")
                        responsePartialLine = null
                    },
                    onDuplicateRequestBelow = { rid ->
                        saveEditorIfBound()
                        val newId = repository.duplicateRequestBelow(rid) ?: return@CollectionTreeSidebar
                        refreshTree()
                        val placed = repository.getRequest(newId) ?: return@CollectionTreeSidebar
                        expandedCollectionIds = expandedCollectionIds + placed.collectionId
                        if (placed.folderId != null) {
                            expandedFolderIds = expandedFolderIds + placed.folderId
                        }
                        applyRequestToEditor(newId)
                        treeSelection = TreeSelection.Request(newId)
                    },
                    onApplyTreeDrop = { payload, target ->
                        val ok = repository.applyTreeDrop(payload, target)
                        if (ok) {
                            refreshTree()
                            when (target) {
                                is TreeDropTarget.IntoFolder -> {
                                    expandedCollectionIds = expandedCollectionIds + target.collectionId
                                    expandedFolderIds = expandedFolderIds + target.folderId
                                }
                                is TreeDropTarget.IntoCollection -> {
                                    expandedCollectionIds = expandedCollectionIds + target.collectionId
                                }
                                is TreeDropTarget.FolderSlot -> {
                                    expandedCollectionIds = expandedCollectionIds + target.collectionId
                                    target.parentFolderId?.let { pid ->
                                        expandedFolderIds = expandedFolderIds + pid
                                    }
                                }
                                is TreeDropTarget.RequestSlot -> {
                                    expandedCollectionIds = expandedCollectionIds + target.collectionId
                                    target.folderId?.let { fid ->
                                        expandedFolderIds = expandedFolderIds + fid
                                    }
                                }
                            }
                        }
                        ok
                    },
                )
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .fillMaxHeight()
                        .pointerInput(contentRowWidthPx) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val next = treeSplitRatio + (dragAmount.x / contentRowWidthPx)
                                treeSplitRatio = next.coerceIn(0.12f, 0.42f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
                    )
                }
                RequestSidePanel(
                    modifier = Modifier.weight(middleFraction * splitRatio),
                    exchangeMetrics = exchangeMetrics,
                    editorRequestId = editorRequestId,
                    isLoading = isLoading,
                    method = method,
                    methodMenuExpanded = methodMenuExpanded,
                    onMethodMenuExpandedChange = { methodMenuExpanded = it },
                    onMethodSelected = { method = it },
                    url = url,
                    onUrlChange = { url = it },
                    onSendOrCancel = {
                        if (!isLoading) startRequest() else cancelActiveRequest()
                    },
                    leftTabIndex = leftTabIndex,
                    onLeftTabIndexChange = { leftTabIndex = it.coerceIn(0, 2) },
                    bodyText = bodyText,
                    onBodyTextChange = { bodyText = it },
                    headersText = headersText,
                    onHeadersTextChange = { headersText = it },
                    paramsText = paramsText,
                    onParamsTextChange = { paramsText = it },
                )
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .fillMaxHeight()
                        .pointerInput(contentRowWidthPx, middleFraction) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                if (middleFraction > 0.02f) {
                                    val next = splitRatio + dragAmount.x / (contentRowWidthPx * middleFraction)
                                    splitRatio = next.coerceIn(0.2f, 0.8f)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
                    )
                }
                ResponsePanel(
                    modifier = Modifier.weight(middleFraction * (1f - splitRatio)),
                    exchangeMetrics = exchangeMetrics,
                    statusCodeText = statusCodeText,
                    responseTimeText = responseTimeText,
                    responseSizeText = responseSizeText,
                    responseLines = responseLines,
                    responsePartialLine = responsePartialLine,
                    responseHeaderLines = responseHeaderLines,
                    rightTabIndex = rightTabIndex,
                    onRightTabIndexChange = { rightTabIndex = it },
                    isSseResponse = isSseResponse,
                    isResponseLoading = isLoading,
                    responseListState = responseListState,
                    responseHeadersListState = responseHeadersListState
                )
            }
        }
            SettingsDialogWindow(
                visible = showSettings,
                isDarkTheme = isDarkTheme,
                typographyBase = typographyFromSettings(appSettings),
                onCloseRequest = { showSettings = false },
                onSaved = { saved ->
                    AppSettingsStore.replace(saved)
                    appSettings = saved
                },
            )
            EnvironmentManagerDialogWindow(
                visible = showEnvironmentManager,
                isDarkTheme = isDarkTheme,
                typographyBase = typographyFromSettings(appSettings),
                initial = environmentsState,
                onCloseRequest = { showEnvironmentManager = false },
                onSaved = { saved -> commitEnvironmentsState(saved) },
            )
        }
    }
}

private const val UI_REFRESH_INTERVAL_MS = 100L

private val lightThemeDefaultBackground = hexToColor("#EBECF0")

/** 深色主题：前景统一为白色，避免桌面端部分组件仍使用默认深色字色。 */
internal fun appMaterialColors(isDark: Boolean, backgroundHex: String) =
    parseHexColorOrNull(backgroundHex).let { customBg ->
        when {
            isDark -> {
                val base = apiXDarkColors()
                if (customBg != null) base.copy(background = customBg) else base
            }
            else -> {
                // 设置里存的深色预设只在深色主题下合理；浅色主题仍套用会得到深色底 + 浅色主题的深色字。
                val bg = when {
                    customBg == null -> lightThemeDefaultBackground
                    customBg.isVisuallyDarkBackground() -> lightThemeDefaultBackground
                    else -> customBg
                }
                lightColors(background = bg)
            }
        }
    }

internal fun apiXDarkColors() = darkColors(
    primary = Color(0xFF90CAF9),
    primaryVariant = Color(0xFF42A5F5),
    secondary = Color(0xFF90CAF9),
    background = Color(0xFF292B2E),
    surface = Color(0xFF32353B),
    error = Color(0xFFCF6679),
    onPrimary = Color(0xFF0D47A1),
    onSecondary = Color(0xFF0D47A1),
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.Black
)

internal fun hexToColor(hex: String): Color {
    val value = hex.removePrefix("#")
    val argb = when (value.length) {
        6 -> "FF$value"
        8 -> value
        else -> throw IllegalArgumentException("颜色格式错误: $hex，需为 #RRGGBB 或 #AARRGGBB")
    }

    val alpha = argb.substring(0, 2).toInt(16)
    val red = argb.substring(2, 4).toInt(16)
    val green = argb.substring(4, 6).toInt(16)
    val blue = argb.substring(6, 8).toInt(16)
    return Color(red, green, blue, alpha)
}

private fun hexToColorCode(hex: String): ULong {
    val value = hex.removePrefix("#")
    val argb = when (value.length) {
        6 -> "FF$value"
        8 -> value
        else -> throw IllegalArgumentException("颜色格式错误: $hex，需为 #RRGGBB 或 #AARRGGBB")
    }
    return ("0x$argb").removePrefix("0x").toULong(16)
}

private fun applyBufferUpdate(
    update: BufferUpdate,
    responseLines: MutableList<String>,
    setPartial: (String?) -> Unit
) {
    if (update.newLines.isNotEmpty()) {
        responseLines.addAll(update.newLines)
    }
    if (update.partialLine != null || update.newLines.isNotEmpty()) {
        setPartial(update.partialLine)
    }
}

private fun setSingleResponseMessage(responseLines: MutableList<String>, message: String) {
    responseLines.clear()
    responseLines += message
}

private fun formatDuration(ms: Long): String {
    return when {
        ms < 10_000 -> "${ms}ms"
        ms < 80_000 -> String.format("%.1fS", ms / 1000.0)
        else -> String.format("%.1fmin", ms / 60_000.0)
    }
}

private fun formatBytes(bytes: Long): String {
    val b = bytes.toDouble()
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.2f KB", b / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format("%.2f MB", b / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", b / (1024.0 * 1024.0 * 1024.0))
    }
}

private fun readClipboardText(): String {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val data = clipboard.getData(DataFlavor.stringFlavor) as? String ?: ""
    if (data.isBlank()) throw IllegalArgumentException("剪贴板为空")
    return data.trim()
}

private fun writeClipboardText(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

/** 仅用于取得与业务代码相同的 ClassLoader，以探测打包进 jar 的 `app-icon.png`。 */
private object AppClasspathAnchor

fun main() = application {
    val loaded = remember { WindowPrefs.load() }
    val hasWindowIcon = remember {
        AppClasspathAnchor::class.java.classLoader?.getResource("app-icon.png") != null
    }
    val windowIcon = if (hasWindowIcon) painterResource("app-icon.png") else null
    val windowState = rememberWindowState(
        position = if (loaded.xDp != null && loaded.yDp != null) {
            WindowPosition.Absolute(loaded.xDp.dp, loaded.yDp.dp)
        } else {
            WindowPosition.PlatformDefault
        },
        width = loaded.widthDp.dp,
        height = loaded.heightDp.dp,
    )
    Window(
        title = "Api-X",
        icon = windowIcon,
        state = windowState,
        onCloseRequest = {
            persistWindowGeometry(windowState)
            exitApplication()
        },
    ) {
        App()
    }
}

private fun persistWindowGeometry(state: WindowState) {
    if (state.placement != WindowPlacement.Floating) return
    val pos = state.position
    if (pos !is WindowPosition.Absolute) return
    if (!state.size.isSpecified) return
    WindowPrefs.save(
        xDp = pos.x.value,
        yDp = pos.y.value,
        widthDp = state.size.width.value,
        heightDp = state.size.height.value,
    )
}
