package app.core

import app.state.TreeState
import app.state.RequestEditorState
import app.state.ResponseState
import app.state.EnvironmentState
import app.state.RequestSession
import app.state.AppSettingsState
import app.state.ToastState
import app.settings.AppSettings
import db.CollectionRepository
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
import http.formatActualRequestPlainText
import http.mergeUrlWithParams
import http.parseHeadersForSend
import http.resolveAuthToHeaders
import http.responseHeaderLinesForHar
import http.sendRequestStreaming
import http.substitutionMapForActive
import tree.TreeSelection
import tree.firstRequestSelection
import java.awt.EventQueue
import kotlin.concurrent.thread

fun selectTreeNode(sel: TreeSelection, treeState: TreeState, editorState: RequestEditorState) {
    when (sel) {
        is TreeSelection.Request -> {
            if (sel.id in editorState.openTabIds) {
                editorState.selectTab(sel.id)
                treeState.treeSelection = sel
            } else {
                editorState.saveEditorIfBound()
                editorState.applyRequestToEditor(sel.id)
                treeState.treeSelection = sel
            }
        }
        else -> treeState.treeSelection = sel
    }
}

fun addRequestAt(at: TreeSelection, treeState: TreeState, editorState: RequestEditorState) {
    val target = treeState.repository.newRequestTarget(at) ?: return
    val (cid, fid) = target
    editorState.saveEditorIfBound()
    val rid = treeState.repository.createRequest(cid, fid, "新请求")
    treeState.refresh()
    treeState.expandedCollectionIds = treeState.expandedCollectionIds + cid
    if (fid != null) treeState.expandedFolderIds = treeState.expandedFolderIds + fid
    editorState.applyRequestToEditor(rid)
    treeState.treeSelection = TreeSelection.Request(rid)
}

fun startRequest(
    editorState: RequestEditorState,
    responseState: ResponseState,
    environmentState: EnvironmentState,
    repository: CollectionRepository,
) {
    val boundRequestId = editorState.editorRequestId ?: return
    val session = responseState.getOrCreateSession(boundRequestId)
    if (session.isLoading) return
    editorState.saveEditorIfBound()
    RequestResponseStore.ensureLayout(boundRequestId)
    val tabAtStart = session.rightTabIndex
    val reqMethodSnap = editorState.method
    val varMap = environmentState.environmentsState.substitutionMapForActive()
    val reqUrlSnap = applyEnvironmentVariables(editorState.url, varMap)
    val reqParamsSnap = applyEnvironmentVariables(editorState.paramsText, varMap)
    val effectiveRequestUrl = ensureDefaultHttpScheme(mergeUrlWithParams(reqUrlSnap, parseHeadersForSend(reqParamsSnap)))
    val reqHeadersFullSnap = applyEnvironmentVariables(editorState.headersText, varMap)
    val effectiveAuth = if (editorState.auth?.type == "inherit") repository.resolveEffectiveAuth(boundRequestId) else editorState.auth
    val authHeaders = resolveAuthToHeaders(effectiveAuth, varMap)
    val finalHeaders = if (authHeaders.isEmpty()) reqHeadersFullSnap
        else if (reqHeadersFullSnap.isEmpty()) authHeaders.joinToString("\n") { "${it.first}: ${it.second}" }
        else reqHeadersFullSnap + "\n" + authHeaders.joinToString("\n") { "${it.first}: ${it.second}" }
    val reqBodySnap = bodyWirePayloadForHttp(applyEnvironmentVariables(editorState.bodyText, varMap), finalHeaders)
    session.exchangeRequestPlainText = formatActualRequestPlainText(reqMethodSnap, effectiveRequestUrl, finalHeaders, reqBodySnap)
    val control = RequestControl()
    control.startTimeMs = System.currentTimeMillis()
    session.control = control
    session.requestGen++
    val gen = session.requestGen
    session.isLoading = true
    session.isCacheLoading = false
    session.isSseResponse = false
    responseState.addRunningRequest(boundRequestId)
    session.responseLines.clear()
    session.responsePartialLine = null
    session.responseHeaderLines.clear()
    session.responseHeaderLines.add("等待响应…")
    session.statusCodeText = ""
    session.responseTimeText = ""
    session.responseSizeText = ""
    session.responseSseEventCount = ""
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
            method = editorState.method, url = effectiveRequestUrl, body = reqBodySnap, headersText = finalHeaders, control = control,
            onSseDetected = { isSse -> EventQueue.invokeLater { if (session.control === control) session.isSseResponse = isSse } },
            onStatusCode = { code -> EventQueue.invokeLater { if (session.control === control) session.statusCodeText = code.toString() } },
            onResponseTime = { },
            onProgress = { bytes -> EventQueue.invokeLater { if (session.control === control) session.responseSizeText = formatBytes(bytes) } },
            onSseEventCount = { count -> EventQueue.invokeLater { if (session.control === control) session.responseSseEventCount = "${count}个事件" } },
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
                    responseSseEventCountText = session.responseSseEventCount,
                ))
                if (editorState.editorRequestId == boundRequestId) {
                    session.historyEntries = RequestResponseStore.listHistory(boundRequestId)
                }
            }
            session.isLoading = false
            responseState.removeRunningRequest(boundRequestId)
            if (session.control === control) { session.control = null; session.workerThread = null; session.flusherThread = null }
            if (!control.cancelled) {
                if (control.requestFailed) { session.statusCodeText = HttpExchangeErrorStatusMark; if (control.responseStatusCode < 0) { session.responseHeaderLines.clear(); session.responseHeaderLines.add("(无响应头 — 请求未成功)") } }
                applyBufferUpdate(control.lineBuffer.drainUpdate(), session.responseLines) { session.responsePartialLine = it }
            } else { control.lineBuffer.drainUpdate() }
            session.responseTimeText = timeText; session.responseSizeText = formatBytes(control.totalBytes)
            flusher.interrupt()
        }
    }
    session.workerThread = worker
    session.flusherThread = flusher
}

