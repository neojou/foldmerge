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
}
