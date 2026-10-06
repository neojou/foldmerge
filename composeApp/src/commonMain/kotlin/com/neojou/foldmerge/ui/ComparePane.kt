package com.neojou.foldmerge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neojou.foldmerge.CompareDecided
import com.neojou.foldmerge.CompareDecidedSelected
import com.neojou.foldmerge.CompareInk
import com.neojou.foldmerge.CompareLeft
import com.neojou.foldmerge.CompareLeftPad
import com.neojou.foldmerge.CompareLeftSelected
import com.neojou.foldmerge.CompareMuted
import com.neojou.foldmerge.ComparePaper
import com.neojou.foldmerge.CompareRight
import com.neojou.foldmerge.CompareRightPad
import com.neojou.foldmerge.CompareRightSelected
import com.neojou.foldmerge.diff.AlignedRow
import com.neojou.foldmerge.diff.BlockDecision
import com.neojou.foldmerge.diff.Hunk
import com.neojou.foldmerge.diff.HunkKind
import com.neojou.foldmerge.diff.firstRowOf
import com.neojou.foldmerge.diff.hunkTitle
import com.neojou.foldmerge.diff.linesFromEditor
import com.neojou.foldmerge.diff.previewRows
import com.neojou.foldmerge.diff.sideLines
import com.neojou.foldmerge.model.ComparePhase
import com.neojou.foldmerge.model.FoldSession
import com.neojou.foldmerge.model.Side
import kotlinx.coroutines.launch

/**
 * Full side-by-side text of the two selected files.
 *
 * Equal rows share one paper background, one monospace size, and ink. Difference rows use a warm
 * wash on the left and a sage wash on the right. Both columns are rows of one list, so they scroll together.
 */
