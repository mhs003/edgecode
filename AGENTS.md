# AGENTS.md

Guidance for coding agents working on **EdgeCode** — an Android client for
OpenCode servers.

## What this is

A Kotlin + Jetpack Compose app that connects to an OpenCode server and drives it
from a phone: sessions, streaming chat, tool calls, permissions, model and agent
selection, attachments.

`DESIGN.md` is the binding visual standard. `ARCHITECTURE.md` explains why the
code is shaped the way it is. `PLAN.md` is the delivery plan and the API notes.
`DESIGN-HISTORY.md` keeps superseded design decisions, the taste notes behind
them, and the current critique of the interface. Read all four before
non-trivial changes; the first three are authoritative, the last is reference.

**Current state:** the UI is complete and the app talks to a real OpenCode v1
server. `data/net/HttpOpenCodeApi` is the live client; `DemoOpenCodeApi` remains
behind a `demo://` URL so the interface can still be driven without a server.

## Build and run

This project is built on-device in Termux with [TAD](../../.local/share/TAD).
There is no Android Studio and no desktop host.

```bash
tad doctor                 # verify the toolchain first
tad build debug            # assemble + verify the APK
tad run                    # build, install, launch, optional --log
tad test unit              # JVM unit tests
tad test instrumented      # Compose UI tests on this device
```

Builds are slow here (minutes, not seconds). Prefer one build over several, and
check `tad doctor` before assuming a compile error is a code error.

## Layout

```
app/src/main/java/dev/edgecode/
├── MainActivity.kt          entry point + navigation host
├── Notifications.kt         background alerts for questions and permissions
├── core/
│   ├── design/              tokens, theme, icons, and reusable components
│   ├── markdown/            CommonMark subset parser, inline spans, highlighter
│   ├── model/               domain types — the UI never sees wire types
│   └── util/                format, diff, fuzzy, Json, clipboard, images
├── data/
│   ├── OpenCodeApi.kt       the API seam + ChatReducer (pure)
│   ├── AppRepository.kt     state, event reduction, actions
│   ├── SettingsStore.kt     preferences
│   ├── ConnectionsStore.kt  saved servers
│   ├── net/                 the real client: HttpOpenCodeApi, Transport,
│   │                        OpenCodeWire, Sse — all protocol logic
│   └── demo/                fixture content + demo API implementation
└── ui/
    ├── AppViewModel.kt      navigation stack, wiring
    ├── chat/                transcript parts, composer, pickers, questions
    └── screens/             Connect, Sessions, Chat, Settings
```

## Rules

- **One Gradle module, on purpose.** Splitting into modules multiplies on-device
  build time for no benefit at this size. Enforce boundaries with packages.
- **The UI sees domain types, never wire types.** Screens read `core/model` and
  the repository's state types (`Catalog`, `SessionChatState`, `Settings`,
  `SavedConnection`), but nothing above `data/` may reference a wire format. All
  server interaction goes through `OpenCodeApi`.
- **Keep pure logic pure.** `ChatReducer`, `Markdown`, `SyntaxHighlighter`,
  `Diff`, `Format`, `Fuzzy`, `Json`, `Sse`, `OpenCodeWire` and `QuestionDraft`
  must stay free of Android and coroutine types so they are unit-testable on
  the JVM. That is
  where the real complexity lives.
- **Protocol logic belongs in a pure function.** `Json`, `Sse` and
  `OpenCodeWire` hold every decision about the wire format and are tested on the
  JVM against captured server responses. `Transport` and `HttpOpenCodeApi` are
  thin by design: they route, they do not decide.
- **No JSON or HTTP dependency.** `org.json` is Android-only and stubbed to
  throw under JVM unit tests, which would put the mappers out of test reach.
  OkHttp and kotlinx.serialization are artifacts to fetch and dex on a phone.
- **Unknown wire values degrade, they do not throw.** An unrecognised part type
  becomes a note naming it; an unmapped event type is dropped. The server adds
  union variants between minor versions and documents its event stream as lossy.
- **Streaming is a delta channel, never the truth.** The v1 server documents its
  event stream as lossy. Any new state that arrives by event must also be
  derivable from a REST fetch.
