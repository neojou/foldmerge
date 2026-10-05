package com.neojou.foldmerge

import com.neojou.foldmerge.model.FoldSession
import com.neojou.foldmerge.model.Side
import com.neojou.foldmerge.model.isIgnoredName
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWorkspaceTest {
    @Test
    fun listSkipsHiddenAndGitAndRoundTripsText() {
        val root = Files.createTempDirectory("foldmerge")
        try {
            Files.writeString(root.resolve("keep.txt"), "hi\n")
            Files.createDirectories(root.resolve("sub"))
            Files.writeString(root.resolve("sub").resolve("inner.txt"), "in\n")
            Files.writeString(root.resolve(".secret"), "no\n")
            Files.createDirectories(root.resolve(".git"))
            Files.writeString(root.resolve(".git").resolve("config"), "x\n")
            Files.write(root.resolve("bin.dat"), byteArrayOf(0, 1, 2, 4))

            val access = DesktopWorkspace()
            val paths = runBlocking { access.listTree(root.toString()) }.map { it.relativePath }.toSet()
            assertTrue("keep.txt" in paths)
            assertTrue("sub" in paths)
            assertTrue("sub/inner.txt" in paths)
            assertTrue(paths.none { path -> path.split('/').any(::isIgnoredName) })

            val text = runBlocking { access.readDocument(root.resolve("keep.txt").toString()) }
            assertFalse(text.binary)
            assertEquals("hi\n", text.text)

            val binary = runBlocking { access.readDocument(root.resolve("bin.dat").toString()) }
            assertTrue(binary.binary)
            assertEquals("", binary.text)

            runBlocking { access.writeDocument(root.resolve("keep.txt").toString(), "yo\n") }
            assertEquals("yo\n", Files.readString(root.resolve("keep.txt")))
            val stamp = runBlocking { access.stampOf(root.resolve("keep.txt").toString()) }
            assertEquals(text.stamp.sizeBytes, 3L)
            assertTrue(stamp != null && stamp != text.stamp)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun copyCreatesParentsAndRefusesToOverwrite() = runBlocking {
        val sourceRoot = Files.createTempDirectory("foldmerge-src")
        val destinationRoot = Files.createTempDirectory("foldmerge-dst")
        try {
            val payload = byteArrayOf(0, 9, 4)
            Files.createDirectories(sourceRoot.resolve("sub"))
            Files.write(sourceRoot.resolve("sub").resolve("bin.dat"), payload)
            val access = DesktopWorkspace()
            val destination = destinationRoot.resolve("sub").resolve("bin.dat").toString()
            access.copyFile(sourceRoot.resolve("sub").resolve("bin.dat").toString(), destination)
            assertTrue(Files.readAllBytes(destinationRoot.resolve("sub").resolve("bin.dat")).contentEquals(payload))
            var refused = false
            try {
                access.copyFile(sourceRoot.resolve("sub").resolve("bin.dat").toString(), destination)
            } catch (error: IllegalStateException) {
                refused = error.message == "對方已有這個檔案"
            }
            assertTrue(refused)
            assertTrue(Files.readAllBytes(destinationRoot.resolve("sub").resolve("bin.dat")).contentEquals(payload))
        } finally {
            sourceRoot.toFile().deleteRecursively()
            destinationRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun missingDirectoryIsReported() = runBlocking {
        val parent = Files.createTempDirectory("foldmerge-missing")
        try {
            val missing = parent.resolve("absent").toString()
            val session = FoldSession(DesktopWorkspace())
            session.openRoot(Side.Left, missing)
            assertEquals(missing, session.leftRoot)
            assertTrue(session.notice?.contains("無法讀取目錄") == true)
            assertFalse(session.leftBusy)
            assertTrue(session.leftNodes.isEmpty())
        } finally {
            parent.toFile().deleteRecursively()
        }
    }
}
