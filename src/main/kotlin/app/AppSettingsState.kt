package app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AppSettingsState {
    var appSettings by mutableStateOf(AppSettingsStore.snapshot())
}
