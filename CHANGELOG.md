# Changelog

All notable changes to EdgeCode are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

`version.properties` is the source of truth for the current version. Every
released version has a matching `vX.Y.Z` git tag and a published APK; the
release workflow uses the section below as the release notes.

## [Unreleased]

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
