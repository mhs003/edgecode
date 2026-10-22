# EdgeCode — Implementation Plan

An Android client for **OpenCode** servers. Kotlin + Jetpack Compose, built
entirely on-device in Termux with [TAD](../../.local/share/TAD).

**Status:** the v1 client is implemented and shipped (1.0.x). The v2 adapter is
not implemented. This document describes the design as built; `ARCHITECTURE.md`
holds the rationale and invariants, and `DESIGN-HISTORY.md` keeps superseded
design decisions and the current critique for the next redesign.

---

## 1. What we are building

An OpenCode server runs somewhere — a VPS, a laptop, or Termux on the phone
itself. The app connects to it, verifies the connection, and then acts as a
remote control for it:

- **Connect** to a server by URL + optional credentials, validate it, land in
  the session list.
- **Sessions** — list with live status, search, resume, create in a directory,
  rename, delete.
- **Chat** — streaming replies with markdown in both directions, reasoning,
  tool-call rendering, permissions, diffs, slash commands.
- **Model & agent control** — pick a model per chat, switch agents.
- **Subagents** — inspect sessions spawned by a `task` call, read-only.
- **Attachments** — pick files into a prompt.
- **Settings** — dark (default), light and system themes; reasoning, tool and
  send preferences.
- **Demo mode** — `demo://` drives the whole interface without a server.

`DESIGN.md` is the binding visual standard: balanced minimalism, quiet
sophistication, adaptive density, subtle surfaces, no card-per-section.

---

## 2. Current state

| Area | State |
|---|---|
| v1 transport (HTTP + SSE) | Implemented in `data/net` |
| Event reduction | Implemented as pure `ChatReducer` |
| Connect / saved servers | Implemented |
| Sessions (list, status, search, create, rename, delete) | Implemented |
| Chat transcript, streaming, markdown, code, diffs | Implemented |
| Tools, permissions | Implemented |
| Models, agents, slash commands | Implemented |
| Subagents | Implemented |
| Attachments | Picker and composer implemented; base64 encoding is not (see §10) |
| Demo mode | Implemented |
| v2 adapter | Not implemented |
| JVM + instrumented tests | Implemented |

---

## 3. Research findings

### 3.1 Two server APIs exist

| | **v1** (target) | **v2** |
|---|---|---|
| Install | `opencode.ai/install` (stable) | `opencode.ai/v2/install` (beta) |
| Routes | root (`/session`, `/event`) | `/api/*` |
| Spec | OpenAPI 3.1 at `/doc` | `packages/protocol/openapi.json` |
| Status | documented, shipped | self-described "experimental" |
| Client | ours | official web + desktop |

**Decision: v1 first behind an interface; v2 later.**

### 3.2 v1 transport

- Base URL `http://host:port`, default port **4096**.
- **Auth**: HTTP Basic. `OPENCODE_SERVER_PASSWORD` sets the password; the
  username defaults to `opencode` (`OPENCODE_SERVER_USERNAME` overrides).
  No password set → no auth.
- **Health**: `GET /global/health` → `{ healthy, version }`.
- **Events**: `GET /event`, SSE. First event is `server.connected`.
- **Spec**: `GET /doc`.

### 3.3 v1 data model

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
`message.part.delta`, `permission.asked`, `permission.replied`,
`session.idle`, `session.error`, `session.created`, `session.updated`,
`session.deleted`, `session.status`, `server.connected`.

### 3.4 Interface references

**OpenCode TUI** establishes the patterns carried over to touch:

- Compact **one-line tool calls** with a status glyph, expanding on demand.
- A **session meta bar**: title, token count, context %, cost — kept behind the
  overflow menu in the chat rather than repeated under every reply.
- **Agent + model shown in the composer footer**, acting as the switch point.
- Reasoning rendered distinctly (dimmed) and separately from answer text.
- User turns set apart by a tonal surface, not a heavy bubble.

**ChatGPT / Claude / Grok Android** establish mobile conventions we keep:
full-width message rows with a role affordance rather than left/right chat
bubbles; floating chrome over an edge-to-edge transcript; the composer pinned
above the keyboard with attachments on `+`; a welcome screen with terse,
scannable facts and one primary CTA.

### 3.5 Constraints that shape the design

1. **The v1 event stream is best-effort by nature.** It can drop a slow
   consumer, and events during a disconnect are simply lost. State must be
   re-derivable from REST.
