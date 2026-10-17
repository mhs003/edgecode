# EdgeCode — Implementation Plan

An Android client for **OpenCode** servers. Kotlin + Jetpack Compose, built
entirely on-device in Termux with [TAD](../../.local/share/TAD).

---

## 1. What we are building

An OpenCode server runs somewhere — a VPS, a laptop, or Termux on the phone
itself. The app connects to it, verifies the connection, and then acts as a
full remote control for it:

- **Connect** to a server by URL + optional credentials, validate it, land in
  the session list.
- **Sessions** — list, resume, create in a directory, delete.
- **Chat** — streaming replies with markdown in both directions, tool-call
  rendering, permissions, slash commands, shell execution.
- **Model & agent control** — pick provider/model/agent per session.
- **Attachments** — send images and documents into a prompt.
- **Settings** — dark (default) and light themes, server profiles, chat
  preferences.

`DESIGN.md` is the binding visual standard: balanced minimalism, quiet
sophistication, adaptive density, subtle surfaces, no card-per-section.

---

## 2. Research findings

### 2.1 Two server APIs exist

| | **v1** (target) | **v2** |
|---|---|---|
| Install | `opencode.ai/install` (stable) | `opencode.ai/v2/install` (beta) |
| Routes | root (`/session`, `/event`) | `/api/*` |
| Spec | OpenAPI 3.1 at `/doc` | `packages/protocol/openapi.json` |
| Status | documented, shipped | self-described "experimental" |
| Client | ours | official web + desktop |

**Decision: build v1 first behind an interface, add v2 later.**

### 2.2 v1 transport

- Base URL `http://host:port`, default port **4096**.
- **Auth**: HTTP Basic. `OPENCODE_SERVER_PASSWORD` sets the password; the
  username defaults to `opencode` (`OPENCODE_SERVER_USERNAME` overrides).
  No password set → no auth.
- **Health**: `GET /global/health` → `{ healthy, version }`.
- **Events**: `GET /event`, SSE. First event is `server.connected`.
- **Spec**: `GET /doc`.

### 2.3 v1 data model (extracted from `@opencode-ai/sdk@1.19.3`)

```
Message  = UserMessage | AssistantMessage          // discriminated by `role`

Part     = TextPart | ReasoningPart | FilePart | ToolPart
         | StepStartPart | StepFinishPart | SnapshotPart
         | PatchPart | AgentPart | RetryPart | CompactionPart
         | { type: "subtask", prompt, description, agent }

ToolPart = { callID, tool, state: ToolState, metadata? }
ToolState= Pending | Running | Completed | Error
TextPart = { text, synthetic?, ignored?, time? }
FilePart = { mime, filename?, url, source? }        // url may be a data: URI
Session  = { id, projectID, directory, parentID?,
             title?, time, summary?{ additions, deletions, files, diffs } }
Permission = { id, type, pattern?, sessionID, messageID, callID?,
               title, metadata }
Command  = { name, description?, agent?, model?, template, subtask? }
Agent    = { name, description?, mode, builtIn, ... }
Diff     = { file, before, after, additions, deletions }
```

Events worth handling: `message.updated`, `message.part.updated`,
`permission.updated`, `permission.replied`, `session.idle`, `session.error`,
`session.created`, `session.updated`, `session.deleted`, `session.diff`,
`session.status`, `file.edited`, `todo.updated`, `command.executed`,
`server.connected`.

### 2.4 Interface references

**OpenCode TUI** (the closest functional analogue) establishes the patterns we
should carry over to touch:

- Compact **one-line tool calls** with a status glyph, expanding on demand —
  e.g. `* Grep "Home" (18 matches)`, `→ Read path/to/file`. Not cards.
- A **session meta bar**: title, token count, context %, cost.
- **Agent + model shown in the composer footer**, acting as the switch point.
- Reasoning rendered distinctly (dimmed) and separately from answer text.
- User turns set apart by a tonal surface, not a heavy bubble.

**ChatGPT / Claude / Grok Android** establish mobile conventions we keep:
full-width message rows with a role affordance rather than left/right chat
bubbles; a compact top bar with the model as the primary affordance; the
composer pinned above the keyboard with attachments on a `+`; a welcome
screen with terse, scannable facts and one primary CTA.

### 2.5 Constraints that shape the design

1. **The v1 event stream is best-effort by nature.** It can drop a slow
   consumer, and events during a disconnect are simply lost. State must be
   re-derivable: the stream is an optimization, never the source of truth.
2. **Builds happen on the phone.** Every extra Gradle module and dependency
   costs real minutes. Favour one module and few dependencies.
3. **Tool output can be enormous** (file dumps, diffs, command output).
   Truncation and collapsing are correctness features, not polish.
4. **Attachments travel as base64 data URIs** inside the prompt payload.
   Memory and size limits are real.
5. **LAN servers are usually plain `http://`.** Android blocks cleartext by
   default, so this needs explicit allowance.

---

## 3. Architecture

**One Gradle module.** Multi-module splits would multiply on-device build time
for no benefit at this size. Boundaries are enforced by package structure
instead, which is cheap and reviewable:

