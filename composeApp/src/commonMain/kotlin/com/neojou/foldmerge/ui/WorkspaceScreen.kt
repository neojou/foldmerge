package com.neojou.foldmerge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.neojou.foldmerge.AboutRequest
import com.neojou.foldmerge.AppVersion
import com.neojou.foldmerge.model.FoldSession
import com.neojou.foldmerge.model.Side
import com.neojou.foldmerge.model.alignVisibleRows
import com.neojou.foldmerge.model.entryName
import com.neojou.foldmerge.model.visibleFileRows
import com.neojou.foldmerge.platformWorkspaceAccess
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Masthead of two path cards and the file lists.
 *
 * When both directories are loaded, one list aligns matching names. Otherwise each side scrolls alone.
 * Selecting one file on each side opens a compare window. Closing that window keeps the block decisions.
 */
@Composable
fun WorkspaceScreen(about: AboutRequest) {
    val access = remember { platformWorkspaceAccess() }
    val session = remember(access) { FoldSession(access) }
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }

    fun pick(side: Side) {
        if (picking) return
        picking = true
        scope.launch {
            try {
                val initial = if (side == Side.Left) session.leftRoot else session.rightRoot
                val title = if (side == Side.Left) "選擇左側目錄" else "選擇右側目錄"
                val path = runCatching { access.chooseDirectory(title, initial) }
                    .getOrElse { error ->
                        session.showNotice("無法開啟目錄選擇：${error.message ?: "未知錯誤"}")
                        null
                    }
                if (path != null) session.openRoot(side, path)
            } finally {
                picking = false
            }
        }
    }

    val leftFile = session.leftSelected
    val rightFile = session.rightSelected
    val pairKey = if (leftFile != null && rightFile != null) {
        listOf(session.leftRoot.orEmpty(), leftFile, session.rightRoot.orEmpty(), rightFile)
            .joinToString("\u0000")
    } else {
        null
    }
    var compareOpen by remember { mutableStateOf(false) }
    var promptClose by remember { mutableStateOf(false) }
    var trackedPair by remember { mutableStateOf<String?>(null) }
    // A new pair opens the window in this same frame. Closing leaves the pair unchanged, so it stays closed.
    if (pairKey != trackedPair) {
        trackedPair = pairKey
        compareOpen = pairKey != null
        promptClose = false
    }
    val compareVisible = compareOpen && pairKey != null
    val bothListed = session.leftRoot != null &&
        session.rightRoot != null &&
        !session.leftBusy &&
        !session.rightBusy
    val pairedRows = remember(
        bothListed,
        session.leftNodes,
        session.rightNodes,
        session.leftExpanded,
        session.rightExpanded,
    ) {
        if (bothListed) {
            alignVisibleRows(
                session.leftNodes,
                session.rightNodes,
                session.leftExpanded,
                session.rightExpanded,
            )
        } else {
            null
        }
    }
    val leftRows = if (pairedRows == null) {
        visibleFileRows(session.leftNodes, session.leftExpanded)
    } else {
        emptyList()
    }
    val rightRows = if (pairedRows == null) {
        visibleFileRows(session.rightNodes, session.rightExpanded)
    } else {
        emptyList()
    }
    val places = remember { mutableStateMapOf<String, RowPlace>() }
    var leftListBounds by remember { mutableStateOf<Rect?>(null) }
    var rightListBounds by remember { mutableStateOf<Rect?>(null) }
    var drag by remember { mutableStateOf<DraggedFile?>(null) }
    var paneOrigin by remember { mutableStateOf(Offset.Zero) }

    fun placeKey(rowSide: Side, path: String): String = "${rowSide.ordinal}\u0000$path"

    fun dropDirectoryFor(current: DraggedFile): String? {
        val destination = if (current.side == Side.Left) Side.Right else Side.Left
        val rootMissing = if (destination == Side.Left) session.leftRoot == null else session.rightRoot == null
        val busy = if (destination == Side.Left) session.leftBusy else session.rightBusy
        if (rootMissing || busy) return null
        return resolveDropDirectory(
            pointer = current.pointerInRoot,
            source = current.side,
            places = places.values,
            otherListBounds = if (destination == Side.Left) leftListBounds else rightListBounds,
        )
    }

    fun finishDrag() {
        val current = drag ?: return
        drag = null
        val directory = dropDirectoryFor(current) ?: return
        val destination = if (current.side == Side.Left) Side.Right else Side.Left
        scope.launch {
            session.copyInto(current.side, current.relativePath, destination, directory)
        }
    }

    fun requestCloseCompare() {
        if (session.canSave(Side.Left) || session.canSave(Side.Right) || session.askOverwrite) {
            promptClose = true
        } else {
            compareOpen = false
            promptClose = false
        }
    }

    val dragging = drag
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates -> paneOrigin = coordinates.positionInRoot() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Masthead(
                leftPath = session.leftRoot,
                rightPath = session.rightRoot,
                leftBusy = picking || session.leftBusy,
                rightBusy = picking || session.rightBusy,
                onPickLeft = { pick(Side.Left) },
                onPickRight = { pick(Side.Right) },
                onAbout = about::show,
            )
            session.notice?.let { message ->
                NoticeBar(message = message, onDismiss = session::dismissNotice)
            }
            if (pairKey == null && session.leftRoot != null && session.rightRoot != null) {
                Text(
                    text = "左右各選一個檔案，會打開比較視窗。",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (pairKey != null && !compareVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (session.canSave(Side.Left) || session.canSave(Side.Right)) {
                            "比較視窗已關閉，尚未儲存的決定仍保留。"
                        } else {
                            "比較視窗已關閉。"
                        },
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = {
                        promptClose = false
                        compareOpen = true
                    }) {
                        Text("開啟比較")
                    }
                }
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (pairedRows != null) {
                    AlignedFileList(
                        pairs = pairedRows,
                        leftExpanded = session.leftExpanded,
                        rightExpanded = session.rightExpanded,
                        leftSelected = session.leftSelected,
                        rightSelected = session.rightSelected,
                        leftDropDirectory = dragging?.let { current ->
                            if (current.side == Side.Right) dropDirectoryFor(current) else null
                        },
                        rightDropDirectory = dragging?.let { current ->
                            if (current.side == Side.Left) dropDirectoryFor(current) else null
                        },
                        onToggle = { side, path -> session.toggleExpanded(side, path) },
                        onSelect = { side, path -> scope.launch { session.select(side, path) } },
                        onDragStart = { side, path, position -> drag = DraggedFile(side, path, position) },
                        onDragMove = { side, path, position -> drag = DraggedFile(side, path, position) },
                        onDragFinish = ::finishDrag,
                        onPlace = { place ->
                            val key = placeKey(place.side, place.relativePath)
                            if (places[key] != place) places[key] = place
                        },
                        onPlaceGone = { side, path -> places.remove(placeKey(side, path)) },
                        onListBounds = { left, right ->
                            if (leftListBounds != left) leftListBounds = left
                            if (rightListBounds != right) rightListBounds = right
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    FileListPane(
                        title = "左側檔案",
                        side = Side.Left,
                        rootSelected = session.leftRoot != null,
                        busy = session.leftBusy,
                        rows = leftRows,
                        expanded = session.leftExpanded,
                        selectedPath = session.leftSelected,
                        dropDirectory = dragging?.let { current ->
                            if (current.side == Side.Right) dropDirectoryFor(current) else null
                        },
                        onToggle = { session.toggleExpanded(Side.Left, it) },
                        onSelect = { path -> scope.launch { session.select(Side.Left, path) } },
                        onDragStart = { path, position -> drag = DraggedFile(Side.Left, path, position) },
                        onDragMove = { path, position -> drag = DraggedFile(Side.Left, path, position) },
                        onDragFinish = ::finishDrag,
                        onPlace = { place ->
                            val key = placeKey(place.side, place.relativePath)
                            if (places[key] != place) places[key] = place
                        },
                        onPlaceGone = { path -> places.remove(placeKey(Side.Left, path)) },
                        onListBounds = { bounds -> if (leftListBounds != bounds) leftListBounds = bounds },
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outline),
                    )
                    FileListPane(
                        title = "右側檔案",
                        side = Side.Right,
                        rootSelected = session.rightRoot != null,
                        busy = session.rightBusy,
                        rows = rightRows,
                        expanded = session.rightExpanded,
                        selectedPath = session.rightSelected,
                        dropDirectory = dragging?.let { current ->
                            if (current.side == Side.Left) dropDirectoryFor(current) else null
                        },
                        onToggle = { session.toggleExpanded(Side.Right, it) },
                        onSelect = { path -> scope.launch { session.select(Side.Right, path) } },
                        onDragStart = { path, position -> drag = DraggedFile(Side.Right, path, position) },
                        onDragMove = { path, position -> drag = DraggedFile(Side.Right, path, position) },
                        onDragFinish = ::finishDrag,
                        onPlace = { place ->
                            val key = placeKey(place.side, place.relativePath)
                            if (places[key] != place) places[key] = place
                        },
                        onPlaceGone = { path -> places.remove(placeKey(Side.Right, path)) },
                        onListBounds = { bounds -> if (rightListBounds != bounds) rightListBounds = bounds },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (dragging != null) {
            val local = dragging.pointerInRoot - paneOrigin
            val chipShape = RoundedCornerShape(8.dp)
            Text(
                text = entryName(dragging.relativePath),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset((local.x + 12f).roundToInt(), (local.y + 12f).roundToInt())
                    }
                    .clip(chipShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), chipShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    HostCompareWindow(
        visible = compareVisible,
        title = "比較 · ${leftFile.orEmpty()} · ${rightFile.orEmpty()}",
        onCloseRequest = ::requestCloseCompare,
    ) {
        ComparePane(
            session = session,
            leftLabel = leftFile.orEmpty(),
            rightLabel = rightFile.orEmpty(),
            promptToClose = promptClose,
            onSave = { side -> scope.launch { session.save(side) } },
            onConfirmClose = {
                promptClose = false
                compareOpen = false
            },
            onKeepEditing = { promptClose = false },
        )
    }
}

@Composable
private fun Masthead(
    leftPath: String?,
    rightPath: String?,
    leftBusy: Boolean,
    rightBusy: Boolean,
    onPickLeft: () -> Unit,
    onPickRight: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = AppVersion.APP_NAME,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onAbout) {
                Text("關於")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            PathCard(
                sideLabel = "左側",
                path = leftPath,
                busy = leftBusy,
                onChange = onPickLeft,
                modifier = Modifier.weight(1f),
            )
            PathCard(
                sideLabel = "右側",
                path = rightPath,
                busy = rightBusy,
                onChange = onPickRight,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NoticeBar(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onDismiss) {
            Text("關閉")
        }
    }
}
