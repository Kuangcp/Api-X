package http

import app.EnvVariable
import app.EnvironmentsState

private val PLACEHOLDER_REGEX = Regex("""\{\{\s*([^{}]+?)\s*\}\}""")

/** 将 `variables` 转为替换映射；同名键以后者为准（列表靠后的覆盖靠前的）。 */
fun substitutionMap(variables: List<EnvVariable>): Map<String, String> {
    val m = LinkedHashMap<String, String>()
    for (v in variables) {
        val k = v.key.trim()
        if (k.isNotEmpty()) m[k] = v.value
    }
    return m
}

fun EnvironmentsState.substitutionMapForActive(): Map<String, String> {
    val env = activeEnvironment() ?: return emptyMap()
    // println("Active environment: ${env.name}, variables: ${env.variables.size}")
    return substitutionMap(env.variables)
}

/**
 * 将文本中的 `{{name}}`（允许括号内两侧空格）替换为对应变量值。
 * 支持值中再含占位符，最多迭代 [maxPasses] 次；未知变量保持原文。
 */
fun applyEnvironmentVariables(text: String, vars: Map<String, String>, maxPasses: Int = 32): String {
    if (text.isEmpty() || vars.isEmpty()) return text
    var result = text
    repeat(maxPasses) {
        val next = PLACEHOLDER_REGEX.replace(result) { m ->
            val name = m.groupValues[1].trim()
            if (name.isEmpty()) return@replace m.value
            val value = vars[name]
            if (value != null) {
                // println("Replacing {{ $name }} with $value")
                value
            } else {
                m.value
            }
        }
        if (next == result) return result
        result = next
    }
    return result
}
