# Architecture

## Purpose

EdgeCode is a client for an OpenCode server: it connects to a host running
`opencode serve`, lists that server's sessions, and drives them — sending
prompts, streaming replies, approving tool calls, switching models and agents.

The app is built and installed entirely on-device, in Termux. That single fact
explains most of the decisions below.

## Constraints

| Constraint | Consequence |
|---|---|
| Builds run on the phone (~1.5 GB free RAM, minutes per build) | One Gradle module, few dependencies, no code generation |
| The v1 event stream is documented as lossy | Streaming is a delta channel; REST is the source of truth |
| Assistant text arrives a few characters at a time | Markdown must render incomplete input, and re-render cheaply |
| Tool output can be unbounded | Truncation and collapsing are correctness features |
| Attachments travel as base64 data URIs in the prompt | Decode downscaled, cap sizes, never hold a full-size bitmap |
| LAN servers are usually plain `http://` | Cleartext must be explicitly permitted |

## Layering

```mermaid
graph TD
    UI["ui/ — screens, chat, AppViewModel"]
    MODEL["core/model — domain types"]
    DATA["data/ — OpenCodeApi, AppRepository, stores"]
    NET["data/net — Transport, HttpOpenCodeApi, wire"]
    DEMO["data/demo — DemoOpenCodeApi + fixtures"]
    DESIGN["core/design — tokens, theme, icons, components"]
    MD["core/markdown — parser, inline, highlighter"]
    UTIL["core/util — format, diff, clipboard, images, Json"]

    UI --> MODEL
    UI --> DESIGN
    UI --> MD
    UI --> UTIL
    UI --> DATA
    DATA --> MODEL
    NET --> DATA
    NET --> MODEL
    NET --> UTIL
    DEMO --> DATA
    DEMO --> MODEL
```

The load-bearing rule: **the UI only knows `core/model`.** Nothing above `data/`
may reference a wire format. That is what lets the demo and the real client be
swapped without touching a single screen.

| Component | Responsibility |
|---|---|
| `core/model` | `SessionSummary`, `ChatMessage`, `Part`, `ToolState`, `PermissionRequest`, `ServerEvent`, … |
| `core/design` | Colour, typography, spacing, radius and motion tokens; `EdgeTheme`; `EdgeIcons`; shared components |
| `core/markdown` | `Markdown.parse` → blocks, `renderInline` → `AnnotatedString`, `SyntaxHighlighter` |
| `core/util` | Formatting, unified-diff parsing, clipboard, bitmap decoding, and `Json` |
| `data/OpenCodeApi` | The API seam and `ChatReducer` (pure event reduction) |
| `data/AppRepository` | Owns the API, subscribes to events, exposes `StateFlow`s, performs actions |
| `data/ConnectionsStore` | Saved servers, with the list rules as a pure `Connections` object |
| `data/net` | The real client: `Transport`, `HttpOpenCodeApi`, `OpenCodeWire`, `Sse` |
| `data/demo` | `DemoOpenCodeApi` and fixture content |
| `ui/AppViewModel` | Navigation stack, selection state, settings writes |
| `ui/screens`, `ui/chat` | The four destinations and the transcript components |

## Data flow

**Connect.** `ConnectScreen` → `AppViewModel.connect` → `AppRepository.connect`
→ `OpenCodeApi.health()`. Only on success is the profile persisted and the
navigation stack replaced with Sessions. On failure the `ConnectionState.Failed`
carries the message the banner shows.

**Open a session.** `openSession(id)` marks the chat state loading, fetches
`OpenCodeApi.messages(id)`, then pushes the Chat destination. Because the fetch
is a plain list, re-entering a session re-fetches rather than trusting whatever
the event stream happened to deliver.

**Send.** `Composer` → `AppViewModel.send` → `AppRepository.sendPrompt`. The
optimistic user message is not constructed locally: the API emits
`ServerEvent.MessageUpserted`, which is reduced into state by the same path a
remote event would take. Local and remote behaviour are identical by design.

**Stream.** `DemoOpenCodeApi` plays a script: reasoning, then text, then tool
calls, emitting `PartUpserted` for the same part id as content grows. The
transcript keys `LazyColumn` items by message id, and parts are replaced in
place by id, so a token arriving does not rebuild the list.

**Approve.** A scripted step emits `PermissionRequested` and then *suspends* on a
`CompletableDeferred` until `respondPermission` completes it. The sheet is
therefore a real blocking round-trip, not a mocked animation.

