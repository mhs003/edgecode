package dev.edgecode.data.demo

import dev.edgecode.core.model.AgentInfo
import dev.edgecode.core.model.Attachment
import dev.edgecode.core.model.AttachmentKind
import dev.edgecode.core.model.ChatMessage
import dev.edgecode.core.model.DiffFile
import dev.edgecode.core.model.ModelCapabilities
import dev.edgecode.core.model.ModelInfo
import dev.edgecode.core.model.ModelRef
import dev.edgecode.core.model.Part
import dev.edgecode.core.model.PermissionKind
import dev.edgecode.core.model.PermissionRequest
import dev.edgecode.core.model.ProjectRef
import dev.edgecode.core.model.ProviderInfo
import dev.edgecode.core.model.SessionStatus
import dev.edgecode.core.model.SessionSummary
import dev.edgecode.core.model.SlashCommand
import dev.edgecode.core.model.TokenUsage
import dev.edgecode.core.model.ToolState
import dev.edgecode.core.model.ToolStatus
import dev.edgecode.core.util.parseUnifiedDiff

/**
 * Fixture content for the demo API.
 *
 * Written to the shape a real session actually has — interleaved reasoning,
 * tool calls mid-answer, a diff, a failing command, and a permission prompt —
 * because the point of the demo is to exercise the transcript the same way a
 * live server would.
 */
object DemoContent {

    const val SERVER_VERSION = "1.19.3"

    val providers = listOf(
        ProviderInfo("anthropic", "Anthropic"),
        ProviderInfo("openai", "OpenAI"),
        ProviderInfo("google", "Google"),
        ProviderInfo("opencode", "OpenCode Zen"),
    )

    val models = listOf(
        ModelInfo(
            id = "claude-opus-4-5", providerId = "anthropic", providerName = "Anthropic",
            name = "Claude Opus 4.5", contextLimit = 200_000, outputLimit = 64_000,
            inputCostPerM = 15.0, outputCostPerM = 75.0,
            variants = listOf("default", "thinking"),
            capabilities = ModelCapabilities(tools = true, reasoning = true, vision = true, attachments = true),
            isDefault = true,
        ),
        ModelInfo(
            id = "claude-sonnet-4-5", providerId = "anthropic", providerName = "Anthropic",
            name = "Claude Sonnet 4.5", contextLimit = 200_000, outputLimit = 64_000,
            inputCostPerM = 3.0, outputCostPerM = 15.0,
            variants = listOf("default", "thinking"),
            capabilities = ModelCapabilities(tools = true, reasoning = true, vision = true, attachments = true),
        ),
        ModelInfo(
            id = "claude-haiku-4-5", providerId = "anthropic", providerName = "Anthropic",
            name = "Claude Haiku 4.5", contextLimit = 200_000, outputLimit = 32_000,
            inputCostPerM = 1.0, outputCostPerM = 5.0,
            capabilities = ModelCapabilities(tools = true, reasoning = true, vision = true),
        ),
        ModelInfo(
            id = "gpt-6", providerId = "openai", providerName = "OpenAI",
            name = "GPT-6", contextLimit = 400_000, outputLimit = 128_000,
            inputCostPerM = 2.5, outputCostPerM = 10.0,
            variants = listOf("default", "high"),
            capabilities = ModelCapabilities(tools = true, reasoning = true, vision = true, attachments = true),
        ),
        ModelInfo(
            id = "gpt-6-mini", providerId = "openai", providerName = "OpenAI",
            name = "GPT-6 mini", contextLimit = 400_000, outputLimit = 64_000,
            inputCostPerM = 0.4, outputCostPerM = 1.6,
            capabilities = ModelCapabilities(tools = true, vision = true),
        ),
        ModelInfo(
            id = "gemini-3-pro", providerId = "google", providerName = "Google",
            name = "Gemini 3 Pro", contextLimit = 1_000_000, outputLimit = 64_000,
            inputCostPerM = 1.25, outputCostPerM = 10.0,
            variants = listOf("default", "thinking"),
            capabilities = ModelCapabilities(tools = true, reasoning = true, vision = true, attachments = true),
        ),
        ModelInfo(
            id = "zen-opus-4-5", providerId = "opencode", providerName = "OpenCode Zen",
            name = "Opus 4.5", contextLimit = 200_000, outputLimit = 64_000,
            inputCostPerM = 12.0, outputCostPerM = 60.0,
            capabilities = ModelCapabilities(tools = true, reasoning = true, vision = true),
        ),
        ModelInfo(
            id = "zen-gpt-oss-120b", providerId = "opencode", providerName = "OpenCode Zen",
            name = "GPT-OSS 120B", contextLimit = 131_072, outputLimit = 32_000,
            inputCostPerM = 0.2, outputCostPerM = 0.6,
            capabilities = ModelCapabilities(tools = true),
        ),
    )

