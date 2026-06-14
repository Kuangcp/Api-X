package http.response

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import http.ExchangeFontMetrics

private val TAB_LABELS = listOf("Body", "Headers", "Request")

@Composable
internal fun ResponseTabStrip(
    exchangeMetrics: ExchangeFontMetrics,
    rightTabIndex: Int,
    onRightTabIndexChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(exchangeMetrics.editorTabStripHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
    ) {
        TAB_LABELS.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (rightTabIndex == index) MaterialTheme.colors.primary.copy(alpha = 0.14f)
                        else Color.Transparent
                    )
                    .clickable { onRightTabIndexChange(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = exchangeMetrics.tab,
                    color = if (rightTabIndex == index) MaterialTheme.colors.onSurface
                    else MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                )
            }
        }
    }
}
