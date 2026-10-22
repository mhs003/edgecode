# Changelog

All notable changes to EdgeCode are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

`version.properties` is the source of truth for the current version. Every
released version has a matching `vX.Y.Z` git tag and a published APK; the
release workflow uses the section below as the release notes.

## [Unreleased]

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
