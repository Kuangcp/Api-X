package app.core

import app.state.EnvironmentState
import app.state.TreeState
import app.state.RequestEditorState
import app.state.ResponseState
import app.state.ToastState
import app.settings.LastRequestPrefs
import app.settings.DataDirSync
import app.settings.EnvironmentStore
import app.settings.RecentRequestUsageStore
import app.settings.AppSettings
import db.AppPaths
import db.CollectionRepository
import db.RequestResponseStore
import http.applyEnvironmentVariables
import http.mergeUrlWithParams
import http.parseHeadersForSend
import http.requestToCurlCommand
import http.requestToGoTestMethod
import http.splitUrlQueryForParamsEditor
import http.substitutionMapForActive
import http.migrateFormBodyToEditorLinesIfNeeded
import http.parseCurlCommand
import java.awt.EventQueue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.concurrent.thread
import openapi.parseOpenApiToPortableCollection
import tree.TreeDragPayload
import tree.TreeDropTarget
import tree.PostmanAuth
import tree.TreeSelection
import tree.parsePostmanCollectionJsonToPortable
import tree.portableCollectionToPostmanV21Json
import tree.firstRequestSelection

fun createCollectionFromDialog(
    treeState: TreeState,
    toastState: ToastState,
    repository: CollectionRepository,
    name: String,
    openApiUrl: String,
) {
    val cleanName = name.trim().ifBlank { "New Collection" }
    val cleanUrl = openApiUrl.trim()
    if (cleanUrl.isBlank()) {
        val id = repository.createCollection(cleanName)
        treeState.refresh()
        treeState.expandedCollectionIds = treeState.expandedCollectionIds + id
        treeState.treeSelection = TreeSelection.Collection(id)
        showToast(toastState, "已创建集合 $cleanName")
        return
    }
    thread(name = "openapi-import") {
        var collectionId: String? = null
        try {
            val id = repository.createCollection(cleanName)
            collectionId = id
            val text = fetchOpenApiJson(cleanUrl)
            val result = parseOpenApiToPortableCollection(text, cleanUrl, cleanName, id)
            repository.mergePortableIntoCollection(id, result.portable)
            EventQueue.invokeLater {
                treeState.refresh()
                treeState.expandedCollectionIds = treeState.expandedCollectionIds + id
                treeState.treeSelection = TreeSelection.Collection(id)
                showToast(toastState, "已从 OpenAPI 创建集合：${result.requestCount} 个接口")
            }
        } catch (e: Exception) {
            collectionId?.let { runCatching { repository.deleteCollection(it) } }
            EventQueue.invokeLater {
                showToast(toastState, "OpenAPI 导入失败: ${e.message ?: e::class.simpleName}")
            }
        }
    }
}

fun refreshOpenApiCollection(
    treeState: TreeState,
    toastState: ToastState,
    repository: CollectionRepository,
    collectionId: String,
) {
    val sourceUrl = repository.getCollectionOpenApiSource(collectionId)
    if (sourceUrl.isNullOrBlank()) {
        showToast(toastState, "当前集合没有绑定 OpenAPI 地址")
        return
    }
    thread(name = "openapi-refresh") {
        try {
            val name = repository.exportPortableCollection(collectionId)?.name ?: "OpenAPI Collection"
            val text = fetchOpenApiJson(sourceUrl)
            val result = parseOpenApiToPortableCollection(text, sourceUrl, name, collectionId)
            repository.mergePortableIntoCollection(collectionId, result.portable.copy(auth = repository.getCollectionAuth(collectionId)))
            EventQueue.invokeLater {
                treeState.refresh()
                treeState.expandedCollectionIds = treeState.expandedCollectionIds + collectionId
                treeState.treeSelection = TreeSelection.Collection(collectionId)
                showToast(toastState, "OpenAPI 已刷新：${result.requestCount} 个接口")
            }
        } catch (e: Exception) {
            EventQueue.invokeLater {
                showToast(toastState, "OpenAPI 刷新失败: ${e.message ?: e::class.simpleName}")
            }
        }
    }
}

