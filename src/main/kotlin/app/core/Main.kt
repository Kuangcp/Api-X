package app.core

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.github.kuangcp.api_x.generated.resources.Res
import com.github.kuangcp.api_x.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource
import db.AppPaths
import db.CollectionRepository
import db.RequestResponseStore
import http.ExchangeFontMetrics
import http.exchangeFontMetrics
import http.request.RequestEditorProps
import http.request.RequestSidePanel
import http.request.RequestTabBar
import http.request.RequestTopBar
import http.response.ResponsePanel
import kotlinx.coroutines.delay
import tree.collectAllFolderIds
import tree.CollectionTreeSidebar
import tree.TreeDragPayload
import tree.TreeDropTarget
import tree.TreeSelection
import tree.expandSetsForRequest
import tree.firstRequestSelection
import app.state.TreeState
import app.state.RequestEditorState
import app.state.ResponseState
import app.state.ThemeState
import app.state.DialogState
import app.state.EnvironmentState
import app.state.AppSettingsState
import app.state.ToastState
import app.state.RecentSwitcherState
import app.settings.WindowPrefs
import app.settings.TreeExpandPrefs
import app.settings.LastRequestPrefs
import app.settings.EnvironmentStore
import app.settings.AppSettingsStore
import app.settings.AppSettingsBridge
import app.dialog.RecentRequestSwitcherOverlay
import app.ui.Dialogs
import app.ui.ErrorBoundary
import app.ui.appMaterialColors
import app.ui.typographyFromSettings
import app.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent

@Composable
fun App(onExitRequest: () -> Unit) {
    val loaded = remember { WindowPrefs.load() }
    val windowIcon = painterResource(Res.drawable.app_icon)
    val windowState = rememberWindowState(
        position = if (loaded.xDp != null && loaded.yDp != null) {
            WindowPosition.Absolute(loaded.xDp.dp, loaded.yDp.dp)
        } else {
            WindowPosition.PlatformDefault
        },
        width = loaded.widthDp.dp,
        height = loaded.heightDp.dp,
    )
    val repository = remember { CollectionRepository(AppPaths.collectionDatabasePath()) }
    val expandLoaded = remember { TreeExpandPrefs.load() }

    val environmentStore = remember { EnvironmentStore() }
    val appSettingsStore = remember { AppSettingsStore() }
    remember { AppSettingsBridge.store = appSettingsStore }

    val (
        treeState,
        editorState,
        responseState,
        themeState,
        dialogState,
        environmentState,
        appSettingsState,
        toastState,
        recentSwitcherState,
    ) = rememberAppDependencies(repository, expandLoaded, environmentStore, appSettingsStore)

    val exchangeMetrics = remember(appSettingsState.appSettings.requestResponseFontSizeSp) {
        exchangeFontMetrics(appSettingsState.appSettings.requestResponseFontSizeSp)
    }

    var splitRatio by remember { mutableStateOf(0.5f) }
    var contentRowWidthPx by remember { mutableStateOf(1f) }

    val currentSession = editorState.editorRequestId?.let { responseState.getOrCreateSession(it) }

    LaunchedEffect(editorState.editorRequestId) {
        val id = editorState.editorRequestId ?: return@LaunchedEffect
        Logger.info("CACHE") { "LaunchedEffect start: id=$id, requestGen=${responseState.getOrCreateSession(id).requestGen}" }
        val session = responseState.getOrCreateSession(id)
        try {
            Logger.info("CACHE") { "Loading cache for id=$id, isCacheLoading=${session.isCacheLoading}" }
            val cached = withContext(Dispatchers.Default) {
                val start = System.currentTimeMillis()
                val result = runCatching { RequestResponseStore.loadLatest(id) }.getOrElse { e ->
                    Logger.error("CACHE", e) { "loadLatest failed for id=$id: ${e.message}" }
                    null
                }
                Logger.info("CACHE") { "loadLatest for id=$id done in ${System.currentTimeMillis() - start}ms, found=${result != null}" }
                result
            }
            Logger.info("CACHE") { "After loadLatest: id=$id, requestGen=${session.requestGen}, isCacheLoading=${session.isCacheLoading}, cached=${cached != null}" }
            if (session.requestGen != 0) {
                Logger.info("CACHE") { "Skip cache apply for id=$id: requestGen=${session.requestGen} (HTTP request started)" }
                return@LaunchedEffect
            }
            if (cached != null) {
                Logger.info("CACHE") { "Applying cached data for id=$id, bodyLines=${cached.responseBodyLines.size}, headerLines=${cached.responseHeaderLines.size}" }
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
                Logger.info("CACHE") { "Applied cache for id=$id, responseLines=${session.responseLines.size}" }
            } else {
                Logger.info("CACHE") { "No cached data for id=$id, showing empty state" }
                session.responseLines.clear()
                session.responseLines.add("响应结果会显示在这里")
                session.responseHeaderLines.clear()
                session.responseHeaderLines.add("(暂无响应头)")
            }
        } catch (e: Exception) {
            Logger.error("CACHE", e) { "Exception in cache LaunchedEffect for id=$id: ${e.message}" }
        } finally {
            Logger.info("CACHE") { "Cache LaunchedEffect finally for id=$id, isCacheLoading=${session.isCacheLoading} -> false, lines=${session.responseLines.size}, firstLine=${session.responseLines.firstOrNull()}" }
            session.isCacheLoading = false
            if (session.responseLines.firstOrNull() == "加载中…") {
                session.responseLines.clear()
                session.responseLines.add("响应结果会显示在这里")
                session.responseHeaderLines.clear()
                session.responseHeaderLines.add("(暂无响应头)")
            }
            responseState.cacheRefreshVersion++
            Logger.info("CACHE") { "cacheRefreshVersion incremented to ${responseState.cacheRefreshVersion}" }
        }
    }

    val cacheVersion = responseState.cacheRefreshVersion

    fun cycleRecentSwitcher(forward: Boolean) {
        val list = recentSwitcherState.ids
        if (list.isEmpty()) {
            recentSwitcherState.active = false
            return
        }
        val next = if (forward) {
            (recentSwitcherState.index + 1) % list.size
        } else {
            (recentSwitcherState.index - 1 + list.size) % list.size
        }
        recentSwitcherState.index = next
    }

    fun commitRecentSwitcherSelectionAndClose() {
        recentSwitcherState.active = false
        val list = recentSwitcherState.ids
        if (list.isEmpty()) return
        val id = list[recentSwitcherState.index.coerceIn(0, list.lastIndex)]
        if (repository.getRequest(id) != null) {
            expandSetsForRequest(treeState.tree, id)?.let { (cols, folders) ->
                treeState.expandedCollectionIds = treeState.expandedCollectionIds + cols
                treeState.expandedFolderIds = treeState.expandedFolderIds + folders
            }
            selectTreeNode(TreeSelection.Request(id), treeState, editorState)
            treeState.treeScrollToRequestId = id
        }
    }

    fun activateRecentSwitcher(forward: Boolean) {
        val list = editorState.mruRequestIdsForSwitcher()
        if (list.isEmpty()) return
        recentSwitcherState.ids = list
        recentSwitcherState.active = true
        recentSwitcherState.index = when {
            list.size == 1 -> 0
            forward -> 1
            else -> list.lastIndex
        }
    }

    DisposableEffect(recentSwitcherState.active, recentSwitcherState.ids, recentSwitcherState.index) {
        val listener = AWTEventListener { raw ->
            val keyEvent = raw as? KeyEvent ?: return@AWTEventListener
            val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow ?: return@AWTEventListener
            if (!activeWindow.isFocused) return@AWTEventListener

            if (!recentSwitcherState.active) {
                if (
                    keyEvent.id == KeyEvent.KEY_PRESSED &&
                    keyEvent.keyCode == KeyEvent.VK_TAB &&
                    keyEvent.isControlDown
                ) {
                    activateRecentSwitcher(forward = !keyEvent.isShiftDown)
                    keyEvent.consume()
                }
                return@AWTEventListener
            }

            when (keyEvent.id) {
                KeyEvent.KEY_PRESSED -> {
                    when {
                        keyEvent.keyCode == KeyEvent.VK_ESCAPE -> {
                            recentSwitcherState.active = false
                            keyEvent.consume()
                        }
                        keyEvent.keyCode == KeyEvent.VK_TAB -> {
                            cycleRecentSwitcher(forward = !keyEvent.isShiftDown)
                            keyEvent.consume()
                        }
                    }
                }
                KeyEvent.KEY_RELEASED -> {
                    if (keyEvent.keyCode == KeyEvent.VK_CONTROL || !keyEvent.isControlDown) {
                        commitRecentSwitcherSelectionAndClose()
                        keyEvent.consume()
                    }
                }
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.KEY_EVENT_MASK)
        onDispose {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
        }
    }

    RequestEditorEffects(editorState, treeState, repository)
    LastRequestEffects(editorState)
    TreeEffects(treeState, editorState, repository, expandLoaded)
    AppDisposableEffect(editorState, repository)

    Window(
        title = "Api-X",
        icon = windowIcon,
        state = windowState,
        undecorated = true,
        onCloseRequest = {
            persistWindowGeometry(windowState)
            onExitRequest()
        },
        onPreviewKeyEvent = { event ->
            if (recentSwitcherState.active) {
                true
            } else {
                if (event.type == KeyEventType.KeyDown) {
                    when {
                        (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.K -> { dialogState.showGlobalSearch = true; true }
                        (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.B -> { treeState.treeSidebarVisible = !treeState.treeSidebarVisible; true }
                        event.isCtrlPressed && event.key == Key.Enter -> { startRequest(editorState, responseState, environmentState, repository); true }
                        event.key == Key.Escape -> { if (currentSession?.isLoading == true) { cancelActiveRequest(editorState, responseState, environmentState, repository); true } else false }
                        else -> false
                    }
                } else false
            }
        },
    ) {
        MaterialTheme(colors = appMaterialColors(themeState.isDarkTheme, appSettingsState.appSettings.backgroundHex), typography = typographyFromSettings(appSettingsState.appSettings)) {
            Box(modifier = Modifier.fillMaxSize()) {
                    var isDragging by remember { mutableStateOf(false) }
                    var ghostDelta by remember { mutableStateOf(0f) }
                    var splitHandlePos by remember { mutableStateOf(Offset.Zero) }
                    var splitHandleH by remember { mutableStateOf(0) }
                    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background).padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RequestTopBar(
                        isLoading = currentSession?.isLoading ?: false,
                        isDarkTheme = themeState.isDarkTheme,
                        environmentsState = environmentState.environmentsState,
                        onActiveEnvironmentChange = { environmentState.environmentsState = environmentState.environmentsState.copy(activeEnvironmentId = it) },
                        onManageEnvironmentsClick = { dialogState.showEnvironmentManager = true },
                        onThemeToggle = { themeState.isDarkTheme = !themeState.isDarkTheme },
                        onSettingsClick = { dialogState.showSettings = true },
                        mainWindowState = windowState,
                        onWindowCloseRequest = { persistWindowGeometry(windowState); onExitRequest() },
                        onImportCollectionClick = { importCollection(treeState, toastState, repository) },
                        onImportCurlClick = { importCurl(treeState, editorState, responseState, toastState, repository) },
                        onPushDataClick = { pushData(repository, environmentStore, toastState) },
                        onPullDataClick = { pullData(treeState, environmentState, environmentStore, toastState, repository) },
                    )
                    Row(modifier = Modifier.fillMaxWidth().weight(1f).onSizeChanged { contentRowWidthPx = it.width.toFloat().coerceAtLeast(1f) }) {
                        val middleFraction = if (treeState.treeSidebarVisible) 1f - treeState.treeSplitRatio else 1f
                        if (treeState.treeSidebarVisible) {
                            CollectionTreeSidebar(
                                modifier = Modifier.weight(treeState.treeSplitRatio),
                                tree = treeState.tree,
                                selectedNode = treeState.treeSelection,
                                treeScrollToRequestId = treeState.treeScrollToRequestId,
                                onTreeScrollToRequestHandled = { treeState.treeScrollToRequestId = null },
                                editorBoundRequestId = editorState.editorRequestId,
                                expandedCollectionIds = treeState.expandedCollectionIds,
                                expandedFolderIds = treeState.expandedFolderIds,
                                runningRequestIds = responseState.runningRequestIds,
                                onToggleCollection = { treeState.toggleCollection(it) },
                                onToggleFolder = { treeState.toggleFolder(it) },
                                onSelectNode = { selectTreeNode(it, treeState, editorState) },
                                onAddCollection = {
                                    val id = repository.createCollection("新集合")
                                    treeState.refresh()
                                    treeState.expandedCollectionIds = treeState.expandedCollectionIds + id
                                    treeState.treeSelection = TreeSelection.Collection(id)
                                },
                                onAddFolder = { treeState.treeSelection?.let { treeState.addFolderAt(it) } },
                                onAddRequest = { treeState.treeSelection?.let { addRequestAt(it, treeState, editorState) } },
                                onContextAddFolder = { treeState.addFolderAt(it) },
                                onContextAddRequest = { addRequestAt(it, treeState, editorState) },
                                onRename = { sel, newName ->
                                    when (sel) {
                                        is TreeSelection.Collection -> repository.renameCollection(sel.id, newName)
                                        is TreeSelection.Folder -> repository.renameFolder(sel.id, newName)
                                        is TreeSelection.Request -> repository.renameRequest(sel.id, newName)
                                    }
                                    treeState.refresh()
                                },
                                onDelete = { deleteSelection(treeState, editorState, responseState, repository, it) },
                                onCountFolderContents = { repository.countFolderContents(it.id) },
                                onSettings = { dialogState.collectionSettingsTarget = it; dialogState.showCollectionSettings = true },
                                folderAddEnabled = repository.newFolderTarget(treeState.treeSelection) != null,
                                requestAddEnabled = repository.newRequestTarget(treeState.treeSelection) != null,
                                onExportRequestAsCurl = { exportAsCurl(repository, environmentState, toastState, it) },
                                onExportRequestAsGo = { exportAsGo(repository, environmentState, toastState, it) },
                                onExportPostmanCollection = { exportPostmanCollection(repository, toastState, responseState, it) },
                                onDuplicateRequestBelow = { duplicateRequest(treeState, editorState, repository, it) },
                                onApplyTreeDrop = { payload, target -> applyTreeDrop(treeState, repository, payload, target) },
                            )
                            var treeDragStartRatio by remember { mutableStateOf(0f) }
                            var treeDragStartWidth by remember { mutableStateOf(1f) }
                            SplitHandle(contentRowWidthPx,
                                onDragStart = { treeDragStartRatio = treeState.treeSplitRatio; treeDragStartWidth = contentRowWidthPx },
                                onDrag = { totalDelta -> treeState.treeSplitRatio = (treeDragStartRatio + totalDelta / treeDragStartWidth).coerceIn(0.02f, 0.98f) },
                                onDragEnd = {},
                            )
                        }
                        var splitDragStartRatio by remember { mutableStateOf(0f) }
                        var splitDragStartWidth by remember { mutableStateOf(1f) }
                        var splitDragStartMf by remember { mutableStateOf(1f) }
                        Column(modifier = Modifier.weight(middleFraction * splitRatio)) {
                            if (editorState.openTabIds.isNotEmpty()) {
                                RequestTabBar(
                                    openTabIds = editorState.openTabIds,
                                    activeTabId = editorState.activeTabId,
                                    onTabSelected = { editorState.selectTab(it) },
                                    onTabClosed = { editorState.closeTab(it) },
                                    tree = treeState.tree,
                                )
                            }
                            RequestSidePanel(
                                modifier = Modifier.weight(1f),
                                exchangeMetrics = exchangeMetrics,
                                isDarkTheme = themeState.isDarkTheme,
                                props = RequestEditorProps(
                                    editorRequestId = editorState.editorRequestId,
                                    isLoading = currentSession?.isLoading ?: false,
                                    method = editorState.method,
                                    methodMenuExpanded = editorState.methodMenuExpanded,
                                    onMethodMenuExpandedChange = { editorState.methodMenuExpanded = it },
                                    onMethodSelected = { editorState.method = it },
                                    url = editorState.url,
                                    onUrlChange = { editorState.url = it },
                                    onSendOrCancel = {
                                        if (currentSession?.isLoading != true) {
                                            startRequest(editorState, responseState, environmentState, repository)
                                        } else {
                                            cancelActiveRequest(editorState, responseState, environmentState, repository)
                                        }
                                    },
                                    leftTabIndex = editorState.leftTabIndex,
                                    onLeftTabIndexChange = { editorState.leftTabIndex = it.coerceIn(0, 3) },
                                    bodyText = editorState.bodyText,
                                    onBodyTextChange = { editorState.bodyText = it },
                                    headersText = editorState.headersText,
                                    onHeadersTextChange = { editorState.headersText = it },
                                    paramsText = editorState.paramsText,
                                    onParamsTextChange = { editorState.paramsText = it },
                                    auth = editorState.auth,
                                    onAuthChange = { editorState.auth = it },
                                    envVars = environmentState.environmentsState.collectAllVariables(),
                                ),
                            )
                        }
                        SplitHandle(contentRowWidthPx * middleFraction,
                            onDragStart = {
                                isDragging = true; ghostDelta = 0f
                                splitDragStartRatio = splitRatio; splitDragStartWidth = contentRowWidthPx; splitDragStartMf = middleFraction
                            },
                            onDrag = { totalDelta -> ghostDelta = totalDelta },
                            onDragEnd = {
                                val denom = splitDragStartWidth * splitDragStartMf
                                splitRatio = (splitDragStartRatio + ghostDelta / denom).coerceIn(0.02f, 0.98f)
                                isDragging = false; ghostDelta = 0f
                            },
                            onPositioned = { coords -> splitHandlePos = coords.boundsInRoot().topLeft; splitHandleH = coords.size.height },
                        )
                        key(cacheVersion) {
                            ResponsePanel(
                                modifier = Modifier.weight(middleFraction * (1f - splitRatio)),
                                exchangeMetrics = exchangeMetrics,
                                statusCodeText = currentSession?.statusCodeText ?: "",
                                responseTimeText = currentSession?.responseTimeText ?: "",
                                responseSizeText = currentSession?.responseSizeText ?: "",
                                responseSseEventCount = currentSession?.responseSseEventCount ?: "",
                                responseLines = currentSession?.responseLines ?: responseState.placeholderResponseLines,
                                responsePartialLine = currentSession?.responsePartialLine,
                                responseHeaderLines = currentSession?.responseHeaderLines ?: responseState.placeholderResponseHeaders,
                                requestPlainText = currentSession?.exchangeRequestPlainText ?: "请先选择或创建一个请求",
                                rightTabIndex = currentSession?.rightTabIndex ?: 0,
                                onRightTabIndexChange = { currentSession?.rightTabIndex = it.coerceIn(0, 2) },
                                isSseResponse = currentSession?.isSseResponse ?: false,
                                isResponseLoading = currentSession?.isLoading ?: false,
                                isCacheLoading = currentSession?.isCacheLoading ?: false,
                                responseListState = currentSession?.responseListState ?: responseState.placeholderListState,
                                responseHeadersListState = currentSession?.responseHeadersListState ?: responseState.placeholderListState,
                                copyResponseBodyEnabled = editorState.editorRequestId != null && responseBodyTextForClipboard(currentSession?.responseLines ?: emptyList(), currentSession?.responsePartialLine).isNotBlank(),
                                onCopyResponseBody = { val text = responseBodyTextForClipboard(currentSession?.responseLines ?: emptyList(), currentSession?.responsePartialLine); if (text.isNotBlank()) writeClipboardText(text) },
                                clearResponseLogsEnabled = editorState.editorRequestId != null && currentSession?.isLoading != true,
                                onClearResponseLogs = {
                                    val id = editorState.editorRequestId ?: return@ResponsePanel
                                    val s = currentSession ?: return@ResponsePanel
                                    RequestResponseStore.clearResponseAndBenchLogs(id)
                                    s.responseLines.clear(); s.responseLines.add("响应结果会显示在这里")
                                    s.responseHeaderLines.clear(); s.responseHeaderLines.add("(暂无响应头)")
                                    s.responsePartialLine = null
                                    s.statusCodeText = ""; s.responseTimeText = ""; s.responseSizeText = ""; s.responseSseEventCount = ""
                                    s.isSseResponse = false
                                    s.exchangeRequestPlainText = "尚无已发送请求记录；发送后将显示实际发出的请求头与正文。"
                                },
                                jsonSyntaxHighlightEnabled = themeState.jsonSyntaxHighlightEnabled,
                                onJsonSyntaxHighlightEnabledChange = { themeState.jsonSyntaxHighlightEnabled = it },
                                historyEntries = currentSession?.historyEntries ?: emptyList(),
                                selectedHistoryEpochMs = currentSession?.selectedHistoryEpochMs,
                                onHistorySelected = { loadHistory(editorState, responseState, it) },
                            )
                        }
                    }
                }
                if (isDragging && splitHandlePos != Offset.Zero) {
                    val density = LocalDensity.current
                    val ghostH = with(density) { splitHandleH.toDp() }
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((splitHandlePos.x + ghostDelta).toInt(), splitHandlePos.y.toInt()) }
                            .width(10.dp).height(ghostH)
                            .zIndex(10f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.width(2.dp).fillMaxSize().background(MaterialTheme.colors.onSurface.copy(alpha = 0.25f)))
                    }
                }
                val msg = toastState.toastMessage
                if (msg != null) {
                    ToastMessage(message = msg, onDismiss = { toastState.clear() })
                }
                if (recentSwitcherState.active && recentSwitcherState.ids.isNotEmpty()) {
                    RecentRequestSwitcherOverlay(requestIds = recentSwitcherState.ids, highlightIndex = recentSwitcherState.index, tree = treeState.tree, repository = repository)
                }
                Dialogs(
                    showSettings = dialogState.showSettings,
                    isDarkTheme = themeState.isDarkTheme,
                    appSettings = appSettingsState.appSettings,
                    appSettingsStore = appSettingsStore,
                    exchangeMetrics = exchangeMetrics,
                    onCloseSettings = { dialogState.showSettings = false },
                    onSavedSettings = { appSettingsState.replace(it) },
                    showEnvironmentManager = dialogState.showEnvironmentManager,
                    environmentsState = environmentState.environmentsState,
                    onCloseEnvironmentManager = { dialogState.showEnvironmentManager = false },
                    onSavedEnvironments = { environmentState.commit(it) },
                    showGlobalSearch = dialogState.showGlobalSearch,
                    tree = treeState.tree,
                    repository = repository,
                    onCloseGlobalSearch = { dialogState.showGlobalSearch = false },
                    onPickRequest = { id ->
                        expandSetsForRequest(treeState.tree, id)?.let { (cols, folders) ->
                            treeState.expandedCollectionIds = treeState.expandedCollectionIds + cols
                            treeState.expandedFolderIds = treeState.expandedFolderIds + folders
                        }
                        selectTreeNode(TreeSelection.Request(id), treeState, editorState)
                        treeState.treeScrollToRequestId = id
                    },
                    showCollectionSettings = dialogState.showCollectionSettings,
                    collectionSettingsTarget = dialogState.collectionSettingsTarget,
                    onCloseCollectionSettings = { dialogState.showCollectionSettings = false },
                    onRefreshTree = { treeState.refresh() },
                )
            }
        }
    }
}