    val agents = listOf(
        AgentInfo("build", "Build", "Writes and edits code, runs commands", builtIn = true),
        AgentInfo("plan", "Plan", "Reads and proposes changes without editing", builtIn = true),
        AgentInfo("general", "General", "Searches and answers, no edits", builtIn = true, isPrimary = false),
        AgentInfo("explore", "Explore", "Fast read-only codebase search", builtIn = false, isPrimary = false),
    )

    val commands = listOf(
        SlashCommand("init", "Analyse the project and write AGENTS.md"),
        SlashCommand("undo", "Revert the last change and restore the prompt"),
        SlashCommand("redo", "Reapply the last reverted change"),
        SlashCommand("compact", "Summarise the session to free up context"),
        SlashCommand("share", "Create a shareable link for this session"),
        SlashCommand("models", "Switch the model for this session"),
        SlashCommand("agents", "Switch the active agent"),
        SlashCommand("new", "Start a new session"),
        SlashCommand("sessions", "Jump to another session"),
        SlashCommand("editor", "Open the last message in your editor"),
        SlashCommand("help", "Show available commands and keybinds"),
    )

    val projects = listOf(
        ProjectRef("p1", "/home/dev/acme/dashboard", "dashboard", isCurrent = true),
        ProjectRef("p2", "/home/dev/acme/api", "api"),
        ProjectRef("p3", "/home/dev/acme/web", "web"),
        ProjectRef("p4", "/home/dev/acme/monorepo", "monorepo"),
        ProjectRef("p5", "/home/dev/acme/ops", "ops"),
    )

    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
    private const val DAY = 24 * HOUR

    fun sessions(now: Long): List<SessionSummary> = listOf(
        SessionSummary(
            id = "ses_dashboard_contrast",
            title = "Fix export button contrast in Settings",
            directory = "/home/dev/acme/dashboard",
            createdAt = now - 3 * HOUR,
            updatedAt = now - 2 * MIN,
            status = SessionStatus.Working,
            cost = 0.29,
            tokens = TokenUsage(input = 39_413, output = 2_184, reasoning = 812, cacheRead = 22_400),
            additions = 6, deletions = 3, changedFiles = 2,
            modelLabel = "Opus 4.5",
        ),
        SessionSummary(
            id = "ses_api_ratelimit",
            title = "Add rate limiting to the public API",
            directory = "/home/dev/acme/api",
            createdAt = now - 6 * HOUR,
            updatedAt = now - 48 * MIN,
            cost = 1.84,
            tokens = TokenUsage(input = 184_220, output = 12_930, cacheRead = 96_100),
            additions = 214, deletions = 18, changedFiles = 9,
            modelLabel = "GPT-6",
        ),
        SessionSummary(
            id = "ses_ci_slow",
            title = "Why is the build slow on CI?",
            directory = "/home/dev/acme/monorepo",
            createdAt = now - 2 * DAY,
            updatedAt = now - 5 * HOUR,
            cost = 0.41,
            tokens = TokenUsage(input = 61_004, output = 3_120),
            modelLabel = "Gemini 3 Pro",
        ),
        SessionSummary(
            id = "ses_web_migrate",
            title = "Migrate remaining class components",
            directory = "/home/dev/acme/web",
            createdAt = now - 3 * DAY,
            updatedAt = now - 9 * HOUR,
            status = SessionStatus.Error,
            cost = 2.60,
            tokens = TokenUsage(input = 240_880, output = 18_442),
            additions = 640, deletions = 502, changedFiles = 31,
            modelLabel = "Opus 4.5",
        ),
        SessionSummary(
            id = "ses_parser_tests",
            title = "Write tests for the CSV parser",
            directory = "/home/dev/acme/api",
            createdAt = now - 4 * DAY,
            updatedAt = now - 1 * DAY,
            cost = 0.12,
            tokens = TokenUsage(input = 12_300, output = 4_010),
            additions = 96, deletions = 0, changedFiles = 1,
            modelLabel = "Claude Sonnet 4.5",
        ),
        SessionSummary(
            id = "ses_deploy_docs",
            title = "Document the deploy process",
            directory = "/home/dev/acme/ops",
            createdAt = now - 9 * DAY,
            updatedAt = now - 6 * DAY,
            cost = 0.07,
            tokens = TokenUsage(input = 8_220, output = 1_930),
            additions = 42, deletions = 5, changedFiles = 1,
            modelLabel = "GPT-6 mini",
        ),
    )

