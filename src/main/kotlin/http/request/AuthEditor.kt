package http.request

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import http.ExchangeFontMetrics
import tree.AuthProperty
import tree.PostmanAuth
import tree.findValue

@Composable
fun AuthEditor(
    auth: PostmanAuth?,
    onAuthChange: (PostmanAuth?) -> Unit,
    exchangeMetrics: ExchangeFontMetrics,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    var showTypeMenu by remember { mutableStateOf(false) }
    val currentType = auth?.type ?: "noauth"
    val typeLabel = when (currentType) {
        "inherit" -> "Inherit from parent"
        "basic" -> "Basic Auth"
        "bearer" -> "Bearer Token"
        "apikey" -> "API Key"
        else -> "No Auth"
    }

    Column(modifier = modifier.padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Type: ", fontSize = exchangeMetrics.tab, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
            Box {
                TextButton(onClick = { showTypeMenu = true }) {
                    Text(typeLabel, fontSize = exchangeMetrics.tab)
                    Icon(Icons.Filled.ArrowDropDown, null)
                }
                DropdownMenu(expanded = showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                    listOf(
                        "inherit" to "Inherit from parent",
                        "noauth" to "No Auth",
                        "basic" to "Basic Auth",
                        "bearer" to "Bearer Token",
                        "apikey" to "API Key"
                    ).forEach { (type, label) ->
                        DropdownMenuItem(onClick = {
                            showTypeMenu = false
                            if (type == "noauth") onAuthChange(null)
                            else if (type == "inherit") onAuthChange(PostmanAuth(type = "inherit"))
                            else onAuthChange(PostmanAuth(type = type))
                        }) {
                            Text(label, fontSize = exchangeMetrics.tab, color = MaterialTheme.colors.onSurface)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (currentType) {
            "basic" -> {
                AuthField(
                    label = "Username",
                    value = auth?.basic.findValue("username") ?: "",
                    onValueChange = { nv ->
                        val props = (auth?.basic?.filter { it.key != "username" } ?: emptyList()) + AuthProperty("username", nv, "string")
                        onAuthChange(auth?.copy(basic = props))
                    },
                    exchangeMetrics = exchangeMetrics,
                    isDarkTheme = isDarkTheme,
                )
                AuthField(
                    label = "Password",
                    value = auth?.basic.findValue("password") ?: "",
                    isPassword = true,
                    onValueChange = { nv ->
                        val props = (auth?.basic?.filter { it.key != "password" } ?: emptyList()) + AuthProperty("password", nv, "string")
                        onAuthChange(auth?.copy(basic = props))
                    },
                    exchangeMetrics = exchangeMetrics,
                    isDarkTheme = isDarkTheme,
                )
            }
            "bearer" -> {
                AuthField(
                    label = "Token",
                    value = auth?.bearer.findValue("token") ?: "",
                    onValueChange = { nv ->
                        val props = (auth?.bearer?.filter { it.key != "token" } ?: emptyList()) + AuthProperty("token", nv, "string")
                        onAuthChange(auth?.copy(bearer = props))
                    },
                    exchangeMetrics = exchangeMetrics,
                    isDarkTheme = isDarkTheme,
                )
            }
            "apikey" -> {
                AuthField(
                    label = "Key",
                    value = auth?.apikey.findValue("key") ?: "",
                    onValueChange = { nv ->
                        val props = (auth?.apikey?.filter { it.key != "key" } ?: emptyList()) + AuthProperty("key", nv, "string")
                        onAuthChange(auth?.copy(apikey = props))
                    },
                    exchangeMetrics = exchangeMetrics,
                    isDarkTheme = isDarkTheme,
                )
                AuthField(
                    label = "Value",
                    value = auth?.apikey.findValue("value") ?: "",
                    onValueChange = { nv ->
                        val props = (auth?.apikey?.filter { it.key != "value" } ?: emptyList()) + AuthProperty("value", nv, "string")
                        onAuthChange(auth?.copy(apikey = props))
                    },
                    exchangeMetrics = exchangeMetrics,
                    isDarkTheme = isDarkTheme,
                )
                AuthField(
                    label = "Add to",
                    value = auth?.apikey.findValue("in") ?: "header",
                    onValueChange = { nv ->
                        val props = (auth?.apikey?.filter { it.key != "in" } ?: emptyList()) + AuthProperty("in", nv, "string")
                        onAuthChange(auth?.copy(apikey = props))
                    },
                    exchangeMetrics = exchangeMetrics,
                    isDarkTheme = isDarkTheme,
                    hint = "header or query"
                )
            }
            "inherit" -> {
                Text(
                    "This request will use the authentication settings from its parent collection or folder.",
                    fontSize = exchangeMetrics.body,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    exchangeMetrics: ExchangeFontMetrics,
    isDarkTheme: Boolean,
    isPassword: Boolean = false,
    hint: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = exchangeMetrics.tiny, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        val cursorBrush = if (isDarkTheme) SolidColor(Color.White) else SolidColor(Color.Black)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            cursorBrush = cursorBrush,
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = MaterialTheme.typography.body2.copy(fontSize = exchangeMetrics.body, color = MaterialTheme.colors.onSurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                .padding(8.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty() && hint != null) {
                        Text(hint, fontSize = exchangeMetrics.body, color = MaterialTheme.colors.onSurface.copy(alpha = 0.3f))
                    }
                    innerTextField()
                }
            }
        )
    }
}