```
dev.edgecode
├── app/                  application, DI graph, navigation host
├── core/
│   ├── design/           tokens, theme, typography, shared components
│   ├── model/            provider-agnostic domain types
│   └── net/              OkHttp, auth, SSE, error mapping
├── data/
│   └── opencode/         OpenCodeApi (interface) + V1 impl + mappers
├── domain/               repositories, event reducer, use cases
└── feature/
    ├── connect/          onboarding + server profiles
    ├── sessions/         session list
    ├── chat/             transcript, composer, tool calls, permissions
    └── settings/         appearance, servers, chat prefs, about
```

### 3.1 The API seam

The single most important interface in the project:

```kotlin
interface OpenCodeApi {
    suspend fun health(): ServerHealth
    suspend fun listSessions(): List<Session>
    suspend fun createSession(directory: String?, title: String?): Session
    suspend fun messages(sessionId: String, limit: Int): List<ChatMessage>
    suspend fun prompt(sessionId: String, parts: List<InputPart>,
                       model: ModelRef?, agent: String?): ChatMessage
    suspend fun command(sessionId: String, cmd: String, args: String): Unit
    suspend fun replyPermission(sessionId: String, id: String,
                                reply: PermissionReply, remember: Boolean): Unit
    suspend fun abort(sessionId: String): Unit
    fun events(): Flow<ServerEvent>          // SSE, auto-reconnecting
    // models, agents, commands, files, diff, providers…
}
```

Everything above `data/opencode` speaks domain types only. `V1Api` is the first
implementation; `V2Api` later satisfies the same interface, chosen by a probe
(`GET /global/health` → v1, `GET /api/info` → v2).

### 3.2 State and streaming

- A single `SessionStore` holds messages and parts keyed by
  `sessionId → messageId → partId`.
- The SSE stream is reduced into that store by a **pure function**
  `(State, ServerEvent) -> State`. Pure means it is unit-testable without a
  network, which is where most of our bug risk lives.
- On every (re)connect: fetch `/session/:id/message` and **reconcile**, rather
  than assuming the missed events will arrive. Streaming is a delta channel;
  REST is truth.
- Streaming text updates a part in place. The transcript keys by part id so a
  token arriving does not recompose the whole list.

### 3.3 Key libraries

| Need | Choice | Why |
|---|---|---|
| HTTP + SSE | OkHttp + `okhttp-sse` | SSE is built in; no extra protocol work |
| JSON | kotlinx.serialization | No reflection, works with Compose/R8 |
| DI | Hilt (or manual) | Manual first if build time hurts |
| Persistence | DataStore + `security-crypto` | Profiles and password at rest |
| Navigation | `navigation-compose` type-safe routes | Current convention |
| Markdown | **Own Compose renderer** | Streaming and code blocks need control a general parser won't give; avoids pulling a WebView into the transcript |
| Highlighting | lightweight Maven highlighter | Optional; degrade to plain monospace |

Markdown scope: headings, bold/italic/strikethrough, inline code, fenced code
with language, lists, task lists, blockquotes, links, rules, tables. Deliver it
incrementally — the renderer must never block core chat.

---

## 4. Design system

Tokens before screens, per `DESIGN.md` §7.

- **Color** — neutral base, one accent, semantic success/warning/error.
  Dark is the default theme; light and system-follow are supported. Contrast
  is verified in both.
- **Typography** — semantic scale (display/title/body/label/meta/mono) with
  controlled line height and measure. Regular + medium weights carry the UI;
  heavy weights are rare.
- **Spacing** — 4dp base step; relationships expressed by spacing, not boxes.
- **Radii** — a small systematic set (e.g. 6 / 10 / 16, plus full only for
  avatars and status dots). Nothing becomes a pill by default.
- **Surfaces** — tonal difference and hairlines instead of shadows. Elevation
  only when it conveys hierarchy or interaction.
- **Motion** — short, purposeful durations for panel and state changes;
  respects reduced-motion.

Components: `MessageRow`, `ToolCallRow`, `CodeBlock`, `Composer`,
`AttachmentStrip`, `SessionRow`, `ModelPicker`, `AgentPicker`,
`PermissionSheet`, `SlashCommandList`, `EmptyState`, `ErrorState`,
`ConnectionBadge`.

---

## 5. Screens

1. **Connect** — URL, optional username/password, optional profile name.
   Live validation with a capability report (version, auth, health) before
   continuing. Multiple saved servers.
2. **Sessions** — list with title, directory, relative time, cost, status dot.
   New-session action chooses a directory. Swipe/menu to rename or delete.
3. **Chat** — transcript, streaming indicator, abort, error states, permission
   prompts inline, session meta bar, composer with attachments, `/` commands,
   and model/agent in the footer.
4. **Settings** — appearance (theme, density, message width), servers
   (add/edit/reorder/remove), chat (show reasoning, tool-call detail,
   auto-scroll, send-on-enter), about (version, server version, licences).

Navigation: `Connect → Sessions → Chat`, with `Settings` reachable from
Sessions. Back from Chat returns to the list without losing stream state.

