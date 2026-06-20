package app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import db.McpDraftStore
import mcp.McpPromptSummary
import mcp.McpResourceSummary
import mcp.McpToolSummary
import mcp.buildMcpPromptGetTemplate
import mcp.buildMcpResourceReadTemplate
import mcp.buildMcpToolCallTemplate

sealed class McpSelection(
    val method: String,
    val identifier: String,
) {
    class Tool(val tool: McpToolSummary) : McpSelection("tools/call", tool.name)
    class Resource(val resource: McpResourceSummary) : McpSelection("resources/read", resource.uri)
    class Prompt(val prompt: McpPromptSummary) : McpSelection("prompts/get", prompt.name)
}

class McpSelectionState {
    var selectedRequestId by mutableStateOf<String?>(null)
        private set
    var selectedItem by mutableStateOf<McpSelection?>(null)
        private set

    fun rememberCurrentDraft(bodyText: String) {
        val requestId = selectedRequestId ?: return
        val item = selectedItem ?: return
        McpDraftStore.saveDraft(requestId, item.method, item.identifier, bodyText)
    }

    fun selectTool(requestId: String, tool: McpToolSummary): String {
        val item = McpSelection.Tool(tool)
        select(requestId, item)
        return loadDraft(requestId, item) ?: buildMcpToolCallTemplate(tool)
    }

    fun selectResource(requestId: String, resource: McpResourceSummary): String {
        val item = McpSelection.Resource(resource)
        select(requestId, item)
        return loadDraft(requestId, item) ?: buildMcpResourceReadTemplate(resource)
    }

    fun selectPrompt(requestId: String, prompt: McpPromptSummary): String {
        val item = McpSelection.Prompt(prompt)
        select(requestId, item)
        return loadDraft(requestId, item) ?: buildMcpPromptGetTemplate(prompt)
    }

    private fun select(requestId: String, item: McpSelection) {
        selectedRequestId = requestId
        selectedItem = item
    }

    private fun loadDraft(requestId: String, item: McpSelection): String? {
        return McpDraftStore.loadDraft(requestId, item.method, item.identifier)
    }
}