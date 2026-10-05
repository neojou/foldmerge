package com.neojou.foldmerge.ui

import androidx.compose.runtime.Composable

/**
 * Desktop window around the compare pane.
 *
 * The pane itself stays in common code. Only the window host is platform-specific.
 */
@Composable
expect fun HostCompareWindow(
    visible: Boolean,
    title: String,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit,
)
