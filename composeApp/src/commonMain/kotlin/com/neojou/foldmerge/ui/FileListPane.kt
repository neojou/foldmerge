package com.neojou.foldmerge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neojou.foldmerge.model.FileRow
import com.neojou.foldmerge.model.Side
import com.neojou.foldmerge.model.entryName

/**
 * A file being dragged. [pointerInRoot] is the pointer position in the composition root.
 */
data class DraggedFile(
    val side: Side,
    val relativePath: String,
    val pointerInRoot: Offset,
)

/**
 * A visible row's bounds in composition-root coordinates. A file row is reported so a drop on it is not the root.
 */
data class RowPlace(
    val side: Side,
    val relativePath: String,
    val directory: Boolean,
    val bounds: Rect,
)

/**
 * Directory to receive a drop, or null when the pointer is not over the other side's list.
 *
 * An empty string is that side's root. A file row blocks the drop. The smallest containing row wins.
 * [otherListBounds] is the destination list area, which covers the blank space below the rows.
 */
internal fun resolveDropDirectory(
    pointer: Offset,
    source: Side,
    places: Iterable<RowPlace>,
    otherListBounds: Rect?,
): String? {
    val destination = if (source == Side.Left) Side.Right else Side.Left
    val hit = places
        .filter { place -> place.side == destination && pointer in place.bounds }
        .minByOrNull { place -> place.bounds.width * place.bounds.height }
    if (hit != null) {
        return if (hit.directory) hit.relativePath else null
    }
    if (otherListBounds != null && pointer in otherListBounds) return ""
    return null
}

/**
 * A scrollable list of one directory.
 *
 * A file row selects on release when the pointer did not move. Moving it drags the file.
 * Directory rows expand and can receive a drop.
 *
 * [dropDirectory] is null when this pane is not the target, empty when the drop is the root,
 * and a relative path when a directory row is the target.
 */
@Composable
fun FileListPane(
    title: String,
    side: Side,
    rootSelected: Boolean,
    busy: Boolean,
    rows: List<FileRow>,
    expanded: Set<String>,
    selectedPath: String?,
    dropDirectory: String?,
    onToggle: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDragStart: (String, Offset) -> Unit,
    onDragMove: (String, Offset) -> Unit,
    onDragFinish: () -> Unit,
    onPlace: (RowPlace) -> Unit,
    onPlaceGone: (String) -> Unit,
    onListBounds: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(side) {
        onDispose { onListBounds(null) }
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
        val listWash = dropDirectory == ""
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    if (listWash) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    },
                )
                .onGloballyPositioned { coordinates ->
                    onListBounds(coordinates.boundsInRoot())
                },
        ) {
            when {
                !rootSelected -> EmptyListMessage("選擇上方的目錄")
                busy -> EmptyListMessage("正在讀取目錄…")
                rows.isEmpty() -> EmptyListMessage("這個目錄沒有可顯示的檔案")
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp),
                    ) {
                        items(rows, key = { it.node.relativePath }) { row ->
                            FileRowItem(
                                row = row,
                                side = side,
                                expanded = row.node.relativePath in expanded,
                                selected = row.node.relativePath == selectedPath,
                                dropTarget = dropDirectory != null &&
                                    dropDirectory.isNotEmpty() &&
                                    row.node.directory &&
                                    row.node.relativePath == dropDirectory,
                                onToggle = onToggle,
                                onSelect = onSelect,
                                onDragStart = onDragStart,
                                onDragMove = onDragMove,
                                onDragFinish = onDragFinish,
                                onPlace = onPlace,
                                onPlaceGone = onPlaceGone,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyListMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FileRowItem(
    row: FileRow,
    side: Side,
    expanded: Boolean,
    selected: Boolean,
    dropTarget: Boolean,
    onToggle: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDragStart: (String, Offset) -> Unit,
    onDragMove: (String, Offset) -> Unit,
    onDragFinish: () -> Unit,
    onPlace: (RowPlace) -> Unit,
    onPlaceGone: (String) -> Unit,
) {
    val path = row.node.relativePath
    val directory = row.node.directory
    DisposableEffect(side, path) {
        onDispose { onPlaceGone(path) }
    }
    val rowOrigin = remember { mutableStateOf(Offset.Zero) }
    val selectState = rememberUpdatedState(onSelect)
    val dragStartState = rememberUpdatedState(onDragStart)
    val dragMoveState = rememberUpdatedState(onDragMove)
    val dragFinishState = rememberUpdatedState(onDragFinish)
    val background = when {
        dropTarget -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }
    val shape = RoundedCornerShape(8.dp)
    val gesture = if (directory) {
        Modifier.clickable { onToggle(path) }
    } else {
        // Release before the touch slop only selects. A cancelled drag finishes only after it has started,
        // so a vertical scroll that the list takes over does not select the row.
        Modifier.pointerInput(path) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val pointerId = down.id
                val start = down.position
                var dragging = false
                var released = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                        val distance = (change.position - start).getDistance()
                        if (!dragging && distance > viewConfiguration.touchSlop) {
                            dragging = true
                            dragStartState.value(path, rowOrigin.value + change.position)
                        }
                        if (dragging) {
                            change.consume()
                            dragMoveState.value(path, rowOrigin.value + change.position)
                        }
                        if (!change.pressed) {
                            released = true
                            break
                        }
                    }
                } finally {
                    if (dragging) dragFinishState.value() else if (released) selectState.value(path)
                }
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(shape)
            .background(background)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                rowOrigin.value = bounds.topLeft
                onPlace(
                    RowPlace(
                        side = side,
                        relativePath = path,
                        directory = directory,
                        bounds = bounds,
                    ),
                )
            }
            .then(gesture)
            .padding(start = (8 + row.depth * 16).dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                directory && expanded -> "▾"
                directory -> "▸"
                else -> " "
            },
            modifier = Modifier.width(16.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = entryName(path),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (directory || selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
