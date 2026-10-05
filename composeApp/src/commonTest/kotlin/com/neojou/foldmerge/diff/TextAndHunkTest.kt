package com.neojou.foldmerge.diff

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextAndHunkTest {
    @Test
    fun splitAndRenderKeepLineEndings() {
        val crlf = splitText("a\r\nb\r\n")
        assertEquals(listOf("a", "b"), crlf.lines)
        assertEquals("\r\n", crlf.newline)
        assertTrue(crlf.trailingNewline)
        assertEquals("a\r\nb\r\n", renderDocument(crlf.lines, crlf.newline, crlf.trailingNewline))

        val noTrailing = splitText("a\nb")
        assertEquals(listOf("a", "b"), noTrailing.lines)
        assertFalse(noTrailing.trailingNewline)
        assertEquals("a\nb", renderDocument(noTrailing.lines, noTrailing.newline, false))

        val blankLines = splitText("a\n\nb\n")
        assertEquals(listOf("a", "", "b"), blankLines.lines)

        val empty = splitText("")
        assertEquals(emptyList(), empty.lines)
        assertEquals("", renderDocument(empty.lines, empty.newline, empty.trailingNewline))
    }

    @Test
    fun identicalFilesAreOneEqualBlock() {
        val hunks = diffHunks(listOf("a", "b"), listOf("a", "b"))
        assertEquals(1, hunks.size)
        assertEquals(HunkKind.Equal, hunks[0].kind)
        assertEquals(1, hunks[0].leftStart)
        assertEquals(2, hunks[0].leftEnd)
        assertEquals(listOf("a", "b"), applySide(hunks, emptyMap(), onLeft = false))
    }

    @Test
    fun insertionDeletionAndReplacement() {
        val inserted = diffHunks(listOf("a", "c"), listOf("a", "b", "c"))
        assertEquals(
            listOf(HunkKind.Equal, HunkKind.RightOnly, HunkKind.Equal),
            inserted.map { it.kind },
        )
        assertEquals(listOf("b"), inserted[1].rightLines)
        assertEquals(2, inserted[1].rightStart)
        assertEquals(0, inserted[1].leftStart)

        val deleted = diffHunks(listOf("a", "b", "c"), listOf("a", "c"))
        assertEquals(
            listOf(HunkKind.Equal, HunkKind.LeftOnly, HunkKind.Equal),
            deleted.map { it.kind },
        )
        assertEquals(listOf("b"), deleted[1].leftLines)
        assertEquals(2, deleted[1].leftStart)

        val replaced = diffHunks(listOf("a", "b", "c", "d"), listOf("a", "x", "d"))
        assertEquals(
            listOf(HunkKind.Equal, HunkKind.Modified, HunkKind.Equal),
            replaced.map { it.kind },
        )
        assertEquals(listOf("b", "c"), replaced[1].leftLines)
        assertEquals(listOf("x"), replaced[1].rightLines)
        assertEquals(2, replaced[1].leftStart)
        assertEquals(3, replaced[1].leftEnd)
        assertEquals(2, replaced[1].rightStart)
        assertEquals(4, replaced[2].leftStart)
        assertEquals(3, replaced[2].rightStart)
        assertEquals("內容不同 · 左 2–3 行 · 右 第 2 行", hunkTitle(replaced[1]))
    }

    @Test
    fun emptySideIsOnlyTheOtherSide() {
        assertEquals(HunkKind.RightOnly, diffHunks(emptyList(), listOf("a")).single().kind)
        assertEquals(HunkKind.LeftOnly, diffHunks(listOf("a"), emptyList()).single().kind)
        assertEquals(emptyList(), diffHunks(emptyList(), emptyList()))
    }

    @Test
    fun copyEitherDirectionAndRevert() {
        val left = listOf("alpha", "beta", "gamma")
        val right = listOf("alpha", "BETA", "gamma")
        val hunks = diffHunks(left, right)
        val index = hunks.indexOfFirst { it.kind == HunkKind.Modified }
        val toRight = mapOf(index to BlockDecision.CopyLeftToRight)
        assertEquals(left, applySide(hunks, toRight, onLeft = true))
        assertEquals(left, applySide(hunks, toRight, onLeft = false))
        assertTrue(decisionsDirty(hunks, toRight))

        val toLeft = mapOf(index to BlockDecision.CopyRightToLeft)
        assertEquals(right, applySide(hunks, toLeft, onLeft = true))
        assertEquals(right, applySide(hunks, toLeft, onLeft = false))
        assertTrue(decisionsDirty(hunks, toLeft))

        assertEquals(left, applySide(hunks, emptyMap(), onLeft = true))
        assertEquals(right, applySide(hunks, emptyMap(), onLeft = false))
        assertFalse(decisionsDirty(hunks, emptyMap()))
        assertFalse(hasBlockDecision(emptyMap()))
        assertTrue(hasBlockDecision(toRight))
    }

    @Test
    fun neighboringBlocksStayIndependent() {
        val left = listOf("a", "b", "c", "d", "e")
        val right = listOf("a", "B", "c", "D", "e")
        val hunks = diffHunks(left, right)
        val first = hunks.indexOfFirst { it.kind == HunkKind.Modified }
        val second = hunks.indexOfLast { it.kind == HunkKind.Modified }
        assertTrue(first != second)
        val toRight = mapOf(first to BlockDecision.CopyLeftToRight)
        assertEquals(listOf("a", "b", "c", "D", "e"), applySide(hunks, toRight, onLeft = false))
        assertEquals(left, applySide(hunks, toRight, onLeft = true))
    }

    @Test
    fun allDecisionsRoundTripAndMatchLongestCommonSubsequence() {
        val samples = listOf(
            emptyList<String>() to emptyList(),
            listOf("a") to listOf("a"),
            listOf("a") to listOf("b"),
            listOf("a", "b", "c") to listOf("a", "x", "c"),
            listOf("a", "b", "c") to listOf("a", "b", "c", "d"),
            listOf("a", "b", "c", "d") to listOf("a", "c"),
            listOf("a", "b", "b", "c") to listOf("a", "b", "c"),
            listOf("only") to emptyList(),
            emptyList<String>() to listOf("only"),
            listOf("a", "b", "c", "d", "e") to listOf("a", "x", "c", "y", "e"),
            listOf("z", "a", "b") to listOf("a", "b", "z"),
        )
        samples.forEach { (left, right) -> assertOptimalRoundTrip(left, right) }

        val random = Random(1)
        val alphabet = listOf("a", "b", "c", "d", "e")
        repeat(40) {
            val left = List(random.nextInt(0, 30)) { alphabet[random.nextInt(alphabet.size)] }
            val right = left.toMutableList()
            repeat(random.nextInt(0, 8)) {
                when (random.nextInt(3)) {
                    0 -> if (right.isNotEmpty()) right.removeAt(random.nextInt(right.size))
                    1 -> right.add(random.nextInt(right.size + 1), alphabet[random.nextInt(alphabet.size)])
                    else -> if (right.isNotEmpty()) right[random.nextInt(right.size)] = alphabet[random.nextInt(alphabet.size)]
                }
            }
            assertOptimalRoundTrip(left, right)
        }
    }

    @Test
    fun lineMarksUseStrikeForLeftAndUnderlineForRight() {
        assertEquals(LineMark.Removed to LineMark.Added, lineMarks(HunkKind.Modified))
        assertEquals(LineMark.Removed to LineMark.Plain, lineMarks(HunkKind.LeftOnly))
        assertEquals(LineMark.Plain to LineMark.Added, lineMarks(HunkKind.RightOnly))
        assertEquals(LineMark.Plain to LineMark.Plain, lineMarks(HunkKind.Equal))
    }

    @Test
    fun alignedRowsNumberRealLinesAndPadTheShorterSide() {
        val equal = alignRows(diffHunks(listOf("a", "b"), listOf("a", "b")))
        assertEquals(listOf(1, 2), equal.map { it.leftNumber })
        assertEquals(listOf(1, 2), equal.map { it.rightNumber })
        assertEquals(listOf("a", "b"), equal.map { it.leftText })
        assertTrue(equal.all { it.equal })

        val blank = alignRows(diffHunks(listOf(""), listOf(""))).single()
        assertEquals("", blank.leftText)
        assertEquals("", blank.rightText)
        assertEquals(1, blank.leftNumber)
        assertEquals(1, blank.rightNumber)

        val rightOnly = alignRows(diffHunks(listOf("a", "c"), listOf("a", "b", "c")))
        assertEquals(listOf("a", null, "c"), rightOnly.map { it.leftText })
        assertEquals(listOf("a", "b", "c"), rightOnly.map { it.rightText })
        assertEquals(listOf(1, null, 2), rightOnly.map { it.leftNumber })
        assertEquals(listOf(1, 2, 3), rightOnly.map { it.rightNumber })
        assertFalse(rightOnly[1].equal)
        assertEquals(1, firstRowOf(rightOnly, 1))

        val leftOnly = alignRows(diffHunks(listOf("a", "b", "c"), listOf("a", "c")))
        assertEquals(listOf("a", "b", "c"), leftOnly.map { it.leftText })
        assertEquals(listOf("a", null, "c"), leftOnly.map { it.rightText })
        assertEquals(listOf(1, 2, 3), leftOnly.map { it.leftNumber })
        assertEquals(listOf(1, null, 2), leftOnly.map { it.rightNumber })

        val modified = alignRows(diffHunks(listOf("a", "b", "c", "d"), listOf("a", "x", "d")))
        assertEquals(listOf("a", "b", "c", "d"), modified.mapNotNull { it.leftText })
        assertEquals(listOf("a", "x", "d"), modified.mapNotNull { it.rightText })
        assertEquals(listOf(1, 2, 3, 4), modified.mapNotNull { it.leftNumber })
        assertEquals(listOf(1, 2, 3), modified.mapNotNull { it.rightNumber })
        assertNull(modified[2].rightText)
        assertNull(modified[2].rightNumber)
        assertEquals("c", modified[2].leftText)
        assertEquals(3, modified[2].leftNumber)
        assertEquals(-1, firstRowOf(modified, 9))
    }

    @Test
    fun copyingFromAnEmptySideRemovesTheOtherSide() {
        val modified = diffHunks(listOf("a", "b", "c", "d"), listOf("a", "x", "d"))
        val modifiedIndex = modified.indexOfFirst { it.kind == HunkKind.Modified }
        val useLeft = mapOf(modifiedIndex to BlockDecision.CopyLeftToRight)
        assertEquals(listOf("a", "b", "c", "d"), applySide(modified, useLeft, onLeft = true))
        assertEquals(listOf("a", "b", "c", "d"), applySide(modified, useLeft, onLeft = false))
        val useRight = mapOf(modifiedIndex to BlockDecision.CopyRightToLeft)
        assertEquals(listOf("a", "x", "d"), applySide(modified, useRight, onLeft = true))
        assertEquals(listOf("a", "x", "d"), applySide(modified, useRight, onLeft = false))

        val leftOnly = diffHunks(listOf("a", "b", "c"), listOf("a", "c"))
        val leftIndex = leftOnly.indexOfFirst { it.kind == HunkKind.LeftOnly }
        val deleteLeft = mapOf(leftIndex to BlockDecision.CopyRightToLeft)
        assertEquals(listOf("a", "c"), applySide(leftOnly, deleteLeft, onLeft = true))
        assertEquals(listOf("a", "c"), applySide(leftOnly, deleteLeft, onLeft = false))
        assertTrue(decisionsDirty(leftOnly, deleteLeft))
        val insertRight = mapOf(leftIndex to BlockDecision.CopyLeftToRight)
        assertEquals(listOf("a", "b", "c"), applySide(leftOnly, insertRight, onLeft = false))
        assertEquals(listOf("a", "b", "c"), applySide(leftOnly, insertRight, onLeft = true))

        val rightOnly = diffHunks(listOf("a", "c"), listOf("a", "b", "c"))
        val rightIndex = rightOnly.indexOfFirst { it.kind == HunkKind.RightOnly }
        val deleteRight = mapOf(rightIndex to BlockDecision.CopyLeftToRight)
        assertEquals(listOf("a", "c"), applySide(rightOnly, deleteRight, onLeft = false))
        assertEquals(listOf("a", "c"), applySide(rightOnly, deleteRight, onLeft = true))
        assertTrue(decisionsDirty(rightOnly, deleteRight))
    }

    @Test
    fun previewShowsTheDecidedResultAndRenumbersLaterLines() {
        val hunks = diffHunks(listOf("a", "b", "c"), listOf("a", "c"))
        val untouched = previewRows(hunks, emptyMap())
        assertEquals(listOf("a", "b", "c"), untouched.map { it.leftText })
        assertEquals(listOf("a", null, "c"), untouched.map { it.rightText })
        assertEquals(listOf(1, 2, 3), untouched.map { it.leftNumber })
        assertEquals(listOf(1, null, 2), untouched.map { it.rightNumber })
        assertTrue(untouched.none { it.decided })

        val leftIndex = hunks.indexOfFirst { it.kind == HunkKind.LeftOnly }
        val inserted = previewRows(hunks, mapOf(leftIndex to BlockDecision.CopyLeftToRight))
        assertEquals(listOf("a", "b", "c"), inserted.map { it.leftText })
        assertEquals(listOf("a", "b", "c"), inserted.map { it.rightText })
        assertEquals(listOf(1, 2, 3), inserted.map { it.leftNumber })
        assertEquals(listOf(1, 2, 3), inserted.map { it.rightNumber })
        assertTrue(inserted[1].decided)
        assertFalse(inserted[1].deleted)
        assertEquals(3, inserted.size)

        val deleted = previewRows(hunks, mapOf(leftIndex to BlockDecision.CopyRightToLeft))
        assertTrue(deleted[1].deleted)
        assertNull(deleted[1].leftText)
        assertNull(deleted[1].leftNumber)
        assertNull(deleted[1].rightNumber)
        assertEquals("c", deleted[2].leftText)
        assertEquals(2, deleted[2].leftNumber)
        assertEquals(2, deleted[2].rightNumber)
        assertEquals(1, firstRowOf(deleted, leftIndex))

        val modified = diffHunks(listOf("a", "b", "c", "d"), listOf("a", "x", "d"))
        val modifiedIndex = modified.indexOfFirst { it.kind == HunkKind.Modified }
        val replaced = previewRows(modified, mapOf(modifiedIndex to BlockDecision.CopyLeftToRight))
        val decidedRows = replaced.filter { it.hunkIndex == modifiedIndex }
        assertEquals(listOf("b", "c"), decidedRows.map { it.leftText })
        assertEquals(decidedRows.map { it.leftText }, decidedRows.map { it.rightText })
        assertTrue(decidedRows.all { it.decided && !it.deleted })
        assertEquals("d", replaced.last().leftText)
        assertEquals(4, replaced.last().leftNumber)
        assertEquals(4, replaced.last().rightNumber)
    }
}

