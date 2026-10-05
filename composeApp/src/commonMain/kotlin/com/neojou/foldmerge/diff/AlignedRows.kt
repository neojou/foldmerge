package com.neojou.foldmerge.diff

/**
 * One visual row in the side-by-side file view.
 *
 * A null side is a blank pad so the other side's extra lines stay aligned.
 * Pads have no line number. An empty string is a real empty line and keeps its number.
 */
data class AlignedRow(
    val hunkIndex: Int,
    val equal: Boolean,
    val leftText: String?,
    val rightText: String?,
    val leftNumber: Int?,
    val rightNumber: Int?,
    val decided: Boolean = false,
    val deleted: Boolean = false,
)

/**
 * Expands hunks into aligned rows. A shorter side of a change is padded with blank rows.
 */
fun alignRows(hunks: List<Hunk>): List<AlignedRow> {
    val rows = mutableListOf<AlignedRow>()
    hunks.forEachIndexed { index, hunk ->
        val count = maxOf(hunk.leftLines.size, hunk.rightLines.size)
        val equal = hunk.kind == HunkKind.Equal
        for (offset in 0 until count) {
            val left = hunk.leftLines.getOrNull(offset)
            val right = hunk.rightLines.getOrNull(offset)
            rows.add(
                AlignedRow(
                    hunkIndex = index,
                    equal = equal,
                    leftText = left,
                    rightText = right,
                    leftNumber = if (left != null) hunk.leftStart + offset else null,
                    rightNumber = if (right != null) hunk.rightStart + offset else null,
                ),
            )
        }
    }
    return rows
}

/**
 * Index of the first row belonging to [hunkIndex], or -1 when that block has no rows.
 */
fun firstRowOf(rows: List<AlignedRow>, hunkIndex: Int): Int {
    return rows.indexOfFirst { it.hunkIndex == hunkIndex }
}

/**
 * Rows drawn for the current decisions.
 *
 * An untouched difference stays aligned, with a blank pad on the shorter side.
 * A decided block shows the text that will be saved, on both sides, and its line numbers are
 * the numbers those lines will have after every decision so far. A block that disappears
 * stays as one row with no line number, so it can still be selected and reverted.
 */
fun previewRows(hunks: List<Hunk>, decisions: Map<Int, BlockDecision>): List<AlignedRow> {
    val rows = mutableListOf<AlignedRow>()
    var leftNumber = 1
    var rightNumber = 1
    hunks.forEachIndexed { index, hunk ->
        val decision = decisions[index] ?: BlockDecision.Original
        val decided = hunk.kind != HunkKind.Equal && decision != BlockDecision.Original
        if (!decided) {
            val count = maxOf(hunk.leftLines.size, hunk.rightLines.size)
            val equal = hunk.kind == HunkKind.Equal
            for (offset in 0 until count) {
                val left = hunk.leftLines.getOrNull(offset)
                val right = hunk.rightLines.getOrNull(offset)
                rows.add(
                    AlignedRow(
                        hunkIndex = index,
                        equal = equal,
                        leftText = left,
                        rightText = right,
                        leftNumber = if (left != null) leftNumber++ else null,
                        rightNumber = if (right != null) rightNumber++ else null,
                    ),
                )
            }
        } else {
            val result = sideLines(hunk, decision, onLeft = true)
            if (result.isEmpty()) {
                rows.add(
                    AlignedRow(
                        hunkIndex = index,
                        equal = false,
                        leftText = null,
                        rightText = null,
                        leftNumber = null,
                        rightNumber = null,
                        decided = true,
                        deleted = true,
                    ),
                )
            } else {
                result.forEach { line ->
                    rows.add(
                        AlignedRow(
                            hunkIndex = index,
                            equal = false,
                            leftText = line,
                            rightText = line,
                            leftNumber = leftNumber++,
                            rightNumber = rightNumber++,
                            decided = true,
                        ),
                    )
                }
            }
        }
    }
    return rows
}
