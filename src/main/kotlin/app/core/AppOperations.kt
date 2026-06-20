package app.core

import app.log.Logger
import app.state.TreeState
import app.state.RequestEditorState
import app.state.ResponseState
import app.state.EnvironmentState
import app.state.RequestSession
import app.state.McpConnectionState
import app.state.AppSettingsState
import app.state.ToastState
import app.settings.AppSettings
import db.CollectionRepository
import db.HarLogCodec
import db.McpCatalogStore
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
import mcp.extractMcpCatalogFromLog
import mcp.openMcpLiveConnection
import mcp.runMcpStdioDebug
import mcp.runMcpSseDebug
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
    Logger.info("HTTP") { "startRequest: $boundRequestId, url=${editorState.url}" }
    val session = responseState.getOrCreateSession(boundRequestId)
    if (session.isLoading) {
        Logger.info("HTTP") { "startRequest: $boundRequestId already loading, skip" }
        return
    }
    editorState.saveEditorIfBound()
    if (editorState.method.equals("MCP", ignoreCase = true)) {
        startMcpStdioRequest(editorState, responseState, environmentState, boundRequestId)
        return
    }
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
    Logger.info("HTTP") { "startRequest: $boundRequestId gen=$gen, control started" }
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
        try {
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
        } catch (e: Exception) {
            Logger.error("HTTP", e) { "sendRequestStreaming exception for $boundRequestId: ${e.message}" }
        }
        EventQueue.invokeLater {
            if (session.requestGen != gen) return@invokeLater
            control.finished = true
            val elapsed = System.currentTimeMillis() - control.startTimeMs
            val timeText = formatDuration(elapsed)
            if (!control.cancelled && !control.requestFailed) {
                val code = control.responseStatusCode
                try {
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
                    Logger.info("HTTP") { "Saved response for $boundRequestId, status=$code, time=${elapsed}ms, bytes=${control.totalBytes}" }
                } catch (e: Exception) {
                    Logger.error("HTTP", e) { "Save response failed for $boundRequestId: ${e.message}" }
                }
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
            Logger.info("HTTP") { "Request done: $boundRequestId, gen=$gen, cancelled=${control.cancelled}, failed=${control.requestFailed}, lines=${session.responseLines.size}" }
        }
    }
    session.workerThread = worker
    session.flusherThread = flusher
}

fun connectMcpSession(
    editorState: RequestEditorState,
    responseState: ResponseState,
    environmentState: EnvironmentState,
    mcpConnectionState: McpConnectionState,
    forceReconnect: Boolean = false,
) {
    val boundRequestId = editorState.editorRequestId ?: return
    if (!editorState.method.equals("MCP", ignoreCase = true)) return
    val session = responseState.getOrCreateSession(boundRequestId)
    if (session.isLoading) return

    val varMap = environmentState.environmentsState.substitutionMapForActive()
    val commandLine = applyEnvironmentVariables(editorState.url, varMap)
    val envText = applyEnvironmentVariables(editorState.headersText, varMap)
    val headersOrEnv = parseHeadersForSend(envText)
    val liveSession = mcpConnectionState.getOrCreate(boundRequestId)
    val targetChanged = liveSession.target != commandLine || liveSession.envText != envText
    if (liveSession.isConnected && !forceReconnect && !targetChanged) {
        session.responseLines.add("[MCP] Already connected")
        return
    }
    if (forceReconnect || targetChanged) {
        mcpConnectionState.close(boundRequestId)
    }

    val isHttpMcp = commandLine.startsWith("http://", ignoreCase = true) ||
        commandLine.startsWith("https://", ignoreCase = true)
    val transportLabel = if (isHttpMcp) "MCP SSE" else "MCP STDIO"
    val control = RequestControl()
    control.startTimeMs = System.currentTimeMillis()
    session.control = control
    session.requestGen++
    val gen = session.requestGen
    session.isLoading = true
    session.isCacheLoading = false
    session.isSseResponse = false
    liveSession.isConnecting = true
    responseState.addRunningRequest(boundRequestId)
    session.responseLines.clear()
    session.responsePartialLine = null
    session.responseHeaderLines.clear()
    session.responseHeaderLines.add(transportLabel)
    session.statusCodeText = "MCP"
    session.responseTimeText = ""
    session.responseSizeText = ""
    session.responseSseEventCount = ""
    session.exchangeRequestPlainText = buildString {
        appendLine(if (forceReconnect) "Reconnect MCP" else "Connect MCP")
        appendLine(commandLine)
        if (envText.isNotBlank()) {
            appendLine()
            appendLine("Env:")
            appendLine(envText)
        }
    }

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
        try {
            val connection = openMcpLiveConnection(commandLine, headersOrEnv)
            liveSession.connection = connection
            liveSession.target = commandLine
            liveSession.envText = envText
            val onMcpChunk: (String) -> Unit = { chunk ->
                if (session.control === control && !control.cancelled) {
                    control.lineBuffer.append(chunk)
                    control.appendRawResponse(chunk)
                }
            }
            connection.connect(
                isCancelled = { control.cancelled || Thread.currentThread().isInterrupted },
                onChunk = onMcpChunk,
            )
            liveSession.isConnected = true
            control.responseStatusCode = 0
        } catch (e: Exception) {
            control.requestFailed = true
            control.lineBuffer.append("[MCP error] ${e.message ?: e::class.simpleName}\n")
            Logger.error("MCP", e) { "MCP connect failed for $boundRequestId: ${e.message}" }
            mcpConnectionState.close(boundRequestId)
        }
        EventQueue.invokeLater {
            if (session.requestGen != gen) return@invokeLater
            control.finished = true
            val elapsed = System.currentTimeMillis() - control.startTimeMs
            applyBufferUpdate(control.lineBuffer.drainUpdate(), session.responseLines) { session.responsePartialLine = it }
            val catalog = extractMcpCatalogFromLog(session.responseLines, session.responsePartialLine)
            if (!catalog.isEmpty) {
                McpCatalogStore.saveCatalog(boundRequestId, catalog)
                responseState.cacheRefreshVersion++
            }
            session.responseTimeText = formatDuration(elapsed)
            session.responseSizeText = formatBytes(control.totalBytes)
            session.isLoading = false
            liveSession.isConnecting = false
            responseState.removeRunningRequest(boundRequestId)
            if (session.control === control) {
                session.control = null
                session.workerThread = null
                session.flusherThread = null
            }
            flusher.interrupt()
        }
    }
    session.workerThread = worker
    session.flusherThread = flusher
}

