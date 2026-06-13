package app.state

import app.settings.AppSettings
import app.settings.AppSettingsStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AppSettingsState(private val store: AppSettingsStore) {
    var appSettings by mutableStateOf(store.snapshot())

    fun replace(newSettings: AppSettings) {
        store.replace(newSettings)
        appSettings = newSettings
    }
}