@Composable
private fun RequestEditorEffects(
    editorState: RequestEditorState,
    treeState: TreeState,
    repository: CollectionRepository,
) {
    LaunchedEffect(editorState.method, editorState.url, editorState.headersText, editorState.paramsText, editorState.bodyText, editorState.auth, editorState.editorRequestId) {
        val id = editorState.editorRequestId ?: return@LaunchedEffect
        delay(450)
        repository.saveRequestEditorFields(id, editorState.method, editorState.url, editorState.headersText, editorState.paramsText, editorState.bodyText, editorState.auth)
        treeState.refresh()
    }
}

@Composable
private fun LastRequestEffects(editorState: RequestEditorState) {
    LaunchedEffect(editorState.editorRequestId) {
        val id = editorState.editorRequestId ?: return@LaunchedEffect
        LastRequestPrefs.save(id)
    }
}

@Composable
private fun TreeEffects(
    treeState: TreeState,
    editorState: RequestEditorState,
    repository: CollectionRepository,
    expandLoaded: TreeExpandPrefs.Loaded,
) {
    var didApplyDefaultTreeExpand by remember { mutableStateOf(false) }
    var didPickInitialRequest by remember { mutableStateOf(false) }
    var persistTreeExpand by remember { mutableStateOf(expandLoaded.fromSavedFile) }

    LaunchedEffect(treeState.tree) {
        if (treeState.tree.isEmpty()) return@LaunchedEffect
        if (!didApplyDefaultTreeExpand) {
            didApplyDefaultTreeExpand = true
            if (!expandLoaded.fromSavedFile && treeState.expandedCollectionIds.isEmpty() && treeState.expandedFolderIds.isEmpty()) {
                treeState.expandedCollectionIds = treeState.tree.map { it.id }.toSet()
                treeState.expandedFolderIds = collectAllFolderIds(treeState.tree)
            }
        }
        persistTreeExpand = true
        if (!didPickInitialRequest) {
            didPickInitialRequest = true
            val savedId = LastRequestPrefs.load()
            if (savedId.isNotEmpty() && repository.getRequest(savedId) != null) {
                expandSetsForRequest(treeState.tree, savedId)?.let { (cols, folders) ->
                    treeState.expandedCollectionIds = treeState.expandedCollectionIds + cols
                    treeState.expandedFolderIds = treeState.expandedFolderIds + folders
                }
                editorState.applyRequestToEditor(savedId)
                treeState.treeSelection = TreeSelection.Request(savedId)
            } else {
                firstRequestSelection(treeState.tree)?.let { sel ->
                    editorState.applyRequestToEditor(sel.id)
                    treeState.treeSelection = sel
                }
            }
        }
    }

    LaunchedEffect(treeState.expandedCollectionIds, treeState.expandedFolderIds, persistTreeExpand) {
        if (!persistTreeExpand) return@LaunchedEffect
        TreeExpandPrefs.save(treeState.expandedCollectionIds, treeState.expandedFolderIds)
    }
}

