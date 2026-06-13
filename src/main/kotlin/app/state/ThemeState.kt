package app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ThemeState {
    var isDarkTheme by mutableStateOf(true)
    var jsonSyntaxHighlightEnabled by mutableStateOf(true)
}
