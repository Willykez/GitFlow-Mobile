package willykez.gitflowmobile.git

/**
 * Shared unified-diff parsing — used by the diff viewer (to render hunks
 * with line numbers) and by hunk-level staging (to know exactly which
 * lines a chosen hunk covers so only that hunk gets applied to the index).
 * Kept in the git layer, not the UI layer, so both can depend on one
 * source of truth for what a "hunk" is.
 */

enum class DiffLineType { ADDED, REMOVED, CONTEXT }

data class DiffLine(
    val content: String,      // line text with the leading +/-/space marker stripped
    val type: DiffLineType,
    val oldLineNo: Int? = null,
    val newLineNo: Int? = null,
)

/** One `@@ ... @@` section of a diff, or (for multi-file commit diffs) one
 *  file's section. [oldStart]/[oldCount]/[newStart] are the hunk header's
 *  own numbers (needed to apply just this hunk to the index); [oldCount]
 *  is also derivable from [lines] (context + removed count) and kept in
 *  sync with that by [parseDiff] rather than trusted blindly from the
 *  header text, in case of any header/body mismatch. */
data class DiffHunk(
    val headerText: String,
    val filePath: String,
    val oldStart: Int,
    val newStart: Int,
    val lines: List<DiffLine>,
) {
    /** Old-side span (context + removed lines) — how many index/HEAD lines this hunk covers. */
    val oldCount: Int get() = lines.count { it.type != DiffLineType.ADDED }
}

private val HunkHeaderPattern = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@.*""")

/**
 * Splits a raw unified diff into per-hunk groups with running old/new line
 * numbers, tracking which file each hunk belongs to (a commit diff can
 * cover several files, each introduced by a `diff --git a/x b/y` line).
 */
fun parseDiff(raw: String, fallbackPath: String): List<DiffHunk> {
    val hunks = mutableListOf<DiffHunk>()
    var currentPath = fallbackPath
    var currentHeader: String? = null
    var currentOldStart = 1
    var currentNewStart = 1
    var currentLines = mutableListOf<DiffLine>()
    var oldLine = 0
    var newLine = 0

    fun flush() {
        val header = currentHeader
        if (header != null && currentLines.isNotEmpty()) {
            hunks.add(DiffHunk(header, currentPath, currentOldStart, currentNewStart, currentLines))
        }
        currentLines = mutableListOf()
    }

    for (line in raw.lines()) {
        when {
            line.startsWith("diff --git ") -> {
                flush()
                currentHeader = null
                // "diff --git a/old/path b/new/path" — take the b/ side (post-change path).
                val bIdx = line.indexOf(" b/")
                if (bIdx != -1) currentPath = line.substring(bIdx + 3)
            }
            line.startsWith("+++") || line.startsWith("---") || line.startsWith("index ") ||
                line.startsWith("new file") || line.startsWith("deleted file") -> {
                // Metadata lines between "diff --git" and the first "@@" — not diff content.
            }
            HunkHeaderPattern.matches(line) -> {
                flush()
                val m = HunkHeaderPattern.find(line)!!
                oldLine = m.groupValues[1].toIntOrNull() ?: 1
                newLine = m.groupValues[2].toIntOrNull() ?: 1
                currentOldStart = oldLine
                currentNewStart = newLine
                currentHeader = line
            }
            line.startsWith("+") -> {
                currentLines.add(DiffLine(line.removePrefix("+"), DiffLineType.ADDED, newLineNo = newLine))
                newLine++
            }
            line.startsWith("-") -> {
                currentLines.add(DiffLine(line.removePrefix("-"), DiffLineType.REMOVED, oldLineNo = oldLine))
                oldLine++
            }
            else -> {
                val content = line.removePrefix(" ")
                currentLines.add(DiffLine(content, DiffLineType.CONTEXT, oldLineNo = oldLine, newLineNo = newLine))
                oldLine++; newLine++
            }
        }
    }
    flush()
    return hunks
}
