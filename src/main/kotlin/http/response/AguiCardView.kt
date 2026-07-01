package http.response

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import http.ExchangeFontMetrics

@Composable
internal fun AguiStreamingText(
    runState: AguiRunState,
    exchangeMetrics: ExchangeFontMetrics,
) {
    val lines = remember(runState) {
        buildList {
            for (msg in runState.messages) {
                when (msg) {
                    is AguiTextMessage -> {
                        add("[${msg.role}]")
                        msg.text.lines().forEach { add(it) }
                    }
                    is AguiToolCallMessage -> {
                        val name = msg.toolName ?: "tool"
                        add("[Tool: $name]")
                        if (msg.args.isNotBlank()) add("  args: ${msg.args.take(200)}")
                        if (msg.result != null) add("  result: ${msg.result.take(200)}")
                    }
                    is AguiReasoningBlock -> {
                        if (msg.text.isNotBlank()) {
                            add("[reasoning]")
                            msg.text.lines().forEach { add("  $it") }
                        }
                    }
                }
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
            itemsIndexed(lines) { _, line ->
                Text(
                    line,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = exchangeMetrics.body,
                        color = MaterialTheme.colors.onSurface,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun AguiCardView(
    runState: AguiRunState,
    exchangeMetrics: ExchangeFontMetrics,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item("status") {
            AguiRunStatusBar(runState, exchangeMetrics)
        }
        items(runState.messages.indices.toList(), key = { it }) { idx ->
            val msg = runState.messages[idx]
            when (msg) {
                is AguiTextMessage -> AguiTextMessageCard(msg, exchangeMetrics)
                is AguiToolCallMessage -> AguiToolCallCard(msg, exchangeMetrics)
                is AguiReasoningBlock -> AguiReasoningCard(msg, exchangeMetrics)
            }
        }
        if (runState.messages.isEmpty()) {
            item("empty") {
                Box(modifier = Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        "No model output extracted",
                        fontSize = exchangeMetrics.body,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    )
                }
            }
        }
    }
}

@Composable
private fun AguiRunStatusBar(
    runState: AguiRunState,
    exchangeMetrics: ExchangeFontMetrics,
) {
    val (color, label) = when (runState.status) {
        AguiRunStatus.Running -> MaterialTheme.colors.primary to "Running"
        AguiRunStatus.Finished -> MaterialTheme.colors.primary to "Finished"
        AguiRunStatus.Error -> MaterialTheme.colors.error to "Error"
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = exchangeMetrics.tab, color = color, fontWeight = FontWeight.Medium)
        runState.runId?.let {
            Spacer(Modifier.width(12.dp))
            Text("run: $it", fontSize = exchangeMetrics.tiny, color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium))
        }
        if (runState.status == AguiRunStatus.Error && runState.errorMessage != null) {
            Spacer(Modifier.width(12.dp))
            Text(runState.errorMessage, fontSize = exchangeMetrics.tiny, color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium))
        }
    }
}

@Composable
private fun AguiTextMessageCard(
    message: AguiTextMessage,
    exchangeMetrics: ExchangeFontMetrics,
) {
    val bgColor = if (message.role == "user") {
        MaterialTheme.colors.primary.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colors.onSurface.copy(alpha = 0.05f)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.role == "user") Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bgColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionContainer {
                Text(
                    message.text,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = exchangeMetrics.body,
                        lineHeight = exchangeMetrics.body * 1.35f,
                        color = MaterialTheme.colors.onSurface,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AguiToolCallCard(
    toolCall: AguiToolCallMessage,
    exchangeMetrics: ExchangeFontMetrics,
) {
    val label = toolCall.toolName ?: "Tool call"
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.05f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape)
                        .background(if (toolCall.result != null) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.3f)))
                    Spacer(Modifier.width(6.dp))
                    Text("Tool: $label", fontSize = exchangeMetrics.body, fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (toolCall.isComplete) "(done)" else if (toolCall.argsComplete) "(args complete)" else "(streaming...)",
                        fontSize = exchangeMetrics.tiny,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    )
                }
            }
            Text(
                if (expanded) "▲" else "▼",
                fontSize = exchangeMetrics.tab,
                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
                if (toolCall.args.isNotBlank()) {
                    Text("Arguments:", fontSize = exchangeMetrics.tiny, fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        toolCall.args,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = exchangeMetrics.body, color = MaterialTheme.colors.onSurface),
                    )
                }
                if (toolCall.result != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Result:", fontSize = exchangeMetrics.tiny, fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        toolCall.result,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = exchangeMetrics.body, color = MaterialTheme.colors.onSurface),
                    )
                }
            }
        }
    }
}

@Composable
private fun AguiReasoningCard(
    reasoning: AguiReasoningBlock,
    exchangeMetrics: ExchangeFontMetrics,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.03f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Reasoning", fontSize = exchangeMetrics.body, fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.weight(1f))
            if (!reasoning.isComplete) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colors.primary.copy(alpha = 0.6f)))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                if (expanded) "▲" else "▼",
                fontSize = exchangeMetrics.tab,
                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            SelectionContainer {
                Text(
                    reasoning.text.ifBlank { "(empty)" },
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = exchangeMetrics.body,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    ),
                )
            }
        }
    }
}
