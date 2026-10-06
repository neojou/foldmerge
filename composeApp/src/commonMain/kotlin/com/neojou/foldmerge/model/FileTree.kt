package com.neojou.foldmerge.model

/**
 * One visible row in a file list. [depth] is the number of `/` separators in the relative path.
 */
data class FileRow(
    val node: FileNode,
    val depth: Int,
)

/**
 * One row in the side-by-side file list.
 *
 * A null side is a blank pad so the other side's extra name stays on this row.
 * Both sides share [depth], counted from the root.
 */
data class PairedFileRow(
    val left: FileNode?,
    val right: FileNode?,
    val depth: Int,
) {
    init {
        require(left != null || right != null) { "a paired row needs a file on at least one side" }
        if (left != null && right != null) {
            require(left.relativePath == right.relativePath) { "paired names must be the same path" }
        }
    }
}

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
 * Aligns the visible names of two trees so equal relative paths share a row.
 *
 * Children are inserted directly under their directory, and only on a side that has expanded it.
 * A name that exists on only one side leaves the other side blank. Siblings are ordered by
 * lowercase name, then by the path itself, so `Readme` and `README` stay on separate rows.
 */
fun alignVisibleRows(
    leftNodes: List<FileNode>,
    rightNodes: List<FileNode>,
    leftExpanded: Set<String>,
    rightExpanded: Set<String>,
): List<PairedFileRow> {
    val leftChildren = childrenByParent(leftNodes)
    val rightChildren = childrenByParent(rightNodes)
    val rows = mutableListOf<PairedFileRow>()
    fun walk(parent: String, depth: Int, leftOpen: Boolean, rightOpen: Boolean) {
        val leftKids = if (leftOpen) leftChildren[parent].orEmpty() else emptyList()
        val rightKids = if (rightOpen) rightChildren[parent].orEmpty() else emptyList()
        mergeChildren(leftKids, rightKids).forEach { (left, right) ->
            rows.add(PairedFileRow(left = left, right = right, depth = depth))
            val path = (left ?: right)!!.relativePath
            val descendLeft = left?.directory == true && path in leftExpanded
            val descendRight = right?.directory == true && path in rightExpanded
            if (descendLeft || descendRight) {
                walk(path, depth + 1, descendLeft, descendRight)
            }
        }
    }
    walk(parent = "", depth = 0, leftOpen = true, rightOpen = true)
    return rows
}

/**
 * Last segment of a relative path. A root entry's name is the path itself.
 */
fun entryName(relativePath: String): String = relativePath.substringAfterLast('/')

private fun childrenByParent(nodes: List<FileNode>): Map<String, List<FileNode>> {
    return nodes.groupBy { parentOf(it.relativePath) }.mapValues { (_, children) ->
        children.sortedWith(
            compareBy<FileNode> { entryName(it.relativePath).lowercase() }.thenBy { it.relativePath },
        )
    }
}

private fun parentOf(relativePath: String): String {
    val slash = relativePath.lastIndexOf('/')
    return if (slash < 0) "" else relativePath.substring(0, slash)
}

private fun mergeChildren(
    left: List<FileNode>,
    right: List<FileNode>,
): List<Pair<FileNode?, FileNode?>> {
    val rows = mutableListOf<Pair<FileNode?, FileNode?>>()
    var leftIndex = 0
    var rightIndex = 0
    while (leftIndex < left.size && rightIndex < right.size) {
        val leftNode = left[leftIndex]
        val rightNode = right[rightIndex]
        if (leftNode.relativePath == rightNode.relativePath) {
            rows.add(leftNode to rightNode)
            leftIndex++
            rightIndex++
            continue
        }
        if (comparePaths(leftNode.relativePath, rightNode.relativePath) < 0) {
            rows.add(leftNode to null)
            leftIndex++
        } else {
            rows.add(null to rightNode)
            rightIndex++
        }
    }
    while (leftIndex < left.size) {
        rows.add(left[leftIndex] to null)
        leftIndex++
    }
    while (rightIndex < right.size) {
        rows.add(null to right[rightIndex])
        rightIndex++
    }
    return rows
}

private fun comparePaths(left: String, right: String): Int {
    val byName = entryName(left).lowercase().compareTo(entryName(right).lowercase())
    if (byName != 0) return byName
    return left.compareTo(right)
}

internal fun ancestorsExpanded(path: String, expanded: Set<String>): Boolean {
    var slash = path.lastIndexOf('/')
    while (slash >= 0) {
        val parent = path.substring(0, slash)
        if (parent !in expanded) return false
        slash = parent.lastIndexOf('/')
    }
    return true
}