@Composable
fun ComparePane(
    session: FoldSession,
    leftLabel: String,
    rightLabel: String,
    promptToClose: Boolean,
    onSave: (Side) -> Unit,
    onConfirmClose: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val phase = session.phase) {
            ComparePhase.Idle, ComparePhase.Running -> {
                CompareHeader(
                    leftLabel = leftLabel,
                    rightLabel = rightLabel,
                    summary = "正在比較…",
                    gate = session.saveGateMessage(),
                    canSaveLeft = false,
                    canSaveRight = false,
                    canJump = false,
                    onPrevious = {},
                    onNext = {},
                    onSave = onSave,
                )
                StatusMessage("正在比較…")
            }
            is ComparePhase.NotText -> {
                CompareHeader(
                    leftLabel = leftLabel,
                    rightLabel = rightLabel,
                    summary = phase.detail,
                    gate = session.saveGateMessage(),
                    canSaveLeft = false,
                    canSaveRight = false,
                    canJump = false,
                    onPrevious = {},
                    onNext = {},
                    onSave = onSave,
                )
                StatusMessage(phase.detail)
            }
            is ComparePhase.Broken -> {
                CompareHeader(
                    leftLabel = leftLabel,
                    rightLabel = rightLabel,
                    summary = "比較失敗",
                    gate = session.saveGateMessage(),
                    canSaveLeft = false,
                    canSaveRight = false,
                    canJump = false,
                    onPrevious = {},
                    onNext = {},
                    onSave = onSave,
                )
                StatusMessage("比較失敗：${phase.message}")
            }
            is ComparePhase.Ready -> ReadyCompare(
                session = session,
                phase = phase,
                leftLabel = leftLabel,
                rightLabel = rightLabel,
                onSave = onSave,
            )
        }
    }
    if (promptToClose) {
        AlertDialog(
            onDismissRequest = onKeepEditing,
            title = { Text("有未儲存的變更") },
            text = { Text("這些變更還沒寫入檔案。關閉視窗後，變更仍會保留，可再打開比較。") },
            confirmButton = {
                TextButton(onClick = onConfirmClose) { Text("關閉") }
            },
            dismissButton = {
                TextButton(onClick = onKeepEditing) { Text("繼續編輯") }
            },
        )
    } else if (session.askOverwrite) {
        AlertDialog(
            onDismissRequest = session::dismissOverwrite,
            title = {
                Text(
                    when {
                        session.overwriteLeft && session.overwriteRight -> "左右檔已在外部變更"
                        session.overwriteLeft -> "左檔已在外部變更"
                        else -> "右檔已在外部變更"
                    },
                )
            },
            text = { Text("讀入之後，要寫入的檔案內容已經不同，或檔案已經不在原處。仍要儲存會把目前的決定寫回有變更的檔案。") },
            confirmButton = {
                TextButton(onClick = {
                    val side = if (session.overwriteLeft) Side.Left else Side.Right
                    scope.launch { session.save(side, force = true) }
                }) { Text("仍要儲存") }
            },
            dismissButton = {
                TextButton(onClick = session::dismissOverwrite) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ColumnScope.ReadyCompare(
    session: FoldSession,
    phase: ComparePhase.Ready,
    leftLabel: String,
    rightLabel: String,
    onSave: (Side) -> Unit,
) {
    val rows = remember(phase.hunks, session.decisions) { previewRows(phase.hunks, session.decisions) }
    val diffIndices = remember(phase.hunks) {
        phase.hunks.indices.filter { phase.hunks[it].kind != HunkKind.Equal }
    }
    var selectedHunk by remember(phase.hunks) { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val changeCount = diffIndices.size
    val decidedCount = diffIndices.count { session.decisionAt(it) != BlockDecision.Original }
    val summary = if (changeCount == 0) {
        "兩份內容相同"
    } else {
        "差異 $changeCount 處 · 已決定 $decidedCount 處"
    }

    fun jump(forward: Boolean) {
        if (diffIndices.isEmpty()) return
        val position = diffIndices.indexOf(selectedHunk ?: -1)
        val target = when {
            position < 0 && forward -> diffIndices.first()
            position < 0 -> diffIndices.last()
            forward -> diffIndices.getOrNull(position + 1) ?: return
            else -> diffIndices.getOrNull(position - 1) ?: return
        }
        selectedHunk = target
        val row = firstRowOf(rows, target)
        if (row >= 0) {
            scope.launch { listState.animateScrollToItem(row) }
        }
    }

    CompareHeader(
        leftLabel = leftLabel,
        rightLabel = rightLabel,
        summary = summary,
        gate = session.saveGateMessage(),
        canSaveLeft = session.canSave(Side.Left),
        canSaveRight = session.canSave(Side.Right),
        canJump = diffIndices.isNotEmpty(),
        onPrevious = { jump(forward = false) },
        onNext = { jump(forward = true) },
        onSave = onSave,
    )
    var menu by remember { mutableStateOf<LineMenuState?>(null) }
    var edit by remember { mutableStateOf<LineEditState?>(null) }
    val density = LocalDensity.current
    var listOrigin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) menu = null
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .onGloballyPositioned { coordinates -> listOrigin = coordinates.positionInRoot() },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(rows, key = { index, _ -> index }) { _, row ->
                CompareRow(
                    row = row,
                    selected = selectedHunk == row.hunkIndex,
                    onSelect = { selectedHunk = row.hunkIndex },
                    onLineMenu = { side, lineNumber, text, positionInRoot ->
                        val relative = positionInRoot - listOrigin
                        menu = LineMenuState(
                            side = side,
                            lineNumber = lineNumber,
                            text = text,
                            offset = with(density) { DpOffset(relative.x.toDp(), relative.y.toDp()) },
                        )
                    },
                )
            }
        }
        val open = menu
        if (open != null) {
            // A zero-size anchor at the pointer. The menu opens from that point, not from the list.
            Box(modifier = Modifier.offset(open.offset.x, open.offset.y).size(0.dp)) {
                DropdownMenu(expanded = true, onDismissRequest = { menu = null }) {
                    DropdownMenuItem(
                        text = { Text("新增空行") },
                        onClick = {
                            menu = null
                            session.insertBlankAbove(open.side, open.lineNumber)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("編輯") },
                        onClick = {
                            menu = null
                            edit = LineEditState(open.side, open.lineNumber, open.text)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("刪除") },
                        onClick = {
                            menu = null
                            session.deleteLine(open.side, open.lineNumber)
                        },
                    )
                }
            }
        }
    }
    edit?.let { current ->
        EditLineDialog(
            side = current.side,
            lineNumber = current.lineNumber,
            initial = current.text,
            onConfirm = { lines ->
                session.replaceLine(current.side, current.lineNumber, lines)
                edit = null
            },
            onDismiss = { edit = null },
        )
    }
    val selectedIndex = selectedHunk
    val selected = selectedIndex?.let { index -> phase.hunks.getOrNull(index) }
    if (selectedIndex != null && selected != null && selected.kind != HunkKind.Equal) {
        BlockActions(
            hunk = selected,
            decision = session.decisionAt(selectedIndex),
            onCopyLeftToRight = { session.decide(selectedIndex, BlockDecision.CopyLeftToRight) },
            onCopyRightToLeft = { session.decide(selectedIndex, BlockDecision.CopyRightToLeft) },
            onRevert = { session.revert(selectedIndex) },
        )
    }
}

@Composable
private fun CompareHeader(
    leftLabel: String,
    rightLabel: String,
    summary: String,
    gate: String?,
    canSaveLeft: Boolean,
    canSaveRight: Boolean,
    canJump: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSave: (Side) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = leftLabel,
                    color = CompareInk,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "左側",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onSave(Side.Left) }, enabled = canSaveLeft) { Text("儲存左檔") }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rightLabel,
                    color = CompareInk,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "右側",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onSave(Side.Right) }, enabled = canSaveRight) { Text("儲存右檔") }
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary,
                color = CompareMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            if (gate != null) {
                Text(
                    text = "  ·  $gate",
                    color = CompareMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onPrevious, enabled = canJump) { Text("上一處") }
            TextButton(onClick = onNext, enabled = canJump) { Text("下一處") }
        }
    }
}

