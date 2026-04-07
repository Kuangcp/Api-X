package app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.Typography
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import java.util.UUID

@Composable
fun EnvironmentManagerDialogWindow(
    visible: Boolean,
    isDarkTheme: Boolean,
    appBackgroundHex: String,
    typographyBase: Typography,
    initial: EnvironmentsState,
    onCloseRequest: () -> Unit,
    onSaved: (EnvironmentsState) -> Unit,
) {
    if (!visible) return
    DialogWindow(
        onCloseRequest = onCloseRequest,
        title = "环境变量",
        state = rememberDialogState(width = 720.dp, height = 520.dp),
    ) {
        val colors = appMaterialColors(isDarkTheme, appBackgroundHex)
        MaterialTheme(
            colors = colors,
            typography = typographyBase,
        ) {
            EnvironmentManagerDialogBody(
                initial = initial,
                onCancel = onCloseRequest,
                onSave = {
                    onSaved(it)
                    onCloseRequest()
                },
            )
        }
    }
}

@Composable
private fun EnvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        colors = TextFieldDefaults.outlinedTextFieldColors(
            textColor = MaterialTheme.colors.onSurface,
            cursorColor = MaterialTheme.colors.primary,
            focusedBorderColor = MaterialTheme.colors.primary.copy(alpha = ContentAlpha.high),
            unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled),
            placeholderColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            focusedLabelColor = MaterialTheme.colors.primary.copy(alpha = ContentAlpha.high),
            unfocusedLabelColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
        )
    )
}

@Composable
private fun EnvironmentManagerDialogBody(
    initial: EnvironmentsState,
    onCancel: () -> Unit,
    onSave: (EnvironmentsState) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial.normalized()) }
    var selectedId by remember(initial) {
        mutableStateOf(
            initial.activeEnvironmentId
                ?: initial.environments.firstOrNull()?.id,
        )
    }
    val variableRows = remember { mutableStateListOf<EnvVariable>() }
    var envNameEdit by remember { mutableStateOf("") }

    LaunchedEffect(selectedId) {
        variableRows.clear()
        val id = selectedId
        if (id == null) {
            envNameEdit = ""
            return@LaunchedEffect
        }
        val env = draft.environments.find { it.id == id }
        if (env == null) {
            envNameEdit = ""
            return@LaunchedEffect
        }
        variableRows.addAll(env.variables)
        envNameEdit = env.name
    }

    fun persistCurrentEnvIntoDraft(): EnvironmentsState {
        val sid = selectedId ?: return draft
        val idx = draft.environments.indexOfFirst { it.id == sid }
        if (idx < 0) return draft
        val old = draft.environments[idx]
        val updated = old.copy(
            name = envNameEdit.trim().ifBlank { old.name },
            variables = variableRows.toList(),
        )
        val list = draft.environments.toMutableList()
        list[idx] = updated
        return draft.copy(environments = list)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(16.dp),
    ) {
        Text(
            "使用 {{变量名}} 作为占位符（如 {{host}}）。发送请求时按当前选中的环境替换；请求正文仍保存未解析的原文。",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colors.surface)
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = {
                        draft = persistCurrentEnvIntoDraft()
                        val id = UUID.randomUUID().toString()
                        val n = draft.environments.size + 1
                        draft = draft.copy(
                            environments = draft.environments + Environment(
                                id = id,
                                name = "环境 $n",
                                variables = emptyList(),
                            ),
                            activeEnvironmentId = id,
                        )
                        selectedId = id
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("新建环境")
                }
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    for (env in draft.environments) {
                        val sel = env.id == selectedId
                        TextButton(
                            onClick = {
                                draft = persistCurrentEnvIntoDraft()
                                selectedId = env.id
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                env.name.ifBlank { "(未命名)" },
                                color = if (sel) {
                                    MaterialTheme.colors.primary
                                } else {
                                    MaterialTheme.colors.onSurface
                                },
                                maxLines = 2,
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        val sid = selectedId ?: return@TextButton
                        draft = persistCurrentEnvIntoDraft()
                        val nextList = draft.environments.filter { it.id != sid }
                        var nextActive = draft.activeEnvironmentId
                        if (nextActive == sid) nextActive = nextList.firstOrNull()?.id
                        draft = draft.copy(environments = nextList, activeEnvironmentId = nextActive)
                        selectedId = nextList.firstOrNull()?.id
                    },
                    enabled = selectedId != null && draft.environments.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("删除当前环境")
                }
            }
            Divider(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (selectedId == null) {
                    Text(
                        "请新建或选择一个环境",
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled),
                    )
                } else {
                    EnvTextField(
                        value = envNameEdit,
                        onValueChange = { envNameEdit = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("环境名称") },
                        singleLine = true,
                    )
                    Text(
                        "变量",
                        style = MaterialTheme.typography.subtitle2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (i in variableRows.indices) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                EnvTextField(
                                    value = variableRows[i].key,
                                    onValueChange = { v ->
                                        variableRows[i] = variableRows[i].copy(key = v)
                                    },
                                    modifier = Modifier.weight(0.42f),
                                    label = { Text("变量名") },
                                    placeholder = { Text("host") },
                                    singleLine = true,
                                )
                                EnvTextField(
                                    value = variableRows[i].value,
                                    onValueChange = { v ->
                                        variableRows[i] = variableRows[i].copy(value = v)
                                    },
                                    modifier = Modifier.weight(0.58f),
                                    label = { Text("当前值") },
                                    placeholder = { Text("https://api.example.com") },
                                    singleLine = true,
                                )
                                TextButton(onClick = { variableRows.removeAt(i) }) {
                                    Text("删除")
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = { variableRows.add(EnvVariable()) },
                        modifier = Modifier.align(Alignment.Start),
                    ) {
                        Text("添加变量")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
            TextButton(
                onClick = {
                    val merged = persistCurrentEnvIntoDraft().normalized()
                    onSave(merged)
                },
            ) {
                Text("保存")
            }
        }
    }
}
