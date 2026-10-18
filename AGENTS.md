# AGENTS.md

Guidance for coding agents working on **EdgeCode** — an Android client for
OpenCode servers.

## What this is

A Kotlin + Jetpack Compose app that connects to an OpenCode server and drives it
from a phone: sessions, streaming chat, tool calls, permissions, model and agent
selection, attachments.

`DESIGN.md` is the binding visual standard. `ARCHITECTURE.md` explains why the
code is shaped the way it is. `PLAN.md` is the delivery plan and the API notes.
Read all three before non-trivial changes.

**Current state:** the UI is complete and runs against an in-process demo dataset
(`DemoOpenCodeApi`). The chat surface and the shared scaffolding have been
redesigned around a single-canvas design language (see *Design language* below).
The real network client is not written yet; it replaces that one class.

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
├── core/
│   ├── design/              tokens, theme, icons, and reusable components
│   ├── markdown/            CommonMark subset parser, inline spans, highlighter
│   ├── model/               domain types — the only types the UI knows
│   └── util/                formatting, diffs, clipboard, image decoding
├── data/
│   ├── OpenCodeApi.kt       the API seam + ChatReducer (pure)
│   ├── AppRepository.kt     state, event reduction, actions
│   ├── SettingsStore.kt     preferences
│   └── demo/                fixture content + demo API implementation
└── ui/
    ├── AppViewModel.kt      navigation stack, wiring
    ├── chat/                transcript parts, composer, pickers, permissions
    └── screens/             Connect, Sessions, Chat, Settings
```

## Rules

- **One Gradle module, on purpose.** Splitting into modules multiplies on-device
  build time for no benefit at this size. Enforce boundaries with packages.
- **The UI only sees `core/model`.** Nothing above `data/` may reference a wire
  format. All server interaction goes through `OpenCodeApi`.
- **Keep pure logic pure.** `ChatReducer`, `Markdown`, `SyntaxHighlighter`,
  `Diff` and `core/util/Format` must stay free of Android and coroutine types so
  they are unit-testable on the JVM. That is where the real complexity lives.
- **Streaming is a delta channel, never the truth.** The v1 server documents its
  event stream as lossy. Any new state that arrives by event must also be
  derivable from a REST fetch.
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
- **Components come from `core/design/components`.** Extend them rather than
  styling a Material component inline.
- **Respect `DESIGN.md` §10 anti-patterns:** no card around every section, no
  pills by default, no gradients or glows, no oversized headings, no placeholder
  screens presented as finished.

## Design language

Current chat clients render one canvas with one fill. Match that.

- **One fill, not a ladder.** `background == surface` is the canvas;
  `surfaceContainer` is *the* fill (composer, user turns, control circles);
  `surfaceContainerHigh` is the pressed/inset step. Do not introduce a fourth
  tone, and do not tune the existing ones to sit closer together — the flatness
  is the point.
- **Nothing is outlined and nothing is divided.** No resting borders on inputs,
  no separators in lists, no rules under headers. Separate with space.
- **Chat has no app bar.** A floating row of circular controls sits over the
  transcript and a floating composer pill sits below it; both paint the canvas
  colour so content disappears into them rather than sliding under a band.
- **Assistant turns are bare text.** No bubble, no container, no meta footer. A
  settled turn shows one action (copy). Anything that describes the session as a
  whole belongs in the overflow menu.
- **Circular controls come from `CircleButton`.** Do not hand-roll a
  `Box` + `clip` + `background` circle.
- **Every inset has one owner per screen.** They are additive; two owners means
  content pushed off the window with the keyboard open.
- **At most one `weight`ed child per `Row`.** Unweighted siblings measure first
  and the last one absorbs any overflow, which is how a status label once
  rendered one character per line down the screen edge.
- **Text clears 4.5:1** against the surface it sits on, canvas *and* tonal fill.
  `ContrastTest` enforces this; run it after any palette change.

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

## Commits

Short, meaningful subject lines. Commit at each coherent point rather than
batching unrelated work.
