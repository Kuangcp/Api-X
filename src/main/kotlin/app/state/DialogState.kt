package app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import tree.TreeSelection

class DialogState {
    var showSettings by mutableStateOf(false)
    var showEnvironmentManager by mutableStateOf(false)
    var showGlobalSearch by mutableStateOf(false)
    var showCollectionSettings by mutableStateOf(false)
    var collectionSettingsTarget by mutableStateOf<TreeSelection?>(null)
}