2. **Builds happen on the phone.** Every extra Gradle module and dependency
   costs real minutes. One module, few dependencies.
3. **Tool output can be enormous** (file dumps, diffs, command output).
   Truncation and collapsing are correctness features.
4. **Attachments must be downscaled and capped.** The prompt contract expects
   base64 data URIs; memory and size limits are real.
5. **LAN servers are usually plain `http://`.** Android blocks cleartext by
   default, so this needs explicit allowance.

---

## 4. Architecture (as built)

**One Gradle module.** Multi-module splits would multiply on-device build time
for no benefit at this size. Boundaries are enforced by package structure:

```
dev.edgecode
├── MainActivity.kt          entry point + navigation host
├── core/
│   ├── design/              tokens, theme, icons, shared components
│   ├── markdown/            CommonMark subset parser, inline spans, highlighter
│   ├── model/               domain types — the UI never sees wire types
│   └── util/                Format, Diff, Fuzzy, Json, Clipboard, Images
├── data/
│   ├── OpenCodeApi.kt       the API seam + ChatReducer (pure)
│   ├── AppRepository.kt     state, event reduction, actions
│   ├── SettingsStore.kt     preferences (SharedPreferences)
│   ├── ConnectionsStore.kt  saved servers (JSON in SharedPreferences)
│   ├── net/                 HttpOpenCodeApi, Transport, OpenCodeWire, Sse
│   └── demo/                DemoOpenCodeApi + fixture content
└── ui/
    ├── AppViewModel.kt      navigation stack, selection, wiring
    ├── chat/                transcript parts, composer, pickers, permissions
    └── screens/             Connect, Sessions, Chat, Settings
```

### 4.1 The API seam

`OpenCodeApi` is the single interface between the app and a server. Everything
above it speaks `core/model` types. `HttpOpenCodeApi` is the live v1 client;
`DemoOpenCodeApi` satisfies the same interface behind `demo://`.

```kotlin
interface OpenCodeApi {
    val version: ApiVersion

    suspend fun health(): ServerInfo
    suspend fun projects(): List<ProjectRef>
    suspend fun sessions(): List<SessionSummary>
    suspend fun sessionStatuses(): Map<String, SessionStatus>

    suspend fun createSession(directory: String?, title: String?): SessionSummary
    suspend fun renameSession(sessionId: String, title: String)
    suspend fun deleteSession(sessionId: String)
    suspend fun messages(sessionId: String): List<ChatMessage>

    suspend fun sendPrompt(
        sessionId: String,
        text: String,
        attachments: List<Attachment> = emptyList(),
        agent: String? = null,
        model: ModelRef? = null,
        messageId: String? = null,
    ): String

    suspend fun runCommand(sessionId: String, command: String, arguments: String)
    suspend fun runShell(sessionId: String, command: String)
    suspend fun abort(sessionId: String)

    suspend fun respondPermission(
        sessionId: String,
        requestId: String,
        decision: PermissionDecision,
        remember: Boolean,
    )

    suspend fun commands(): List<SlashCommand>
    suspend fun agents(): List<AgentInfo>
    suspend fun providers(): List<ProviderInfo>
    suspend fun models(): List<ModelInfo>

    fun events(): Flow<ServerEvent>
    suspend fun close()
}
```

`runShell` is part of the seam but has no UI surface yet; slash commands route
through `runCommand`.

### 4.2 State and streaming

- `AppRepository` owns the API, subscribes to `events()`, and exposes
  `StateFlow`s for connection, sessions, subagents, permissions, the catalog,
  and one `StateFlow<SessionChatState>` per opened chat.
- `ChatReducer` is a set of pure `(State, ServerEvent) -> State` functions for
  messages, sessions and permissions. It has no Android or coroutine types, so
  the hard part — merging deltas, reconciling stale frames — is JVM-testable.
- `AppRepository` writes transport state directly: loading, errors, the working
  flag, optimistic sends and permission removal.
- Sessions are published as two views of one list: roots (`sessions`) and
  children (`subagents`). A subagent is a branch, not a chat.
- Initial state comes from REST: connect fetches the catalog and session list,
  and the first open of a chat fetches its messages. The stream accelerates the
  UI; it never defines truth.

### 4.3 Model selection