fun cancelActiveRequest(
    editorState: RequestEditorState,
    responseState: ResponseState,
    environmentState: EnvironmentState,
    repository: CollectionRepository,
) {
    val boundId = editorState.editorRequestId ?: return
    val session = responseState.getSession(boundId) ?: return
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
    val varMap = environmentState.environmentsState.substitutionMapForActive()
    val resolvedUrl = applyEnvironmentVariables(editorState.url, varMap)
    val resolvedParams = applyEnvironmentVariables(editorState.paramsText, varMap)
    val cancelUrl = mergeUrlWithParams(resolvedUrl, parseHeadersForSend(resolvedParams))
    val resolvedHeaders = applyEnvironmentVariables(editorState.headersText, varMap)
    val resolvedBody = bodyWirePayloadForHttp(applyEnvironmentVariables(editorState.bodyText, varMap), resolvedHeaders)
    RequestResponseStore.save(boundId, HarSnapshot(
        savedAtEpochMs = System.currentTimeMillis(), requestMethod = editorState.method, requestUrl = cancelUrl,
        requestHeadersFullText = resolvedHeaders, requestBody = resolvedBody, requestHeadersSent = parseHeadersForSend(resolvedHeaders),
        responseStatus = if (sc >= 0) sc else 0,
        responseStatusText = if (sc >= 0) HarLogCodec.responseStatusPhrase(sc) else "",
        responseHeaderLines = responseHeaderLinesForHar(control.responseHeaderSnapshot, control.responseBodyDecodedForHar),
        responseBodyLines = control.snapshotRawBodyLines(), responseTimeMs = System.currentTimeMillis() - control.startTimeMs,
        responseSizeBytes = control.totalBytes, responseTimeLabel = timeText, responseSizeLabel = formatBytes(control.totalBytes),
        rightTabIndex = session.rightTabIndex.coerceIn(0, 2), isSseResponse = control.responseWasSse,
        responseSseEventCountText = session.responseSseEventCount,
    ))
    session.workerThread?.interrupt()
    session.flusherThread?.interrupt()
    session.isLoading = false; responseState.removeRunningRequest(boundId); session.control = null; session.workerThread = null; session.flusherThread = null
}
