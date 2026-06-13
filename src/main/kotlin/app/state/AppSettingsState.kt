package app.state

import app.settings.AppSettingsStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AppSettingsState {
    var appSettings by mutableStateOf(AppSettingsStore.snapshot())
}