`ModelSelection` keeps three layers:

- **pending** — what the picker is showing but has not sent with.
- **committed** — the model each chat last sent a turn with.
- **recent** — the last six models used anywhere.

A new chat inherits the most recent model. Picking a model only stages it;
sending commits it to the chat and moves it to the front of the recent list.

### 4.4 Dependencies

No JSON library, no HTTP library, no DI framework, no DataStore, no navigation
library, no markdown library, no image loader. AndroidX + Compose only.

- `core/util/Json.kt` — hand-written parser/serialiser, kept pure so every wire
  mapper is JVM-testable.
- `HttpURLConnection` provides HTTP and the SSE body stream.
- `SharedPreferences` backs settings and saved connections.
- Manual construction in `AppViewModel` is the object graph.

### 4.5 Transcript layout

Top-anchored, like the reference clients. There is no app bar: a floating row
of circular controls sits over the top and a floating composer pill over the
bottom. Following the newest content is a remembered intent updated when a
scroll settles, not a measured `atBottom` test, and the follow effect is skipped
while a gesture or fling is in progress. See `ARCHITECTURE.md` for the rationale.

---

## 5. Design system

Tokens before screens, per `DESIGN.md` §7.

- **Color** — neutral base, one accent, semantic success/warning/error and diff
  colours. Dark is default; light and system-follow are supported. `ContrastTest`
  enforces 4.5:1 for every text tier against the canvas and the tonal fill.
- **Typography** — semantic scale (display/title/body/label/meta/mono) with
  controlled line height and measure. Regular + medium carry the UI.
- **Spacing** — 4dp base step in `Space`.
- **Radii** — `Radius.xs/sm/md/lg/xl/full`; nothing is a pill by default.
- **Surfaces** — one canvas and one tonal fill. No resting borders on inputs,
  no separators in lists, no rules under headers.
- **Motion** — `Motion.fast/base/slow` with standard and emphasized easings.

Components live in `core/design/components`: `MarkdownText`, `CodeBlock`,
`CircleButton`, `EdgeButton`, `EdgeSecondaryButton`, `Chip`, `EdgeTextField`,
`EdgeTopBar`, `EdgeSearchBar`, `IconAction`, `EmptyState`, `ErrorNotice`,
`InlineProgress`, `Section`, `SettingsRow`, `RowDivider`, `SheetHeader`,
`StatusDot`, `TextAction`, and the inset/tap modifiers.

---

## 6. Screens (as built)

1. **Connect** — saved servers first, with the row being dialled showing a
   spinner; a disclosed form for a new connection (URL, username, password,
   "Save for later"); demo entry. Passwords are memory-only.
2. **Sessions** — search, active/recent sections, live status, directory and
   model metadata, create in a reported project directory, rename, delete,
   refresh, settings, disconnect.
3. **Chat** — edge-to-edge transcript, streaming deltas and caret, reasoning,
   one-line expandable tool calls, diffs, notes, permission sheet, slash
   commands, model and agent pickers, session statistics and context pressure
   in the header overflow, subagent links and sheet, abort.
4. **Settings** — theme (dark/light/system), reasoning, expanded tools,
   send-on-enter, server state, about.

Navigation is a hand-rolled stack: `Connect → Sessions → Chat`, with `Settings`
pushed over Sessions or Chat. Back from Chat returns without losing stream state.

---

## 7. Feature mapping

| Requirement | Approach |
|---|---|
| Credentials at launch, validate, then land in sessions | Connect screen → `GET /global/health` then `GET /session`; only then persist the profile |
| Session list, resume or create in a directory | `GET /session`, `POST /session` with `directory`, `PATCH` rename, `DELETE` |
| Live status in the list | `session.status` events merged with `GET /session/status`; the payload only infers compacting |
| Dark default + light/system themes | `SharedPreferences`-backed `ThemeMode`; `DESIGN.md` tokens for both |
| Tool calling in chats | `Part.Tool` → one-line row with status, expandable input/output |
| Markdown, both directions | Hand-rolled renderer; user text rendered too |
| Slash commands | `GET /command`; `/models`, `/agents` are client surfaces, the rest post to `/session/{id}/command` |
| Model selecting/changing | `GET /provider`; per-chat `ModelSelection`; picker fuzzy-searches connected providers |
| Agent selecting | `GET /agent`; picker in the composer footer |
| Subagents | `task` metadata `sessionId` links the child; header chip lists active ones; opened read-only |
| Image/document sending | File picker → `Attachment` → `FilePartInput`; base64 conversion is still an open item (§10) |
| Permissions | `permission.asked` → sheet → `POST /permission/{id}/reply` |

