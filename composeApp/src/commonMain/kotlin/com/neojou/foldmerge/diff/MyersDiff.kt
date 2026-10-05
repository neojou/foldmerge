package com.neojou.foldmerge.diff

/**
 * One step of a line edit script from the left sequence to the right sequence.
 */
internal sealed class Edit {
    abstract val line: String

    data class Same(override val line: String) : Edit()
    data class Delete(override val line: String) : Edit()
    data class Insert(override val line: String) : Edit()
}

/**
 * Shortest line edit script from [left] to [right].
 *
 * This is an original implementation of Myers' O(ND) algorithm (Eugene W. Myers, 1986).
 * It does not wrap a diff library.
 */
internal fun myersEdits(left: List<String>, right: List<String>): List<Edit> {
    val n = left.size
    val m = right.size
    if (n == 0 && m == 0) return emptyList()

    val max = n + m
    val offset = max
    val v = IntArray(2 * max + 1)
    val trace = ArrayList<IntArray>(max + 1)
    // Diagonal k = 1 is the conventional seed so the d = 0 step can read x = 0.
    v[offset + 1] = 0

    for (d in 0..max) {
        trace.add(v.copyOf())
        for (k in -d..d step 2) {
            val xStart = if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) {
                v[offset + k + 1]
            } else {
                v[offset + k - 1] + 1
            }
            var x = xStart
            var y = x - k
            while (x < n && y < m && left[x] == right[y]) {
                x++
                y++
            }
            v[offset + k] = x
            if (x >= n && y >= m) {
                return backtrack(trace, left, right, offset)
            }
        }
    }
    error("差異計算沒有在預期步數內結束")
}

/**
 * Walks the saved frontiers from the end of both sequences back to the origin.
 *
 * [trace] entry `d` is the frontier *before* the edits of distance `d` were written.
 */
private fun backtrack(
    trace: List<IntArray>,
    left: List<String>,
    right: List<String>,
    offset: Int,
): List<Edit> {
    var x = left.size
    var y = right.size
    val reversed = ArrayList<Edit>()
    for (d in trace.lastIndex downTo 0) {
        val frontier = trace[d]
        val k = x - y
        val prevK = if (k == -d || (k != d && frontier[offset + k - 1] < frontier[offset + k + 1])) {
            k + 1
        } else {
            k - 1
        }
        val prevX = frontier[offset + prevK]
        val prevY = prevX - prevK
        while (x > prevX && y > prevY) {
            reversed.add(Edit.Same(left[x - 1]))
            x--
            y--
        }
        if (d > 0) {
            if (x == prevX) {
                reversed.add(Edit.Insert(right[y - 1]))
                y--
            } else {
                reversed.add(Edit.Delete(left[x - 1]))
                x--
            }
        }
    }
    reversed.reverse()
    return reversed
}
