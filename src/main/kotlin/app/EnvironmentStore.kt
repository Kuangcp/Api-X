package app

import db.AppPaths
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference

private val envJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object EnvironmentStore {
    private val ref = AtomicReference(loadRaw().normalized())

    fun snapshot(): EnvironmentsState = ref.get()

    fun replace(state: EnvironmentsState) {
        val n = state.normalized()
        ref.set(n)
        save(n)
    }

    private fun path() = AppPaths.environmentsJsonPath()

    private fun loadRaw(): EnvironmentsState {
        val file = path()
        if (!Files.isRegularFile(file)) return EnvironmentsState()
        return runCatching {
            val text = Files.readString(file)
            envJson.decodeFromString<EnvironmentsState>(text)
        }.getOrElse { EnvironmentsState() }
    }

    private fun save(state: EnvironmentsState) {
        runCatching {
            Files.createDirectories(path().parent)
            Files.writeString(path(), envJson.encodeToString(state.normalized()))
        }
    }
}