---

## 8. Network implementation

Status: **spec verified against a live v1.19.3 server** (`GET /doc`, 162 paths).
Every shape below was read off that server, not the SDK types and not guessed.

### 8.1 No new dependencies

- `AGENTS.md` forbids a DI framework and a markdown library, and the codebase
  hand-rolls where a dependency would be less honest.
- `ChatReducer`, `Markdown`, `Diff`, `Format`, `Fuzzy`, `Json`, `Sse` and
  `OpenCodeWire` must stay JVM-testable, so the wire format cannot land in
  Android-only code.
- `org.json` is Android-only and *stubbed to throw* in JVM unit tests, so using
  it would push the mapping layer out of test reach.

So: a small hand-written JSON parser in `core/util/Json.kt`, and
`HttpURLConnection` for transport. Both are dependency-free, and the parser is
pure so the mappers that consume it are unit-testable exactly like `ChatReducer`.

### 8.2 Layers

```
ui/            reads core/model (+ data state types), never wire types
data/          repository, stores, OpenCodeApi seam
data/net/      transport + mappers (Sse, OpenCodeWire)
core/util/     Json (+ formatting, diffs, fuzzy)
```

Three pure-by-design units:

| File | Responsibility | Testable |
|---|---|---|
| `core/util/Json.kt` | parse/serialise JSON to a `JsonValue` tree | JVM |
| `data/net/Sse.kt` | reassemble SSE frames from byte chunks | JVM |
| `data/net/OpenCodeWire.kt` | `JsonValue` ⇄ `core/model` | JVM |

And two Android-bound units, kept thin so there is little to test:

| File | Responsibility |
|---|---|
| `data/net/Transport.kt` | interface + `HttpURLConnection` impl, streaming body callback |
| `data/net/HttpOpenCodeApi.kt` | `OpenCodeApi` over the transport |

### 8.3 Transport seam

```kotlin
interface Transport {
    suspend fun request(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: String? = null,
    ): HttpResponse

    /** Streams SSE frames until the caller cancels. */
    fun stream(path: String): Flow<String>   // one emission per `data:` payload

    fun close()
}

data class HttpResponse(val code: Int, val body: String)
```

`HttpURLConnection` has no async API, so `request` runs on `Dispatchers.IO` and
`stream` reads on a `flow {}` with `flowOn(Dispatchers.IO)`. A non-2xx raises
`OpenCodeHttpException(code, message)` carrying the server's error body, so the
Connect screen can distinguish 401 from an unreachable host.

The URL is used as entered, with `http://` prefixed when no scheme is given.
There is no implicit port: the connect form's placeholder shows the conventional
`4096`.

### 8.4 Wire mapping rules

Read from the live server. The parts that are easy to get wrong:

- **Message envelope.** `GET /session/{id}/message` returns
  `[{ info: UserMessage|AssistantMessage, parts: [...] }]` — the message and its
  parts are siblings in one object, not nested. Discriminate on `info.role`.
- **Parts are discriminated on `type`**, not `role`: `text`, `reasoning`, `file`,
  `tool`, `step-start`, `step-finish`, `snapshot`, `patch`, `agent`, `retry`,
  `compaction`, `subtask`. `text`, `reasoning`, `tool` and `file` become rendered
  parts; `patch` and `snapshot` become an (empty) `Part.Diff`; `retry`,
  `compaction`, `subtask` and `agent` become `Part.Note`; step boundaries are
  dropped; any unrecognised type becomes a Note naming it, so it degrades to a
  visible line rather than vanishing.
- **Tool state** is a union on `state.status`: `pending` (no time), `running`
  (`time.start`), `completed` (`time.start`+`end`, `output`), `error`
  (`time.start`+`end`, `error`). `durationMs` derives from that pair.
  `ToolState.input`/`output` are already-serialised text.
- **Timestamps are epoch millis** in `time.created` / `time.updated` /
  `time.completed`. `createdAt`/`updatedAt` on the model are millis.