## State model

- `AppRepository` holds `StateFlow`s for connection, sessions, permissions and
  the catalog, plus one `StateFlow<SessionChatState>` per opened session.
- All mutation of that state goes through `ChatReducer`, a set of pure functions
  `(State, ServerEvent) -> State`.
- `ChatReducer` has no Android or coroutine types. The bulk of the app's real
  complexity — merging streaming parts, reconciling a stale frame — is therefore
  testable on the JVM without a device.

## Transcript layout

The chat list is top-anchored, like every current chat client: the first turn
sits under the header and the conversation grows downward.

An earlier revision used `reverseLayout = true`, which pins the newest message to
the bottom and lets streaming growth extend upward without touching the scroll
position. It was correct but read wrong — a two-turn session rendered as a large
void above the messages, which is the opposite of how the reference clients look.
The replacement keeps the useful half of that property explicitly:

- Following is a remembered intent, not a measured position. An append makes the
  list scrollable before layout runs, so an `atBottom` test can fail exactly when
  the view most needs to follow. The intent is updated when a scroll settles:
  leaving the end disarms it, returning to the end re-arms it. `atBottom`
  (`!canScrollForward`) is still what decides whether the jump-to-latest control
  is shown.
- A `LaunchedEffect` keyed on the message count *and* the newest reply's text
  length re-pins the view as the reply streams. Keying on the count alone would
  leave the newest text below the fold, which is the failure mode `reverseLayout`
  was avoiding.
- The follow effect is skipped while a gesture or fling is in progress, so a
  reader who scrolls away mid-stream is never dragged back by the next token.
- `scrollToEnd` scrolls past the final item rather than to it. `scrollToItem`
  alone lands on the *top* of that item, which is wrong whenever the last turn is
  taller than the viewport.

## Design decisions

| # | Decision | Rationale, and what was rejected |
|---|---|---|
| 1 | One Gradle module | Multi-module multiplies on-device build time for no benefit at this size; boundaries are enforced by package instead |
| 2 | Hand-rolled markdown renderer | **compose-richtext** (1.0.0-alpha05) was evaluated and rejected: an alpha dependency on the most design-critical and streaming-critical path, and it renders code blocks as plain styled text, so our code chrome (language label, copy, collapse, horizontal scroll) would still need a custom renderer |
| 3 | Hand-authored `EdgeIcons` | `material-icons-extended` is ~35k generated classes to dex on a device with ~1.5 GB RAM. A 24dp grid, 1.8dp stroke and round caps also yields a more consistent icon voice than a mixed pack |
| 4 | Hand-rolled navigation stack | Four destinations, one-line back behaviour. Avoids a dependency and keeps transitions under our control |
| 5 | No image loading library | Attachments are a small bounded set, never loaded from the network; `ImageDecoder`/`BitmapFactory` with bounds sampling is ~60 lines |
| 6 | Manual DI | The object graph is a ViewModel, a repository and a settings store. A framework would obscure it |
| 7 | `SharedPreferences`, not DataStore | Settings are six scalars read once at startup; DataStore's flow machinery earns nothing here. Passwords are deliberately *not* persisted |
| 8 | Dark is the default theme | Matches the reference clients and how the app is used; light and follow-system are one tap away in Settings |
| 9 | API seam is an interface, not a client | The demo and the future v1 client implement the same `OpenCodeApi`, so the UI cannot tell them apart — except for the "Sample data" chip |
| 10 | Optimistic writes go through the event stream | Guarantees the local and remote paths are exercised identically, which is what makes the demo a real test of the UI |
| 11 | Conservative syntax highlighting | Only comments, strings, numbers and a small keyword set. Aggressive per-language highlighting is confidently wrong more often than it is right |
| 12 | One canvas, one fill | An earlier revision had five surface tones (`background` → `surfaceContainerHighest`), which turned every region into a card and gave the whole app a boxed-in feel. Surfaces now collapse: `background == surface` is the canvas, `surfaceContainer` is the single fill used by the composer, user turns and control circles, and `surfaceContainerHigh` is the pressed/inset step. **Nothing is outlined and nothing is divided.** Rejected: keeping the ladder and merely tuning it — the problem was the number of steps, not their values |
| 13 | No app bar on chat | The transcript runs edge to edge between a floating row of circular controls (top) and a floating composer pill (bottom). Both paint the canvas colour, so scrolled content disappears into them rather than sliding under a separate band. Rejected: keeping `EdgeTopBar` on chat — a title, a directory and two lines of chrome cost ~56dp of transcript for information the header no longer needs to carry |
| 14 | Session statistics behind the overflow menu | Token totals, cost, diff size and context percentage describe the whole session, so they moved out of the transcript. Repeating them under every reply turned the conversation into a log. Context pressure is the one figure that changes what you do next, so it alone surfaces automatically — as a chip in the header once it passes 80% |
| 15 | Per-turn meta footer removed | The agent name, model and cost are visible in the composer and the overflow menu. A settled turn now shows exactly one action (copy). Rejected: keeping a four-field footer for parity with the old design |
| 16 | Top-anchored transcript with explicit follow | See *Transcript layout* above |

