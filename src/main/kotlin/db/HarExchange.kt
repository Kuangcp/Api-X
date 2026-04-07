package db

import http.parseHeaderLine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.format.DateTimeFormatter

/** 一次 HTTP 交换，用于生成 HAR 1.2 文件（可导入 Chrome DevTools 等）。 */
data class HarSnapshot(
    val savedAtEpochMs: Long,
    val requestMethod: String,
    val requestUrl: String,
    /** 编辑器内完整 Header 文本（含 `! ` 禁行行）。 */
    val requestHeadersFullText: String,
    val requestBody: String,
    /** 实际随请求发送的 Header。 */
    val requestHeadersSent: List<Pair<String, String>>,
    val responseStatus: Int,
    val responseStatusText: String,
    val responseHeaderLines: List<String>,
    val responseBodyLines: List<String>,
    val responseTimeMs: Long,
    val responseSizeBytes: Long,
    val responseTimeLabel: String,
    val responseSizeLabel: String,
    val rightTabIndex: Int,
    val isSseResponse: Boolean,
)

private val harJson = kotlinx.serialization.json.Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
    ignoreUnknownKeys = true
}

private fun httpReasonPhrase(status: Int): String = when (status) {
    200 -> "OK"
    201 -> "Created"
    204 -> "No Content"
    301 -> "Moved Permanently"
    302 -> "Found"
    304 -> "Not Modified"
    400 -> "Bad Request"
    401 -> "Unauthorized"
    403 -> "Forbidden"
    404 -> "Not Found"
    500 -> "Internal Server Error"
    502 -> "Bad Gateway"
    503 -> "Service Unavailable"
    else -> ""
}

private fun headerLinesToHarArray(lines: List<String>): JsonArray = buildJsonArray {
    for (line in lines) {
        val p = parseHeaderLine(line) ?: continue
        add(
            buildJsonObject {
                put("name", JsonPrimitive(p.first))
                put("value", JsonPrimitive(p.second))
            },
        )
    }
}

private fun pairsToHarArray(pairs: List<Pair<String, String>>): JsonArray = buildJsonArray {
    for ((n, v) in pairs) {
        add(
            buildJsonObject {
                put("name", JsonPrimitive(n))
                put("value", JsonPrimitive(v))
            },
        )
    }
}

private fun guessRequestMime(headersSent: List<Pair<String, String>>): String =
    headersSent.firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }?.second
        ?.substringBefore(';').orEmpty().ifBlank { "application/octet-stream" }