- **Tokens** live at `tokens.input|output|reasoning|cache.read|cache.write` and
  map straight onto `TokenUsage`.
- **Model refs** appear in two shapes: `{id, providerID}` on a session and
  `{providerID, modelID}` on a message. Both map to `ModelRef(providerId,
  modelId, variant)`. Getting this backwards silently breaks the model chip.
- **`disabled`/`hidden` filtering**: agents with `hidden = true` are dropped
  from the picker; `mode = subagent` are not primary.
- **`Part.Text` carries `time.end`** — absent means still streaming. This is how
  `streaming` is decided on a REST fetch, without relying on the event stream.
- **Errors** are a union on `error.name`; only `data.message` is surfaced.
- **Subagent links** are at `state.metadata.sessionId` on a `task` part, with
  `parentSessionId` beside it. The output repeats the id inside `<task id="…">`.
- **Session status is not in the session payload.** `GET /session/status` returns
  `{ses_…: {type: "busy" | "retry" | "idle"}}` and omits idle sessions;
  `session.status` pushes the same union. `time.compacting` is the only
  in-payload working hint.

### 8.5 Streaming

`GET /event` emits:

```
data: {"id":"evt_…","type":"server.connected","properties":{}}
```

The envelope is `{id, type, properties}` — the payload is under `properties`,
not `data`. Event types map to `ServerEvent`:

| Event | → |
|---|---|
| `server.connected` | `Connected(version)` |
| `session.created` / `session.updated` | `SessionUpserted` |
| `session.deleted` | `SessionRemoved` |
| `message.updated` | `MessageUpserted` (`properties.info`) |
| `message.part.updated` | `PartUpserted` (`properties.part`) |
| `message.removed` | `MessageRemoved` |
| `message.part.delta` | `PartDelta(sessionID, messageID, partID, field, delta)` |
| `permission.asked` / `permission.replied` | `PermissionRequested` / `PermissionResolved` |
| `session.idle` | `SessionIdle` |
| `session.status` | `SessionStatusChanged(sessionID, status)` |
| `session.error` | `SessionFailed` |

Unmapped types are dropped rather than logged as errors — the stream is
documented as lossy and carries a great deal we do not render.

`session.idle` ends a turn (as does `session.error`), and `session.status` with
`type: "idle"` is the matching status transition. The chat clears its working
flag on `session.idle`/`session.error`; the session list row is updated by
`session.status` and by the REST status map. Both matter: a chat opened while it
is already running has no local `SessionIdle` of its own to rely on, and a
session the user is not looking at has no other way to show that it is busy.

A dropped stream is reported as `Disconnected` and reconnected with backoff
(500 ms → 8 s). A re-fetch of open state on reconnect is a known gap (§10).

### 8.6 Sending a prompt

`POST /session/{id}/prompt_async` — returns immediately, replies arrive over
SSE. This is the right call for a chat client; `POST /message` blocks until the
whole turn completes and would make the UI wait for an entire response before
showing anything.

Body: `{ parts: [TextPartInput], model: {providerID, modelID}, agent, messageID? }`.
The client generates a `msg_…` id and shows the user turn immediately; the
server keeps that id, so its echo merges onto the same row instead of
duplicating it.

**Attachments** ride along as `FilePartInput { type:"file", mime, url, filename }`.
The picker currently supplies the local content URI as `url`; converting it to
the base64 data URI the contract expects is an open item (§10).

### 8.7 Connection manager

`ConnectionsStore` mirrors `SettingsStore`: SharedPreferences, a
`MutableStateFlow` mirror, and optimistic writes. All decision-making lives in
the pure `Connections` object so it is unit-testable off-device.

```kotlin
data class SavedConnection(
    val id: String,
    val name: String,
    val url: String,
    val username: String? = null,
    val lastUsedAt: Long = 0L,
    val lastStatus: String? = null,
)
```

The list is persisted as one JSON document under a single key, using `Json` —
no delimiter splitting, which would break on URLs.

**Credentials are not persisted.** A saved connection carries url, username and
a display name only. `AppViewModel` keeps passwords in memory keyed by URL, so a
retry or a tap on a saved server does not re-prompt.

### 8.8 Connect screen

- Saved connections list first — name, url, relative "last used", and the last
  failure when there is one. Tapping connects; the row being dialled shows a
  spinner.
