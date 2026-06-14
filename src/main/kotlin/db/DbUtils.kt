package db

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