- **Health is the connection; the session list loads behind it.** `connect`
  returns after `health()` and starts the catalogue, session list and pending
  questions in the background. The sessions screen must show its loading state
  until the session fetch finishes, never the empty state.
- **A subagent is a branch, not a chat.** A `task` call creates an ordinary
  session with a `parentId`. Keep those out of the session list: the chat that
  spawned it reaches it from the call and from the header chip, and opens it
  read-only.
- **Do not add a markdown library.** The hand-rolled renderer exists because
  assistant text arrives a few characters at a time and must render mid-fence;
  and because code blocks need chrome (language, copy, collapse, horizontal
  scroll) that a general renderer will not give us. See ARCHITECTURE.md.
- **Do not add `material-icons-extended`.** It is thousands of generated classes
  to dex on a phone. Add to `EdgeIcons` instead, keeping the shared 24dp grid and
  1.8dp stroke.
- **No DI framework.** Manual construction in `AppViewModel` is enough at this
  size and keeps the graph readable.
- **Tokens, not literals.** Colours come from `MaterialTheme.colorScheme` and
  `LocalEdgeColors`; spacing, radii, sizes and durations from `core/design/Tokens`.
  No raw hex or arbitrary `dp` in feature code.
- **Typography comes from `EdgeTypography` and `EdgeText`.** Fira Code and the
  `calt`/`liga` feature string live only in `core/design/Fonts.kt`; feature code
  sets neither `fontFamily` nor `fontFeatureSettings`. Tracking stays `0` so the
  monospace ligature grid holds.
- **Components come from `core/design/components`.** Extend them rather than
  styling a Material component inline.
- **Respect `DESIGN.md` §10 anti-patterns:** no card around every section, no
  pills by default, no gradients or glows, no oversized headings, no placeholder
  screens presented as finished.

## Design language

The default is compact monochrome. Optional theme families change the palette,
never the layout. Match that.

- **Compact before spacious.** Chat body is `14/22`; app-bar icons are bare 32dp
  controls; buttons are 38dp; cards use 8–10dp padding. Do not inflate controls
  to Material's default footprint.
- **One canvas, a small tonal set.** `background == surface` is the canvas;
  `surfaceContainerLow` is the object/composer fill; `surfaceContainerHigh` is
  the selected/inset step. Cards belong to sessions, saved servers and grouped
  choices, not to every section.
- **Chat has floating chrome.** A row of bare icon controls floats over a
  gradient scrim at the top; the composer floats over a mirrored scrim at the
  bottom. Both reserve their height in the transcript. Do not replace this with
  a static app bar.
- **Assistant turns are bare text.** No bubble, no container, no meta footer. A
  settled turn shows one action (copy). Session statistics belong in the
  overflow menu.
- **User turns are compact tonal bubbles** with a hairline and an 18/6 radius.
  Do not make a short prompt fill the width.
- **Controls look like their action.** Use `BarIcon` for app-bar actions;
  `CircleButton` only for genuinely filled circular actions such as send.
  Touch feedback is a short opacity change inside the control; there is no
  ripple. The visible shape is the hit target.
- **Typography comes from `EdgeTypography` and `EdgeText`.** Fira Code and the
  `calt`/`liga` feature string live only in `core/design/Fonts.kt`; feature code
  sets neither `fontFamily` nor `fontFeatureSettings`. Tracking stays `0`.
- **Colour comes from the palette.** The default Edge family is monochrome;
  themed families use their accent roles. No hex values or new accent tones in
  feature code. All families live in `core/design/Themes.kt`.
- **Every theme stays legible.** `ContrastTest` checks every family and variant,
  not only Edge. Run it after any palette or token change.
- **Components come from `core/design/components`.** Extend them rather than
  styling a Material component inline. Replace stock dialogs, switches and
  chips when their default surface conflicts with the palette.
- **Overflow actions are sheets.** A three-dot control opens `ActionSheet` with
  the session, server or connection in its header. Do not use `DropdownMenu`.
- **New and saved connections share the same fields and Test/Connect
  behavior.** Test checks health without connecting or saving; Connect/Save
  persists only after the check succeeds. Passwords stay memory-only.
- **Every inset has one owner per screen.** They are additive; two owners means
  content pushed off the window with the keyboard open.
