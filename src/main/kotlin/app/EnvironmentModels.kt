package app

import kotlinx.serialization.Serializable

@Serializable
data class EnvVariable(
    val key: String = "",
    val value: String = "",
)

@Serializable
data class Environment(
    val id: String,
    val name: String,
    val variables: List<EnvVariable> = emptyList(),
)

@Serializable
data class EnvironmentsState(
    val version: Int = 1,
    val activeEnvironmentId: String? = null,
    val environments: List<Environment> = emptyList(),
) {
    fun normalized(): EnvironmentsState {
        val ids = environments.map { it.id }.toSet()
        val active = activeEnvironmentId?.takeIf { it in ids }
        return copy(activeEnvironmentId = active)
    }

    fun activeEnvironment(): Environment? =
        activeEnvironmentId?.let { id -> environments.find { it.id == id } }
}

/**
 * 磁盘上未写 active 且仅有一个环境时，用于启动后默认启用；
 * 不并入 [EnvironmentsState.normalized]，以免在用户显式选择「无环境」后被误改回。
 */
fun withDefaultActiveWhenSingle(state: EnvironmentsState): EnvironmentsState {
    if (state.activeEnvironmentId != null) return state
    if (state.environments.size != 1) return state
    return state.copy(activeEnvironmentId = state.environments.first().id)
}