@Composable
private fun AppDisposableEffect(
    editorState: RequestEditorState,
    repository: CollectionRepository,
) {
    DisposableEffect(Unit) {
        onDispose {
            editorState.saveEditorIfBound()
            repository.close()
        }
    }
}

@Composable
private fun SplitHandle(
    widthPx: Float,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onPositioned: (LayoutCoordinates) -> Unit = {},
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnPositioned by rememberUpdatedState(onPositioned)
    Box(
        modifier = Modifier.width(10.dp).fillMaxSize()
            .onGloballyPositioned { currentOnPositioned(it) }
            .pointerInput(Unit) {
                var totalDelta = 0f
                detectDragGestures(
                    onDragStart = { totalDelta = 0f; currentOnDragStart() },
                    onDrag = { change, dragAmount -> change.consume(); totalDelta += dragAmount.x; currentOnDrag(totalDelta) },
                    onDragEnd = { currentOnDragEnd() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.width(1.dp).fillMaxSize().background(MaterialTheme.colors.onSurface.copy(alpha = 0.12f)))
    }
}

private fun persistWindowGeometry(state: WindowState) {
    if (state.placement != WindowPlacement.Floating) return
    val pos = state.position
    if (pos !is WindowPosition.Absolute) return
    if (!state.size.isSpecified) return
    WindowPrefs.save(xDp = pos.x.value, yDp = pos.y.value, widthDp = state.size.width.value, heightDp = state.size.height.value)
}

@Composable
private fun ToastMessage(message: String, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000L)
        onDismiss()
    }

    val surfaceColor = MaterialTheme.colors.surface
    val onSurfaceColor = MaterialTheme.colors.onSurface

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier
                .offset(x = (-10).dp, y = (-10).dp)
                .background(
                    color = surfaceColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.body2,
                color = onSurfaceColor
            )
        }
    }
}

