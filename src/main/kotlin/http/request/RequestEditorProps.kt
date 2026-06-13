package http.request

import app.settings.EnvVariable
import tree.PostmanAuth

/**
 * 主窗口请求编辑区所需状态与回调；由 [app.RequestEditorState] 持有数据，
 * 此处为纯快照 + 无状态回调。
 */
data class RequestEditorProps(
    val editorRequestId: String?,
    val isLoading: Boolean,
    val method: String,
    val methodMenuExpanded: Boolean,
    val onMethodMenuExpandedChange: (Boolean) -> Unit,
    val onMethodSelected: (String) -> Unit,
    val url: String,
    val onUrlChange: (String) -> Unit,
    val onSendOrCancel: () -> Unit,
    val leftTabIndex: Int,
    val onLeftTabIndexChange: (Int) -> Unit,
    val bodyText: String,
    val onBodyTextChange: (String) -> Unit,
    val headersText: String,
    val onHeadersTextChange: (String) -> Unit,
    val paramsText: String,
    val onParamsTextChange: (String) -> Unit,
    val auth: PostmanAuth?,
    val onAuthChange: (PostmanAuth?) -> Unit,
    val envVars: List<EnvVariable> = emptyList(),
)
