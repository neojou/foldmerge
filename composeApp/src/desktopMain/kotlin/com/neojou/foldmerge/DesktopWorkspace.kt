package com.neojou.foldmerge

import com.neojou.foldmerge.model.FileNode
import com.neojou.foldmerge.model.FileStamp
import com.neojou.foldmerge.model.LoadedDocument
import com.neojou.foldmerge.model.WorkspaceAccess
import com.neojou.foldmerge.model.isIgnoredName
import com.neojou.foldmerge.model.loadDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * JVM file system for the desktop app.
 *
 * On macOS the folder dialog is an AWT [FileDialog] with `apple.awt.fileDialogForDirectories`.
 * Other desktops use [JFileChooser] limited to directories.
 */
class DesktopWorkspace : WorkspaceAccess {
    override suspend fun chooseDirectory(title: String, initialDirectory: String?): String? {
        return suspendCancellableCoroutine { continuation ->
            SwingUtilities.invokeLater {
                val chosen = runCatching { showDirectoryChooser(title, initialDirectory) }
                if (!continuation.isActive) return@invokeLater
                chosen.fold(
                    onSuccess = continuation::resume,
                    onFailure = continuation::resumeWithException,
                )
            }
        }
    }

    override suspend fun listTree(rootPath: String): List<FileNode> = withContext(Dispatchers.IO) {
        val root = Path.of(rootPath)
        if (!Files.isDirectory(root)) {
            throw IllegalArgumentException("不是目錄")
        }
        val nodes = mutableListOf<FileNode>()
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (dir == root) return FileVisitResult.CONTINUE
                    val name = dir.fileName?.toString().orEmpty()
                    if (isIgnoredName(name)) return FileVisitResult.SKIP_SUBTREE
                    nodes.add(FileNode(relativePath = relative(root, dir), directory = true))
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isSymbolicLink) return FileVisitResult.CONTINUE
                    val name = file.fileName?.toString().orEmpty()
                    if (isIgnoredName(name) || !attrs.isRegularFile) return FileVisitResult.CONTINUE
                    nodes.add(FileNode(relativePath = relative(root, file), directory = false))
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    return FileVisitResult.CONTINUE
                }
            },
        )
        nodes.sortedBy { it.relativePath.lowercase() }
    }

    override suspend fun readDocument(absolutePath: String): LoadedDocument = withContext(Dispatchers.IO) {
        val bytes = Files.readAllBytes(Path.of(absolutePath))
        loadDocument(bytes)
    }

    override suspend fun writeDocument(absolutePath: String, text: String) {
        withContext(Dispatchers.IO) {
            val path = Path.of(absolutePath)
            val parent = path.parent
            if (parent != null && !Files.isDirectory(parent)) {
                throw IllegalArgumentException("找不到資料夾")
            }
            Files.writeString(path, text)
        }
    }

    override suspend fun copyFile(sourcePath: String, destinationPath: String) {
        withContext(Dispatchers.IO) {
            val source = Path.of(sourcePath)
            val destination = Path.of(destinationPath)
            if (!Files.isRegularFile(source)) throw IllegalArgumentException("找不到檔案")
            if (Files.exists(destination)) throw IllegalStateException("對方已有這個檔案")
            val parent = destination.parent
            if (parent != null) Files.createDirectories(parent)
            Files.copy(source, destination)
        }
    }

    override suspend fun stampOf(absolutePath: String): FileStamp? = withContext(Dispatchers.IO) {
        val path = Path.of(absolutePath)
        if (!Files.isRegularFile(path)) return@withContext null
        loadDocument(Files.readAllBytes(path)).stamp
    }
}

actual fun platformWorkspaceAccess(): WorkspaceAccess = DesktopWorkspace()

private fun showDirectoryChooser(title: String, initialDirectory: String?): String? {
    val os = System.getProperty("os.name").orEmpty()
    return if (os.contains("mac", ignoreCase = true)) {
        showMacDirectoryDialog(title, initialDirectory)
    } else {
        showSwingDirectoryChooser(title, initialDirectory)
    }
}

/**
 * Native folder panel. The property is set only while this dialog is open, and only a directory
 * path is returned.
 */
private fun showMacDirectoryDialog(title: String, initialDirectory: String?): String? {
    val key = "apple.awt.fileDialogForDirectories"
    val previous = System.getProperty(key)
    System.setProperty(key, "true")
    try {
        val dialog = FileDialog(focusedFrame(), title, FileDialog.LOAD)
        val start = initialDirectory?.let(::File)?.takeIf { it.isDirectory }
        if (start != null) dialog.directory = start.absolutePath
        dialog.isVisible = true
        val name = dialog.file ?: return null
        val parent = dialog.directory ?: return null
        val selected = File(parent, name)
        if (!selected.isDirectory) return null
        return selected.absolutePath
    } finally {
        if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
    }
}

private fun showSwingDirectoryChooser(title: String, initialDirectory: String?): String? {
    val chooser = JFileChooser()
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.dialogTitle = title
    chooser.approveButtonText = "選擇"
    chooser.isAcceptAllFileFilterUsed = false
    val start = initialDirectory?.let(::File)?.takeIf { it.isDirectory }
        ?: File(System.getProperty("user.home"))
    chooser.currentDirectory = start
    val result = chooser.showOpenDialog(focusedFrame())
    if (result != JFileChooser.APPROVE_OPTION) return null
    val selected = chooser.selectedFile ?: return null
    if (!selected.isDirectory) return null
    return selected.absolutePath
}

private fun focusedFrame(): Frame? {
    val windows = Window.getWindows()
    val focused = windows.firstOrNull { it.isFocused } ?: windows.firstOrNull { it.isVisible }
    return focused as? Frame
}

private fun relative(root: Path, path: Path): String {
    return root.relativize(path).toString().replace('\\', '/')
}