private fun fetchOpenApiJson(url: String): String {
    val request = HttpRequest.newBuilder(URI.create(url))
        .GET()
        .header("Accept", "application/json")
        .build()
    val response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    if (response.statusCode() !in 200..299) {
        throw IllegalStateException("HTTP ${response.statusCode()}")
    }
    return response.body()
}
fun importCollection(
    treeState: TreeState,
    toastState: ToastState,
    repository: CollectionRepository,
) {
    EventQueue.invokeLater {
        val chooser = JFileChooser()
        chooser.dialogTitle = "导入 Postman Collection"
        chooser.fileFilter = FileNameExtensionFilter("JSON (*.json)", "json")
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@invokeLater
        val file = chooser.selectedFile ?: return@invokeLater
        try {
            val text = Files.readString(file.toPath(), StandardCharsets.UTF_8)
            val portable = parsePostmanCollectionJsonToPortable(text)
            val newId = repository.importAsNewCollection(portable)
            treeState.refresh()
            treeState.expandedCollectionIds = treeState.expandedCollectionIds + newId
            treeState.treeSelection = TreeSelection.Collection(newId)
            showToast(toastState, "已导入集合「${portable.name}」")
        } catch (e: IllegalArgumentException) {
            JOptionPane.showMessageDialog(null, e.message ?: "文件格式不正确", "导入失败", JOptionPane.ERROR_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message ?: "文件格式不正确", "导入失败", JOptionPane.ERROR_MESSAGE)
        }
    }
}

fun importCurl(
    treeState: TreeState,
    editorState: RequestEditorState,
    responseState: ResponseState,
    toastState: ToastState,
    repository: CollectionRepository,
) {
    try {
        val clipboardText = readClipboardText()
        val parsed = parseCurlCommand(clipboardText)
        editorState.saveEditorIfBound()

        val newId: String
        val urlIfCurlUrlBlank: String

        val treeSel = treeState.treeSelection
        if (treeSel is TreeSelection.Folder) {
            val target = repository.newRequestTarget(treeSel)
            if (target == null) { showToast(toastState, "无法在选中文件夹下创建请求"); return }
            val (cid, fid) = target
            newId = repository.createRequest(cid, fid, "新请求")
            treeState.refresh()
            val placed = repository.getRequest(newId) ?: throw IllegalStateException("新建请求后无法读取")
            treeState.expandedCollectionIds = treeState.expandedCollectionIds + placed.collectionId
            if (fid != null) treeState.expandedFolderIds = treeState.expandedFolderIds + fid
            urlIfCurlUrlBlank = ""
        } else {
            val activeId = editorState.editorRequestId
            if (activeId == null) { showToast(toastState, "请先在编辑器中打开一个请求后再导入 cURL"); return }
            urlIfCurlUrlBlank = editorState.url
            newId = repository.createRequestBelow(activeId) ?: throw IllegalStateException("无法在当前请求下新建条目")
            treeState.refresh()
            val placed = repository.getRequest(newId) ?: throw IllegalStateException("新建请求后无法读取")
            treeState.expandedCollectionIds = treeState.expandedCollectionIds + placed.collectionId
            if (placed.folderId != null) treeState.expandedFolderIds = treeState.expandedFolderIds + placed.folderId
        }

        val rawUrl = parsed.url.ifBlank { urlIfCurlUrlBlank }
        if (rawUrl.isNotBlank()) {
            try {
                val uri = java.net.URI(rawUrl)
                val path = uri.path
                if (!path.isNullOrBlank()) {
                    val name = path.substringAfterLast('/')
                    if (name.isNotBlank()) { repository.renameRequest(newId, name); treeState.refresh() }
                }
            } catch (_: Exception) { }
        }
        val (urlNoQuery, queryParamsText) = splitUrlQueryForParamsEditor(rawUrl)
        val headersJoined = parsed.headers.joinToString("\n")
        val bodyForEditor = migrateFormBodyToEditorLinesIfNeeded(parsed.body, headersJoined)
        editorState.doApplyCurlToEditor(
            newId,
            parsed.method,
            urlNoQuery.ifBlank { rawUrl },
            queryParamsText,
            headersJoined,
            bodyForEditor
        )
        treeState.treeSelection = TreeSelection.Request(newId)

        val authHeaderKeys = listOf("authorization", "proxy-authorization", "x-api-key", "api-key", "x-auth-token")
        val hasAuthHeader = parsed.headers.any { h ->
            val key = h.substringBefore(':').trim().lowercase()
            authHeaderKeys.any { key.startsWith(it) }
        }
        if (!hasAuthHeader) {
            editorState.auth = PostmanAuth(type = "inherit")
        }

        repository.saveRequestEditorFields(newId, editorState.method, editorState.url, editorState.headersText, editorState.paramsText, editorState.bodyText, editorState.auth)
        showToast(toastState, "已新建请求并导入剪贴板中的 cURL")
        responseState.getSession(newId)?.let { session ->
            session.responseHeaderLines.clear(); session.responseHeaderLines.add("(暂无响应头)")
            session.statusCodeText = "-"; session.responseTimeText = "-"; session.responseSizeText = "0 B"
        }
    } catch (e: Exception) {
        showToast(toastState, "导入 cURL 失败: ${e.message}")
    }
}

