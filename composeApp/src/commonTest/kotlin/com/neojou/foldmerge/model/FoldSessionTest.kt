package com.neojou.foldmerge.model

import com.neojou.foldmerge.diff.BlockDecision
import com.neojou.foldmerge.diff.HunkKind
import com.neojou.foldmerge.diff.applySide
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoldSessionTest {
    @Test
    fun saveBeforeComparisonDoesNotWrite() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/a.txt", "one\n")
        files.put("/right/a.txt", "two\n")
        val session = FoldSession(files)
        session.openRoot(Side.Left, "/left")
        session.openRoot(Side.Right, "/right")
        assertEquals("尚未比對完，不能儲存", session.saveGateMessage())
        assertFalse(session.canSave(Side.Left))
        assertFalse(session.canSave(Side.Right))
        session.save(Side.Right)
        session.save(Side.Left)
        assertEquals("one\n", files.text("/left/a.txt"))
        assertEquals("two\n", files.text("/right/a.txt"))
    }

    @Test
    fun adoptLeftUpdatesTheRightFileAndComparesAgain() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/sub/a.txt", "alpha\nbeta\ngamma\n")
        files.put("/right/sub/a.txt", "alpha\nBETA\ngamma\n")
        val session = FoldSession(files)
        session.openRoot(Side.Left, "/left")
        session.openRoot(Side.Right, "/right")
        session.select(Side.Left, "sub/a.txt")
        session.select(Side.Right, "sub/a.txt")
        val ready = assertIs<ComparePhase.Ready>(session.phase)
        val index = ready.hunks.indexOfFirst { it.kind == HunkKind.Modified }
        session.decide(index, BlockDecision.CopyLeftToRight)
        assertFalse(session.canSave(Side.Left))
        assertTrue(session.canSave(Side.Right))
        assertNull(session.saveGateMessage())
        session.save(Side.Left)
        assertEquals("alpha\nBETA\ngamma\n", files.text("/right/sub/a.txt"))
        session.save(Side.Right)
        assertEquals("alpha\nbeta\ngamma\n", files.text("/right/sub/a.txt"))
        assertEquals("alpha\nbeta\ngamma\n", files.text("/left/sub/a.txt"))
        val again = assertIs<ComparePhase.Ready>(session.phase)
        assertTrue(again.hunks.all { it.kind == HunkKind.Equal })
        assertFalse(session.canSave(Side.Left))
        assertFalse(session.canSave(Side.Right))
        assertNull(session.saveGateMessage())
        assertEquals("已儲存，並重新比較。", session.notice)
    }

    @Test
    fun adoptRightAndRevertDoNotChangeTheFile() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/a.txt", "left\n")
        files.put("/right/a.txt", "right\n")
        val session = openPair(files)
        val ready = assertIs<ComparePhase.Ready>(session.phase)
        val index = ready.hunks.indexOfFirst { it.kind != HunkKind.Equal }
        session.decide(index, BlockDecision.CopyLeftToRight)
        assertFalse(session.canSave(Side.Left))
        assertTrue(session.canSave(Side.Right))
        session.revert(index)
        assertFalse(session.canSave(Side.Left))
        assertFalse(session.canSave(Side.Right))
        session.save(Side.Right)
        assertEquals("left\n", files.text("/left/a.txt"))
        assertEquals("right\n", files.text("/right/a.txt"))
    }

    @Test
    fun externalEditBlocksSaveUntilConfirmed() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/a.txt", "from-left\n")
        files.put("/right/a.txt", "from-right\n")
        val session = openPair(files)
        val ready = assertIs<ComparePhase.Ready>(session.phase)
        val index = ready.hunks.indexOfFirst { it.kind != HunkKind.Equal }
        session.decide(index, BlockDecision.CopyLeftToRight)
        files.put("/right/a.txt", "edited-outside\n")
        assertFalse(session.overwriteLeft)
        session.save(Side.Right)
        assertTrue(session.askOverwrite)
        assertFalse(session.canSave(Side.Right))
        assertTrue(session.overwriteRight)
        assertEquals("edited-outside\n", files.text("/right/a.txt"))
        assertEquals("from-left\n", files.text("/left/a.txt"))
        session.save(Side.Right, force = true)
        assertFalse(session.askOverwrite)
        assertEquals("from-left\n", files.text("/right/a.txt"))
    }

    @Test
    fun copyRightToLeftUpdatesOnlyTheLeftFile() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/a.txt", "alpha\nbeta\ngamma\n")
        files.put("/right/a.txt", "alpha\nBETA\ngamma\n")
        val session = openPair(files)
        val ready = assertIs<ComparePhase.Ready>(session.phase)
        val index = ready.hunks.indexOfFirst { it.kind == HunkKind.Modified }
        session.decide(index, BlockDecision.CopyRightToLeft)
        assertTrue(session.canSave(Side.Left))
        assertFalse(session.canSave(Side.Right))
        session.save(Side.Right)
        assertEquals("alpha\nbeta\ngamma\n", files.text("/left/a.txt"))
        session.save(Side.Left)
        assertEquals("alpha\nBETA\ngamma\n", files.text("/left/a.txt"))
        assertEquals("alpha\nBETA\ngamma\n", files.text("/right/a.txt"))
        val again = assertIs<ComparePhase.Ready>(session.phase)
        assertTrue(again.hunks.all { it.kind == HunkKind.Equal })
        assertFalse(session.canSave(Side.Left))
        assertFalse(session.canSave(Side.Right))
        assertEquals("已儲存，並重新比較。", session.notice)
    }

    @Test
    fun mixedDirectionsWriteBothFiles() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/a.txt", "a\nb\nc\nd\n")
        files.put("/right/a.txt", "a\nB\nc\nD\n")
        val session = openPair(files)
        val ready = assertIs<ComparePhase.Ready>(session.phase)
        val first = ready.hunks.indexOfFirst { it.kind == HunkKind.Modified }
        val second = ready.hunks.indexOfLast { it.kind == HunkKind.Modified }
        session.decide(first, BlockDecision.CopyRightToLeft)
        session.decide(second, BlockDecision.CopyLeftToRight)
        assertTrue(session.canSave(Side.Left))
        assertTrue(session.canSave(Side.Right))
        session.save(Side.Left)
        assertEquals("a\nB\nc\nd\n", files.text("/left/a.txt"))
        assertEquals("a\nB\nc\nD\n", files.text("/right/a.txt"))
        assertFalse(session.canSave(Side.Left))
        assertTrue(session.canSave(Side.Right))
        assertEquals("已儲存左檔。", session.notice)
        assertIs<ComparePhase.Ready>(session.phase)
        session.save(Side.Right)
        assertEquals("a\nB\nc\nd\n", files.text("/right/a.txt"))
        val again = assertIs<ComparePhase.Ready>(session.phase)
        assertTrue(again.hunks.all { it.kind == HunkKind.Equal })
        assertEquals("已儲存，並重新比較。", session.notice)
    }

    @Test
    fun externalEditOnTheLeftBlocksSaveUntilConfirmed() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/a.txt", "from-left\n")
        files.put("/right/a.txt", "from-right\n")
        val session = openPair(files)
        val ready = assertIs<ComparePhase.Ready>(session.phase)
        val index = ready.hunks.indexOfFirst { it.kind != HunkKind.Equal }
        session.decide(index, BlockDecision.CopyRightToLeft)
        files.put("/left/a.txt", "edited-outside\n")
        session.save(Side.Left)
        assertTrue(session.askOverwrite)
        assertTrue(session.overwriteLeft)
        assertFalse(session.overwriteRight)
        assertEquals("edited-outside\n", files.text("/left/a.txt"))
        assertEquals("from-right\n", files.text("/right/a.txt"))
        session.save(Side.Left, force = true)
        assertFalse(session.askOverwrite)
        assertEquals("from-right\n", files.text("/left/a.txt"))
        assertEquals("from-right\n", files.text("/right/a.txt"))
    }

    @Test
    fun failedWriteLeavesTheFileAndKeepsTheButtonEnabled() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/a.txt", "a\nb\nc\nd\n")
        files.put("/right/a.txt", "a\nB\nc\nD\n")
        val flaky = FailingWrite(files)
        val session = FoldSession(flaky)
        session.openRoot(Side.Left, "/left")
        session.openRoot(Side.Right, "/right")
        session.select(Side.Left, "a.txt")
        session.select(Side.Right, "a.txt")
        val ready = assertIs<ComparePhase.Ready>(session.phase)
        val first = ready.hunks.indexOfFirst { it.kind == HunkKind.Modified }
        val second = ready.hunks.indexOfLast { it.kind == HunkKind.Modified }
        session.decide(first, BlockDecision.CopyRightToLeft)
        session.decide(second, BlockDecision.CopyLeftToRight)
        session.save(Side.Left)
        assertTrue(session.notice.orEmpty().contains("儲存失敗"))
        assertTrue(session.canSave(Side.Left))
        assertTrue(session.canSave(Side.Right))
        assertEquals("a\nb\nc\nd\n", files.text("/left/a.txt"))
        assertEquals("a\nB\nc\nD\n", files.text("/right/a.txt"))
        flaky.fail = false
        session.save(Side.Left)
        assertFalse(session.canSave(Side.Left))
        assertTrue(session.canSave(Side.Right))
        assertEquals("a\nB\nc\nd\n", files.text("/left/a.txt"))
        assertEquals("a\nB\nc\nD\n", files.text("/right/a.txt"))
        assertEquals("已儲存左檔。", session.notice)
    }

    @Test
    fun binaryFileCannotBeSaved() = runBlocking {
        val files = MemoryWorkspace()
        files.putBytes("/left/a.bin", byteArrayOf(0, 1, 2, 3))
        files.put("/right/a.txt", "text\n")
        val session = FoldSession(files)
        session.openRoot(Side.Left, "/left")
        session.openRoot(Side.Right, "/right")
        session.select(Side.Left, "a.bin")
        session.select(Side.Right, "a.txt")
        val phase = assertIs<ComparePhase.NotText>(session.phase)
        assertTrue(phase.detail.contains("無法以文字比較"))
        assertFalse(session.canSave(Side.Left))
        assertFalse(session.canSave(Side.Right))
        session.save(Side.Right)
        assertEquals("text\n", files.text("/right/a.txt"))
    }

    @Test
    fun copyIntoDirectoryKeepsBytesAndSelectsIt() = runBlocking {
        val files = MemoryWorkspace()
        files.putBytes("/left/src/App.kt", byteArrayOf(9, 0, 4))
        files.put("/right/App.kt", "root-stay\n")
        files.put("/right/lib/keep.txt", "stay\n")
        val session = FoldSession(files)
        session.openRoot(Side.Left, "/left")
        session.openRoot(Side.Right, "/right")
        session.select(Side.Left, "src/App.kt")
        session.toggleExpanded(Side.Right, "lib")
        session.copyInto(Side.Left, "src/App.kt", Side.Right, "lib")
        assertContentEquals(byteArrayOf(9, 0, 4), files.bytes("/right/lib/App.kt"))
        assertContentEquals(byteArrayOf(9, 0, 4), files.bytes("/left/src/App.kt"))
        assertEquals("stay\n", files.text("/right/lib/keep.txt"))
        assertEquals("root-stay\n", files.text("/right/App.kt"))
        assertEquals("src/App.kt", session.leftSelected)
        assertEquals("lib/App.kt", session.rightSelected)
        assertTrue("lib" in session.rightExpanded)
        assertEquals("已複製到右側。", session.notice)

        val back = MemoryWorkspace()
        back.put("/right/src/Notes.kt", "beta\n")
        back.put("/left/lib/keep.txt", "k\n")
        val other = FoldSession(back)
        other.openRoot(Side.Left, "/left")
        other.openRoot(Side.Right, "/right")
        other.select(Side.Right, "src/Notes.kt")
        other.copyInto(Side.Right, "src/Notes.kt", Side.Left, "lib")
        assertEquals("beta\n", back.text("/left/lib/Notes.kt"))
        assertEquals("k\n", back.text("/left/lib/keep.txt"))
        assertEquals("lib/Notes.kt", other.leftSelected)
        assertEquals("src/Notes.kt", other.rightSelected)
        assertEquals("已複製到左側。", other.notice)
    }

    @Test
    fun copyOntoRootUsesTheFileName() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/src/App.kt", "fun main() {}\n")
        files.put("/right/lib/App.kt", "nested\n")
        files.put("/right/other.txt", "stay\n")
        val session = FoldSession(files)
        session.openRoot(Side.Left, "/left")
        session.openRoot(Side.Right, "/right")
        session.copyInto(Side.Left, "src/App.kt", Side.Right, "")
        assertEquals("fun main() {}\n", files.text("/right/App.kt"))
        assertEquals("nested\n", files.text("/right/lib/App.kt"))
        assertEquals("stay\n", files.text("/right/other.txt"))
        assertEquals("fun main() {}\n", files.text("/left/src/App.kt"))
        assertNull(session.leftSelected)
        assertEquals("App.kt", session.rightSelected)
        assertEquals("已複製到右側。", session.notice)
    }

    @Test
    fun copyRefusesWhenThatDirectoryAlreadyHasTheName() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/src/App.kt", "fresh\n")
        files.put("/left/notes.txt", "new\n")
        files.put("/right/lib/App.kt", "old\n")
        files.put("/right/lib/notes.txt/inside.txt", "x\n")
        files.put("/right/App.kt", "root-old\n")
        files.put("/right/other.txt", "stay\n")
        val session = FoldSession(files)
        session.openRoot(Side.Left, "/left")
        session.openRoot(Side.Right, "/right")
        session.select(Side.Right, "other.txt")

        session.copyInto(Side.Left, "src/App.kt", Side.Left, "lib")
        assertNull(session.notice)
        assertEquals("fresh\n", files.text("/left/src/App.kt"))

        session.copyInto(Side.Left, "src/App.kt", Side.Right, "missing")
        assertEquals("複製失敗：找不到資料夾", session.notice)
        assertFailsWith<NoSuchElementException> { files.text("/right/missing/App.kt") }

        session.copyInto(Side.Left, "src/App.kt", Side.Right, "lib")
        assertEquals("old\n", files.text("/right/lib/App.kt"))
        assertEquals("這個目錄已有同名檔案", session.notice)
        assertEquals("other.txt", session.rightSelected)

        session.copyInto(Side.Left, "notes.txt", Side.Right, "lib")
        assertEquals("x\n", files.text("/right/lib/notes.txt/inside.txt"))
        assertFailsWith<NoSuchElementException> { files.text("/right/lib/notes.txt") }
        assertEquals("這個目錄已有同名檔案", session.notice)

        session.copyInto(Side.Left, "src/App.kt", Side.Right, "")
        assertEquals("root-old\n", files.text("/right/App.kt"))
        assertEquals("fresh\n", files.text("/left/src/App.kt"))
        assertEquals("這個目錄已有同名檔案", session.notice)
        assertEquals("other.txt", session.rightSelected)
    }

    @Test
    fun copyThatChangesSelectionDropsUnsavedDecisions() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/a.txt", "alpha\n")
        files.put("/right/b.txt", "beta\n")
        val session = FoldSession(files)
        session.openRoot(Side.Left, "/left")
        session.openRoot(Side.Right, "/right")
        session.select(Side.Left, "a.txt")
        session.select(Side.Right, "b.txt")
        val ready = assertIs<ComparePhase.Ready>(session.phase)
        val index = ready.hunks.indexOfFirst { it.kind != HunkKind.Equal }
        session.decide(index, BlockDecision.CopyLeftToRight)
        assertTrue(session.canSave(Side.Right))
        session.copyInto(Side.Left, "a.txt", Side.Right, "")
        assertEquals("alpha\n", files.text("/right/a.txt"))
        assertEquals("beta\n", files.text("/right/b.txt"))
        assertEquals("a.txt", session.leftSelected)
        assertEquals("a.txt", session.rightSelected)
        assertEquals("已捨棄尚未儲存的區塊決定。", session.notice)
        assertFalse(session.canSave(Side.Right))
    }

    @Test
    fun subdirectoriesStartCollapsedAndCanExpand() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/src/App.kt", "fun main() {}\n")
        files.put("/left/.secret", "no\n")
        val session = FoldSession(files)
        session.openRoot(Side.Left, "/left")
        assertEquals(listOf("src", "src/App.kt"), session.leftNodes.map { it.relativePath })
        assertTrue(session.leftExpanded.isEmpty())
        assertEquals(
            listOf("src"),
            visibleFileRows(session.leftNodes, session.leftExpanded).map { it.node.relativePath },
        )
        session.toggleExpanded(Side.Left, "src")
        assertEquals(
            listOf("src", "src/App.kt"),
            visibleFileRows(session.leftNodes, session.leftExpanded).map { it.node.relativePath },
        )
    }

    @Test
    fun insertBlankAboveShiftsOnlyThatSide() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/a.txt", "a\nb\nc\n")
        files.put("/right/a.txt", "a\nb\nc\n")
        val session = openPair(files)
        session.insertBlankAbove(Side.Left, 2)
        val ready = assertIs<ComparePhase.Ready>(session.phase)
        assertTrue(session.decisions.isEmpty())
        assertEquals(listOf("a", "", "b", "c"), applySide(ready.hunks, session.decisions, onLeft = true))
        assertEquals(listOf("a", "b", "c"), applySide(ready.hunks, session.decisions, onLeft = false))
        session.insertBlankAbove(Side.Left, 0)
        session.insertBlankAbove(Side.Left, 9)
        val still = assertIs<ComparePhase.Ready>(session.phase)
        assertEquals(listOf("a", "", "b", "c"), applySide(still.hunks, session.decisions, onLeft = true))
    }

    @Test
    fun deleteLineRemovesOnlyThatRow() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/a.txt", "a\nb\nc\n")
        files.put("/right/a.txt", "a\nb\nc\n")
        val session = openPair(files)
        session.deleteLine(Side.Left, 2)
        val ready = assertIs<ComparePhase.Ready>(session.phase)
        assertTrue(session.decisions.isEmpty())
        assertEquals(listOf("a", "c"), applySide(ready.hunks, session.decisions, onLeft = true))
        assertEquals(listOf("a", "b", "c"), applySide(ready.hunks, session.decisions, onLeft = false))
        session.deleteLine(Side.Left, 9)
        val still = assertIs<ComparePhase.Ready>(session.phase)
        assertEquals(listOf("a", "c"), applySide(still.hunks, session.decisions, onLeft = true))

        session.deleteLine(Side.Right, 1)
        session.deleteLine(Side.Right, 1)
        session.deleteLine(Side.Right, 1)
        val empty = assertIs<ComparePhase.Ready>(session.phase)
        assertEquals(emptyList(), applySide(empty.hunks, session.decisions, onLeft = false))
        assertEquals(listOf("a", "c"), applySide(empty.hunks, session.decisions, onLeft = true))
        session.save(Side.Right)
        assertEquals("", files.text("/right/a.txt"))
        assertEquals("a\nb\nc\n", files.text("/left/a.txt"))
    }

    @Test
    fun replaceLineKeepsTheOriginalNewlineAndTheOtherFile() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/a.txt", "a\r\nb\r\nc\r\n")
        files.put("/right/a.txt", "a\r\nb\r\nc\r\n")
        val session = openPair(files)
        session.replaceLine(Side.Left, 2, listOf("B", "X"))
        session.save(Side.Left)
        assertEquals("a\r\nB\r\nX\r\nc\r\n", files.text("/left/a.txt"))
        assertEquals("a\r\nb\r\nc\r\n", files.text("/right/a.txt"))
        assertFalse(session.canSave(Side.Left))
    }

    @Test
    fun editAfterABlockCopyKeepsBothInTheSavedText() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/a.txt", "a\nb\nc\n")
        files.put("/right/a.txt", "a\nB\nc\n")
        val session = openPair(files)
        val ready = assertIs<ComparePhase.Ready>(session.phase)
        val index = ready.hunks.indexOfFirst { it.kind != HunkKind.Equal }
        session.decide(index, BlockDecision.CopyLeftToRight)
        session.insertBlankAbove(Side.Right, 2)
        assertTrue(session.decisions.isEmpty())
        session.save(Side.Right)
        assertEquals("a\n\nb\nc\n", files.text("/right/a.txt"))
        assertEquals("a\nb\nc\n", files.text("/left/a.txt"))
    }

    @Test
    fun lineEditWaitsWhileOverwriteIsUnanswered() = runBlocking {
        val files = MemoryWorkspace()
        files.put("/left/a.txt", "from-left\n")
        files.put("/right/a.txt", "from-right\n")
        val session = openPair(files)
        val ready = assertIs<ComparePhase.Ready>(session.phase)
        val index = ready.hunks.indexOfFirst { it.kind != HunkKind.Equal }
        session.decide(index, BlockDecision.CopyRightToLeft)
        files.put("/left/a.txt", "edited-outside\n")
        session.save(Side.Left)
        assertTrue(session.askOverwrite)
        session.insertBlankAbove(Side.Left, 1)
        session.replaceLine(Side.Left, 1, listOf("nope"))
        assertEquals(BlockDecision.CopyRightToLeft, session.decisionAt(index))
        assertEquals("edited-outside\n", files.text("/left/a.txt"))
    }

    private class FailingWrite(private val inner: MemoryWorkspace) : WorkspaceAccess by inner {
        var fail: Boolean = true

        override suspend fun writeDocument(absolutePath: String, text: String) {
            if (fail) throw IllegalStateException("寫入失敗")
            inner.writeDocument(absolutePath, text)
        }
    }

    private suspend fun openPair(files: MemoryWorkspace): FoldSession {
        val session = FoldSession(files)
        session.openRoot(Side.Left, "/left")
        session.openRoot(Side.Right, "/right")
        session.select(Side.Left, "a.txt")
        session.select(Side.Right, "a.txt")
        return session
    }
}