    // --- Transcript --------------------------------------------------------

    fun transcript(sessionId: String, now: Long): List<ChatMessage> =
        when (sessionId) {
            "ses_dashboard_contrast" -> contrastSession(now)
            "ses_ci_slow" -> ciSession(now)
            "ses_api_ratelimit" -> rateLimitSession(now)
            "ses_web_migrate" -> migrateSession(now)
            "ses_parser_tests" -> parserSession(now)
            else -> deploySession(now)
        }

    private fun user(id: String, at: Long, text: String, attachments: List<Attachment> = emptyList()) =
        ChatMessage.User(id = id, createdAt = at, text = text, attachments = attachments)

    private fun assistant(
        id: String,
        at: Long,
        parts: List<Part>,
        agent: String = "build",
        model: ModelRef = ModelRef("anthropic", "claude-opus-4-5"),
        cost: Double = 0.0,
        tokens: TokenUsage = TokenUsage(),
    ) = ChatMessage.Assistant(
        id = id, createdAt = at, parts = parts, agent = agent, model = model,
        streaming = false, cost = cost, tokens = tokens, completedAt = at + 9_000,
    )

    private fun contrastSession(now: Long) = listOf(
        user(
            "msg_u1", now - 3 * HOUR,
            "The export button in Settings is nearly invisible in light mode. " +
                "Can you find out why and fix it?",
        ),
        assistant(
            id = "msg_a1", at = now - 3 * HOUR + 4_000,
            parts = listOf(
                Part.Reasoning(
                    id = "pr_1",
                    text = "The report is specific to light mode, which points at a hardcoded " +
                        "colour rather than a theme token. I'll find the button first, then check " +
                        "how its background is derived.",
                    seconds = 6,
                ),
                Part.Text(
                    id = "pt_1",
                    text = "Light-mode-only contrast problems are almost always a hardcoded colour " +
                        "that never got a light variant. Let me locate the button.",
                ),
                Part.Tool(
                    id = "tl_1", toolId = "grep", name = "Grep",
                    state = ToolState(
                        status = ToolStatus.Completed,
                        title = "grep \"export\" --glob \"*.tsx\"",
                        subtitle = "7 matches in 4 files",
                        input = "{\n  \"pattern\": \"export\",\n  \"include\": \"*.tsx\"\n}",
                        output = "src/settings/ExportPanel.tsx:42\n" +
                            "src/settings/ExportPanel.tsx:88\n" +
                            "src/settings/SettingsHeader.tsx:21\n" +
                            "src/components/Button.tsx:14\n" +
                            "src/components/Button.tsx:57\n" +
                            "src/settings/__tests__/ExportPanel.test.tsx:12\n" +
                            "src/settings/__tests__/ExportPanel.test.tsx:64",
                        durationMs = 312,
                    ),
                ),
                Part.Tool(
                    id = "tl_2", toolId = "read", name = "Read",
                    state = ToolState(
                        status = ToolStatus.Completed,
                        title = "read src/settings/ExportPanel.tsx",
                        subtitle = "118 lines",
                        input = "{\n  \"path\": \"src/settings/ExportPanel.tsx\"\n}",
                        output = "export function ExportPanel() {\n" +
                            "  return (\n" +
                            "    <Panel>\n" +
                            "      <Button\n" +
                            "        variant=\"primary\"\n" +
                            "        style={{ background: \"#2F6FED\", color: \"#ffffff\" }}\n" +
                            "        onClick={handleExport}\n" +
                            "      >\n" +
                            "        Export\n" +
                            "      </Button>\n" +
                            "    </Panel>\n" +
                            "  );\n" +
                            "}",
                        meta = listOf("encoding" to "utf-8"),
                        durationMs = 84,
                    ),
                ),
                Part.Text(
                    id = "pt_2",
                    text = "Found it. The button sets `background: \"#2F6FED\"` inline, which bypasses " +
                        "the `Button` component's theming entirely — so it never picks up the light-mode " +
                        "variant. Two things to fix:\n\n" +
                        "1. Drop the inline style so the component's own theming applies.\n" +
                        "2. Give `Button` a light-mode contrast pass, because `variant=\"primary\"` " +
                        "is currently under 3:1 on white.",
                ),
                Part.Diff(
                    id = "df_1",
                    files = listOf(
                        parseUnifiedDiff(
                            """
                            --- a/src/settings/ExportPanel.tsx
                            +++ b/src/settings/ExportPanel.tsx
                            @@ -39,10 +39,7 @@ export function ExportPanel() {
                                   <Button
                                     variant="primary"
                            -        style={{ background: "#2F6FED", color: "#ffffff" }}
                            +        aria-label="Export settings"
                                     onClick={handleExport}
                                   >
                                     Export
                            """.trimIndent(),
                        ),
                        parseUnifiedDiff(
                            """
                            --- a/src/components/Button.tsx
                            +++ b/src/components/Button.tsx
                            @@ -54,6 +54,9 @@ function variantStyles(variant: Variant, theme: Theme) {
                             +  if (variant === "primary" && theme.mode === "light") {
                             +    return { background: "#1B4FC4", color: "#ffffff" };
                             +  }
                               return base[variant];
                             }
                            """.trimIndent(),
                        ),
                    ),
                ),
                Part.Tool(
                    id = "tl_3", toolId = "bash", name = "Bash",
                    state = ToolState(
                        status = ToolStatus.Completed,
                        title = "npm test -- Button",
                        subtitle = "exit 0",
                        input = "{\n  \"command\": \"npm test -- Button\"\n}",
                        output = "PASS src/components/__tests__/Button.test.tsx\n" +
                            "  Button\n" +
                            "    ✓ renders primary variant\n" +
                            "    ✓ applies light-mode contrast override\n" +
                            "    ✓ forwards aria attributes\n" +
                            "\nTests: 3 passed, 3 total\nTime:  1.24 s",
                        meta = listOf("exit" to "0"),
                        durationMs = 4_120,
                    ),
                ),
                Part.Text(
                    id = "pt_3",
                    text = "Both changes are in and the tests pass.\n\n" +
                        "**What changed**\n\n" +
                        "- `ExportPanel.tsx` — removed the inline `background`/`color`, so the button " +
                        "now derives its colours from the theme. Added an `aria-label` while I was there.\n" +
                        "- `Button.tsx` — added a light-mode override for the `primary` variant " +
                        "(`#1B4FC4` on white), which clears 4.5:1.\n\n" +
                        "Worth noting: `secondary` has the same problem, it just isn't visible in " +
                        "this screen. I'd fix it in a follow-up rather than widen this change.\n\n" +
                        "```ts\n" +
                        "if (variant === \"primary\" && theme.mode === \"light\") {\n" +
                        "  return { background: \"#1B4FC4\", color: \"#ffffff\" };\n" +
                        "}\n" +
                        "```",
                ),
            ),
            cost = 0.29,
            tokens = TokenUsage(input = 39_413, output = 2_184, reasoning = 812, cacheRead = 22_400),
        ),
    )

