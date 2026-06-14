package app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.core.GlobalExceptionHandler

@Composable
fun ErrorBoundary(
    content: @Composable () -> Unit,
) {
    var capturedError by remember { mutableStateOf<Throwable?>(null) }

    DisposableEffect(Unit) {
        val previousHandler = GlobalExceptionHandler.onErrorCaptured
        GlobalExceptionHandler.onErrorCaptured = { throwable ->
            capturedError = throwable
        }
        onDispose {
            GlobalExceptionHandler.onErrorCaptured = previousHandler
        }
    }

    val error = capturedError ?: GlobalExceptionHandler.lastError

    if (error == null) {
        content()
    } else {
        ErrorScreen(error = error)
    }
}

@Composable
private fun ErrorScreen(error: Throwable) {
    val surfaceColor = MaterialTheme.colors.surface
    val errorColor = MaterialTheme.colors.error
    val onSurfaceColor = MaterialTheme.colors.onSurface

    Box(
        modifier = Modifier.fillMaxSize().background(surfaceColor).padding(24.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "应用异常",
                style = MaterialTheme.typography.h6.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                ),
                color = errorColor,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = error.message ?: error.javaClass.simpleName,
                style = MaterialTheme.typography.body1,
                color = onSurfaceColor,
            )

            Spacer(modifier = Modifier.height(16.dp))

            val stackTrace = remember(error) {
                error.stackTraceToString().take(4000)
            }

            Text(
                text = "堆栈信息",
                style = MaterialTheme.typography.subtitle2.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = onSurfaceColor.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = surfaceColor.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp),
                    )
                    .padding(8.dp),
            ) {
                Text(
                    text = stackTrace,
                    style = MaterialTheme.typography.body2.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    ),
                    color = onSurfaceColor.copy(alpha = 0.6f),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "请尝试重启应用。如果问题持续出现，请将上述信息反馈给开发者。",
                style = MaterialTheme.typography.body2,
                color = onSurfaceColor.copy(alpha = 0.5f),
            )
        }
    }
}
