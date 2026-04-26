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
                SettingsDialogWindow(visible = vm.showSettings, isDarkTheme = vm.isDarkTheme, typographyBase = typographyFromSettings(vm.appSettings), onCloseRequest = { vm.setShowSettings(false) }, onSaved = { saved -> AppSettingsStore.replace(saved); vm.setAppSettings(saved) })
                EnvironmentManagerDialogWindow(visible = vm.showEnvironmentManager, isDarkTheme = vm.isDarkTheme, appBackgroundHex = vm.appSettings.backgroundHex, typographyBase = typographyFromSettings(vm.appSettings), initial = vm.environmentsState, onCloseRequest = { vm.setShowEnvironmentManager(false) }, onSaved = { vm.onCommitEnvironments(it) })
                GlobalSearchDialogWindow(visible = vm.showGlobalSearch, isDarkTheme = vm.isDarkTheme, appBackgroundHex = vm.appSettings.backgroundHex, typographyBase = typographyFromSettings(vm.appSettings), tree = vm.tree, repository = vm.repository, onCloseRequest = { vm.setShowGlobalSearch(false) }, onPickRequest = { id -> expandSetsForRequest(vm.tree, id)?.let { (cols, folders) -> vm.setExpandedCollectionIds(vm.expandedCollectionIds + cols); vm.setExpandedFolderIds(vm.expandedFolderIds + folders) }; vm.onSelectTreeNode(TreeSelection.Request(id)); vm.setTreeScrollToRequestId(id) })
                CollectionSettingsDialog(visible = vm.showCollectionSettings, target = vm.collectionSettingsTarget, repository = vm.repository, isDarkTheme = vm.isDarkTheme, typographyBase = typographyFromSettings(vm.appSettings), exchangeMetrics = vm.exchangeMetrics, onCloseRequest = { vm.setShowCollectionSettings(false); vm.onRefreshTree() })
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

private fun importCollection(vm: AppViewModel) {
    EventQueue.invokeLater {
        val chooser = JFileChooser()
        chooser.dialogTitle = "导入 Postman Collection"
        chooser.fileFilter = FileNameExtensionFilter("JSON (*.json)", "json")
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@invokeLater
        val file = chooser.selectedFile ?: return@invokeLater
        try {
            val text = Files.readString(file.toPath(), StandardCharsets.UTF_8)
            val portable = parsePostmanCollectionJsonToPortable(text)
            val newId = vm.repository.importAsNewCollection(portable)
            vm.onRefreshTree()
            vm.setExpandedCollectionIds(vm.expandedCollectionIds + newId)
            vm.setTreeSelection(TreeSelection.Collection(newId))
            setSingleResponseMessage(vm.responseLines, "已导入集合「${portable.name}」")
        } catch (e: IllegalArgumentException) {
            JOptionPane.showMessageDialog(null, e.message ?: "文件格式不正确", "导入失败", JOptionPane.ERROR_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message ?: e.toString(), "导入失败", JOptionPane.ERROR_MESSAGE)
        }
        vm.setResponsePartialLine(null)
    }
}

private fun importCurl(vm: AppViewModel) {
    try {
        val activeId = vm.editorRequestId
        if (activeId == null) { setSingleResponseMessage(vm.responseLines, "请先在编辑器中打开一个请求后再导入 cURL"); vm.setResponsePartialLine(null); return }
        val clipboardText = readClipboardText()
        val parsed = parseCurlCommand(clipboardText)
        vm.onSaveEditor()
        val newId = vm.repository.createRequestBelow(activeId) ?: throw IllegalStateException("无法在当前请求下新建条目")
        vm.onRefreshTree()
        val placed = vm.repository.getRequest(newId) ?: throw IllegalStateException("新建请求后无法读取")
        vm.setExpandedCollectionIds(vm.expandedCollectionIds + placed.collectionId)
        if (placed.folderId != null) vm.setExpandedFolderIds(vm.expandedFolderIds + placed.folderId)
        vm.onApplyRequestToEditor(newId)
        vm.setTreeSelection(TreeSelection.Request(newId))
        vm.setMethod(parsed.method)
        val rawUrl = parsed.url.ifBlank { vm.url }
        if (rawUrl.isNotBlank()) {
            try {
                val uri = java.net.URI(rawUrl)
                val path = uri.path
                if (!path.isNullOrBlank()) {
                    val name = path.substringAfterLast('/')
                    if (name.isNotBlank()) { vm.repository.renameRequest(newId, name); vm.onRefreshTree() }
                }
            } catch (_: Exception) { }
        }
        val (urlNoQuery, queryParamsText) = splitUrlQueryForParamsEditor(rawUrl)
        vm.setUrl(urlNoQuery.ifBlank { rawUrl })
        vm.setParamsText(queryParamsText)
        vm.setHeadersText(parsed.headers.joinToString("\n"))
        vm.setBodyText(http.migrateFormBodyToEditorLinesIfNeeded(parsed.body, vm.headersText))
        vm.repository.saveRequestEditorFields(newId, vm.method, vm.url, vm.headersText, vm.paramsText, vm.bodyText, vm.auth)
        setSingleResponseMessage(vm.responseLines, "已新建请求并导入剪贴板中的 cURL")
        vm.setResponsePartialLine(null)
        vm.responseHeaderLines.clear(); vm.responseHeaderLines.add("(暂无响应头)")
        vm.setStatusCodeText("-"); vm.setResponseTimeText("-"); vm.setResponseSizeText("0 B")
    } catch (e: Exception) {
        setSingleResponseMessage(vm.responseLines, "导入 cURL 失败: ${e.message}")
        vm.setResponsePartialLine(null)
        vm.responseHeaderLines.clear(); vm.responseHeaderLines.add("(暂无响应头)")
    }
}