    private fun ciSession(now: Long) = listOf(
        user("msg_u1", now - 5 * HOUR - 20 * MIN, "Why is the build slow on CI? It used to be ~4 minutes and now it's 14."),
        assistant(
            id = "msg_a1", at = now - 5 * HOUR - 16 * MIN,
            agent = "plan",
            model = ModelRef("google", "gemini-3-pro"),
            parts = listOf(
                Part.Text(
                    id = "pt_1",
                    text = "Let me look at the pipeline config and the timing report from the last run.",
                ),
                Part.Tool(
                    id = "tl_1", toolId = "read", name = "Read",
                    state = ToolState(
                        status = ToolStatus.Completed,
                        title = "read .github/workflows/ci.yml",
                        subtitle = "64 lines",
                        input = "{\n  \"path\": \".github/workflows/ci.yml\"\n}",
                        output = "jobs:\n  build:\n    steps:\n      - uses: actions/checkout@v4\n" +
                            "      - run: npm ci\n      - run: npm run build\n      - run: npm test",
                        durationMs = 61,
                    ),
                ),
                Part.Text(
                    id = "pt_2",
                    text = "The workflow itself hasn't changed, so the regression is in what it runs. " +
                        "Two candidates from the last green run versus now:\n\n" +
                        "1. `npm ci` jumped from 48s to 6m 10s — dependency count grew.\n" +
                        "2. `npm test` now runs the full suite with no sharding.\n\n" +
                        "The cache key is the likely culprit. It's keyed on `package.json` only, so " +
                        "any lockfile-only change silently invalidates it.",
                ),
                Part.Tool(
                    id = "tl_2", toolId = "grep", name = "Grep",
                    state = ToolState(
                        status = ToolStatus.Failed,
                        title = "grep \"cache:\" .github/workflows",
                        subtitle = "no matches",
                        input = "{\n  \"pattern\": \"cache:\",\n  \"path\": \".github/workflows\"\n}",
                        output = "No files matched.",
                        meta = listOf("exit" to "1"),
                        durationMs = 22,
                    ),
                ),
                Part.Text(
                    id = "pt_3",
                    text = "That confirms it — there is no cache step at all. Adding one keyed on " +
                        "`package-lock.json` should return most of the lost time.\n\n" +
                        "> I'm in plan mode, so I haven't changed anything. Say the word and I'll " +
                        "apply it.",
                ),
            ),
            cost = 0.41,
            tokens = TokenUsage(input = 61_004, output = 3_120),
        ),
    )

