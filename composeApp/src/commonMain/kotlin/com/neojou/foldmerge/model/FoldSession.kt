package com.neojou.foldmerge.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neojou.foldmerge.diff.BlockDecision
import com.neojou.foldmerge.diff.Hunk
import com.neojou.foldmerge.diff.HunkKind
import com.neojou.foldmerge.diff.applySide
import com.neojou.foldmerge.diff.diffHunks
import com.neojou.foldmerge.diff.renderDocument
import com.neojou.foldmerge.diff.splitText
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Which root, list, and selection an action applies to.
 */
enum class Side {
    Left,
    Right,
}

/**
 * Comparison lifecycle. A side can be saved from [Ready] when its decided text differs from the stamp on record.
 */
sealed interface ComparePhase {
    /** A side is missing, so there is nothing to compare yet. */
    data object Idle : ComparePhase

    /** Both files are selected and the read or diff is still running. */
    data object Running : ComparePhase

    /**
     * At least one file is not text.
     *
     * [detail] always contains the phrase `無法以文字比較`.
     */
    data class NotText(val detail: String) : ComparePhase

    /**
     * A finished text comparison.
     *
     * Each stamp is that file as it was read, or the bytes last written by a partial save.
     * Save refuses to overwrite a newer stamp until the user confirms.
     */
    data class Ready(
        val hunks: List<Hunk>,
        val leftStamp: FileStamp,
        val rightStamp: FileStamp,
        val leftNewline: String,
        val leftTrailingNewline: Boolean,
        val rightNewline: String,
        val rightTrailingNewline: Boolean,
    ) : ComparePhase

    /** The read failed. */
    data class Broken(val message: String) : ComparePhase
}

/**
 * Holds both roots, the visible trees, the selected files, block decisions, and save warnings.
 */
