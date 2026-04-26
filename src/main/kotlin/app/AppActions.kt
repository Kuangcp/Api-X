package app

import db.AppPaths
import db.RequestResponseStore
import http.applyEnvironmentVariables
import http.mergeUrlWithParams
import http.parseHeadersForSend
import http.requestToCurlCommand
import http.splitUrlQueryForParamsEditor
import http.substitutionMapForActive
import http.migrateFormBodyToEditorLinesIfNeeded
import http.parseCurlCommand
import java.awt.EventQueue
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.concurrent.thread
import tree.TreeDragPayload
import tree.TreeDropTarget
import tree.TreeSelection
import tree.parsePostmanCollectionJsonToPortable
import tree.portableCollectionToPostmanV21Json
import tree.firstRequestSelection

fun importCollection(vm: AppViewModel) {
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

fun importCurl(vm: AppViewModel) {
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

fun pushData(vm: AppViewModel) {
    thread {
        val r = DataDirSync.pushToDataDir(vm.repository)
        EventQueue.invokeLater {
            if (r.error != null) JOptionPane.showMessageDialog(null, r.error, "同步到 data 失败", JOptionPane.ERROR_MESSAGE)
            else setSingleResponseMessage(vm.responseLines, "已写入 ${AppPaths.gitDataRoot()}：${r.collectionFilesWritten} 个 collection JSON；${if (r.envWritten) "环境已同步到 data/env" else "环境未写入"}")
            vm.setResponsePartialLine(null)
        }
    }
}

fun pullData(vm: AppViewModel) {
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

fun deleteSelection(vm: AppViewModel, sel: TreeSelection) {
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

fun exportAsCurl(vm: AppViewModel, rid: String) {
    val r = vm.repository.getRequest(rid) ?: return
    val vmEnv = vm.environmentsState.substitutionMapForActive()
    writeClipboardText(requestToCurlCommand(r.method, mergeUrlWithParams(applyEnvironmentVariables(r.url, vmEnv), parseHeadersForSend(applyEnvironmentVariables(r.paramsText, vmEnv))), applyEnvironmentVariables(r.headersText, vmEnv), applyEnvironmentVariables(r.bodyText, vmEnv)))
    setSingleResponseMessage(vm.responseLines, "已复制 cURL 到剪贴板")
    vm.setResponsePartialLine(null)
}

fun exportPostmanCollection(vm: AppViewModel, collectionId: String) {
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

fun duplicateRequest(vm: AppViewModel, rid: String) {
    vm.onSaveEditor()
    val newId = vm.repository.duplicateRequestBelow(rid) ?: return
    vm.onRefreshTree()
    val placed = vm.repository.getRequest(newId) ?: return
    vm.setExpandedCollectionIds(vm.expandedCollectionIds + placed.collectionId)
    if (placed.folderId != null) vm.setExpandedFolderIds(vm.expandedFolderIds + placed.folderId)
    vm.onApplyRequestToEditor(newId)
    vm.setTreeSelection(TreeSelection.Request(newId))
}

fun applyTreeDrop(vm: AppViewModel, payload: TreeDragPayload, target: TreeDropTarget): Boolean {
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

fun loadHistory(vm: AppViewModel, epochMs: Long?) {
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
        vm.setExchangeRequestPlainText(response.requestPlainText.ifBlank { "尚无已发送请求记录；��送��将显示实际发出的请求头与正文。" })
    }
}