private fun assertOptimalRoundTrip(left: List<String>, right: List<String>) {
    val hunks = diffHunks(left, right)
    val takeLeft = hunks.indices.associateWith { BlockDecision.CopyLeftToRight }
    val takeRight = hunks.indices.associateWith { BlockDecision.CopyRightToLeft }
    assertEquals(left, applySide(hunks, takeLeft, onLeft = true), "left=$left right=$right")
    assertEquals(left, applySide(hunks, takeLeft, onLeft = false), "left=$left right=$right")
    assertEquals(right, applySide(hunks, takeRight, onLeft = true), "left=$left right=$right")
    assertEquals(right, applySide(hunks, takeRight, onLeft = false), "left=$left right=$right")
    assertEquals(left, applySide(hunks, emptyMap(), onLeft = true), "left=$left right=$right")
    assertEquals(right, applySide(hunks, emptyMap(), onLeft = false), "left=$left right=$right")
    assertEquals(left, hunks.flatMap { it.leftLines }, "left=$left right=$right")
    assertEquals(right, hunks.flatMap { it.rightLines }, "left=$left right=$right")

    var nextLeft = 1
    var nextRight = 1
    hunks.forEach { hunk ->
        if (hunk.leftLines.isEmpty()) {
            assertEquals(0, hunk.leftStart)
        } else {
            assertEquals(nextLeft, hunk.leftStart)
            nextLeft += hunk.leftLines.size
        }
        if (hunk.rightLines.isEmpty()) {
            assertEquals(0, hunk.rightStart)
        } else {
            assertEquals(nextRight, hunk.rightStart)
            nextRight += hunk.rightLines.size
        }
        if (hunk.kind == HunkKind.Equal) {
            assertEquals(hunk.leftLines, hunk.rightLines)
        }
    }
    val equalLines = hunks.filter { it.kind == HunkKind.Equal }.sumOf { it.leftLines.size }
    assertEquals(lcsLength(left, right), equalLines, "left=$left right=$right")
}

private fun lcsLength(left: List<String>, right: List<String>): Int {
    val previous = IntArray(right.size + 1)
    val current = IntArray(right.size + 1)
    for (leftLine in left) {
        for (column in 1..right.size) {
            current[column] = if (leftLine == right[column - 1]) {
                previous[column - 1] + 1
            } else {
                maxOf(previous[column], current[column - 1])
            }
        }
        current.copyInto(previous)
    }
    return previous[right.size]
}