    private fun rateLimitSession(now: Long) = listOf(
        user("msg_u1", now - 6 * HOUR, "Add rate limiting to the public API. Per-key, sliding window, 1000 req/min."),
        assistant(
            id = "msg_a1", at = now - 6 * HOUR + 5_000,
            agent = "build",
            model = ModelRef("openai", "gpt-6"),
            parts = listOf(
                Part.Text(
                    id = "pt_1",
                    text = "Before I write anything: a sliding window per key needs shared state. " +
                        "Is Redis in play here, or should this be in-process?",
                ),
                Part.Note(id = "pn_1", text = "Session waited for input for 4 minutes"),
            ),
            cost = 1.84,
            tokens = TokenUsage(input = 184_220, output = 12_930),
        ),
    )

    private fun migrateSession(now: Long): List<ChatMessage> {
        val asked = user("msg_u1", now - 3 * DAY, "Migrate the remaining class components in src/legacy to hooks.")
        val reply = assistant(
            id = "msg_a1", at = now - 3 * DAY + 6_000,
            parts = listOf(
                Part.Text(id = "pt_1", text = "Starting with the largest file so the pattern is settled early."),
                Part.Tool(
                    id = "tl_1", toolId = "edit", name = "Edit",
                    state = ToolState(
                        status = ToolStatus.Failed,
                        title = "edit src/legacy/DataTable.tsx",
                        subtitle = "write failed",
                        input = "{\n  \"path\": \"src/legacy/DataTable.tsx\"\n}",
                        output = "Error: EACCES: permission denied, open 'src/legacy/DataTable.tsx'",
                        meta = listOf("errno" to "EACCES"),
                        durationMs = 18,
                    ),
                ),
                Part.Text(
                    id = "pt_2",
                    text = "`src/legacy/DataTable.tsx` is read-only on disk, so I can't write it. " +
                        "Fix the permissions or move the file and I'll continue.",
                ),
            ),
            cost = 2.60,
            tokens = TokenUsage(input = 240_880, output = 18_442),
        ).copy(error = "Stopped: 1 file could not be written")
        return listOf(asked, reply)
    }

