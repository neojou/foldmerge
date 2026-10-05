package com.neojou.foldmerge.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.neojou.foldmerge.model.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DropTargetTest {
    private val otherList = Rect(100f, 0f, 200f, 100f)

    @Test
    fun directoryRowOnTheOtherSideIsTheTarget() {
        val lib = RowPlace(Side.Right, "lib", directory = true, bounds = Rect(100f, 20f, 180f, 40f))
        assertEquals("lib", resolveDropDirectory(Offset(120f, 30f), Side.Left, listOf(lib), otherList))
    }

    @Test
    fun fileRowDoesNotFallThroughToTheRoot() {
        val file = RowPlace(Side.Right, "App.kt", directory = false, bounds = Rect(100f, 40f, 180f, 60f))
        assertNull(resolveDropDirectory(Offset(120f, 50f), Side.Left, listOf(file), otherList))
    }

    @Test
    fun blankListAreaDropsOnTheRoot() {
        assertEquals("", resolveDropDirectory(Offset(150f, 80f), Side.Left, emptyList(), otherList))
    }

    @Test
    fun sameSideAndOutsideDoNotDrop() {
        val sourceDir = RowPlace(Side.Left, "src", directory = true, bounds = Rect(0f, 0f, 50f, 50f))
        assertNull(resolveDropDirectory(Offset(10f, 10f), Side.Left, listOf(sourceDir), otherList))
        assertNull(resolveDropDirectory(Offset(5f, 5f), Side.Left, emptyList(), otherList))
    }

    @Test
    fun smallestRowWinsAndAFileBlocksALargerDirectory() {
        val directory = RowPlace(Side.Right, "lib", directory = true, bounds = Rect(100f, 0f, 200f, 80f))
        val nested = RowPlace(Side.Right, "lib/nested", directory = true, bounds = Rect(110f, 10f, 150f, 30f))
        assertEquals(
            "lib/nested",
            resolveDropDirectory(Offset(120f, 20f), Side.Left, listOf(directory, nested), otherList),
        )
        val file = RowPlace(Side.Right, "lib/App.kt", directory = false, bounds = Rect(110f, 40f, 160f, 60f))
        assertNull(resolveDropDirectory(Offset(130f, 50f), Side.Left, listOf(directory, file), otherList))
    }
}
