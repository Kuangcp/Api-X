package app

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import http.BufferUpdate

const val UI_REFRESH_INTERVAL_MS = 100L

fun applyBufferUpdate(
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

fun setSingleResponseMessage(responseLines: MutableList<String>, message: String) {
    responseLines.clear()
    responseLines += message
}

fun formatDuration(ms: Long): String {
    return when {
        ms < 10_000 -> "${ms}ms"
        ms < 80_000 -> String.format("%.1fS", ms / 1000.0)
        else -> String.format("%.1fmin", ms / 60_000.0)
    }
}

fun formatBytes(bytes: Long): String {
    val b = bytes.toDouble()
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.2f KB", b / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format("%.2f MB", b / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", b / (1024.0 * 1024.0 * 1024.0))
    }
}

fun responseBodyTextForClipboard(lines: List<String>, partial: String?): String =
    buildString {
        append(lines.joinToString("\n"))
        partial?.let { p ->
            if (lines.isNotEmpty()) append('\n')
            append(p)
        }
    }

fun readClipboardText(): String {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val data = clipboard.getData(DataFlavor.stringFlavor) as? String ?: ""
    if (data.isBlank()) throw IllegalArgumentException("剪贴板为空")
    return data.trim()
}

fun writeClipboardText(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

object AppClasspathAnchor