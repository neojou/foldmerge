package com.neojou.foldmerge.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.neojou.foldmerge.AppTheme
import java.awt.Dimension

@Composable
actual fun HostCompareWindow(
    visible: Boolean,
    title: String,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (visible) {
        CompareWindowFrame(
            title = title,
            onCloseRequest = onCloseRequest,
            content = content,
        )
    }
}

@Composable
private fun CompareWindowFrame(
    title: String,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
    Window(
        onCloseRequest = onCloseRequest,
        title = title,
        state = windowState,
    ) {
        window.minimumSize = Dimension(960, 640)
        AppTheme(content)
    }
}
