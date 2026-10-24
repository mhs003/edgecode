# Changelog

All notable changes to EdgeCode are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

`version.properties` is the source of truth for the current version. Every
released version has a matching `vX.Y.Z` git tag and a published APK; the
release workflow uses the section below as the release notes.

## [Unreleased]

## [1.2.2] - 2026-10-24

### Added

- Nine theme families: Edge, Catppuccin, Tokyo Night, Gruvbox, Nord, Solarized,
  Rosé Pine, Everforest and One. Each has a light and dark variant, and the
  existing System/light/dark mode selects between them. The choice is persisted.
- A compact monochrome default palette. The previous teal accent is gone unless
  a themed family supplies one.

### Changed

- The interface is smaller and calmer: bare app-bar icons, 42dp buttons, 36dp
  icon controls, a 34dp send control, a tighter type scale, compact cards and
  picker rows.
- Chat keeps its floating header and composer, now over functional scrims with
  bare controls. The composer remains one rounded card with the model, agent
  and send controls inside it.
- Assistant replies show Copy only; model and response statistics stay in the
  session overflow. Session rows show the leaf directory, model and relative
  time without truncating the time.
- Settings gains a theme-family picker with a four-dot preview. Connections,
  sessions, reasonings, tools, code blocks, sheets, dialogs and switches use
  the product's own surfaces and geometry.

### Fixed

- Touch feedback is now a short in-place fade with no ripple, so a circular
  control no longer reveals a larger square press surface.
- Every theme variant is contrast-normalised and checked by `ContrastTest`.

## [1.2.1] - 2026-10-23

### Changed

- The whole interface now uses Fira Code, bundled as one variable font with
  programming ligatures enabled. Tracking is zero so the ligatures and the
  monospace grid hold.
- Inline markdown spans are cached per block, so streaming a reply no longer
  rebuilds every settled block on each frame.

## [1.2.0] - 2026-10-22

### Added

- Every three-dot control now opens a bottom sheet, with the context it applies
  to above the actions: the session menu, a session row's actions, the chat's
  overflow, and a saved connection's actions.
- Saved connections can be edited: name, server address and username. Passwords
  remain memory-only and are never stored.
- The new-connection form has an optional display name, used for the saved row
  and the connection banner.
- The connection form has a **Test connection** action beside Connect (and
  beside Save when editing) that checks the server without connecting or
  saving. Connect still checks health itself when Test is skipped.
- A separator now sets the demo entry apart from the saved servers and the
  connection form.

### Fixed

- A new connection is saved only after its health check succeeds, so a mistyped
  address no longer leaves a dead row behind.
- The session screen appears as soon as the health check succeeds, instead of
  waiting for the catalogue, session list and pending questions. Those load in
  the background, and the list shows its loading state rather than "No
  sessions yet" while the fetch is running.

## [1.1.1] - 2026-10-22

### Fixed

- Connecting no longer waits on the server's full multi-megabyte provider
  registry. Connect reads `/config/providers` — the configured subset the model
  picker can actually use — once, off the main thread, so the session list
  appears as soon as the health check and session fetch finish.
- Tapping a saved connection while another attempt is still connecting is
  ignored instead of cancelling it and reporting a failure on the tapped row.
- Release builds get a larger Gradle heap, so R8 no longer dies with JVM
  garbage-collector thrashing on the CI runner.

## [1.1.0] - 2026-10-22

### Added

- Questions. The agent can ask one or more single-select or multi-select
  questions, with an optional free-text answer. A pinned card shows the pending
  question and a sheet collects every answer in the order the server expects.
- The answered exchange stays in the transcript as the `question` tool's
  output: each prompt and the labels chosen, or `Skipped`.
- Background notifications for questions and permission requests while the app
  is not visible. Tapping one opens the session that is blocked.
- A wrap toggle beside the copy action on a tool call's input, so long commands
  and JSON can be read without horizontal scrolling.

### Fixed

- A cancelled connection attempt is no longer recorded as a "Canceled"
  saved-server failure.

## [1.0.2] - 2026-10-22

### Added

- Subagents. A `task` call now links to the session it spawned, a header chip
  appears while any subagent is running and lists them, and each one opens
  read-only.
- The session list shows which sessions are still working.

### Fixed

- Sessions spawned by a subagent no longer appear as chats of their own in the
  session list.

## [1.0.1] - 2026-10-19

### Changed

- Documented building and debugging on-device with TAD, with the Gradle
  commands kept as the desktop alternative.

## [1.0.0] - 2026-10-19

First release: an Android client for an OpenCode v1 server — sessions, live
transcripts, tool calls, permissions, and model and agent selection, driven
from a phone.

### Added

- Connect to a server over HTTP, with saved connections, optional basic auth,
  and a spinner on the saved row being dialled.
- Session list with live status, and a chat transcript that renders assistant
  text as it is written — parts, reasoning, tool calls, diffs and notes.
- A working indicator from the moment a turn is sent until the server reports
  the session idle, naming the model being used.
- A footer under each settled reply with the model that served it, its output
  tokens, and the turn's average tokens per second.
- Model picker limited to connected providers, with fuzzy search across model
  and provider names, a recently-used section, and a per-chat model that is
  only committed when a turn is actually sent.
- Agent picker, slash commands, attachments, and permission prompts.
- Demo mode (`demo://`) that exercises the whole interface without a server.