@Composable
private fun ColumnScope.StatusMessage(message: String) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
        Text(text = message, color = CompareInk, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CompareRow(
    row: AlignedRow,
    selected: Boolean,
    onSelect: () -> Unit,
    onLineMenu: (Side, Int, String, Offset) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clickable(enabled = !row.equal, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SideCell(
            text = row.leftText,
            number = row.leftNumber,
            side = Side.Left,
            equal = row.equal,
            decided = row.decided,
            deleted = row.deleted,
            selected = selected && !row.equal,
            onLineMenu = onLineMenu,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline),
        )
        SideCell(
            text = row.rightText,
            number = row.rightNumber,
            side = Side.Right,
            equal = row.equal,
            decided = row.decided,
            deleted = row.deleted,
            selected = selected && !row.equal,
            onLineMenu = onLineMenu,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SideCell(
    text: String?,
    number: Int?,
    side: Side,
    equal: Boolean,
    decided: Boolean,
    deleted: Boolean,
    selected: Boolean,
    onLineMenu: (Side, Int, String, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pad = text == null && !deleted
    val onLeft = side == Side.Left
    val onMenu = rememberUpdatedState(onLineMenu)
    val origin = remember { mutableStateOf(Offset.Zero) }
    val lineText = text
    val lineNumber = number
    val secondary = if (lineText != null && lineNumber != null) {
        Modifier.pointerInput(lineText, lineNumber) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    // Open after the click ends, so the menu is not dismissed by the same press.
                    if (event.type != PointerEventType.Release || event.button != PointerButton.Secondary) {
                        continue
                    }
                    val change = event.changes.firstOrNull() ?: continue
                    change.consume()
                    onMenu.value(side, lineNumber, lineText, origin.value + change.position)
                }
            }
        }
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(
                cellColor(
                    onLeft = onLeft,
                    pad = pad,
                    equal = equal,
                    decided = decided,
                    selected = selected,
                ),
            )
            .onGloballyPositioned { coordinates -> origin.value = coordinates.positionInRoot() }
            .then(secondary),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number?.toString()?.padStart(4, ' ') ?: "    ",
            modifier = Modifier.width(44.dp).padding(start = 8.dp),
            style = codeStyle(),
            color = CompareMuted,
            softWrap = false,
        )
        Text(
            text = when {
                deleted -> "此段將刪除"
                text == null -> "（沒有這一行）"
                text.isEmpty() -> "·"
                else -> text
            },
            modifier = Modifier.weight(1f).clipToBounds(),
            style = codeStyle(),
            color = if (deleted || text == null) CompareMuted else CompareInk,
            softWrap = false,
            maxLines = 1,
        )
    }
}

