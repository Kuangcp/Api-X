package app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ToastState {
    var toastMessage by mutableStateOf<String?>(null)

    fun show(message: String) {
        toastMessage = message
    }

    fun clear() {
        toastMessage = null
    }
}