fun pushData(
    repository: CollectionRepository,
    environmentStore: EnvironmentStore,
    toastState: ToastState,
) {
    thread {
        val r = DataDirSync.pushToDataDir(repository, environmentStore)
        EventQueue.invokeLater {
            if (r.error != null) JOptionPane.showMessageDialog(null, r.error, "同步到 data 失败", JOptionPane.ERROR_MESSAGE)
            else showToast(toastState, "已写入 ${AppPaths.gitDataRoot()}：${r.collectionFilesWritten} 个 collection JSON；${if (r.envWritten) "环境已同步到 data/env" else "环境未写入"}")
        }
    }
}

fun pullData(
    treeState: TreeState,
    environmentState: EnvironmentState,
    environmentStore: EnvironmentStore,
    toastState: ToastState,
    repository: CollectionRepository,
) {
    thread {
        val r = DataDirSync.pullFromDataDir(repository, environmentStore)
        EventQueue.invokeLater {
            if (r.error != null) JOptionPane.showMessageDialog(null, r.error, "从 data 合并失败", JOptionPane.ERROR_MESSAGE)
            else {
                treeState.refresh()
                environmentState.environmentsState = environmentStore.snapshot()
                val errLines = r.fileErrors.joinToString("\n")
                val baseMsg = "已合并：更新 ${r.merged} 个集合、新建 ${r.created} 个；环境 ${if (r.envMerged) "已合并" else "未变更"}"
                if (r.fileErrors.isNotEmpty()) JOptionPane.showMessageDialog(null, "$baseMsg\n\n部分文件未导入：\n$errLines", "从 data 合并", JOptionPane.INFORMATION_MESSAGE)
                showToast(toastState, if (r.fileErrors.isEmpty()) baseMsg else "$baseMsg；详见弹窗中失败项")
            }
        }
    }
}

fun deleteSelection(
    treeState: TreeState,
    editorState: RequestEditorState,
    responseState: ResponseState,
    repository: CollectionRepository,
    sel: TreeSelection,
) {
    editorState.saveEditorIfBound()
    when (sel) {
        is TreeSelection.Collection -> repository.deleteCollection(sel.id)
        is TreeSelection.Folder -> repository.deleteFolder(sel.id)
        is TreeSelection.Request -> { repository.deleteRequest(sel.id); RecentRequestUsageStore.remove(sel.id); RequestResponseStore.deleteRequestArtifacts(sel.id); responseState.removeSession(sel.id) }
    }
    val nextTree = repository.loadTree()
    treeState.tree = nextTree
    if (editorState.editorRequestId != null && repository.getRequest(editorState.editorRequestId!!) == null) {
        editorState.editorRequestId = null
        firstRequestSelection(nextTree)?.let { next ->
            editorState.applyRequestToEditor(next.id)
            treeState.treeSelection = next
        } ?: run {
            LastRequestPrefs.clear()
            editorState.method = "GET"
            editorState.url = ""
            editorState.headersText = ""
            editorState.paramsText = ""
            editorState.bodyText = ""
            treeState.treeSelection = nextTree.firstOrNull()?.let { TreeSelection.Collection(it.id) }
        }
    }
}

fun exportAsCurl(
    repository: CollectionRepository,
    environmentState: EnvironmentState,
    toastState: ToastState,
    rid: String,
) {
    val r = repository.getRequest(rid) ?: return
    val vmEnv = environmentState.environmentsState.substitutionMapForActive()
    writeClipboardText(requestToCurlCommand(r.method, mergeUrlWithParams(applyEnvironmentVariables(r.url, vmEnv), parseHeadersForSend(applyEnvironmentVariables(r.paramsText, vmEnv))), applyEnvironmentVariables(r.headersText, vmEnv), applyEnvironmentVariables(r.bodyText, vmEnv)))
    showToast(toastState, "已复制 cURL 到剪贴板")
}

