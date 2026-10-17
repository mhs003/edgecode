package dev.edgecode.core.util

import dev.edgecode.core.model.DiffFile
import dev.edgecode.core.model.DiffLine
import dev.edgecode.core.model.DiffLineKind

/**
 * Parses a unified diff body into renderable lines.
 *
 * The v1 API hands back `before`/`after` blobs, so this is also where a real
 * implementation would be extended to derive a diff rather than only parse one.
 */
fun parseUnifiedDiff(body: String, fallbackPath: String = ""): DiffFile {
    var path = fallbackPath
    var additions = 0
    var deletions = 0
    val lines = mutableListOf<DiffLine>()

    for (raw in body.lines()) {
        when {
            raw.startsWith("+++ ") || raw.startsWith("--- ") -> {
                if (raw.startsWith("+++ ")) {
                    val candidate = raw.removePrefix("+++ ").trim()
                    if (candidate.isNotEmpty() && candidate != "/dev/null") {
                        path = candidate.removePrefix("b/")
                    }
                }
            }
            raw.startsWith("@@") -> lines += DiffLine(DiffLineKind.Hunk, raw)
            raw.startsWith("+") -> {
                additions++
                lines += DiffLine(DiffLineKind.Added, raw.substring(1))
            }
            raw.startsWith("-") -> {
                deletions++
                lines += DiffLine(DiffLineKind.Removed, raw.substring(1))
            }
            else -> lines += DiffLine(DiffLineKind.Context, raw.removePrefix(" "))
        }
    }

    return DiffFile(
        path = path,
        additions = additions,
        deletions = deletions,
        hunks = collapseContext(lines),
    )
}

/** Keeps a little context around changes and elides long unchanged runs. */
private fun collapseContext(lines: List<DiffLine>, keep: Int = 3): List<DiffLine> {
    if (lines.size <= keep * 4) return lines

    val keepIndices = mutableSetOf<Int>()
    for ((index, line) in lines.withIndex()) {
        if (line.kind != DiffLineKind.Context) {
            for (offset in -keep..keep) {
                val target = index + offset
                if (target in lines.indices) keepIndices.add(target)
            }
        }
    }
    if (keepIndices.isEmpty()) return lines

    val result = mutableListOf<DiffLine>()
    var lastEmitted = -1
    for (index in lines.indices) {
        if (index in keepIndices) {
            if (lastEmitted >= 0 && index > lastEmitted + 1) {
                result += DiffLine(DiffLineKind.Hunk, "⋯")
            }
            result += lines[index]
            lastEmitted = index
        }
    }
    return result
}
