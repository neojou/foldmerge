package com.neojou.foldmerge.diff

/**
 * A text file split into logical lines, plus the newline style to use when writing it back.
 *
 * The line list does not contain newline characters. A trailing newline is recorded separately
 * so a file that does not end with one is not given an extra blank line.
 */
data class SplitText(
    val lines: List<String>,
    val newline: String,
    val trailingNewline: Boolean,
)

/**
 * Splits [text] on `\n`, `\r\n`, or `\r`.
 *
 * The newline kept for writing is `\r\n` when that sequence occurs, otherwise `\n` when a
 * line feed occurs, otherwise `\r` when a bare carriage return occurs.
 */
fun splitText(text: String): SplitText {
    if (text.isEmpty()) {
        return SplitText(lines = emptyList(), newline = "\n", trailingNewline = false)
    }
    val newline = when {
        text.contains("\r\n") -> "\r\n"
        text.contains('\n') -> "\n"
        text.contains('\r') -> "\r"
        else -> "\n"
    }
    val trailingNewline = text.endsWith("\n") || text.endsWith("\r")
    val parts = text.split(Regex("\r\n|\n|\r"))
    val lines = if (trailingNewline && parts.isNotEmpty() && parts.last().isEmpty()) {
        parts.dropLast(1)
    } else {
        parts
    }
    return SplitText(lines = lines, newline = newline, trailingNewline = trailingNewline)
}

/**
 * Joins [lines] with [newline].
 *
 * An empty line list is an empty file. A non-empty list gains a final newline only when
 * [trailingNewline] is true.
 */
fun renderDocument(lines: List<String>, newline: String, trailingNewline: Boolean): String {
    if (lines.isEmpty()) return ""
    val body = lines.joinToString(separator = newline)
    return if (trailingNewline) body + newline else body
}
