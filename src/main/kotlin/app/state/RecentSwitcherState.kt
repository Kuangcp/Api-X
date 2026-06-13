package app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class RecentSwitcherState {
    var active by mutableStateOf(false)
    var ids by mutableStateOf<List<String>>(emptyList())
    var index by mutableStateOf(0)
}