class FoldSession(
    private val access: WorkspaceAccess,
) {
    var leftRoot by mutableStateOf<String?>(null)
        private set
    var rightRoot by mutableStateOf<String?>(null)
        private set
    var leftNodes by mutableStateOf<List<FileNode>>(emptyList())
        private set
    var rightNodes by mutableStateOf<List<FileNode>>(emptyList())
        private set
    var leftExpanded by mutableStateOf<Set<String>>(emptySet())
        private set
    var rightExpanded by mutableStateOf<Set<String>>(emptySet())
        private set
    var leftSelected by mutableStateOf<String?>(null)
        private set
    var rightSelected by mutableStateOf<String?>(null)
        private set
    var leftBusy by mutableStateOf(false)
        private set
    var rightBusy by mutableStateOf(false)
        private set
    var phase by mutableStateOf<ComparePhase>(ComparePhase.Idle)
        private set
    var decisions by mutableStateOf<Map<Int, BlockDecision>>(emptyMap())
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var askOverwrite by mutableStateOf(false)
        private set
    var overwriteLeft by mutableStateOf(false)
        private set
    var overwriteRight by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var copying by mutableStateOf(false)
        private set

    private val gate = Mutex()
    private var generation = 0

    /**
     * True when [side] has decided text that differs from the stamp on record.
     *
     * Saving, or an open overwrite question, disables both sides.
     */
    fun canSave(side: Side): Boolean {
        if (saving || askOverwrite) return false
        val ready = phase as? ComparePhase.Ready ?: return false
        return pending(ready, side)
    }

    /**
     * Why saving is unavailable, or null when the comparison is ready.
     *
     * A ready comparison with nothing to write stays quiet. The disabled buttons show that.
     */
    fun saveGateMessage(): String? {
        if (saving) return "正在儲存…"
        return when (val current = phase) {
            ComparePhase.Idle, ComparePhase.Running -> "尚未比對完，不能儲存"
            is ComparePhase.NotText -> current.detail
            is ComparePhase.Broken -> "比較失敗，不能儲存"
            is ComparePhase.Ready -> null
        }
    }

    fun decisionAt(index: Int): BlockDecision = decisions[index] ?: BlockDecision.Original

    fun showNotice(message: String) {
        notice = message
    }

    fun dismissNotice() {
        notice = null
    }

    fun dismissOverwrite() {
        clearOverwrite()
    }

    fun toggleExpanded(side: Side, relativePath: String) {
        when (side) {
            Side.Left -> leftExpanded = toggle(leftExpanded, relativePath)
            Side.Right -> rightExpanded = toggle(rightExpanded, relativePath)
        }
    }

    /**
     * Lists [path] and replaces that side's tree. The previous selection on that side is cleared.
     *
     * Directories start collapsed. The path is shown immediately. A slower listing for a previous
     * path cannot overwrite a newer one.
     */
    suspend fun openRoot(side: Side, path: String) {
        val cleared = gate.withLock {
            val dirty = currentDirty()
            when (side) {
                Side.Left -> {
                    leftRoot = path
                    leftNodes = emptyList()
                    leftExpanded = emptySet()
                    leftSelected = null
                    leftBusy = true
                }
                Side.Right -> {
                    rightRoot = path
                    rightNodes = emptyList()
                    rightExpanded = emptySet()
                    rightSelected = null
                    rightBusy = true
                }
            }
            notice = if (dirty) "已捨棄尚未儲存的區塊決定。" else null
            snapshotLocked()
        }
        finishCompare(cleared)
        val nodes = try {
            access.listTree(path)
        } catch (cancelled: CancellationException) {
            clearBusyIfCurrent(side, path)
            throw cancelled
        } catch (error: Throwable) {
            val stillCurrent = clearBusyIfCurrent(side, path)
            if (stillCurrent) {
                notice = "無法讀取目錄：${error.message ?: "未知錯誤"}"
            }
            MyLog.add(TAG, "list failed: ${error.message}", LogLevel.ERROR)
            return
        }
        val request = gate.withLock {
            if (rootOf(side) != path) return@withLock null
            when (side) {
                Side.Left -> {
                    leftNodes = nodes
                    leftExpanded = emptySet()
                    leftBusy = false
                }
                Side.Right -> {
                    rightNodes = nodes
                    rightExpanded = emptySet()
                    rightBusy = false
                }
            }
            snapshotLocked()
        } ?: return
        finishCompare(request)
    }

    /**
     * Selects a file. Choosing the file that is already selected keeps the current decisions.
     */
    suspend fun select(side: Side, relativePath: String) {
        val request = gate.withLock {
            val current = if (side == Side.Left) leftSelected else rightSelected
            if (current == relativePath) {
                null
            } else {
                val dirty = currentDirty()
                when (side) {
                    Side.Left -> leftSelected = relativePath
                    Side.Right -> rightSelected = relativePath
                }
                val request = snapshotLocked()
                notice = if (dirty) "已捨棄尚未儲存的區塊決定。" else null
                request
            }
        } ?: return
        finishCompare(request)
    }

    /**
     * Records a block decision. Equal blocks have no decision.
     */
    fun decide(index: Int, decision: BlockDecision) {
        val ready = phase as? ComparePhase.Ready ?: return
        val hunk = ready.hunks.getOrNull(index) ?: return
        if (hunk.kind == HunkKind.Equal) return
        decisions = if (decision == BlockDecision.Original) {
            decisions - index
        } else {
            decisions + (index to decision)
        }
    }

    fun revert(index: Int) {
        decide(index, BlockDecision.Original)
    }

    /**
     * Inserts an empty line above [lineNumber] on [side].
     *
     * [lineNumber] is the 1-based number shown for that side after block decisions.
     * Those decisions are folded into the text, the diff is built again, and the decision map
     * is cleared. Saving, or an open overwrite question, leaves the text unchanged.
     */
    fun insertBlankAbove(side: Side, lineNumber: Int) {
        rewriteLine(side, lineNumber) { lines, index ->
            ArrayList(lines).apply { add(index, "") }
        }
    }

    /**
     * Replaces [lineNumber] on [side] with [newLines].
     *
     * [newLines] may contain more than one line. An empty list is ignored, as is a replacement
     * that does not change the line. Decisions are folded in, the diff is built again, and the
     * decision map is cleared.
     */
    fun replaceLine(side: Side, lineNumber: Int, newLines: List<String>) {
        if (newLines.isEmpty()) return
        rewriteLine(side, lineNumber) { lines, index ->
            ArrayList(lines).apply {
                removeAt(index)
                addAll(index, newLines)
            }
        }
    }

    /**
     * Removes [lineNumber] on [side] and leaves every other line in place.
     *
     * Decisions are folded in, the diff is built again, and the decision map is cleared.
     * Saving, or an open overwrite question, leaves the text unchanged.
     */
    fun deleteLine(side: Side, lineNumber: Int) {
        rewriteLine(side, lineNumber) { lines, index ->
            ArrayList(lines).apply { removeAt(index) }
        }
    }

    private fun rewriteLine(
        side: Side,
        lineNumber: Int,
        change: (List<String>, Int) -> List<String>,
    ) {
        if (saving || askOverwrite) return
        val ready = phase as? ComparePhase.Ready ?: return
        val left = applySide(ready.hunks, decisions, onLeft = true)
        val right = applySide(ready.hunks, decisions, onLeft = false)
        val source = if (side == Side.Left) left else right
        val index = lineNumber - 1
        if (index !in source.indices) return
        val updated = change(source, index)
        if (updated == source) return
        phase = ready.copy(
            hunks = diffHunks(
                leftLines = if (side == Side.Left) updated else left,
                rightLines = if (side == Side.Right) updated else right,
            ),
        )
        decisions = emptyMap()
    }

    /**
     * Copies [relativePath] from [source] into [directoryPath] on [destination].
     *
     * [directoryPath] is empty for the destination root. The new relative path is that directory
     * plus the file name. A direct child with the same name, file or directory, is left unchanged.
     */
    suspend fun copyInto(
        source: Side,
        relativePath: String,
        destination: Side,
        directoryPath: String,
    ) {
        val plan = gate.withLock {
            if (copying || source == destination) return@withLock null
            val sourceRoot = rootOf(source) ?: return@withLock null
            val destinationRoot = rootOf(destination) ?: return@withLock null
            if (busyOf(destination)) return@withLock null
            val fileName = entryName(relativePath)
            if (fileName.isEmpty()) return@withLock null
            val nodes = nodesOf(destination)
            if (directoryPath.isNotEmpty() &&
                nodes.none { node -> node.directory && node.relativePath == directoryPath }
            ) {
                notice = "複製失敗：找不到資料夾"
                return@withLock null
            }
            if (nodes.any { node -> directChildName(node, directoryPath) == fileName }) {
                notice = "這個目錄已有同名檔案"
                return@withLock null
            }
            val relative = if (directoryPath.isEmpty()) fileName else "$directoryPath/$fileName"
            copying = true
            CopyPlan(
                destination = destination,
                destinationRoot = destinationRoot,
                relative = relative,
                sourcePath = joinPath(sourceRoot, relativePath),
                destinationPath = joinPath(destinationRoot, relative),
            )
        } ?: return
        try {
            access.copyFile(plan.sourcePath, plan.destinationPath)
            val nodes = access.listTree(plan.destinationRoot)
            val request = gate.withLock {
                if (rootOf(plan.destination) != plan.destinationRoot) return@withLock null
                val ancestors = ancestorDirectories(plan.relative)
                val current = selectedOf(plan.destination)
                val dirty = currentDirty() && current != plan.relative
                when (plan.destination) {
                    Side.Left -> {
                        leftNodes = nodes
                        leftExpanded = leftExpanded + ancestors
                        leftSelected = plan.relative
                    }
                    Side.Right -> {
                        rightNodes = nodes
                        rightExpanded = rightExpanded + ancestors
                        rightSelected = plan.relative
                    }
                }
                notice = if (dirty) {
                    "已捨棄尚未儲存的區塊決定。"
                } else if (plan.destination == Side.Left) {
                    "已複製到左側。"
                } else {
                    "已複製到右側。"
                }
                snapshotLocked()
            }
            if (request != null) finishCompare(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            notice = if (error.message == "對方已有這個檔案") {
                "這個目錄已有同名檔案"
            } else {
                "複製失敗：${error.message ?: "未知錯誤"}"
            }
            MyLog.add(TAG, "copy failed: ${error.message}", LogLevel.ERROR)
        } finally {
            copying = false
        }
    }

    /**
     * Writes [side] when its decided text differs from the stamp on record.
     *
     * The other file is left untouched, and its decisions stay. When that other side also matches
     * its stamp, the two files are compared again and the decisions are cleared.
     * A disk stamp that differs from the one on record sets [askOverwrite] and writes nothing
     * unless [force] is true.
     */
    suspend fun save(side: Side, force: Boolean = false) {
        val plan = gate.withLock {
            if (saving) return@withLock null
            val ready = phase as? ComparePhase.Ready ?: return@withLock null
            val text = renderedText(ready, side)
            val writtenStamp = fingerprint(text.encodeToByteArray())
            if (writtenStamp == recordedStamp(ready, side)) return@withLock null
            val root = rootOf(side) ?: return@withLock null
            val relative = if (side == Side.Left) leftSelected else rightSelected
            relative ?: return@withLock null
            saving = true
            SideSave(
                gen = generation,
                side = side,
                path = joinPath(root, relative),
                text = text,
                recorded = recordedStamp(ready, side),
                writtenStamp = writtenStamp,
            )
        } ?: return
        try {
            val disk = access.stampOf(plan.path)
            if (!force && disk != plan.recorded) {
                overwriteLeft = plan.side == Side.Left
                overwriteRight = plan.side == Side.Right
                askOverwrite = true
                return
            }
            access.writeDocument(plan.path, plan.text)
            val request = gate.withLock {
                if (plan.gen != generation) return@withLock null
                val current = phase as? ComparePhase.Ready ?: return@withLock null
                val updated = when (plan.side) {
                    Side.Left -> current.copy(leftStamp = plan.writtenStamp)
                    Side.Right -> current.copy(rightStamp = plan.writtenStamp)
                }
                clearOverwrite()
                val other = if (plan.side == Side.Left) Side.Right else Side.Left
                if (pending(updated, other)) {
                    phase = updated
                    notice = if (plan.side == Side.Left) "已儲存左檔。" else "已儲存右檔。"
                    null
                } else {
                    notice = "已儲存，並重新比較。"
                    snapshotLocked()
                }
            }
            if (request != null) finishCompare(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            notice = "儲存失敗：${error.message ?: "未知錯誤"}"
            MyLog.add(TAG, "save failed: ${error.message}", LogLevel.ERROR)
        } finally {
            saving = false
        }
    }

    private suspend fun clearBusyIfCurrent(side: Side, path: String): Boolean {
        return gate.withLock {
            if (rootOf(side) != path) return@withLock false
            when (side) {
                Side.Left -> leftBusy = false
                Side.Right -> rightBusy = false
            }
            true
        }
    }

    private fun rootOf(side: Side): String? = when (side) {
        Side.Left -> leftRoot
        Side.Right -> rightRoot
    }

    private fun selectedOf(side: Side): String? = when (side) {
        Side.Left -> leftSelected
        Side.Right -> rightSelected
    }

    private fun nodesOf(side: Side): List<FileNode> = when (side) {
        Side.Left -> leftNodes
        Side.Right -> rightNodes
    }

    private fun busyOf(side: Side): Boolean = when (side) {
        Side.Left -> leftBusy
        Side.Right -> rightBusy
    }

    /**
     * The last segment when [node] sits directly inside [directoryPath]. Root children use an empty directory.
     */
    private fun directChildName(node: FileNode, directoryPath: String): String? {
        val parent = node.relativePath.substringBeforeLast('/', "")
        if (parent != directoryPath) return null
        return entryName(node.relativePath)
    }

    private fun ancestorDirectories(relativePath: String): Set<String> {
        val directories = mutableSetOf<String>()
        var accumulated = ""
        relativePath.split('/').dropLast(1).forEach { part ->
            accumulated = if (accumulated.isEmpty()) part else "$accumulated/$part"
            directories += accumulated
        }
        return directories
    }

    private fun currentDirty(): Boolean {
        val ready = phase as? ComparePhase.Ready ?: return false
        return pending(ready, Side.Left) || pending(ready, Side.Right)
    }

    private fun clearOverwrite() {
        askOverwrite = false
        overwriteLeft = false
        overwriteRight = false
    }

    private fun pending(ready: ComparePhase.Ready, side: Side): Boolean {
        return renderedStamp(ready, side) != recordedStamp(ready, side)
    }

    private fun renderedText(ready: ComparePhase.Ready, side: Side): String {
        val onLeft = side == Side.Left
        return renderDocument(
            lines = applySide(ready.hunks, decisions, onLeft = onLeft),
            newline = if (onLeft) ready.leftNewline else ready.rightNewline,
            trailingNewline = if (onLeft) ready.leftTrailingNewline else ready.rightTrailingNewline,
        )
    }

    private fun renderedStamp(ready: ComparePhase.Ready, side: Side): FileStamp {
        return fingerprint(renderedText(ready, side).encodeToByteArray())
    }

    private fun recordedStamp(ready: ComparePhase.Ready, side: Side): FileStamp {
        return if (side == Side.Left) ready.leftStamp else ready.rightStamp
    }

    /**
     * Starts a new comparison generation from the selections currently stored.
     * Call only while [gate] is held.
     */
    private fun snapshotLocked(): CompareRequest {
        val gen = ++generation
        decisions = emptyMap()
        clearOverwrite()
        val leftPath = leftRoot?.let { root -> leftSelected?.let { joinPath(root, it) } }
        val rightPath = rightRoot?.let { root -> rightSelected?.let { joinPath(root, it) } }
        phase = if (leftPath == null || rightPath == null) ComparePhase.Idle else ComparePhase.Running
        return CompareRequest(gen = gen, leftPath = leftPath, rightPath = rightPath)
    }

    private suspend fun finishCompare(request: CompareRequest) {
        val leftPath = request.leftPath ?: return
        val rightPath = request.rightPath ?: return
        val next = try {
            val leftDoc = access.readDocument(leftPath)
            val rightDoc = access.readDocument(rightPath)
            withContext(Dispatchers.Default) {
                buildPhase(leftDoc, rightDoc)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            gate.withLock {
                if (request.gen != generation) return
                phase = ComparePhase.Broken(error.message ?: "讀取失敗")
            }
            MyLog.add(TAG, "compare failed: ${error.message}", LogLevel.ERROR)
            return
        }
        gate.withLock {
            if (request.gen != generation) return
            phase = next
        }
    }

    private fun buildPhase(leftDoc: LoadedDocument, rightDoc: LoadedDocument): ComparePhase {
        if (leftDoc.binary || rightDoc.binary) {
            val detail = when {
                leftDoc.binary && rightDoc.binary -> "這兩個檔案無法以文字比較"
                leftDoc.binary -> "左側檔案無法以文字比較"
                else -> "右側檔案無法以文字比較"
            }
            return ComparePhase.NotText(detail)
        }
        val leftSplit = splitText(leftDoc.text)
        val rightSplit = splitText(rightDoc.text)
        val hunks = diffHunks(leftSplit.lines, rightSplit.lines)
        return ComparePhase.Ready(
            hunks = hunks,
            leftStamp = leftDoc.stamp,
            rightStamp = rightDoc.stamp,
            leftNewline = leftSplit.newline,
            leftTrailingNewline = leftSplit.trailingNewline,
            rightNewline = rightSplit.newline,
            rightTrailingNewline = rightSplit.trailingNewline,
        )
    }

    private fun toggle(current: Set<String>, path: String): Set<String> {
        return if (path in current) current - path else current + path
    }

    private data class CompareRequest(
        val gen: Int,
        val leftPath: String?,
        val rightPath: String?,
    )

    private data class CopyPlan(
        val destination: Side,
        val destinationRoot: String,
        val relative: String,
        val sourcePath: String,
        val destinationPath: String,
    )

    private data class SideSave(
        val gen: Int,
        val side: Side,
        val path: String,
        val text: String,
        val recorded: FileStamp,
        val writtenStamp: FileStamp,
    )

    private companion object {
        const val TAG = "Fold"
    }
}