fun exportAsGo(
    repository: CollectionRepository,
    environmentState: EnvironmentState,
    toastState: ToastState,
    rid: String,
) {
    val r = repository.getRequest(rid) ?: return
    val vmEnv = environmentState.environmentsState.substitutionMapForActive()
    writeClipboardText(requestToGoTestMethod(
        r.method,
        mergeUrlWithParams(
            applyEnvironmentVariables(r.url, vmEnv),
            parseHeadersForSend(applyEnvironmentVariables(r.paramsText, vmEnv)),
        ),
        applyEnvironmentVariables(r.headersText, vmEnv),
        applyEnvironmentVariables(r.bodyText, vmEnv),
    ))
    showToast(toastState, "已复制 Go 测试代码到剪贴板")
}

fun exportPostmanCollection(
    repository: CollectionRepository,
    toastState: ToastState,
    responseState: ResponseState,
    collectionId: String,
) {
    val portable = repository.exportPortableCollection(collectionId) ?: return
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
            showToast(toastState, "已导出 Postman v2.1：${file.absolutePath}")
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message ?: e.toString(), "导出失败", JOptionPane.ERROR_MESSAGE)
        }
    }
}

fun duplicateRequest(
    treeState: TreeState,
    editorState: RequestEditorState,
    repository: CollectionRepository,
    rid: String,
) {
    editorState.saveEditorIfBound()
    val newId = repository.duplicateRequestBelow(rid) ?: return
    treeState.refresh()
    val placed = repository.getRequest(newId) ?: return
    treeState.expandedCollectionIds = treeState.expandedCollectionIds + placed.collectionId
    if (placed.folderId != null) treeState.expandedFolderIds = treeState.expandedFolderIds + placed.folderId
    editorState.applyRequestToEditor(newId)
    treeState.treeSelection = TreeSelection.Request(newId)
}

fun applyTreeDrop(
    treeState: TreeState,
    repository: CollectionRepository,
    payload: TreeDragPayload,
    target: TreeDropTarget,
): Boolean {
    val ok = repository.applyTreeDrop(payload, target)
    if (ok) {
        treeState.refresh()
        when (target) {
            is TreeDropTarget.IntoFolder -> { treeState.expandedCollectionIds = treeState.expandedCollectionIds + target.collectionId; treeState.expandedFolderIds = treeState.expandedFolderIds + target.folderId }
            is TreeDropTarget.IntoCollection -> { treeState.expandedCollectionIds = treeState.expandedCollectionIds + target.collectionId }
            is TreeDropTarget.FolderSlot -> { treeState.expandedCollectionIds = treeState.expandedCollectionIds + target.collectionId; target.parentFolderId?.let { treeState.expandedFolderIds = treeState.expandedFolderIds + it } }
            is TreeDropTarget.RequestSlot -> { treeState.expandedCollectionIds = treeState.expandedCollectionIds + target.collectionId; target.folderId?.let { treeState.expandedFolderIds = treeState.expandedFolderIds + it } }
        }
    }
    return ok
}

fun loadHistory(
    editorState: RequestEditorState,
    responseState: ResponseState,
    epochMs: Long?,
) {
    val requestId = editorState.editorRequestId ?: return
    val session = responseState.getOrCreateSession(requestId)
    session.selectedHistoryEpochMs = epochMs
    session.isLoading = true
    session.statusCodeText = ""
    session.responseTimeText = ""
    session.responseSizeText = ""
    session.responseSseEventCount = ""
    session.responseLines.clear()
    session.responseLines.add("正在加载历史响应…")
    thread {
        val response = if (epochMs == null) RequestResponseStore.loadLatest(requestId) else RequestResponseStore.loadByTimestamp(requestId, epochMs)
        EventQueue.invokeLater {
            session.isLoading = false
            if (response != null) {
                session.responseLines.clear()
                session.responseLines.addAll(response.responseBodyLines)
                session.responseHeaderLines.clear()
                session.responseHeaderLines.addAll(response.responseHeaderLines)
                session.responsePartialLine = null
                session.statusCodeText = response.statusCodeText
                session.responseTimeText = response.responseTimeText
                session.responseSizeText = response.responseSizeText
                session.isSseResponse = response.isSseResponse
                session.responseSseEventCount = response.responseSseEventCount
                session.rightTabIndex = response.rightTabIndex.coerceIn(0, 2)
                session.exchangeRequestPlainText = response.requestPlainText.ifBlank { "尚无已发送请求记录；发送后将显示实际发出的请求头与正文。" }
            } else {
                session.responseLines.clear()
                session.responseLines.add("(加载失败)")
            }
        }
    }
}