fun main() {
    GlobalExceptionHandler.install()
    Logger.info("APP") { "Api-X started, log dir: ${AppPaths.logDirectory()}" }
    application {
        ErrorBoundary {
            App(onExitRequest = { exitApplication() })
        }
    }
}

data class AppDependencies(
    val treeState: TreeState,
    val editorState: RequestEditorState,
    val responseState: ResponseState,
    val themeState: ThemeState,
    val dialogState: DialogState,
    val environmentState: EnvironmentState,
    val appSettingsState: AppSettingsState,
    val toastState: ToastState,
    val recentSwitcherState: RecentSwitcherState,
)

@Composable
private fun rememberAppDependencies(
    repository: CollectionRepository,
    expandLoaded: TreeExpandPrefs.Loaded,
    environmentStore: EnvironmentStore,
    appSettingsStore: AppSettingsStore,
): AppDependencies {
    val treeState = remember { TreeState(repository, expandLoaded) }
    val editorState = remember { RequestEditorState(repository) }
    val responseState = remember { ResponseState() }
    val themeState = remember { ThemeState() }
    val dialogState = remember { DialogState() }
    val environmentState = remember { EnvironmentState(environmentStore) }
    val appSettingsState = remember { AppSettingsState(appSettingsStore) }
    val toastState = remember { ToastState() }
    val recentSwitcherState = remember { RecentSwitcherState() }
    return AppDependencies(
        treeState = treeState,
        editorState = editorState,
        responseState = responseState,
        themeState = themeState,
        dialogState = dialogState,
        environmentState = environmentState,
        appSettingsState = appSettingsState,
        toastState = toastState,
        recentSwitcherState = recentSwitcherState,
    )
}
