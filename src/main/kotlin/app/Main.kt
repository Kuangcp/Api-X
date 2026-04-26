package app

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
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
import androidx.compose.ui.res.painterResource
import java.awt.EventQueue
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.concurrent.thread
import db.AppPaths
import db.CollectionRepository
import db.RequestResponseStore
import http.RequestSidePanel
import http.RequestTopBar
import http.ResponsePanel
import http.HttpExchangeErrorStatusMark
import http.exchangeFontMetrics
import http.formatActualRequestPlainText
import http.parseHeadersForSend
import http.requestToCurlCommand
import http.mergeUrlWithParams
import http.applyEnvironmentVariables
import http.migrateFormBodyToEditorLinesIfNeeded
import http.splitUrlQueryForParamsEditor
import http.substitutionMapForActive
import http.parseCurlCommand
import tree.CollectionTreeSidebar
import tree.TreeDragPayload
import tree.TreeDropTarget
import tree.TreeSelection
import tree.expandSetsForRequest
import tree.parsePostmanCollectionJsonToPortable
import tree.portableCollectionToPostmanV21Json
import tree.firstRequestSelection
import tree.UiCollection
import app.ui.Dialogs

@Composable
fun App(onExitRequest: () -> Unit) {
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
    val repository = remember { CollectionRepository(AppPaths.collectionDatabasePath()) }
    val expandLoaded = remember { TreeExpandPrefs.load() }
    val vm = rememberAppViewModel(repository, expandLoaded, windowState)

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
            if (vm.recentSwitcherActive) {
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        when {
                            event.key == Key.Escape -> { vm.setRecentSwitcherActive(false); true }
                            event.isCtrlPressed && event.key == Key.Tab -> {
                                val list = vm.recentSwitcherIds
                                if (list.isEmpty()) { vm.setRecentSwitcherActive(false); true }
                                else { vm.setRecentSwitcherIndex(if (!event.isShiftPressed) (vm.recentSwitcherIndex + 1) % list.size else (vm.recentSwitcherIndex - 1 + list.size) % list.size); true }
                            }
                            else -> false
                        }
                    }
                    KeyEventType.KeyUp -> {
                        if (event.key == Key.CtrlLeft || event.key == Key.CtrlRight) {
                            vm.setRecentSwitcherActive(false)
                            val list = vm.recentSwitcherIds
                            if (list.isNotEmpty()) {
                                val id = list[vm.recentSwitcherIndex.coerceIn(0, list.lastIndex)]
                                if (vm.repository.getRequest(id) != null) {
                                    expandSetsForRequest(vm.tree, id)?.let { (cols, folders) ->
                                        vm.setExpandedCollectionIds(vm.expandedCollectionIds + cols)
                                        vm.setExpandedFolderIds(vm.expandedFolderIds + folders)
                                    }
                                    vm.onSelectTreeNode(TreeSelection.Request(id))
                                    vm.setTreeScrollToRequestId(id)
                                }
                            }
                            true
                        } else false
                    }
                    else -> false
                }
            } else {
                if (event.type == KeyEventType.KeyDown) {
                    when {
                        event.isCtrlPressed && event.key == Key.Tab -> {
                            val list = vm.getMruRequestIds()
                            if (list.isEmpty()) false
                            else { vm.setRecentSwitcherIds(list); vm.setRecentSwitcherActive(true); vm.setRecentSwitcherIndex(when { list.size == 1 -> 0; !event.isShiftPressed -> 1; else -> list.lastIndex }); true }
                        }
                        (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.K -> { vm.setShowGlobalSearch(true); true }
                        event.isCtrlPressed && event.key == Key.Enter -> { vm.onStartRequest(); true }
                        event.key == Key.Escape -> { if (vm.isLoading) { vm.onCancelRequest(); true } else false }
                        else -> false
                    }
                } else false
            }
        },
    ) {
        MaterialTheme(colors = appMaterialColors(vm.isDarkTheme, vm.appSettings.backgroundHex), typography = typographyFromSettings(vm.appSettings)) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background).padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RequestTopBar(
                        isLoading = vm.isLoading, isDarkTheme = vm.isDarkTheme, treeSidebarVisible = vm.treeSidebarVisible,
                        onTreeSidebarToggle = { vm.setTreeSidebarVisible(!vm.treeSidebarVisible) },
                        environmentsState = vm.environmentsState,
                        onActiveEnvironmentChange = { vm.onCommitEnvironments(vm.environmentsState.copy(activeEnvironmentId = it)) },
                        onManageEnvironmentsClick = { vm.setShowEnvironmentManager(true) },
                        onThemeToggle = { vm.setIsDarkTheme(!vm.isDarkTheme) },
                        onSettingsClick = { vm.setShowSettings(true) },
                        mainWindowState = windowState,
                        onWindowCloseRequest = { persistWindowGeometry(windowState); onExitRequest() },
                        onImportCollectionClick = { importCollection(vm) },
                        onImportCurlClick = { importCurl(vm) },
                        onPushDataClick = { pushData(vm) },
                        onPullDataClick = { pullData(vm) },
                    )
                    Row(modifier = Modifier.fillMaxWidth().weight(1f).onSizeChanged { vm.setContentRowWidthPx(it.width.toFloat().coerceAtLeast(1f)) }) {
                        val middleFraction = if (vm.treeSidebarVisible) 1f - vm.treeSplitRatio else 1f
                        if (vm.treeSidebarVisible) {
                            CollectionTreeSidebar(
                                modifier = Modifier.weight(vm.treeSplitRatio), tree = vm.tree, selectedNode = vm.treeSelection,
                                treeScrollToRequestId = vm.treeScrollToRequestId, onTreeScrollToRequestHandled = { vm.setTreeScrollToRequestId(null) },
                                editorBoundRequestId = vm.editorRequestId, expandedCollectionIds = vm.expandedCollectionIds, expandedFolderIds = vm.expandedFolderIds,
                                onToggleCollection = { vm.setExpandedCollectionIds(if (it in vm.expandedCollectionIds) vm.expandedCollectionIds - it else vm.expandedCollectionIds + it) },
                                onToggleFolder = { vm.setExpandedFolderIds(if (it in vm.expandedFolderIds) vm.expandedFolderIds - it else vm.expandedFolderIds + it) },
                                onSelectNode = { vm.onSelectTreeNode(it) },
                                onAddCollection = { val id = vm.repository.createCollection("新集合"); vm.onRefreshTree(); vm.setExpandedCollectionIds(vm.expandedCollectionIds + id); vm.setTreeSelection(TreeSelection.Collection(id)) },
                                onAddFolder = { vm.treeSelection?.let { vm.onAddFolderAt(it) } },
                                onAddRequest = { vm.treeSelection?.let { vm.onAddRequestAt(it) } },
                                onContextAddFolder = { vm.onAddFolderAt(it) },
                                onContextAddRequest = { vm.onAddRequestAt(it) },
                                onRename = { sel, newName -> when (sel) { is TreeSelection.Collection -> vm.repository.renameCollection(sel.id, newName); is TreeSelection.Folder -> vm.repository.renameFolder(sel.id, newName); is TreeSelection.Request -> vm.repository.renameRequest(sel.id, newName) }; vm.onRefreshTree() },
                                onDelete = { deleteSelection(vm, it) },
                                onSettings = { vm.setCollectionSettingsTarget(it); vm.setShowCollectionSettings(true) },
                                folderAddEnabled = vm.repository.newFolderTarget(vm.treeSelection) != null,
                                requestAddEnabled = vm.repository.newRequestTarget(vm.treeSelection) != null,
                                onExportRequestAsCurl = { exportAsCurl(vm, it) },
                                onExportPostmanCollection = { exportPostmanCollection(vm, it) },
                                onDuplicateRequestBelow = { duplicateRequest(vm, it) },
                                onApplyTreeDrop = { payload, target -> applyTreeDrop(vm, payload, target) },
                            )
                            SplitHandle(vm.contentRowWidthPx) { vm.setTreeSplitRatio(vm.treeSplitRatio + it / vm.contentRowWidthPx) }
                        }
                        RequestSidePanel(
                            modifier = Modifier.weight(middleFraction * vm.splitRatio), exchangeMetrics = vm.exchangeMetrics,
                            editorRequestId = vm.editorRequestId, isLoading = vm.isLoading,
                            method = vm.method, methodMenuExpanded = vm.methodMenuExpanded,
                            onMethodMenuExpandedChange = { vm.setMethodMenuExpanded(it) },
                            onMethodSelected = { vm.setMethod(it) },
                            url = vm.url, onUrlChange = { vm.setUrl(it) },
                            onSendOrCancel = { if (!vm.isLoading) vm.onStartRequest() else vm.onCancelRequest() },
                            leftTabIndex = vm.leftTabIndex, onLeftTabIndexChange = { vm.setLeftTabIndex(it.coerceIn(0, 3)) },
                            bodyText = vm.bodyText, onBodyTextChange = { vm.setBodyText(it) },
                            headersText = vm.headersText, onHeadersTextChange = { vm.setHeadersText(it) },
                            paramsText = vm.paramsText, onParamsTextChange = { vm.setParamsText(it) },
                            auth = vm.auth, onAuthChange = { vm.setAuth(it) },
                            isDarkTheme = vm.isDarkTheme,
                        )
                        SplitHandle(vm.contentRowWidthPx * middleFraction) { vm.setSplitRatio(vm.splitRatio + it / (vm.contentRowWidthPx * middleFraction)) }
                        ResponsePanel(
                            modifier = Modifier.weight(middleFraction * (1f - vm.splitRatio)), exchangeMetrics = vm.exchangeMetrics,
                            statusCodeText = vm.statusCodeText, responseTimeText = vm.responseTimeText, responseSizeText = vm.responseSizeText,
                            responseLines = vm.responseLines, responsePartialLine = vm.responsePartialLine, responseHeaderLines = vm.responseHeaderLines,
                            requestPlainText = vm.exchangeRequestPlainText, rightTabIndex = vm.rightTabIndex, onRightTabIndexChange = { vm.setRightTabIndex(it.coerceIn(0, 2)) },
                            isSseResponse = vm.isSseResponse, isResponseLoading = vm.isLoading,
                            responseListState = vm.responseListState, responseHeadersListState = vm.responseHeadersListState,
                            copyResponseBodyEnabled = vm.editorRequestId != null && responseBodyTextForClipboard(vm.responseLines, vm.responsePartialLine).isNotBlank(),
                            onCopyResponseBody = { val text = responseBodyTextForClipboard(vm.responseLines, vm.responsePartialLine); if (text.isNotBlank()) writeClipboardText(text) },
                            clearResponseLogsEnabled = vm.editorRequestId != null && !vm.isLoading,
                            onClearResponseLogs = { val id = vm.editorRequestId ?: return@ResponsePanel; RequestResponseStore.clearResponseAndBenchLogs(id); vm.responseLines.clear(); vm.responseLines.add("响应结果会显示在这里"); vm.responseHeaderLines.clear(); vm.responseHeaderLines.add("(暂无响应头)"); vm.setResponsePartialLine(null); vm.setStatusCodeText(""); vm.setResponseTimeText(""); vm.setResponseSizeText(""); vm.setIsSseResponse(false); vm.setExchangeRequestPlainText("尚无已发送请求记录；发送后将显示实际发出的请求头与正文。") },
                            jsonSyntaxHighlightEnabled = vm.jsonSyntaxHighlightEnabled, onJsonSyntaxHighlightEnabledChange = { vm.setJsonSyntaxHighlightEnabled(it) },
                            historyEntries = vm.historyEntries, selectedHistoryEpochMs = vm.selectedHistoryEpochMs,
                            onHistorySelected = { loadHistory(vm, it) },
                        )
                    }
                }
                if (vm.recentSwitcherActive && vm.recentSwitcherIds.isNotEmpty()) {
                    RecentRequestSwitcherOverlay(requestIds = vm.recentSwitcherIds, highlightIndex = vm.recentSwitcherIndex, tree = vm.tree, repository = vm.repository)
                }
                Dialogs(
                    showSettings = vm.showSettings,
                    isDarkTheme = vm.isDarkTheme,
                    appSettings = vm.appSettings,
                    exchangeMetrics = vm.exchangeMetrics,
                    onCloseSettings = { vm.setShowSettings(false) },
                    onSavedSettings = { vm.setAppSettings(it) },
                    showEnvironmentManager = vm.showEnvironmentManager,
                    environmentsState = vm.environmentsState,
                    onCloseEnvironmentManager = { vm.setShowEnvironmentManager(false) },
                    onSavedEnvironments = { vm.onCommitEnvironments(it) },
                    showGlobalSearch = vm.showGlobalSearch,
                    tree = vm.tree,
                    repository = vm.repository,
                    onCloseGlobalSearch = { vm.setShowGlobalSearch(false) },
                    onPickRequest = { id -> expandSetsForRequest(vm.tree, id)?.let { (cols, folders) -> vm.setExpandedCollectionIds(vm.expandedCollectionIds + cols); vm.setExpandedFolderIds(vm.expandedFolderIds + folders) }; vm.onSelectTreeNode(TreeSelection.Request(id)); vm.setTreeScrollToRequestId(id) },
                    showCollectionSettings = vm.showCollectionSettings,
                    collectionSettingsTarget = vm.collectionSettingsTarget,
                    onCloseCollectionSettings = { vm.setShowCollectionSettings(false) },
                    onRefreshTree = { vm.onRefreshTree() },
                )
            }
        }
    }
}

@Composable
private fun SplitHandle(widthPx: Float, onDrag: (Float) -> Unit) {
    Box(modifier = Modifier.width(10.dp).fillMaxSize().pointerInput(widthPx) {
        detectDragGestures { change, dragAmount -> change.consume(); onDrag(dragAmount.x) }
    }, contentAlignment = Alignment.Center) {
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

fun main() = application { App(onExitRequest = { exitApplication() }) }