@Composable
private fun EditLineDialog(
    side: Side,
    lineNumber: Int,
    initial: String,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(side, lineNumber, initial) { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val title = if (side == Side.Left) "編輯左側第 $lineNumber 行" else "編輯右側第 $lineNumber 行"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 280.dp)
                    .focusRequester(focus),
                textStyle = MaterialTheme.typography.bodyMedium,
                minLines = 4,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(linesFromEditor(draft)) }) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun BlockActions(
    hunk: Hunk,
    decision: BlockDecision,
    onCopyLeftToRight: () -> Unit,
    onCopyRightToLeft: () -> Unit,
    onRevert: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val leftShown = sideLines(hunk, decision, onLeft = true)
    val rightShown = sideLines(hunk, decision, onLeft = false)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = "${hunkTitle(hunk)}  ·  ${decisionLabel(hunk, decision)}",
            color = CompareInk,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChoiceButton("從左複製到右", decision == BlockDecision.CopyLeftToRight, onCopyLeftToRight)
            ChoiceButton("從右複製到左", decision == BlockDecision.CopyRightToLeft, onCopyRightToLeft)
            TextButton(onClick = onRevert, enabled = decision != BlockDecision.Original) {
                Text("還原")
            }
            TextButton(
                onClick = { clipboard.setText(AnnotatedString(leftShown.joinToString("\n"))) },
                enabled = leftShown.isNotEmpty(),
            ) {
                Text("複製左側文字")
            }
            TextButton(
                onClick = { clipboard.setText(AnnotatedString(rightShown.joinToString("\n"))) },
                enabled = rightShown.isNotEmpty(),
            ) {
                Text("複製右側文字")
            }
        }
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun codeStyle(): TextStyle {
    return TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
}

private fun cellColor(
    onLeft: Boolean,
    pad: Boolean,
    equal: Boolean,
    decided: Boolean,
    selected: Boolean,
): Color {
    if (equal) return ComparePaper
    if (decided) return if (selected) CompareDecidedSelected else CompareDecided
    return when {
        onLeft && selected -> CompareLeftSelected
        onLeft && pad -> CompareLeftPad
        onLeft -> CompareLeft
        selected -> CompareRightSelected
        pad -> CompareRightPad
        else -> CompareRight
    }
}

private data class LineMenuState(
    val side: Side,
    val lineNumber: Int,
    val text: String,
    val offset: DpOffset,
)

private data class LineEditState(
    val side: Side,
    val lineNumber: Int,
    val text: String,
)

private fun decisionLabel(hunk: Hunk, decision: BlockDecision): String {
    if (decision == BlockDecision.Original) return "未決定"
    val sourceEmpty = when (decision) {
        BlockDecision.CopyLeftToRight -> hunk.leftLines.isEmpty()
        BlockDecision.CopyRightToLeft -> hunk.rightLines.isEmpty()
        BlockDecision.Original -> false
    }
    if (sourceEmpty) return "將刪除有字的那一側"
    val destinationEmpty = when (decision) {
        BlockDecision.CopyLeftToRight -> hunk.rightLines.isEmpty()
        BlockDecision.CopyRightToLeft -> hunk.leftLines.isEmpty()
        BlockDecision.Original -> false
    }
    if (destinationEmpty) return "將補進另一側"
    return when (decision) {
        BlockDecision.CopyLeftToRight -> "右側將改成左側文字"
        BlockDecision.CopyRightToLeft -> "左側將改成右側文字"
        BlockDecision.Original -> "未決定"
    }
}
