package http.response

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import http.ExchangeFontMetrics
import kotlinx.coroutines.delay

@Composable
internal fun ResponseSearchBar(
    exchangeMetrics: ExchangeFontMetrics,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    currentMatchIndex: Int,
    totalMatches: Int,
    onNavigateMatch: (Int) -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
) {
    LaunchedEffect(Unit) {
        delay(50)
        focusRequester.requestFocus()
    }

    val searchTint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
    val searchTintDim = searchTint.copy(alpha = 0.3f)
    val searchBg = MaterialTheme.colors.onSurface.copy(alpha = 0.06f)
    val searchBorder = MaterialTheme.colors.onSurface.copy(alpha = 0.2f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(searchBg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { onSearchQueryChange(it) },
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            placeholder = {
                Text(
                    "搜索...",
                    fontSize = exchangeMetrics.tab,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                )
            },
            singleLine = true,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = MaterialTheme.colors.onSurface,
                cursorColor = MaterialTheme.colors.primary,
                focusedBorderColor = MaterialTheme.colors.primary.copy(alpha = 0.6f),
                unfocusedBorderColor = searchBorder,
                backgroundColor = Color.Transparent,
            ),
        )
        Text(
            text = if (totalMatches > 0) "${currentMatchIndex + 1}/$totalMatches" else "0/0",
            fontSize = exchangeMetrics.tab,
            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        IconButton(
            onClick = { onNavigateMatch(-1) },
            modifier = Modifier.size(28.dp),
            enabled = totalMatches > 0,
        ) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "上一个匹配",
                modifier = Modifier.size(18.dp),
                tint = if (totalMatches > 0) searchTint else searchTintDim,
            )
        }
        IconButton(
            onClick = { onNavigateMatch(1) },
            modifier = Modifier.size(28.dp),
            enabled = totalMatches > 0,
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "下一个匹配",
                modifier = Modifier.size(18.dp),
                tint = if (totalMatches > 0) searchTint else searchTintDim,
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭搜索",
                modifier = Modifier.size(16.dp),
                tint = searchTint,
            )
        }
    }
}
