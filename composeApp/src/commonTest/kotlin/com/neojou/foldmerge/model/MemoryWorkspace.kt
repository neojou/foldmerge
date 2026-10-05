package com.neojou.foldmerge.model

/**
 * In-memory files for session tests. Paths are absolute strings such as `/left/a.txt`.
 */
class MemoryWorkspace : WorkspaceAccess {
    private val files = linkedMapOf<String, ByteArray>()

    fun put(absolutePath: String, text: String) {
        files[absolutePath] = text.encodeToByteArray()
    }

    fun putBytes(absolutePath: String, bytes: ByteArray) {
        files[absolutePath] = bytes.copyOf()
    }

    fun text(absolutePath: String): String = files.getValue(absolutePath).decodeToString()

    fun bytes(absolutePath: String): ByteArray = files.getValue(absolutePath).copyOf()

    override suspend fun chooseDirectory(title: String, initialDirectory: String?): String? = null

    override suspend fun listTree(rootPath: String): List<FileNode> {
        val prefix = rootPath.trimEnd('/', '\\') + "/"
        val relatives = files.keys.mapNotNull { path ->
            if (!path.startsWith(prefix)) null else path.removePrefix(prefix)
        }
        val nodes = mutableListOf<FileNode>()
        val seenDirectories = mutableSetOf<String>()
        relatives.forEach { relative ->
            val parts = relative.split('/')
            if (parts.any(::isIgnoredName)) return@forEach
            var accumulated = ""
            parts.dropLast(1).forEach { part ->
                accumulated = if (accumulated.isEmpty()) part else "$accumulated/$part"
                if (seenDirectories.add(accumulated)) {
                    nodes.add(FileNode(accumulated, directory = true))
                }
            }
            nodes.add(FileNode(relative, directory = false))
        }
        return nodes.sortedBy { it.relativePath.lowercase() }
    }

    override suspend fun readDocument(absolutePath: String): LoadedDocument {
        val bytes = files[absolutePath] ?: throw IllegalArgumentException("找不到檔案")
        return loadDocument(bytes)
    }

    override suspend fun writeDocument(absolutePath: String, text: String) {
        files[absolutePath] = text.encodeToByteArray()
    }

    override suspend fun copyFile(sourcePath: String, destinationPath: String) {
        val bytes = files[sourcePath] ?: throw IllegalArgumentException("找不到檔案")
        if (destinationPath in files) throw IllegalStateException("對方已有這個檔案")
        files[destinationPath] = bytes.copyOf()
    }

    override suspend fun stampOf(absolutePath: String): FileStamp? {
        val bytes = files[absolutePath] ?: return null
        return fingerprint(bytes)
    }
}
