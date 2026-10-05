package com.neojou.foldmerge.model

/**
 * One visible row in a file list. [depth] is the number of `/` separators in the relative path.
 */
data class FileRow(
    val node: FileNode,
    val depth: Int,
)

/**
 * Rows whose parent directories are all in [expanded].
 *
 * Root entries stay visible when nothing is expanded. Children appear only after each ancestor
 * directory has been expanded.
 */
fun visibleFileRows(nodes: List<FileNode>, expanded: Set<String>): List<FileRow> {
    return nodes.mapNotNull { node ->
        if (!ancestorsExpanded(node.relativePath, expanded)) {
            null
        } else {
            FileRow(node = node, depth = node.relativePath.count { it == '/' })
        }
    }
}

/**
 * Last segment of a relative path. A root entry's name is the path itself.
 */
fun entryName(relativePath: String): String = relativePath.substringAfterLast('/')

internal fun ancestorsExpanded(path: String, expanded: Set<String>): Boolean {
    var slash = path.lastIndexOf('/')
    while (slash >= 0) {
        val parent = path.substring(0, slash)
        if (parent !in expanded) return false
        slash = parent.lastIndexOf('/')
    }
    return true
}
