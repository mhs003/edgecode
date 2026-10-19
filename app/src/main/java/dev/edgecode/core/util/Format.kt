package dev.edgecode.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Display formatting. One place, so numbers read the same everywhere. */

fun formatTokens(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${trim(tokens / 1_000_000.0)}M"
    tokens >= 1_000 -> "${trim(tokens / 1_000.0)}k"
    else -> tokens.toString()
}

fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "${trim(n / 1_000_000.0)}M"
    n >= 10_000 -> "${trim(n / 1_000.0)}k"
    n >= 1_000 -> String.format(Locale.US, "%,d", n)
    else -> n.toString()
}

fun formatCost(usd: Double): String = when {
    usd <= 0.0 -> "$0.00"
    usd < 0.01 -> "<$0.01"
    else -> String.format(Locale.US, "$%.2f", usd)
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "${trim(bytes / (1024.0 * 1024 * 1024))} GB"
    bytes >= 1024L * 1024 -> "${trim(bytes / (1024.0 * 1024))} MB"
    bytes >= 1024L -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

fun formatDuration(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> "${trim(ms / 1000.0)}s"
    else -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
}

/**
 * The line under a settled reply: which model served it, how much it produced,
 * and how fast.
 *
 * The rate covers the whole turn — including the pause before the first token,
 * which is the wait the reader actually sits through — over the output tokens
 * only. Anything unknown is left out rather than printed as a zero, so a
 * server that reports no timings or no usage simply shows less.
 */
fun formatResponseMeta(
    model: String?,
    outputTokens: Int,
    startedAt: Long,
    completedAt: Long?,
): String? {
    val elapsedMs = completedAt?.let { it - startedAt }?.takeIf { it > 0 }
    val rate = elapsedMs
        ?.takeIf { outputTokens > 0 }
        ?.let { Math.round(outputTokens * 1000.0 / it).toInt() }

    val parts = buildList {
        if (!model.isNullOrBlank()) add(model)
        if (outputTokens > 0) add("${formatCount(outputTokens)} tokens")
        if (rate != null && rate > 0) add("$rate tok/s")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Compact relative time: `now`, `4m`, `3h`, `2d`, then an absolute date. */
fun formatRelative(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val delta = now - epochMillis
    return when {
        delta < 60_000 -> "now"
        delta < 3_600_000 -> "${delta / 60_000}m ago"
        delta < 86_400_000 -> "${delta / 3_600_000}h ago"
        delta < 7 * 86_400_000L -> "${delta / 86_400_000}d ago"
        else -> SimpleDateFormat("d MMM", Locale.US).format(Date(epochMillis))
    }
}

fun formatClock(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.US).format(Date(epochMillis))

/** Trims a trailing `.0` so ratios read as `1.5M` not `1.50M`. */
private fun trim(value: Double): String {
    val rounded = Math.round(value * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/** Middle-truncates a path so the file name stays visible. */
fun shortenPath(path: String, max: Int = 42): String {
    if (path.length <= max) return path
    val parts = path.split('/')
    val file = parts.last()
    if (file.length + 4 >= max) return "…" + file.takeLast(max - 1)
    val room = max - file.length - 4
    val head = parts.first()
    val headPart = if (head.length <= room) "$head/…/" else "…/"
    return headPart + file
}