private fun pushData(vm: AppViewModel) {
    thread {
        val r = DataDirSync.pushToDataDir(vm.repository)
        EventQueue.invokeLater {
            if (r.error != null) JOptionPane.showMessageDialog(null, r.error, "同步到 data 失败", JOptionPane.ERROR_MESSAGE)
            else setSingleResponseMessage(vm.responseLines, "已写入 ${AppPaths.gitDataRoot()}：${r.collectionFilesWritten} 个 collection JSON；${if (r.envWritten) "环境已同步到 data/env" else "环境未写入"}")
            vm.setResponsePartialLine(null)
        }
    }
}

private fun pullData(vm: AppViewModel) {
    thread {
        val r = DataDirSync.pullFromDataDir(vm.repository)
        EventQueue.invokeLater {
            if (r.error != null) JOptionPane.showMessageDialog(null, r.error, "从 data 合并失败", JOptionPane.ERROR_MESSAGE)
            else {
                vm.onRefreshTree()
                vm.setEnvironmentsState(EnvironmentStore.snapshot())
                val errLines = r.fileErrors.joinToString("\n")
                val baseMsg = "已合并：更新 ${r.merged} 个集合、新建 ${r.created} 个；环境 ${if (r.envMerged) "已合并" else "未变更"}"
                if (r.fileErrors.isNotEmpty()) JOptionPane.showMessageDialog(null, "$baseMsg\n\n部分文件未导入：\n$errLines", "从 data 合并", JOptionPane.INFORMATION_MESSAGE)
                setSingleResponseMessage(vm.responseLines, if (r.fileErrors.isEmpty()) baseMsg else "$baseMsg；详见弹窗中失败项")
            }
            vm.setResponsePartialLine(null)
        }
    }
}

private fun deleteSelection(vm: AppViewModel, sel: TreeSelection) {
    vm.onSaveEditor()
    when (sel) {
        is TreeSelection.Collection -> vm.repository.deleteCollection(sel.id)
        is TreeSelection.Folder -> vm.repository.deleteFolder(sel.id)
        is TreeSelection.Request -> { vm.repository.deleteRequest(sel.id); RecentRequestUsageStore.remove(sel.id); RequestResponseStore.deleteRequestArtifacts(sel.id) }
    }
    val nextTree = vm.repository.loadTree()
    vm.setTree(nextTree)
    if (vm.editorRequestId != null && vm.repository.getRequest(vm.editorRequestId!!) == null) {
        vm.setEditorRequestId(null)
        firstRequestSelection(nextTree)?.let { next -> vm.onApplyRequestToEditor(next.id); vm.setTreeSelection(next) }
            ?: run { LastRequestPrefs.clear(); vm.setMethod("GET"); vm.setUrl(""); vm.setHeadersText(""); vm.setParamsText(""); vm.setBodyText(""); vm.setTreeSelection(nextTree.firstOrNull()?.let { TreeSelection.Collection(it.id) }) }
    }
}

