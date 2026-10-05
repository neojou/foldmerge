package com.neojou.foldmerge.diff

/**
 * How a contiguous block of lines relates across the two files.
 */
enum class HunkKind {
    /** The same lines appear on both sides. */
    Equal,

    /** Lines present only on the left. The right side has nothing in this block. */
    LeftOnly,

    /** Lines present only on the right. The left side has nothing in this block. */
    RightOnly,

    /** A run of differing lines on both sides, with no equal line between them. */
    Modified,
}

/**
 * One contiguous block produced by the line diff.
 *
 * Line numbers are 1-based. A side with no lines uses start `0`.
 */
data class Hunk(
    val kind: HunkKind,
    val leftLines: List<String>,
    val rightLines: List<String>,
    val leftStart: Int,
    val rightStart: Int,
) {
    val leftEnd: Int get() = if (leftLines.isEmpty()) 0 else leftStart + leftLines.size - 1
    val rightEnd: Int get() = if (rightLines.isEmpty()) 0 else rightStart + rightLines.size - 1
}

/**
 * What to do with one differing block.
 *
 * [Original] leaves both files as they were read. Revert returns a block to that state.
 * [CopyLeftToRight] writes the left lines into the right file. The left file stays as read.
 * [CopyRightToLeft] writes the right lines into the left file. The right file stays as read.
 * Copying from a side that has no lines removes the block from the other file.
 */
enum class BlockDecision {
    Original,
    CopyLeftToRight,
    CopyRightToLeft,
}

/**
 * How a line is drawn inside a block. Added lines use a solid underline. Removed lines use strikethrough.
 */
enum class LineMark {
    Plain,
    Added,
    Removed,
}

/**
 * Diffs two line lists into equal, left-only, right-only, and modified blocks.
 */
fun diffHunks(leftLines: List<String>, rightLines: List<String>): List<Hunk> {
    return hunksFromEdits(myersEdits(leftLines, rightLines))
}

internal fun hunksFromEdits(edits: List<Edit>): List<Hunk> {
    val hunks = mutableListOf<Hunk>()
    var index = 0
    var leftLine = 1
    var rightLine = 1
    while (index < edits.size) {
        if (edits[index] is Edit.Same) {
            val start = index
            while (index < edits.size && edits[index] is Edit.Same) index++
            val lines = edits.subList(start, index).map { it.line }
            hunks.add(
                Hunk(
                    kind = HunkKind.Equal,
                    leftLines = lines,
                    rightLines = lines,
                    leftStart = leftLine,
                    rightStart = rightLine,
                ),
            )
            leftLine += lines.size
            rightLine += lines.size
        } else {
            val start = index
            while (index < edits.size && edits[index] !is Edit.Same) index++
            val leftLines = mutableListOf<String>()
            val rightLines = mutableListOf<String>()
            for (edit in edits.subList(start, index)) {
                when (edit) {
                    is Edit.Delete -> leftLines.add(edit.line)
                    is Edit.Insert -> rightLines.add(edit.line)
                    is Edit.Same -> error("相等行不會落在變更區塊")
                }
            }
            val kind = when {
                leftLines.isEmpty() -> HunkKind.RightOnly
                rightLines.isEmpty() -> HunkKind.LeftOnly
                else -> HunkKind.Modified
            }
            hunks.add(
                Hunk(
                    kind = kind,
                    leftLines = leftLines,
                    rightLines = rightLines,
                    leftStart = if (leftLines.isEmpty()) 0 else leftLine,
                    rightStart = if (rightLines.isEmpty()) 0 else rightLine,
                ),
            )
            leftLine += leftLines.size
            rightLine += rightLines.size
        }
    }
    return hunks
}

/**
 * Lines one side of [hunk] will have under [decision].
 *
 * An equal block, or a block left untouched, keeps that side's original lines.
 * Copying uses the source side's lines for both files, so the destination becomes the source.
 * An empty source removes the block from the destination.
 */
fun sideLines(hunk: Hunk, decision: BlockDecision, onLeft: Boolean): List<String> {
    val original = if (onLeft) hunk.leftLines else hunk.rightLines
    if (hunk.kind == HunkKind.Equal || decision == BlockDecision.Original) return original
    return when (decision) {
        BlockDecision.CopyLeftToRight -> hunk.leftLines
        BlockDecision.CopyRightToLeft -> hunk.rightLines
        BlockDecision.Original -> original
    }
}

/**
 * Builds one file's line list. Missing decisions keep that side's original lines.
 */
fun applySide(hunks: List<Hunk>, decisions: Map<Int, BlockDecision>, onLeft: Boolean): List<String> {
    val merged = ArrayList<String>()
    hunks.forEachIndexed { index, hunk ->
        merged.addAll(sideLines(hunk, decisions[index] ?: BlockDecision.Original, onLeft))
    }
    return merged
}

/**
 * True when a saved decision would change either file.
 */
fun decisionsDirty(hunks: List<Hunk>, decisions: Map<Int, BlockDecision>): Boolean {
    return sideChanged(hunks, decisions, onLeft = true) || sideChanged(hunks, decisions, onLeft = false)
}

/**
 * True when at least one differing block has been given a direction.
 */
fun hasBlockDecision(decisions: Map<Int, BlockDecision>): Boolean {
    return decisions.any { (_, decision) -> decision != BlockDecision.Original }
}

private fun sideChanged(hunks: List<Hunk>, decisions: Map<Int, BlockDecision>, onLeft: Boolean): Boolean {
    val original = hunks.flatMap { hunk -> if (onLeft) hunk.leftLines else hunk.rightLines }
    return applySide(hunks, decisions, onLeft) != original
}

/**
 * Left mark then right mark. The left column of a change is struck through.
 * The right column of a change is underlined.
 */
fun lineMarks(kind: HunkKind): Pair<LineMark, LineMark> = when (kind) {
    HunkKind.Equal -> LineMark.Plain to LineMark.Plain
    HunkKind.LeftOnly -> LineMark.Removed to LineMark.Plain
    HunkKind.RightOnly -> LineMark.Plain to LineMark.Added
    HunkKind.Modified -> LineMark.Removed to LineMark.Added
}

/**
 * Card title, for example `內容不同 · 左 4–6 行 · 右 4–5 行`.
 */
fun hunkTitle(hunk: Hunk): String {
    val kindLabel = when (hunk.kind) {
        HunkKind.Equal -> "相同"
        HunkKind.LeftOnly -> "僅左側"
        HunkKind.RightOnly -> "僅右側"
        HunkKind.Modified -> "內容不同"
    }
    return "$kindLabel · ${lineSpan("左", hunk.leftStart, hunk.leftLines.size)} · ${lineSpan("右", hunk.rightStart, hunk.rightLines.size)}"
}

private fun lineSpan(label: String, start: Int, count: Int): String {
    if (count <= 0 || start <= 0) return "$label 無"
    val end = start + count - 1
    return if (start == end) "$label 第 $start 行" else "$label $start–$end 行"
}