fun disconnectMcpSession(
    editorState: RequestEditorState,
    responseState: ResponseState,
    mcpConnectionState: McpConnectionState,
) {
    val boundRequestId = editorState.editorRequestId ?: return
    val session = responseState.getOrCreateSession(boundRequestId)
    val chunks = StringBuilder()
    mcpConnectionState.close(boundRequestId) { chunks.append(it) }
    val text = chunks.toString().ifBlank { "\n[MCP] Disconnected\n" }
    session.responseLines.addAll(text.trimEnd().lines())
    session.responsePartialLine = null
    session.statusCodeText = "MCP"
    responseState.cacheRefreshVersion++
}
fun refreshMcpCatalog(
    editorState: RequestEditorState,
    responseState: ResponseState,
    environmentState: EnvironmentState,
) {
    val boundRequestId = editorState.editorRequestId ?: return
    if (!editorState.method.equals("MCP", ignoreCase = true)) return
    startMcpStdioRequest(
        editorState = editorState,
        responseState = responseState,
        environmentState = environmentState,
        boundRequestId = boundRequestId,
        bodyOverride = "",
    )
}

private fun startMcpStdioRequest(
    editorState: RequestEditorState,
    responseState: ResponseState,
    environmentState: EnvironmentState,
    boundRequestId: String,
    bodyOverride: String? = null,
) {
    Logger.info("MCP") { "startMcpStdioRequest: $boundRequestId, command=${editorState.url}" }
    val session = responseState.getOrCreateSession(boundRequestId)
    if (session.isLoading) return
    val varMap = environmentState.environmentsState.substitutionMapForActive()
    val commandLine = applyEnvironmentVariables(editorState.url, varMap)
    val envText = applyEnvironmentVariables(editorState.headersText, varMap)
    val bodyText = bodyOverride ?: applyEnvironmentVariables(editorState.bodyText, varMap)
    val isHttpMcp = commandLine.startsWith("http://", ignoreCase = true) ||
        commandLine.startsWith("https://", ignoreCase = true)
    val transportLabel = if (isHttpMcp) "MCP SSE" else "MCP STDIO"
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
    session.responseHeaderLines.add(transportLabel)
    session.statusCodeText = "MCP"
    session.responseTimeText = ""
    session.responseSizeText = ""
    session.responseSseEventCount = ""
    session.exchangeRequestPlainText = buildString {
        appendLine(transportLabel)
        appendLine(commandLine)
        if (envText.isNotBlank()) {
            appendLine()
            appendLine("Env:")
            appendLine(envText)
        }
        if (bodyText.isNotBlank()) {
            appendLine()
            appendLine(if (bodyOverride == null) "Tool call:" else "Catalog refresh")
            append(bodyText)
        }
    }

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
        try {
            val headersOrEnv = parseHeadersForSend(envText)
            val onMcpChunk: (String) -> Unit = { chunk ->
                if (session.control === control && !control.cancelled) {
                    control.lineBuffer.append(chunk)
                    control.appendRawResponse(chunk)
                }
            }
            val result = if (isHttpMcp) {
                runMcpSseDebug(
                    sseUrl = commandLine,
                    headerLines = headersOrEnv,
                    toolCallBody = bodyText,
                    isCancelled = { control.cancelled || Thread.currentThread().isInterrupted },
                    onChunk = onMcpChunk,
                )
            } else {
                runMcpStdioDebug(
                    commandLine = commandLine,
                    envLines = headersOrEnv,
                    toolCallBody = bodyText,
                    isCancelled = { control.cancelled || Thread.currentThread().isInterrupted },
                    onChunk = onMcpChunk,
                )
            }
            control.totalBytes = result.bytes
            control.responseStatusCode = result.exitCode ?: 0
        } catch (e: Exception) {
            control.requestFailed = true
            control.lineBuffer.append("[MCP error] ${e.message ?: e::class.simpleName}\n")
            Logger.error("MCP", e) { "MCP STDIO failed for $boundRequestId: ${e.message}" }
        }
        EventQueue.invokeLater {
            if (session.requestGen != gen) return@invokeLater
            control.finished = true
            val elapsed = System.currentTimeMillis() - control.startTimeMs
            applyBufferUpdate(control.lineBuffer.drainUpdate(), session.responseLines) { session.responsePartialLine = it }
            val catalog = extractMcpCatalogFromLog(session.responseLines, session.responsePartialLine)
            if (!catalog.isEmpty) {
                McpCatalogStore.saveCatalog(boundRequestId, catalog)
                responseState.cacheRefreshVersion++
            }
            session.responseTimeText = formatDuration(elapsed)
            session.responseSizeText = formatBytes(control.totalBytes)
            session.isLoading = false
            responseState.removeRunningRequest(boundRequestId)
            if (session.control === control) {
                session.control = null
                session.workerThread = null
                session.flusherThread = null
            }
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
