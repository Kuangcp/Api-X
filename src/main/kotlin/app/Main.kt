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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlin.concurrent.thread
import db.AppPaths
import db.CollectionRepository
import http.BufferUpdate
import http.RequestControl
import http.RequestSidePanel
import http.RequestTopBar
import http.ResponsePanel
import http.closeQuietly
import http.parseCurlCommand
import http.sendRequestStreaming
import tree.CollectionTreeSidebar
import tree.TreeSelection
import tree.collectAllFolderIds
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

    var tree by remember { mutableStateOf(repository.loadTree()) }
    var treeSelection by remember { mutableStateOf<TreeSelection?>(null) }
    var editorRequestId by remember { mutableStateOf<String?>(null) }
    var expandedCollectionIds by remember { mutableStateOf(setOf<String>()) }
    var expandedFolderIds by remember { mutableStateOf(setOf<String>()) }
    var treeSplitRatio by remember { mutableStateOf(0.2f) }
    var didPickInitialRequest by remember { mutableStateOf(false) }

    val editorIdSnap by rememberUpdatedState(editorRequestId)
    val methodSnap by rememberUpdatedState(method)
    val urlSnap by rememberUpdatedState(url)
    val headersSnap by rememberUpdatedState(headersText)
    val bodySnap by rememberUpdatedState(bodyText)

    DisposableEffect(repository) {
        onDispose {
            editorIdSnap?.let {
                repository.saveRequestEditorFields(
                    it,
                    methodSnap,
                    urlSnap,
                    headersSnap,
                    bodySnap
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
        repository.saveRequestEditorFields(id, method, url, headersText, bodyText)
    }

    fun applyRequestToEditor(reqId: String) {
        val r = repository.getRequest(reqId) ?: return
        editorRequestId = reqId
        method = r.method
        url = r.url
        headersText = r.headersText
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
    var responseLines by remember { mutableStateOf(mutableStateListOf("响应结果会显示在这里")) }
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
    var leftTabIndex by remember { mutableStateOf(0) }
    var rightTabIndex by remember { mutableStateOf(0) }
    var responseHeaderLines by remember { mutableStateOf(mutableStateListOf("(暂无响应头)")) }
    val responseListState = rememberLazyListState()
    val responseHeadersListState = rememberLazyListState()
    var isDarkTheme by remember { mutableStateOf(true) }

    LaunchedEffect(method, url, headersText, bodyText, editorRequestId) {
        val id = editorRequestId ?: return@LaunchedEffect
        delay(450)
        repository.saveRequestEditorFields(id, method, url, headersText, bodyText)
    }

    LaunchedEffect(tree) {
        if (tree.isEmpty()) return@LaunchedEffect
        if (expandedCollectionIds.isEmpty()) {
            expandedCollectionIds = tree.map { it.id }.toSet()
            expandedFolderIds = collectAllFolderIds(tree)
        }
        if (!didPickInitialRequest) {
            didPickInitialRequest = true
            firstRequestSelection(tree)?.let { sel ->
                applyRequestToEditor(sel.id)
                treeSelection = sel
            }
        }
    }

    fun startRequest() {
        if (isLoading) return
        saveEditorIfBound()
        val control = RequestControl()
        activeRequestControl = control
        isLoading = true
        responseLines = mutableStateListOf()
        responsePartialLine = null
        responseHeaderLines = mutableStateListOf("等待响应…")
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
                    if (activeRequestControl === control) {
                        applyBufferUpdate(update, responseLines) { partial ->
                            responsePartialLine = partial
                        }
                    }
                }
            }
        }
        val worker = thread {
            sendRequestStreaming(
                method = method,
                url = url,
                body = bodyText,
                headersText = headersText,
                control = control,
                onSseDetected = { isSse ->
                    EventQueue.invokeLater {
                        if (activeRequestControl === control) isSseResponse = isSse
                    }
                },
                onStatusCode = { code ->
                    EventQueue.invokeLater {
                        if (activeRequestControl === control) statusCodeText = code.toString()
                    }
                },
                onResponseTime = { elapsedMs ->
                    EventQueue.invokeLater {
                        if (activeRequestControl === control) {
                            responseTimeText = formatDuration(elapsedMs)
                        }
                    }
                },
                onProgress = { bytes ->
                    EventQueue.invokeLater {
                        if (activeRequestControl === control) {
                            responseSizeText = formatBytes(bytes)
                        }
                    }
                },
                onResponseHeaders = { lines ->
                    EventQueue.invokeLater {
                        if (activeRequestControl === control) {
                            responseHeaderLines = mutableStateListOf<String>().apply {
                                addAll(lines)
                            }
                        }
                    }
                },
                onChunk = { chunk ->
                    if (activeRequestControl === control && !control.cancelled) {
                        control.lineBuffer.append(chunk)
                    }
                }
            )
            EventQueue.invokeLater {
                if (activeRequestControl === control) {
                    control.finished = true
                    applyBufferUpdate(control.lineBuffer.drainUpdate(), responseLines) { partial ->
                        responsePartialLine = partial
                    }
                    isLoading = false
                    isSseResponse = false
                    activeRequestControl = null
                    activeRequestThread = null
                }
            }
            flusher.interrupt()
        }
        activeRequestThread = worker
    }

    fun cancelActiveRequest() {
        if (!isLoading) return
        val control = activeRequestControl
        if (control != null) {
            control.cancelled = true
            closeQuietly(control.activeInput)
            control.lineBuffer.append("\n[请求已取消]\n")
            applyBufferUpdate(control.lineBuffer.drainUpdate(), responseLines) { partial ->
                responsePartialLine = partial
            }
            responseTimeText = formatDuration(System.currentTimeMillis() - control.startTimeMs)
        }
        activeRequestThread?.interrupt()
        isLoading = false
        isSseResponse = false
        activeRequestControl = null
        activeRequestThread = null
    }

    MaterialTheme(
        colors = if (isDarkTheme) apiXDarkColors() else lightColors(background = hexToColor("#f2f2f2"))
    ) {
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
                onThemeToggle = { isDarkTheme = !isDarkTheme },
                onSettingsClick = {
                    setSingleResponseMessage(responseLines, "设置功能待实现")
                    responsePartialLine = null
                    responseHeaderLines = mutableStateListOf("(暂无响应头)")
                    statusCodeText = ""
                    responseTimeText = ""
                    responseSizeText = ""
                },
                onImportCurlClick = {
                    try {
                        val clipboardText = readClipboardText()
                        val parsed = parseCurlCommand(clipboardText)
                        method = parsed.method
                        url = parsed.url.ifBlank { url }
                        headersText = parsed.headers.joinToString("\n")
                        bodyText = parsed.body
                        editorRequestId?.let {
                            repository.saveRequestEditorFields(
                                it, method, url, headersText, bodyText
                            )
                        }
                        setSingleResponseMessage(responseLines, "已从剪贴板导入 cURL")
                        responsePartialLine = null
                        responseHeaderLines = mutableStateListOf("(暂无响应头)")
                        statusCodeText = "-"
                        responseTimeText = "-"
                        responseSizeText = "0 B"
                    } catch (e: Exception) {
                        setSingleResponseMessage(responseLines, "导入 cURL 失败: ${e.message}")
                        responsePartialLine = null
                        responseHeaderLines = mutableStateListOf("(暂无响应头)")
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
                    onAddFolder = {
                        val target = repository.newFolderTarget(treeSelection) ?: return@CollectionTreeSidebar
                        val (cid, pid) = target
                        repository.createFolder(cid, pid, "新文件夹").let { fid ->
                            refreshTree()
                            expandedCollectionIds = expandedCollectionIds + cid
                            if (pid != null) expandedFolderIds = expandedFolderIds + pid
                            treeSelection = TreeSelection.Folder(fid)
                        }
                    },
                    onAddRequest = {
                        val target = repository.newRequestTarget(treeSelection) ?: return@CollectionTreeSidebar
                        val (cid, fid) = target
                        saveEditorIfBound()
                        val rid = repository.createRequest(cid, fid, "新请求")
                        refreshTree()
                        expandedCollectionIds = expandedCollectionIds + cid
                        if (fid != null) expandedFolderIds = expandedFolderIds + fid
                        applyRequestToEditor(rid)
                        treeSelection = TreeSelection.Request(rid)
                    },
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
                            is TreeSelection.Request -> repository.deleteRequest(sel.id)
                        }
                        val nextTree = repository.loadTree()
                        tree = nextTree
                        if (editorRequestId != null && repository.getRequest(editorRequestId!!) == null) {
                            editorRequestId = null
                            firstRequestSelection(nextTree)?.let { next ->
                                applyRequestToEditor(next.id)
                                treeSelection = next
                            } ?: run {
                                method = "GET"
                                url = ""
                                headersText = ""
                                bodyText = ""
                                treeSelection =
                                    nextTree.firstOrNull()?.let { TreeSelection.Collection(it.id) }
                            }
                        }
                    },
                    folderAddEnabled = repository.newFolderTarget(treeSelection) != null,
                    requestAddEnabled = repository.newRequestTarget(treeSelection) != null,
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
                    onLeftTabIndexChange = { leftTabIndex = it },
                    bodyText = bodyText,
                    onBodyTextChange = { bodyText = it },
                    headersText = headersText,
                    onHeadersTextChange = { headersText = it }
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
                    statusCodeText = statusCodeText,
                    responseTimeText = responseTimeText,
                    responseSizeText = responseSizeText,
                    responseLines = responseLines,
                    responsePartialLine = responsePartialLine,
                    responseHeaderLines = responseHeaderLines,
                    rightTabIndex = rightTabIndex,
                    onRightTabIndexChange = { rightTabIndex = it },
                    isSseResponse = isSseResponse,
                    responseListState = responseListState,
                    responseHeadersListState = responseHeadersListState
                )
            }
        }
    }
}

private const val UI_REFRESH_INTERVAL_MS = 100L

/** 深色主题：前景统一为白色，避免桌面端部分组件仍使用默认深色字色。 */
private fun apiXDarkColors() = darkColors(
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

private fun hexToColor(hex: String): Color {
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

fun main() = application {
    val loaded = remember { WindowPrefs.load() }
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
