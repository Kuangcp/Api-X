package app.state

import app.settings.EnvironmentStore
import app.settings.EnvironmentsState
import app.settings.withDefaultActiveWhenSingle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class EnvironmentState(private val store: EnvironmentStore) {
    var environmentsState by mutableStateOf(
        withDefaultActiveWhenSingle(store.snapshot())
    )

    fun commit(newState: EnvironmentsState) {
        store.replace(newState)
        environmentsState = store.snapshot()
    }
}