    private fun parserSession(now: Long) = listOf(
        user(
            "msg_u1", now - 4 * DAY,
            "Write tests for the CSV parser. Cover quoted fields and embedded newlines.",
        ),
        assistant(
            id = "msg_a1", at = now - 4 * DAY + 3_000,
            parts = listOf(
                Part.Text(
                    id = "pt_1",
                    text = "Added 14 cases covering quoted delimiters, embedded newlines, CRLF " +
                        "normalisation and the empty-input edge case.",
                ),
                Part.Tool(
                    id = "tl_1", toolId = "bash", name = "Bash",
                    state = ToolState(
                        status = ToolStatus.Completed,
                        title = "npm test -- parser",
                        subtitle = "14 passed",
                        input = "{\n  \"command\": \"npm test -- parser\"\n}",
                        output = "PASS src/lib/__tests__/parser.test.ts\nTests: 14 passed, 14 total",
                        meta = listOf("exit" to "0"),
                        durationMs = 2_480,
                    ),
                ),
            ),
            agent = "build",
            model = ModelRef("anthropic", "claude-sonnet-4-5"),
            cost = 0.12,
            tokens = TokenUsage(input = 12_300, output = 4_010),
        ),
    )

    private fun deploySession(now: Long) = listOf(
        user("msg_u1", now - 6 * DAY, "Document the deploy process for the ops runbook."),
        assistant(
            id = "msg_a1", at = now - 6 * DAY + 2_000,
            model = ModelRef("openai", "gpt-6-mini"),
            parts = listOf(
                Part.Text(
                    id = "pt_1",
                    text = "Wrote the runbook section covering the release cut, the migration step, " +
                        "and the rollback path.\n\n" +
                        "| Step | Command | Owner |\n" +
                        "| --- | --- | --- |\n" +
                        "| Cut release | `make release` | on-call |\n" +
                        "| Migrate | `make migrate` | on-call |\n" +
                        "| Verify | `make smoke` | on-call |",
                ),
            ),
            cost = 0.07,
            tokens = TokenUsage(input = 8_220, output = 1_930),
        ),
    )

    // --- Reply script ------------------------------------------------------

    /** One step of a scripted, streamed assistant reply. */
    sealed interface ScriptStep {
        data class Reasoning(val text: String) : ScriptStep
        data class Text(val text: String) : ScriptStep
        data class Tool(
            val toolId: String,
            val name: String,
            val title: String,
            val subtitle: String? = null,
            val input: String,
            val output: String,
            val durationMs: Long = 400,
            val fail: Boolean = false,
            val meta: List<Pair<String, String>> = emptyList(),
        ) : ScriptStep
        data class AskPermission(val request: PermissionRequest) : ScriptStep
        data class Diff(val files: List<DiffFile>) : ScriptStep
        data class Note(val text: String) : ScriptStep
    }

    /**
     * Chooses a reply based on the prompt so the demo does not feel canned.
     * Falls back to a generic but still realistic walkthrough.
     */
    fun replyScript(prompt: String, sessionId: String, agent: String): List<ScriptStep> {
        val lower = prompt.lowercase()
        return when {
            lower.contains("test") -> testScript()
            lower.contains("bug") || lower.contains("fix") || lower.contains("error") ->
                fixScript(prompt)
            lower.contains("explain") || lower.contains("how") || lower.contains("why") ->
                explainScript()
            else -> exploreScript(prompt, agent)
        }
    }