private fun requestPlainTextFromHarRequest(request: JsonObject): String {
    val method = request["method"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val url = request["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val headerArr = request["headers"]?.jsonArray ?: JsonArray(emptyList())
    val headerLines = buildString {
        for (el in headerArr) {
            val ho = el.jsonObject
            val n = ho["name"]?.jsonPrimitive?.contentOrNull ?: continue
            val v = ho["value"]?.jsonPrimitive?.contentOrNull ?: ""
            append(n)
            append(": ")
            append(v)
            append('\n')
        }
    }.trimEnd()
    val body = request["postData"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
    return buildString {
        append(method)
        append(' ')
        append(url)
        append("\n\n")
        if (headerLines.isNotBlank()) {
            append(headerLines)
        } else {
            append("(无请求头)")
        }
        if (body.isNotBlank()) {
            append("\n\n")
            append(body)
        }
    }
}

private fun guessResponseMime(lines: List<String>): String {
    for (line in lines) {
        val p = parseHeaderLine(line) ?: continue
        if (p.first.equals("Content-Type", ignoreCase = true)) {
            return p.second.substringBefore(';').ifBlank { "text/plain" }
        }
    }
    return "text/plain"
}

object HarLogCodec {

    fun responseStatusPhrase(statusCode: Int): String = httpReasonPhrase(statusCode)

    fun toJsonString(s: HarSnapshot): String {
        val bodyJoined = s.responseBodyLines.joinToString("\n")
        val started = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(s.savedAtEpochMs))
        val reqMime = guessRequestMime(s.requestHeadersSent)
        val respMime = guessResponseMime(s.responseHeaderLines)
        val root = buildJsonObject {
            put(
                "log",
                buildJsonObject {
                    put("version", JsonPrimitive("1.2"))
                    put(
                        "creator",
                        buildJsonObject {
                            put("name", JsonPrimitive("api-x"))
                            put("version", JsonPrimitive("1.0.0"))
                        },
                    )
                    put("_apiX", buildJsonExtension(s))
                    put("pages", buildJsonArray {})
                    put(
                        "entries",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("startedDateTime", JsonPrimitive(started))
                                    put("time", JsonPrimitive(s.responseTimeMs))
                                    put(
                                        "request",
                                        buildJsonObject {
                                            put("method", JsonPrimitive(s.requestMethod.uppercase()))
                                            put("url", JsonPrimitive(s.requestUrl.trim()))
                                            put("httpVersion", JsonPrimitive("HTTP/1.1"))
                                            put("headers", pairsToHarArray(s.requestHeadersSent))
                                            put("headersSize", JsonPrimitive(-1))
                                            put("bodySize", JsonPrimitive(s.requestBody.toByteArray(Charsets.UTF_8).size.toLong()))
                                            if (s.requestBody.isNotBlank()) {
                                                put(
                                                    "postData",
                                                    buildJsonObject {
                                                        put("mimeType", JsonPrimitive(reqMime))
                                                        put("text", JsonPrimitive(s.requestBody))
                                                    },
                                                )
                                            }
                                        },
                                    )
                                    put(
                                        "response",
                                        buildJsonObject {
                                            put("status", JsonPrimitive(s.responseStatus))
                                            put("statusText", JsonPrimitive(s.responseStatusText))
                                            put("httpVersion", JsonPrimitive("HTTP/1.1"))
                                            put("headers", headerLinesToHarArray(s.responseHeaderLines))
                                            put("headersSize", JsonPrimitive(-1))
                                            put("bodySize", JsonPrimitive(s.responseSizeBytes))
                                            put(
                                                "content",
                                                buildJsonObject {
                                                    put("size", JsonPrimitive(bodyJoined.toByteArray(Charsets.UTF_8).size))
                                                    put("mimeType", JsonPrimitive(respMime))
                                                    put("text", JsonPrimitive(bodyJoined))
                                                },
                                            )
                                        },
                                    )
                                    put("cache", buildJsonObject {})
                                    put(
                                        "timings",
                                        buildJsonObject {
                                            put("send", JsonPrimitive(0))
                                            put("wait", JsonPrimitive(s.responseTimeMs))
                                            put("receive", JsonPrimitive(0))
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }
        return harJson.encodeToString(root)
    }

    private fun buildJsonExtension(s: HarSnapshot): JsonObject = buildJsonObject {
        put("savedAtEpochMs", JsonPrimitive(s.savedAtEpochMs))
        put("responseTimeText", JsonPrimitive(s.responseTimeLabel))
        put("responseSizeText", JsonPrimitive(s.responseSizeLabel))
        put("rightTabIndex", JsonPrimitive(s.rightTabIndex))
        put("isSseResponse", JsonPrimitive(s.isSseResponse))
        put("requestHeadersFullText", JsonPrimitive(s.requestHeadersFullText))
        put("requestBodyText", JsonPrimitive(s.requestBody))
    }

    /** 从 HAR 或旧版 CachedHttpResponse JSON 还原右侧响应区展示数据。 */
    fun parseToCachedResponse(text: String): CachedHttpResponse? {
        val root = runCatching { responseJsonLegacy.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val log = root["log"]?.jsonObject
        if (log != null) {
            val apiX = log["_apiX"]?.jsonObject
            val entries = log["entries"]?.jsonArray ?: return null
            val entry = entries.firstOrNull()?.jsonObject ?: return null
            val request = entry["request"]?.jsonObject
            val requestPlain = request?.let { requestPlainTextFromHarRequest(it) } ?: ""
            val response = entry["response"]?.jsonObject ?: return null
            val status = response["status"]?.jsonPrimitive?.intOrNull ?: 0
            val content = response["content"]?.jsonObject
            val bodyText = content?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
            val bodyLines = if (bodyText.isEmpty()) emptyList() else bodyText.lines()
            val headerArr = response["headers"]?.jsonArray ?: JsonArray(emptyList())
            val headerLines = mutableListOf<String>()
            for (el in headerArr) {
                val ho = el.jsonObject
                val n = ho["name"]?.jsonPrimitive?.contentOrNull ?: continue
                val v = ho["value"]?.jsonPrimitive?.contentOrNull ?: ""
                headerLines += "$n: $v"
            }
            val savedAt = apiX?.get("savedAtEpochMs")?.jsonPrimitive?.longOrNull
                ?: System.currentTimeMillis()
            val timeLabel = apiX?.get("responseTimeText")?.jsonPrimitive?.contentOrNull ?: ""
            val sizeLabel = apiX?.get("responseSizeText")?.jsonPrimitive?.contentOrNull ?: ""
            val tab = apiX?.get("rightTabIndex")?.jsonPrimitive?.intOrNull ?: 0
            val sse = apiX?.get("isSseResponse")?.jsonPrimitive?.booleanOrNull ?: false
            return CachedHttpResponse(
                savedAtEpochMs = savedAt,
                statusCodeText = if (status > 0) status.toString() else "",
                responseTimeText = timeLabel,
                responseSizeText = sizeLabel,
                responseBodyLines = bodyLines,
                responseHeaderLines = headerLines,
                isSseResponse = sse,
                rightTabIndex = tab,
                requestPlainText = requestPlain,
            )
        }
        return runCatching {
            responseJsonLegacy.decodeFromString(CachedHttpResponse.serializer(), text)
        }.getOrNull()
    }

    private val responseJsonLegacy = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
    }
}
