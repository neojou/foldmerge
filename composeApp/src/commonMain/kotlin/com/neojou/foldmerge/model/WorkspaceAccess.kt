package com.neojou.foldmerge.model

/**
 * A file or directory under a chosen root.
 *
 * [relativePath] uses `/` separators and does not start with a separator.
 */
data class FileNode(
    val relativePath: String,
    val directory: Boolean,
)

/**
 * Identity of a file's bytes at the moment it was read.
 *
 * A later save compares this with a fresh stamp. Size plus content hash catches an external
 * edit even when the length stays the same.
 */
data class FileStamp(
    val sizeBytes: Long,
    val contentHash: Int,
)

/**
 * A file read for comparison. [text] is empty when [binary] is true.
 */
data class LoadedDocument(
    val binary: Boolean,
    val text: String,
    val stamp: FileStamp,
)

/**
 * Directory listing, text reads, and text writes used by the session.
 *
 * The desktop target supplies the real file system. Tests supply an in-memory fake.
 */
interface WorkspaceAccess {
    suspend fun chooseDirectory(title: String, initialDirectory: String?): String?

    suspend fun listTree(rootPath: String): List<FileNode>

    suspend fun readDocument(absolutePath: String): LoadedDocument

    suspend fun writeDocument(absolutePath: String, text: String)

    /**
     * Copies the bytes of [sourcePath] to [destinationPath].
     *
     * Missing parent directories are created. The call fails when [destinationPath] already exists.
     */
    suspend fun copyFile(sourcePath: String, destinationPath: String)

    suspend fun stampOf(absolutePath: String): FileStamp?
}

/**
 * Joins a root directory and a `/`-separated relative path.
 */
fun joinPath(root: String, relativePath: String): String {
    val base = root.trimEnd('/', '\\')
    if (relativePath.isEmpty()) return base
    val relative = relativePath.trimStart('/', '\\').replace('\\', '/')
    val separator = if (base.contains('\\') && !base.contains('/')) '\\' else '/'
    return "$base$separator$relative"
}

/**
 * Hidden names and `.git` are left out of the first version.
 *
 * A name is hidden when it starts with `.`, which already includes `.git`.
 */
fun isIgnoredName(name: String): Boolean = name.startsWith(".") || name == ".git"

/**
 * Bytes that contain a NUL in the sampled prefix are not shown as text.
 */
fun looksBinary(bytes: ByteArray): Boolean {
    val sample = minOf(bytes.size, BINARY_PROBE_BYTES)
    for (index in 0 until sample) {
        if (bytes[index] == 0.toByte()) return true
    }
    return false
}

/**
 * Strict UTF-8 check. Ill-formed sequences are treated as non-text.
 */
fun isValidUtf8(bytes: ByteArray): Boolean {
    var index = 0
    while (index < bytes.size) {
        val lead = bytes[index].toInt() and 0xFF
        val continuation = when {
            lead <= 0x7F -> 0
            lead in 0xC2..0xDF -> 1
            lead == 0xE0 -> 2
            lead in 0xE1..0xEC -> 2
            lead == 0xED -> 2
            lead in 0xEE..0xEF -> 2
            lead == 0xF0 -> 3
            lead in 0xF1..0xF3 -> 3
            lead == 0xF4 -> 3
            else -> return false
        }
        if (index + continuation >= bytes.size) return false
        for (offset in 1..continuation) {
            val unit = bytes[index + offset].toInt() and 0xFF
            if (unit !in 0x80..0xBF) return false
        }
        if (continuation >= 2) {
            val second = bytes[index + 1].toInt() and 0xFF
            if (lead == 0xE0 && second < 0xA0) return false
            if (lead == 0xED && second >= 0xA0) return false
            if (lead == 0xF0 && second < 0x90) return false
            if (lead == 0xF4 && second >= 0x90) return false
        }
        index += continuation + 1
    }
    return true
}

/**
 * Stamp used to detect an external change before writing a file.
 */
fun fingerprint(bytes: ByteArray): FileStamp = FileStamp(
    sizeBytes = bytes.size.toLong(),
    contentHash = bytes.contentHashCode(),
)

/**
 * Shared classification for the desktop reader and in-memory tests.
 */
fun loadDocument(bytes: ByteArray): LoadedDocument {
    val stamp = fingerprint(bytes)
    val binary = looksBinary(bytes) || !isValidUtf8(bytes)
    return LoadedDocument(
        binary = binary,
        text = if (binary) "" else bytes.decodeToString(),
        stamp = stamp,
    )
}

private const val BINARY_PROBE_BYTES = 8_192