    private fun testScript(): List<ScriptStep> = listOf(
        ScriptStep.Reasoning(
            "The prompt asks for tests, so I should look at the existing test setup first rather " +
                "than assume a framework. Then write cases against the real API surface.",
        ),
        ScriptStep.Text("Let me check how tests are set up here before writing any."),
        ScriptStep.Tool(
            toolId = "glob", name = "Glob",
            title = "glob \"**/*.test.*\"",
            subtitle = "6 files",
            input = "{\n  \"pattern\": \"**/*.test.*\"\n}",
            output = "src/lib/__tests__/parser.test.ts\n" +
                "src/lib/__tests__/tokens.test.ts\n" +
                "src/components/__tests__/Button.test.tsx\n" +
                "src/components/__tests__/Panel.test.tsx\n" +
                "src/settings/__tests__/ExportPanel.test.tsx\n" +
                "src/util/__tests__/format.test.ts",
            durationMs = 180,
        ),
        ScriptStep.Tool(
            toolId = "read", name = "Read",
            title = "read src/lib/__tests__/parser.test.ts",
            subtitle = "52 lines",
            input = "{\n  \"path\": \"src/lib/__tests__/parser.test.ts\"\n}",
            output = "import { describe, it, expect } from \"vitest\";\n" +
                "import { parse } from \"../parser\";\n\n" +
                "describe(\"parse\", () => {\n" +
                "  it(\"splits simple rows\", () => {\n" +
                "    expect(parse(\"a,b\\nc,d\")).toEqual([[\"a\", \"b\"], [\"c\", \"d\"]]);\n" +
                "  });\n" +
                "});",
            durationMs = 96,
        ),
        ScriptStep.Text(
            "Vitest, and the existing file already covers the happy path. The gaps are quoted " +
                "delimiters, embedded newlines, and CRLF input.",
        ),
        ScriptStep.Tool(
            toolId = "bash", name = "Bash",
            title = "npm test -- parser",
            subtitle = "exit 0 · 14 passed",
            input = "{\n  \"command\": \"npm test -- parser\"\n}",
            output = "PASS src/lib/__tests__/parser.test.ts\n" +
                "  parse\n" +
                "    ✓ splits simple rows\n" +
                "    ✓ keeps delimiters inside quotes\n" +
                "    ✓ handles embedded newlines\n" +
                "    ✓ normalises CRLF\n" +
                "    ✓ returns [[]] for empty input\n" +
                "\nTests: 14 passed, 14 total",
            durationMs = 3_140,
            meta = listOf("exit" to "0"),
        ),
        ScriptStep.Text(
            "Done — 14 passing cases. The two that caught real bugs were embedded newlines " +
                "(the splitter was naive) and CRLF normalisation.\n\n" +
                "```ts\n" +
                "it(\"handles embedded newlines\", () => {\n" +
                "  expect(parse('a,\"b\\nc\"')).toEqual([[\"a\", \"b\\nc\"]]);\n" +
                "});\n" +
                "```",
        ),
    )

    private fun fixScript(prompt: String): List<ScriptStep> = listOf(
        ScriptStep.Reasoning(
            "A fix needs the failure reproduced before the cause is assumed. I'll find the " +
                "relevant code and the error path first.",
        ),
        ScriptStep.Text("Let me reproduce it before changing anything."),
        ScriptStep.Tool(
            toolId = "grep", name = "Grep",
            title = "grep \"throw new Error\" src",
            subtitle = "11 matches",
            input = "{\n  \"pattern\": \"throw new Error\",\n  \"path\": \"src\"\n}",
            output = "src/lib/parser.ts:31\nsrc/lib/parser.ts:58\nsrc/settings/store.ts:14\n" +
                "src/settings/store.ts:44\nsrc/api/client.ts:72\nsrc/api/client.ts:118\n" +
                "src/util/format.ts:22\nsrc/util/format.ts:63\nsrc/util/format.ts:90\n" +
                "src/auth/session.ts:27\nsrc/auth/session.ts:96",
            durationMs = 240,
        ),
        ScriptStep.Tool(
            toolId = "bash", name = "Bash",
            title = "npm test -- --run store",
            subtitle = "exit 1 · 1 failed",
            input = "{\n  \"command\": \"npm test -- --run store\"\n}",
            output = "FAIL src/settings/__tests__/store.test.ts\n" +
                "  ● store › persists selection across reloads\n\n" +
                "    TypeError: Cannot read properties of undefined (reading 'id')\n" +
                "      at Object.get (src/settings/store.ts:44:18)\n\n" +
                "Tests: 1 failed, 11 passed, 12 total",
            durationMs = 2_210,
            fail = true,
            meta = listOf("exit" to "1"),
        ),
        ScriptStep.Text(
            "Reproduced. `store.ts:44` dereferences a selection that was never hydrated — the " +
                "reset path clears the record but leaves the key behind, so the read is " +
                "non-null but empty.\n\n" +
                "The fix is to make the read tolerant rather than to change the reset.",
        ),
        ScriptStep.AskPermission(
            PermissionRequest(
                id = "perm_demo_1",
                sessionId = "",
                kind = PermissionKind.Edit,
                title = "Edit src/settings/store.ts",
                detail = "Make the persisted-selection read tolerate a missing record.",
                resources = listOf("src/settings/store.ts"),
            ),
        ),
        ScriptStep.Text(
            "Once that's approved I'll re-run the suite to confirm the failure is gone and " +
                "nothing else regressed.",
        ),
    )

