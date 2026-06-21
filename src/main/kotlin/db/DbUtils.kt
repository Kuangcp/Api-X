package db

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import tree.PostmanAuth
import java.util.UUID

internal fun newId(): String = UUID.randomUUID().toString()

internal fun mergeAuthIntoMetaJson(oldMetaJson: String, auth: PostmanAuth?): String {
    val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    val meta = try {
        json.decodeFromString<JsonObject>(oldMetaJson.ifBlank { "{}" }).toMutableMap()
    } catch (e: Exception) {
        mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
    }
    if (auth == null) {
        meta.remove("auth")
    } else {
        meta["auth"] = json.encodeToJsonElement(auth)
    }
    return json.encodeToString(JsonObject(meta))
}

internal fun extractAuthFromMetaJson(metaJson: String): PostmanAuth? {
    val json = Json { ignoreUnknownKeys = true }
    return try {
        val meta = json.decodeFromString<JsonObject>(metaJson.ifBlank { "{}" })
        meta["auth"]?.let { json.decodeFromJsonElement(PostmanAuth.serializer(), it) }
    } catch (e: Exception) {
        null
    }
}

internal fun mergeOpenApiSourceIntoMetaJson(oldMetaJson: String, sourceUrl: String?): String {
    val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    val meta = try {
        json.decodeFromString<JsonObject>(oldMetaJson.ifBlank { "{}" }).toMutableMap()
    } catch (e: Exception) {
        mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
    }
    if (sourceUrl.isNullOrBlank()) {
        meta.remove("openapi")
    } else {
        val oldOpenApi = (meta["openapi"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        oldOpenApi["sourceUrl"] = kotlinx.serialization.json.JsonPrimitive(sourceUrl.trim())
        oldOpenApi["refreshedAt"] = kotlinx.serialization.json.JsonPrimitive(System.currentTimeMillis())
        meta["openapi"] = JsonObject(oldOpenApi)
    }
    return json.encodeToString(JsonObject(meta))
}

internal fun extractOpenApiSourceFromMetaJson(metaJson: String): String? {
    val json = Json { ignoreUnknownKeys = true }
    return try {
        val meta = json.decodeFromString<JsonObject>(metaJson.ifBlank { "{}" })
        meta["openapi"]?.jsonObject?.get("sourceUrl")?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }
}