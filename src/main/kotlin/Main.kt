import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import kotlin.concurrent.thread

@Composable
@Preview
fun App() {
    var method by remember { mutableStateOf("GET") }
    var methodMenuExpanded by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("https://httpbin.org/get") }
    var headersText by remember {
        mutableStateOf(
            "Content-Type: application/json\nAccept: application/json"
        )
    }
    var bodyText by remember { mutableStateOf("{\n  \"name\": \"api-x\"\n}") }
    var responseLines by remember { mutableStateOf(mutableStateListOf("响应结果会显示在这里")) }
    var responsePartialLine by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isSseResponse by remember { mutableStateOf(false) }
    var statusCodeText by remember { mutableStateOf("-") }
    var responseTimeText by remember { mutableStateOf("-") }
    var responseSizeText by remember { mutableStateOf("0 B") }
    var activeRequestControl by remember { mutableStateOf<RequestControl?>(null) }
    var activeRequestThread by remember { mutableStateOf<Thread?>(null) }
    var splitRatio by remember { mutableStateOf(0.5f) }
    var contentRowWidthPx by remember { mutableStateOf(1f) }
    var leftTabIndex by remember { mutableStateOf(0) }
    var rightTabIndex by remember { mutableStateOf(0) }
    var responseHeaderLines by remember { mutableStateOf(mutableStateListOf("(暂无响应头)")) }
    val responseListState = rememberLazyListState()
    val responseHeadersListState = rememberLazyListState()

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(hexToColor("#f2f2f2"))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        setSingleResponseMessage(responseLines, "设置功能待实现")
                        responsePartialLine = null
                        responseHeaderLines = mutableStateListOf("(暂无响应头)")
                        statusCodeText = "-"
                        responseTimeText = "-"
                        responseSizeText = "0 B"
                    },
                    enabled = !isLoading
                ) {
                    Text("设置")
                }
                Button(
                    onClick = {
                        try {
                            val clipboardText = readClipboardText()
                            val parsed = parseCurlCommand(clipboardText)
                            method = parsed.method
                            url = parsed.url.ifBlank { url }
                            headersText = parsed.headers.joinToString("\n")
                            bodyText = parsed.body
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
                    },
                    enabled = !isLoading
                ) {
                    Text("导入 cURL")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Button(
                        onClick = { methodMenuExpanded = true },
                        enabled = !isLoading,
                        modifier = Modifier
                            .height(UrlBarCompactHeight)
                            .defaultMinSize(minWidth = 0.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(method, fontSize = 13.sp)
                    }
                    DropdownMenu(
                        expanded = methodMenuExpanded,
                        onDismissRequest = { methodMenuExpanded = false }
                    ) {
                        DropdownMenuItem(onClick = {
                            method = "GET"
                            methodMenuExpanded = false
                        }) {
                            Text("GET")
                        }
                        DropdownMenuItem(onClick = {
                            method = "POST"
                            methodMenuExpanded = false
                        }) {
                            Text("POST")
                        }
                    }
                }
                // 矮高度下 Material OutlinedTextField 内部 padding 仍会占满，文字被裁切；用 BasicTextField 自控内边距
                val urlFieldEnabled = !isLoading
                BasicTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(UrlBarCompactHeight)
                        .defaultMinSize(minHeight = 0.dp),
                    enabled = urlFieldEnabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.body2.copy(
                        fontSize = 13.sp,
                        color = MaterialTheme.colors.onSurface.copy(
                            alpha = if (urlFieldEnabled) 1f else ContentAlpha.disabled
                        )
                    ),
                    decorationBox = { innerTextField ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colors.onSurface.copy(
                                        alpha = if (urlFieldEnabled) ContentAlpha.medium else ContentAlpha.disabled
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.weight(1f)) {
                                if (url.isEmpty()) {
                                    Text(
                                        "输入 URL",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    }
                )
                Button(
                    modifier = Modifier
                        .height(UrlBarCompactHeight)
                        .defaultMinSize(minWidth = 0.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    onClick = {
                        if (!isLoading) {
                            val control = RequestControl()
                            activeRequestControl = control
                            isLoading = true
                            responseLines = mutableStateListOf()
                            responsePartialLine = null
                            responseHeaderLines = mutableStateListOf("等待响应…")
                            statusCodeText = "-"
                            responseTimeText = "-"
                            responseSizeText = "0 B"
                            // control.lineBuffer.append("请求中...\n")
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
                        } else {
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
                    },
                    enabled = true
                ) {
                    Text(if (isLoading) "取消请求" else "发送请求", fontSize = 13.sp)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { contentRowWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            ) {
                Column(
                    modifier = Modifier.weight(splitRatio).fillMaxHeight().padding(end = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE8E8E8))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    if (leftTabIndex == 0) {
                                        MaterialTheme.colors.primary.copy(alpha = 0.14f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable(enabled = !isLoading) { leftTabIndex = 0 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Body",
                                fontSize = 12.sp,
                                color = if (leftTabIndex == 0) {
                                    MaterialTheme.colors.primary
                                } else {
                                    Color(0xFF9E9E9E)
                                }
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    if (leftTabIndex == 1) {
                                        MaterialTheme.colors.primary.copy(alpha = 0.14f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable(enabled = !isLoading) { leftTabIndex = 1 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Headers",
                                fontSize = 12.sp,
                                color = if (leftTabIndex == 1) {
                                    MaterialTheme.colors.primary
                                } else {
                                    Color(0xFF9E9E9E)
                                }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        when (leftTabIndex) {
                            0 -> OutlinedTextField(
                                modifier = Modifier.fillMaxSize(),
                                value = bodyText,
                                onValueChange = { bodyText = it },
                                label = { Text("Body（POST 时生效）") },
                                enabled = !isLoading
                            )
                            else -> OutlinedTextField(
                                modifier = Modifier.fillMaxSize(),
                                value = headersText,
                                onValueChange = { headersText = it },
                                label = { Text("每行：Key: Value") },
                                enabled = !isLoading
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .fillMaxHeight()
                        .background(Color(0x33000000))
                        .pointerInput(contentRowWidthPx) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val next = splitRatio + (dragAmount.x / contentRowWidthPx)
                                splitRatio = next.coerceIn(0.2f, 0.8f)
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .align(Alignment.Center)
                            .background(Color(0x55000000))
                    )
                }
                Column(
                    modifier = Modifier.weight(1f - splitRatio).fillMaxHeight().padding(start = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = statusCodeText,
                            color = when (statusCodeText.toIntOrNull()) {
                                null -> LocalContentColor.current
                                200 -> Color(0xFF2E7D32)
                                else -> Color(0xFFC62828)
                            }
                        )
                        Text(" ")
                        Text("$responseTimeText ")
                        Text("$responseSizeText")
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFE8E8E8))
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        if (rightTabIndex == 0) {
                                            MaterialTheme.colors.primary.copy(alpha = 0.14f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .clickable { rightTabIndex = 0 },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Body",
                                    fontSize = 12.sp,
                                    color = if (rightTabIndex == 0) {
                                        MaterialTheme.colors.primary
                                    } else {
                                        Color(0xFF9E9E9E)
                                    }
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        if (rightTabIndex == 1) {
                                            MaterialTheme.colors.primary.copy(alpha = 0.14f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .clickable { rightTabIndex = 1 },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Headers",
                                    fontSize = 12.sp,
                                    color = if (rightTabIndex == 1) {
                                        MaterialTheme.colors.primary
                                    } else {
                                        Color(0xFF9E9E9E)
                                    }
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .background(Color.White)
                                .padding(12.dp)
                        ) {
                            when (rightTabIndex) {
                                0 -> {
                                    SelectionContainer {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(end = 12.dp),
                                            state = responseListState
                                        ) {
                                            items(responseLines) { line ->
                                                Text(line, fontSize = 12.sp)
                                            }
                                            responsePartialLine?.let { partial ->
                                                item("partial") {
                                                    Text(partial, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                    VerticalScrollbar(
                                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                        adapter = rememberScrollbarAdapter(responseListState)
                                    )
                                }
                                else -> {
                                    SelectionContainer {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(end = 12.dp),
                                            state = responseHeadersListState
                                        ) {
                                            items(responseHeaderLines) { line ->
                                                Text(line, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                    VerticalScrollbar(
                                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                        adapter = rememberScrollbarAdapter(responseHeadersListState)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(responseLines.size, responsePartialLine, isSseResponse, rightTabIndex) {
        if (isSseResponse && rightTabIndex == 0) {
            val total = responseLines.size + if (responsePartialLine != null) 1 else 0
            if (total > 0) {
                responseListState.scrollToItem(total - 1)
            }
        }
    }
}

private const val UI_REFRESH_INTERVAL_MS = 100L

/** URL 栏与两侧按钮对齐的高度（约 Material 单行 OutlinedTextField 的 70%）；栏内用 BasicTextField 避免默认内边距裁切文字 */
private val UrlBarCompactHeight = 39.dp

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
    Window(title = "Api-X", onCloseRequest = ::exitApplication) {
        App()
    }
}