    private fun explainScript(): List<ScriptStep> = listOf(
        ScriptStep.Reasoning(
            "An explanation request is read-only. I should trace the actual call path rather " +
                "than describe what the code probably does.",
        ),
        ScriptStep.Text("Let me trace the actual path through the code."),
        ScriptStep.Tool(
            toolId = "grep", name = "Grep",
            title = "grep \"createSession\" src",
            subtitle = "3 matches",
            input = "{\n  \"pattern\": \"createSession\",\n  \"path\": \"src\"\n}",
            output = "src/api/client.ts:104\nsrc/settings/store.ts:61\nsrc/routes/session.ts:18",
            durationMs = 140,
        ),
        ScriptStep.Tool(
            toolId = "read", name = "Read",
            title = "read src/api/client.ts",
            subtitle = "142 lines",
            input = "{\n  \"path\": \"src/api/client.ts\"\n}",
            output = "export async function createSession(input: SessionInput) {\n" +
                "  const res = await fetch(`\${base}/session`, {\n" +
                "    method: \"POST\",\n" +
                "    headers: { \"content-type\": \"application/json\" },\n" +
                "    body: JSON.stringify(input),\n" +
                "  });\n" +
                "  if (!res.ok) throw new ApiError(res.status, await res.text());\n" +
                "  return res.json() as Promise<Session>;\n" +
                "}",
            durationMs = 88,
        ),
        ScriptStep.Text(
            "Here's the path, end to end:\n\n" +
                "1. **`src/routes/session.ts:18`** — the route handler validates the body and " +
                "calls into the client.\n" +
                "2. **`src/api/client.ts:104`** — `createSession` POSTs to `/session` and throws " +
                "`ApiError` on any non-2xx.\n" +
                "3. **`src/settings/store.ts:61`** — the store subscribes to the result and " +
                "writes it into the persisted cache.\n\n" +
                "The interesting part is step 3: the store writes before the request resolves, " +
                "so a failed create leaves a stale entry behind. That is the same shape as the " +
                "bug in the parser session.",
        ),
    )

    private fun exploreScript(prompt: String, agent: String): List<ScriptStep> = listOf(
        ScriptStep.Reasoning(
            "The request is open-ended, so the useful first move is to establish the shape of " +
                "the codebase before proposing anything concrete.",
        ),
        ScriptStep.Text("Let me get the lay of the land first."),
        ScriptStep.Tool(
            toolId = "glob", name = "Glob",
            title = "glob \"src/**/*.ts\"",
            subtitle = "128 files",
            input = "{\n  \"pattern\": \"src/**/*.ts\"\n}",
            output = "src/api/client.ts\nsrc/api/errors.ts\nsrc/auth/session.ts\n" +
                "src/lib/parser.ts\nsrc/lib/tokens.ts\nsrc/routes/session.ts\n" +
                "src/settings/store.ts\nsrc/util/format.ts\n… 120 more",
            durationMs = 210,
        ),
        ScriptStep.Tool(
            toolId = "bash", name = "Bash",
            title = "git log --oneline -8",
            subtitle = "exit 0",
            input = "{\n  \"command\": \"git log --oneline -8\"\n}",
            output = "b41c2ae add light-mode contrast override for primary buttons\n" +
                "9f0d117 rate limit public API by key\n" +
                "2ce88a1 normalise CRLF in the CSV parser\n" +
                "77ab5de fix store hydration on reload\n" +
                "1d90f4c document the deploy runbook\n" +
                "5a31c7b shard the CI test job\n" +
                "e0b2f19 add tests for quoted fields\n" +
                "c7d43a8 initial commit",
            durationMs = 160,
        ),
        ScriptStep.Text(
            "Here's what I can see.\n\n" +
                "**Shape**\n\n" +
                "- `src/api` — the HTTP client and error types\n" +
                "- `src/settings` — a persisted store over the API responses\n" +
                "- `src/lib` — pure helpers, the best-tested part of the repo\n" +
                "- `src/routes` — request handlers\n\n" +
                "> Recent history is dense in the settings and parser areas, so that is where " +
                "the churn is and likely where the work is.\n\n" +
                "I ran as agent `$agent`. Tell me which area to go deeper on and I'll read it " +
                "properly rather than guess from filenames.",
        ),
    )
}
