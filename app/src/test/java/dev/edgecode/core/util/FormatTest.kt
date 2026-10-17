package dev.edgecode.core.util

import dev.edgecode.core.model.DiffLineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    @Test
    fun `formats token counts compactly`() {
        assertEquals("500", formatTokens(500))
        assertEquals("1.5k", formatTokens(1_500))
        assertEquals("39.4k", formatTokens(39_413))
        assertEquals("1M", formatTokens(1_000_000))
    }

    @Test
    fun `formats cost with a floor rather than showing zero`() {
        assertEquals("$0.00", formatCost(0.0))
        assertEquals("<$0.01", formatCost(0.004))
        assertEquals("$0.29", formatCost(0.29))
    }

    @Test
    fun `formats relative time across units`() {
        val now = 1_700_000_000_000L
        assertEquals("now", formatRelative(now - 30_000, now))
        assertEquals("4m ago", formatRelative(now - 4 * 60_000, now))
        assertEquals("3h ago", formatRelative(now - 3 * 3_600_000, now))
        assertEquals("2d ago", formatRelative(now - 2 * 86_400_000L, now))
    }

    @Test
    fun `shortens long paths but keeps the file name`() {
        val long = "/home/dev/acme/dashboard/src/settings/components/ExportPanel.tsx"
        val short = shortenPath(long, 30)
        assertTrue(short.length <= 30)
        assertTrue(short.endsWith("ExportPanel.tsx"))
    }

    @Test
    fun `parses a unified diff and counts changes`() {
        val diff = parseUnifiedDiff(
            """
            --- a/src/Button.tsx
            +++ b/src/Button.tsx
            @@ -1,3 +1,4 @@
             const base = {};
            +const light = {};
             export { base };
            """.trimIndent(),
        )
        assertEquals("src/Button.tsx", diff.path)
        assertEquals(1, diff.additions)
        assertEquals(0, diff.deletions)
        assertTrue(diff.hunks.any { it.kind == DiffLineKind.Hunk })
        assertTrue(diff.hunks.any { it.kind == DiffLineKind.Added })
    }

    @Test
    fun `counts removals separately from additions`() {
        val diff = parseUnifiedDiff(
            """
            --- a/app.ts
            +++ b/app.ts
            @@ -1,2 +1,2 @@
            -old line
            +new line
            """.trimIndent(),
        )
        assertEquals(1, diff.additions)
        assertEquals(1, diff.deletions)
    }
}