---

## 6. Feature mapping

| Requirement | Approach |
|---|---|
| Credentials at launch, validate, then land in sessions | Connect screen → `GET /global/health` then `GET /session`; only then persist the profile |
| Session list, resume or create in a directory | `GET /session`, `POST /session`; directory selection needs a Phase 0 check (see §8) |
| Dark mode default + light mode + settings | DataStore-backed theme; `DESIGN.md` tokens for both |
| Tool calling in chats | `ToolPart` → `ToolCallRow`: name, one-line input summary, status, expandable input/output |
| Markdown, both directions | Own Compose renderer; user text rendered too, so pasted markdown looks right |
| Slash commands / shell | `GET /command` for the list; `POST /session/:id/command`; shell via `POST /session/:id/shell` |
| Model selecting/changing | `GET /config/providers` + per-message `model`; picker in the composer footer |
| Image/document sending | File picker → base64 data URI → `FilePartInput`; thumbnails in the composer |

---

## 7. Phases

Each phase ends with a **runnable, committed** app — no placeholder screens
presented as finished work.

| # | Phase | Outcome |
|---|---|---|
| 0 | **Spike** | Run a real server; record fixtures; pin the models; settle v1 unknowns (§8) |
| 1 | Skeleton | Project, theme, tokens, navigation, DI — builds and runs on device |
| 2 | Connect | Profiles, health check, secure storage |
| 3 | Sessions | List, create, rename, delete |
| 4 | Transcript | Read-only render of messages + parts, markdown, code blocks |
| 5 | Send | Composer, streaming, abort, reconcile-on-reconnect |
| 6 | Tools | Tool-call rows, permissions, diffs |
| 7 | Commands | Slash commands, shell execution |
| 8 | Control | Model + agent pickers, session meta bar |
| 9 | Attachments | Images and documents |
| 10 | Polish | Motion, accessibility, empty/error states, both themes |
| 11 | v2 adapter | Probe + `V2Api` behind the same interface |

---

## 8. Open questions to close in Phase 0

These are genuinely unresolved and are cheaper to answer against a live server
than to guess:

1. **Directory scoping.** How does a v1 client target a working directory?
   (`POST /session` takes only `parentID`/`title`; v2 has an explicit
   `/session/{id}/move` and `PUT /session/{id}/environment`.) Confirm whether
   v1 keys off the server's cwd, a query parameter, or a header.
2. **Attachment size ceiling.** Where the server rejects large data URIs, so
   the UI can enforce a limit before the request.
3. **`/doc` shape.** Whether the served spec can drive a contract test.
4. **Auth failure mode.** Whether a bad password yields 401 or something less
   specific, to get the connect-screen error copy right.

---

## 9. Risks

| Risk | Mitigation |
|---|---|
| v1/v2 drift | Everything behind `OpenCodeApi`; adapters are swappable |
| Event stream drops | Reconcile from REST on every reconnect; never trust the stream for truth |
| On-device build time | One module, few dependencies, incremental builds via `tad build` |
| Huge tool output | Truncate + collapse by default; never hand the UI an unbounded string |
| Streaming recomposition | Key the transcript by part id; update in place |
| Base64 attachments | Downscale images before encoding; hard size cap with a clear error |
| Cleartext LAN servers | Explicit network-security-config allowance for user-entered hosts |
| Password at rest | `security-crypto`; never log credentials or full URLs with auth |

---

## 10. Testing

- **JVM unit tests** for the event reducer, mappers, markdown tokenizer and
  formatters — the bulk of the value, and they run fast.
- **MockWebServer** fixtures for the API layer, including a recorded SSE
  transcript, so streaming logic is tested without a device.
- **Compose UI tests on device** for the transcript, composer, tool rows and
  permission flow.
- **Manual pass** against a real server for each phase's definition of done,
  with screenshots inspected in both themes (per `DESIGN.md` §11).

---

## 11. Reference material

Kept locally in `.reference/` (git-ignored, re-fetchable):

```bash
# v2 OpenAPI spec
curl -o .reference/openapi-v2.json \
  https://raw.githubusercontent.com/anomalyco/opencode/dev/packages/protocol/openapi.json

# v1 type definitions
curl -o sdk.tgz https://registry.npmjs.org/@opencode-ai/sdk/-/sdk-1.19.3.tgz
tar xzf sdk.tgz && cp package/dist/gen/types.gen.d.ts .reference/types-v1.d.ts

# a live server publishes its own spec at /doc
curl http://HOST:4096/doc
```

Sources: [OpenCode server docs](https://opencode.ai/docs/server/) ·
[OpenCode intro](https://opencode.ai/docs/) ·
[OpenCode v2 docs](https://opencode.ai/v2/docs/) ·
[protocol/openapi.json](https://github.com/anomalyco/opencode/blob/dev/packages/protocol/openapi.json) ·
[ChatGPT Android UI reference](https://gummble.com/apps/chatgpt-android) ·
[opencode-ui desktop client](https://github.com/marmotz-dev/opencode-ui)