private fun exportAsCurl(vm: AppViewModel, rid: String) {
    val r = vm.repository.getRequest(rid) ?: return
    val vmEnv = vm.environmentsState.substitutionMapForActive()
    writeClipboardText(requestToCurlCommand(r.method, mergeUrlWithParams(applyEnvironmentVariables(r.url, vmEnv), parseHeadersForSend(applyEnvironmentVariables(r.paramsText, vmEnv))), applyEnvironmentVariables(r.headersText, vmEnv), applyEnvironmentVariables(r.bodyText, vmEnv)))
    setSingleResponseMessage(vm.responseLines, "已复制 cURL 到剪贴板")
    vm.setResponsePartialLine(null)
}

private fun exportPostmanCollection(vm: AppViewModel, collectionId: String) {
    val portable = vm.repository.exportPortableCollection(collectionId) ?: return
    val json = portableCollectionToPostmanV21Json(portable)
    EventQueue.invokeLater {
        val chooser = JFileChooser()
        chooser.dialogTitle = "导出 Postman Collection v2.1"
        chooser.fileFilter = FileNameExtensionFilter("JSON (*.json)", "json")
        val safeBase = portable.name.replace(Regex("""[^\w\u4e00-\u9fff\-_. ]"""), "_").trim().ifEmpty { "collection" }
        chooser.selectedFile = java.io.File("$safeBase.postman_collection.json")
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return@invokeLater
        var file = chooser.selectedFile
        if (!file.name.endsWith(".json", ignoreCase = true)) file = java.io.File(file.parentFile, file.name + ".json")
        try {
            Files.writeString(file.toPath(), json, StandardCharsets.UTF_8)
            setSingleResponseMessage(vm.responseLines, "已导出 Postman v2.1：${file.absolutePath}")
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message ?: e.toString(), "导出失败", JOptionPane.ERROR_MESSAGE)
        }
        vm.setResponsePartialLine(null)
    }
}

private fun duplicateRequest(vm: AppViewModel, rid: String) {
    vm.onSaveEditor()
    val newId = vm.repository.duplicateRequestBelow(rid) ?: return
    vm.onRefreshTree()
    val placed = vm.repository.getRequest(newId) ?: return
    vm.setExpandedCollectionIds(vm.expandedCollectionIds + placed.collectionId)
    if (placed.folderId != null) vm.setExpandedFolderIds(vm.expandedFolderIds + placed.folderId)
    vm.onApplyRequestToEditor(newId)
    vm.setTreeSelection(TreeSelection.Request(newId))
}

private fun applyTreeDrop(vm: AppViewModel, payload: tree.TreeDragPayload, target: TreeDropTarget): Boolean {
    val ok = vm.repository.applyTreeDrop(payload, target)
    if (ok) {
        vm.onRefreshTree()
        when (target) {
            is TreeDropTarget.IntoFolder -> { vm.setExpandedCollectionIds(vm.expandedCollectionIds + target.collectionId); vm.setExpandedFolderIds(vm.expandedFolderIds + target.folderId) }
            is TreeDropTarget.IntoCollection -> { vm.setExpandedCollectionIds(vm.expandedCollectionIds + target.collectionId) }
            is TreeDropTarget.FolderSlot -> { vm.setExpandedCollectionIds(vm.expandedCollectionIds + target.collectionId); target.parentFolderId?.let { vm.setExpandedFolderIds(vm.expandedFolderIds + it) } }
            is TreeDropTarget.RequestSlot -> { vm.setExpandedCollectionIds(vm.expandedCollectionIds + target.collectionId); target.folderId?.let { vm.setExpandedFolderIds(vm.expandedFolderIds + it) } }
        }
    }
    return ok
}

private fun loadHistory(vm: AppViewModel, epochMs: Long?) {
    vm.setSelectedHistoryEpochMs(epochMs)
    val requestId = vm.editorRequestId ?: return
    val response = if (epochMs == null) RequestResponseStore.loadLatest(requestId) else RequestResponseStore.loadByTimestamp(requestId, epochMs)
    if (response != null) {
        vm.responseLines.clear(); vm.responseLines.addAll(response.responseBodyLines)
        vm.responseHeaderLines.clear(); vm.responseHeaderLines.addAll(response.responseHeaderLines)
        vm.setResponsePartialLine(null)
        vm.setStatusCodeText(response.statusCodeText); vm.setResponseTimeText(response.responseTimeText)
        vm.setResponseSizeText(response.responseSizeText); vm.setIsSseResponse(response.isSseResponse)
        vm.setRightTabIndex(response.rightTabIndex.coerceIn(0, 2))
        vm.setExchangeRequestPlainText(response.requestPlainText.ifBlank { "尚无已发送请求记录；发送后将显示实际发出的请求头与正文。" })
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