- **At most one `weight`ed child per `Row`.** Unweighted siblings measure first
  and the last one absorbs any overflow.
- **Text clears 4.5:1** against the surface it sits on, canvas *and* tonal fill.
  `ContrastTest` enforces this; run it after any palette change.
- **No source-project references, assets or branding.** Design notes describe
  the product's own decisions.

## Conventions

- Compose-first, no XML layouts beyond `themes.xml` and `strings.xml`.
- State flows down, events flow up. Screens take a `viewModel` plus plain
  parameters; they do not construct repositories.
- `remember` keys matter: streaming parts re-emit the same id with new content.
- Durations, easings and durations come from `Motion`.

## Gotchas

- **AGP 9 compiles Kotlin itself.** Never apply `org.jetbrains.kotlin.android`;
  only the Compose compiler plugin is declared.
- **`compileSdk` uses the minor-API DSL** (`release(37) { minorApiLevel = 2 }`),
  and AndroidX requires compiling against 37+.
- The SDK's bundled `adb` and `aapt2` are x86-64 and cannot run here. TAD
  handles both; do not put SDK directories on `PATH`.
- `material-icons-core` is not guaranteed to come with Material 3 — assume
  `Icons.Default.*` may not resolve and use `EdgeIcons`.
- **A session payload carries no status.** `session.status` events and
  `GET /session/status` are the live and REST sources, and the REST map omits
  idle sessions. `time.compacting` is the only in-payload working hint. Mapping
  neither leaves every row looking idle.
- **A new connection is saved only after health succeeds.** A failed attempt
  must not leave a dead row in the saved list; an existing row may record its
  last failure. `Test connection` uses the same health check without touching
  the active connection.
- **A question is a blocking request, not a permission.** `question.asked`
  carries an array of `QuestionInfo`; answers are arrays of selected labels in
  the same order, and `custom` contributes a free-text label. Reply to
  `/question/{id}/reply`, reject to `/question/{id}/reject`.
- **The question tool part is the durable record.** Its
  `state.input.questions` and `state.metadata.answers` survive a restart, so
  render the exchange from the part rather than only from the event.
- **Notifications cover a backgrounded process, not a killed one.** The SSE
  stream lives as long as the ViewModel; a killed process receives nothing, so
  the notification path is scoped to the backgrounded window.
- **`GET /provider` is the whole registry; `GET /config/providers` is the
  configured subset.** The former is megabytes and mostly providers the user
  cannot pick; the latter is the handful the picker offers and what connect
  reads. Its payload contains provider API keys, so never log it or capture it
  as a fixture.

## Commits

Short, meaningful subject lines. Commit at each coherent point rather than
batching unrelated work.

## Versioning and releases

`version.properties` holds `versionCode` and `versionName`. `app/build.gradle.kts`
reads it; nothing else may hard-code a version.

**Every change gets a version bump. Do it in the same commit as the change —
not at push time, not in a follow-up commit, and never "later".**

1. Run `scripts/bump-version.sh [major|minor|patch|X.Y.Z]` — default `patch`. It
   increments `versionCode` by one and the name in step. Never edit the file by
   hand: `versionCode` must strictly increase or Android will refuse to install
   the new APK over the old one.
2. Move the entries under `## [Unreleased]` in `CHANGELOG.md` into a new
   `## [<new version>] - <YYYY-MM-DD>` section, leaving `Unreleased` empty. Those
   entries are published verbatim as the release notes, so write them for
   someone reading a release page, not for someone reading a diff.
3. Commit the bump and the changelog alongside the work, so a commit that
   changes behaviour and the version that carries it are the same commit. A
   commit that changes anything without a bump is incomplete; amend it before
   moving on.

`.github/workflows/release.yml` tests every push to `master`, then publishes
only when no `vX.Y.Z` tag exists for the current `versionName`: it builds a
minified `assembleRelease` APK, checks that it is signed, tags the commit and
creates the release with the APK attached. Because every change bumps the
version, every push publishes.

The workflow signs with `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD` repository secrets. When they are absent it falls back to the
debug key so the APK still installs — acceptable for personal builds, not for
something you hand to strangers, and the workflow logs a warning when it does.

