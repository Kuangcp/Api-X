package mcp

data class McpArgumentFormField(
    val name: String,
    val type: String,
    val required: Boolean = false,
    val description: String = "",
    val defaultValue: String? = null,
    val enumValues: List<String> = emptyList(),
)