## Invariants

1. Nothing above `data/` imports a wire-format type.
2. `ChatReducer`, `Markdown`, `SyntaxHighlighter`, `Diff` and `Format` stay free
   of Android types.
3. Colour comes from the theme; geometry comes from `Tokens`. No literals in
   feature code.
4. Every part update reuses its part id, so identity is stable while streaming.
5. Demo and real implementations both drive `ServerEvent`.
6. Every inset has exactly one owner per screen. Nested or repeated inset
   padding is additive and silently pushes content off the window.
7. Text that carries information clears 4.5:1 against the surface it sits on.
   `ContrastTest` enforces this for every tier against the canvas *and* the tonal
   fill; a palette change that breaks it fails the build.
8. Full-width `Row`s give at most one child `weight`. Unweighted siblings are
   measured first and the last one absorbs the overflow, which is how a status
   label once rendered one character per line down the screen edge.

## Network layer

`data/net` holds everything that knows about the wire, so that no other package
has to.

| File | Knows about | Tested by |
|---|---|---|
| `core/util/Json.kt` | JSON syntax only | `JsonTest` |
| `data/net/Sse.kt` | the SSE frame syntax only | `SseTest` |
| `data/net/OpenCodeWire.kt` | OpenCode's shapes | `OpenCodeWireTest` |
| `data/net/Transport.kt` | HTTP, auth and the SSE transport | device only |
| `data/net/HttpOpenCodeApi.kt` | routing only | device only |

The split matters because the two files that cannot be unit-tested are the two
that hold no decisions. Everything that could be wrong about the protocol lives
in a pure function with a test.

**No JSON dependency.** `org.json` ships with Android but is stubbed to throw in
JVM unit tests, so using it would push every mapper out of test reach; a
serialization library is an artifact to fetch and dex on a phone. The parser is
a few hundred lines and is the reason the mappers are testable at all.

**Fixtures are captured, not written.** `app/src/test/resources/fixtures/` holds
unmodified responses from a running server, so a mapping cannot be "correct"
only against a reading of the spec.

**Shapes that bite**, each pinned by a test:

- A message is `{info, parts}` — the two are siblings, and the message is
  discriminated by `info.role`.
- A user message carries `model: {providerID, modelID}`; an assistant message
  carries flat `providerID`/`modelID`. Reading one shape loses the other side.
- A part is still streaming while `time.end` is absent.
- The event payload is under `properties`, not `data`.
- A part id is a server-side `prt_…` identity. Sending the client's message id
  on a part is rejected with a 400.
- Step boundaries are structure, not content, and are not mapped at all.

**Failure degrades, never throws.** An unknown part type becomes a `Part.Note`
naming it and an unmapped event type is dropped. The server adds union variants
between minor versions and documents its stream as lossy, so a client that
crashes on the unfamiliar is worse than one that shows a line of text.

## Extension points

| Goal | How |
|---|---|
| Support the v2 server | Add `V2Api : OpenCodeApi` and probe at connect (`GET /global/health` vs `GET /api/info`) |
| Add a screen | Add a `Screen` case, a branch in `EdgeApp`, and a composable under `ui/screens` |
| Add an icon | Add a vector to `EdgeIcons` on the shared 24dp grid |
| Change the look | Edit tokens in `core/design/Tokens.kt` and `Theme.kt`; no screen edits should be needed. Re-run `ContrastTest` — it will fail if a tier drops below 4.5:1 |
| Change the chat surface | `ui/chat/Composer.kt` (the pill), `ui/chat/MessageRow.kt` (one turn), and the header in `ui/screens/ChatScreen.kt`. Shared circular controls come from `CircleButton` |
