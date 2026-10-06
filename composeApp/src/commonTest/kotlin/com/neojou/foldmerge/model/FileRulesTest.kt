package com.neojou.foldmerge.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileRulesTest {
    @Test
    fun ignoredNamesAreHiddenAndGit() {
        assertTrue(isIgnoredName(".git"))
        assertTrue(isIgnoredName(".hidden"))
        assertTrue(isIgnoredName(".DS_Store"))
        assertFalse(isIgnoredName("git"))
        assertFalse(isIgnoredName("src"))
    }

    @Test
    fun joinPathUsesTheRootSeparator() {
        assertEquals("/left/sub/a.txt", joinPath("/left", "sub/a.txt"))
        assertEquals("/left/a.txt", joinPath("/left/", "/a.txt"))
        assertEquals("C:\\work\\a.txt", joinPath("C:\\work", "a.txt"))
    }

    @Test
    fun collapsedDirectoryHidesChildren() {
        val nodes = listOf(
            FileNode("README", directory = false),
            FileNode("src", directory = true),
            FileNode("src/App.kt", directory = false),
            FileNode("src/main", directory = true),
            FileNode("src/main/Main.kt", directory = false),
        )
        assertEquals(
            listOf("README", "src"),
            visibleFileRows(nodes, emptySet()).map { it.node.relativePath },
        )
        assertEquals(
            listOf("README", "src", "src/App.kt", "src/main"),
            visibleFileRows(nodes, setOf("src")).map { it.node.relativePath },
        )
        val open = visibleFileRows(nodes, setOf("src", "src/main"))
        assertEquals(listOf("README", "src", "src/App.kt", "src/main", "src/main/Main.kt"), open.map { it.node.relativePath })
        assertEquals(listOf(0, 0, 1, 1, 2), open.map { it.depth })
        assertEquals("Main.kt", entryName("src/main/Main.kt"))
    }

    @Test
    fun matchingNamesShareARowAndLaterSiblingsStayAligned() {
        val left = listOf(
            FileNode("README", directory = false),
            FileNode("src", directory = true),
            FileNode("src/main", directory = true),
            FileNode("src/main/Main.kt", directory = false),
            FileNode("src/main.kt", directory = false),
            FileNode("zeta.txt", directory = false),
        )
        val right = listOf(
            FileNode("extra.txt", directory = false),
            FileNode("src", directory = true),
            FileNode("src/main", directory = true),
            FileNode("src/main/Main.kt", directory = false),
            FileNode("src/Other.kt", directory = false),
            FileNode("zeta.txt", directory = false),
        )
        assertEquals(
            listOf(
                null to "extra.txt",
                "README" to null,
                "src" to "src",
                "zeta.txt" to "zeta.txt",
            ),
            alignedPaths(alignVisibleRows(left, right, emptySet(), emptySet())),
        )
        assertEquals(
            listOf(
                null to "extra.txt",
                "README" to null,
                "src" to "src",
                "src/main" to null,
                "src/main.kt" to null,
                "zeta.txt" to "zeta.txt",
            ),
            alignedPaths(alignVisibleRows(left, right, setOf("src"), emptySet())),
        )
        val both = alignVisibleRows(
            left,
            right,
            setOf("src", "src/main"),
            setOf("src", "src/main"),
        )
        assertEquals(
            listOf(
                null to "extra.txt",
                "README" to null,
                "src" to "src",
                "src/main" to "src/main",
                "src/main/Main.kt" to "src/main/Main.kt",
                "src/main.kt" to null,
                null to "src/Other.kt",
                "zeta.txt" to "zeta.txt",
            ),
            alignedPaths(both),
        )
        assertEquals(listOf(0, 0, 0, 1, 2, 1, 1, 0), both.map { it.depth })
    }

    @Test
    fun differentCaseStaysOnSeparateRows() {
        val rows = alignVisibleRows(
            leftNodes = listOf(FileNode("Readme", directory = false), FileNode("b.txt", directory = false)),
            rightNodes = listOf(FileNode("README", directory = false), FileNode("A.txt", directory = false)),
            leftExpanded = emptySet(),
            rightExpanded = emptySet(),
        )
        assertEquals(
            listOf(
                null to "A.txt",
                "b.txt" to null,
                null to "README",
                "Readme" to null,
            ),
            alignedPaths(rows),
        )
    }

    @Test
    fun sameNameDirectoryAndFileShareARow() {
        val left = listOf(
            FileNode("notes", directory = true),
            FileNode("notes/a.txt", directory = false),
        )
        val right = listOf(FileNode("notes", directory = false))
        val closed = alignVisibleRows(left, right, emptySet(), emptySet())
        assertEquals(listOf("notes" to "notes"), alignedPaths(closed))
        assertEquals(true, closed.single().left?.directory)
        assertEquals(false, closed.single().right?.directory)

        val open = alignVisibleRows(left, right, setOf("notes"), emptySet())
        assertEquals(
            listOf("notes" to "notes", "notes/a.txt" to null),
            alignedPaths(open),
        )
        assertEquals(listOf(0, 1), open.map { it.depth })
    }

    @Test
    fun expandingANestedDirectoryDoesNothingUntilItsParentIsOpen() {
        val nodes = listOf(
            FileNode("src", directory = true),
            FileNode("src/main", directory = true),
            FileNode("src/main/Main.kt", directory = false),
        )
        val rows = alignVisibleRows(nodes, emptyList(), setOf("src/main"), emptySet())
        assertEquals(listOf("src" to null), alignedPaths(rows))
    }

    @Test
    fun binaryDetectionRejectsNulAndIllFormedUtf8() {
        assertFalse(looksBinary("文字".encodeToByteArray()))
        assertTrue(looksBinary(byteArrayOf(0x61, 0, 0x62)))
        assertTrue(isValidUtf8("測".encodeToByteArray()))
        assertTrue(isValidUtf8(byteArrayOf(0xC2.toByte(), 0xA9.toByte())))
        assertFalse(isValidUtf8(byteArrayOf(0xFF.toByte())))
        assertFalse(isValidUtf8(byteArrayOf(0xC2.toByte())))
        assertFalse(isValidUtf8(byteArrayOf(0xC0.toByte(), 0x80.toByte())))
        assertFalse(isValidUtf8(byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte())))

        val text = loadDocument("hi\n".encodeToByteArray())
        assertFalse(text.binary)
        assertEquals("hi\n", text.text)

        val binary = loadDocument(byteArrayOf(0, 1, 2, 3))
        assertTrue(binary.binary)
        assertEquals("", binary.text)
        assertTrue(binary.stamp.sizeBytes == 4L)
    }

    private fun alignedPaths(rows: List<PairedFileRow>): List<Pair<String?, String?>> {
        return rows.map { row -> row.left?.relativePath to row.right?.relativePath }
    }
}
