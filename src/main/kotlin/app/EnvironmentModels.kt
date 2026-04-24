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

/**
 * 与 Pull 同步：按环境 [Environment.id] 做 upsert，不删除 [base] 中已有而 [overlay] 没有的环境。
 */
fun mergeEnvironmentsStateNoDelete(base: EnvironmentsState, overlay: EnvironmentsState): EnvironmentsState {
    val byId = base.environments.associateBy { it.id }.toMutableMap()
    for (e in overlay.environments) {
        byId[e.id] = e
    }
    val baseOrder = base.environments.map { it.id }
    val onlyInOverlay = overlay.environments.map { it.id }.filter { it !in baseOrder.toSet() }
    val orderedIds = baseOrder + onlyInOverlay
    val envs = orderedIds.mapNotNull { byId[it] }
    val active = when {
        overlay.activeEnvironmentId != null && overlay.activeEnvironmentId in byId -> overlay.activeEnvironmentId
        base.activeEnvironmentId != null && base.activeEnvironmentId in byId -> base.activeEnvironmentId
        else -> base.activeEnvironmentId
    }
    return EnvironmentsState(
        version = maxOf(base.version, overlay.version),
        activeEnvironmentId = active,
        environments = envs,
    ).normalized()
}
