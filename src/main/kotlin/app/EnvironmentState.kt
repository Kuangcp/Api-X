package app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class EnvironmentState {
    var environmentsState by mutableStateOf(
        withDefaultActiveWhenSingle(EnvironmentStore.snapshot())
    )

    fun commit(newState: EnvironmentsState) {
        EnvironmentStore.replace(newState)
        environmentsState = EnvironmentStore.snapshot()
    }
}