- "New connection" opens the form: URL, optional username, optional password,
  and a **"Save for later"** toggle (default on).
- `normalizeUrl` prefixes `http://` when no scheme is given and trims a trailing
  slash.
- Failures render in place with the server's own message.

### 8.9 Testing

Fixtures are captured responses from a running server, so the mappers are tested
against the server's actual output rather than a reading of the spec.

| Test | Covers |
|---|---|
| `JsonTest` | escapes, unicode, nesting, numbers, malformed input, encode round-trip |
| `SseTest` | frame splitting across chunk boundaries, multi-line `data:`, comments, CRLF |
| `OpenCodeWireTest` | every part variant, both model-ref shapes, tool states, errors, a full real message list |
| `ConnectionsStoreTest` | add/remove/touch, round-trip through JSON, URL round-trip |
| `ChatReducerTest` | delta accumulation, stale frames, optimistic echo, user message parts |
| `ModelSelectionTest` | pending/committed/recent semantics |
| `FuzzyTest` | multi-field subsequence ranking |
| `FormatTest` | token, cost, relative time, diff and response-meta formatting |
| `ContrastTest` | 4.5:1 for every text tier in both themes |
| `AppRepositoryTest` | concurrent catalog load, connect, subagent split, status merge |
| `ChatLayoutAndResponsivenessTest` | on-device transcript/composer insets and scrolling |

### 8.10 Order of work

All items are complete:

1. `Json` + tests
2. `Sse` + tests
3. `OpenCodeWire` + tests
4. `Transport` + `HttpOpenCodeApi`
5. `ConnectionsStore` + tests
6. Repository wiring
7. Connect screen
8. On-device verification against the live server
9. Documentation

---

## 9. Risks

| Risk | Mitigation |
|---|---|
| v1/v2 drift | Everything behind `OpenCodeApi`; adapters are swappable |
| Event stream drops | REST is the source of truth; a reconnect reconcile is still open (§10) |
| On-device build time | One module, few dependencies, incremental builds via `tad build` |
| Huge tool output | Detail blocks cap at 14 lines; tool rows collapse by default |
| Streaming recomposition | Key the transcript by message id; parts update in place by id |
| Base64 attachments | Downscale before encoding; hard size cap — not yet implemented (§10) |
| Cleartext LAN servers | `network_security_config` permits user-entered hosts |
| Password at rest | Never persisted; memory-only for the life of the process |
| Model registry size | The picker offers only connected providers; models are indexed once |

---

## 10. Open items

1. **Reconnect reconcile.** The stream reconnects with backoff, but the
   repository does not automatically re-fetch sessions or open transcripts after
   a drop. The design intent is to reconcile on `server.connected`.
2. **Base64 attachment encoding.** The picker and composer are wired, and a file
   part carries the picked URI. The v1 prompt contract expects a base64 `data:`
   URI, so a server that cannot resolve a device content URI will not receive
   the file.
3. **Attachment size ceiling.** The point at which the server rejects large data
   URIs is still unmeasured, so the UI has no hard cap to enforce.
4. **v2 adapter.** A v2 server answers v1 paths with 200 and an HTML document,
   so this is a second wire layer behind `OpenCodeApi`, chosen by a probe at
   connect — not a patch to the v1 one.

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
[protocol/openapi.json](https://github.com/anomalyco/opencode/blob/dev/packages/protocol/openapi.json)

### v2 is a different API, not a newer v1

Measured against a live `opencode v2.0.22` server:

| | v1.19.3 — this client's target | v2.0.22 |
|---|---|---|
| paths | `/session`, `/provider`, `/event`, `/global/health` | same names under `/api` |
| unknown path | JSON error | the web UI's HTML with status 200 |
| auth | none | Basic on every `/api` path; `401 {"_tag":"UnauthorizedError"}` |
| errors | `{data: {message}}` | tagged union `{_tag, message}` |

The consequence is worth stating plainly: a v2 server answers every v1 path with
**200 and an HTML document**, so a v1 client fails while parsing JSON
(`unexpected character '<' at offset 0`) rather than reporting a version
mismatch. None of the v1 mapping transfers unchanged.

Supporting v2 therefore means a second wire layer behind `OpenCodeApi`, chosen
at connect time — not a patch to the existing one. **Not implemented.** The
target stays v1, which is what the Termux server runs.
