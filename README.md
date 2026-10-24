# EdgeCode

**A native Android client for [OpenCode](https://opencode.ai).**

EdgeCode brings your OpenCode coding sessions to your Android device. Connect to an OpenCode server, browse sessions, follow live responses, interact with agents, and manage tool permissions through a mobile-first interface built with Kotlin and Jetpack Compose.

## Features

- **Server connections** — Connect to an OpenCode server over HTTP, save, edit and test connections, and optionally use Basic Authentication. A connection is added only after it answers a health check.
- **Session management** — Browse existing sessions and see at a glance which ones are still working.
- **Live streaming** — Follow assistant responses as they arrive, including reasoning, tool calls, diffs, and notes.
- **Subagents** — Work an agent delegates runs in its own session, reachable from the call that started it and read-only, so it never appears as a chat of its own.
- **Model selection** — Choose models from connected providers, with search and recently used models.
- **Agent selection** — Switch agents and use slash commands.
- **Tool permissions** — Review and respond to permission requests from agents.
- **Questions** — Answer single- or multi-select prompts, or type a custom answer; the exchange stays in the transcript.
- **Background alerts** — Get a notification when the agent asks a question or needs permission while the app is not visible.
- **Attachments** — Add images and documents to a prompt from the composer.
- **Markdown rendering** — Read formatted responses and syntax-highlighted code.
- **Tool details** — Expand a call to inspect its input and output, and wrap long inputs in place instead of scrolling horizontally.
- **Demo mode** — Explore the interface using sample data without connecting to a server.
- **Themes** — A compact monochrome default plus Catppuccin, Tokyo Night, Gruvbox, Nord, Solarized, Rosé Pine, Everforest and One, each with light and dark variants.
- **Native Android experience** — A focused interface designed specifically for mobile devices.

## How It Works

EdgeCode acts as a client for an OpenCode server. The server handles the coding-agent workload, while EdgeCode provides the interface for interacting with sessions from your phone.

```text
┌─────────────────────┐
│   Android Device    │
│                     │
│      EdgeCode       │
│   Kotlin + Compose  │
└──────────┬──────────┘
           │ HTTP / SSE
           │
┌──────────▼──────────┐
│    OpenCode Server  │
│                     │
│ Sessions, agents,   │
│ models and tools    │
└─────────────────────┘
```

Your OpenCode server must be running and reachable from your Android device.

## Getting Started

### Requirements

- An Android device.
- A running OpenCode server compatible with the client.
- Network connectivity between your Android device and the server.
- An HTTP server address accessible from your device.

### 1. Start the OpenCode server

On the machine where OpenCode is installed, start the server:

```bash
opencode serve --hostname 0.0.0.0 --port 4096
```

Use the appropriate hostname and port for your environment. Exposing the server on all network interfaces may make it accessible to other devices on your network, so configure access accordingly.

### 2. Install EdgeCode

Download the latest APK from the repository's [Releases](https://github.com/mhs003/edgecode/releases) page.

Install the APK on your Android device.

### 3. Connect to your server

1. Open EdgeCode.
2. Enter your OpenCode server URL, for example `http://192.168.1.100:4096`.
3. Provide authentication details if your server requires them.
4. Connect and open a session.
5. Start interacting with your coding agent.

Replace the example IP address with the address of the machine running OpenCode.

## Building from Source

EdgeCode is an Android application built with Kotlin, Jetpack Compose, and Gradle.

### Clone the repository

```bash
git clone https://github.com/mhs003/edgecode.git
cd edgecode
```

### Building

Use [TAD](https://github.com/mhs003/TAD) for debugging and building. It builds the app inside Termux on the Android device itself, without a PC or Android Studio.

1. Install TAD once:

   ```bash
   pkg install -y git && git clone --depth 1 https://github.com/mhs003/TAD ~/.local/share/TAD && ~/.local/share/TAD/bin/tad-setup
   source ~/.bashrc
   tad doctor
   ```

2. Build, run, and test:

   ```bash
   tad compile        # fast typecheck, no APK
   tad build debug    # assemble and verify the APK
   tad run            # build, install and launch on this device
   tad test unit      # JVM unit tests
   ```

Optionally, build with Gradle on a desktop. Requires JDK 17 or newer and the Android SDK with platform 37.2:

```bash
./gradlew assembleDebug
./gradlew test
```

Either route writes the debug APK to `app/build/outputs/apk/debug/app-debug.apk`.

## Development

EdgeCode is structured to keep the UI independent of the OpenCode network protocol.

The main areas of the codebase include:

| Component        | Responsibility                                           |
| ---------------- | -------------------------------------------------------- |
| `ui/`            | Screens, navigation, chat interface, and view model      |
| `core/model/`    | Domain models and application state types                |
| `core/design/`   | Theme, design tokens, icons, and reusable components     |
| `core/markdown/` | Markdown parsing and syntax highlighting                 |
| `core/util/`     | Formatting, diffs, fuzzy search, clipboard, images, and JSON |
| `data/`          | Repository, connection storage, and API abstractions     |
| `data/net/`      | HTTP transport, OpenCode API integration, and SSE        |
| `data/demo/`     | Demo implementation and sample fixtures                  |

## Project Status

EdgeCode is a working client for OpenCode v1 servers. The current release connects to a live server and covers sessions, streaming transcripts, tool calls, permissions, model and agent selection, subagents, and attachments. Development continues on polish and reliability.

The target remains an OpenCode v1 server. A v2 adapter is not implemented: v2 answers v1 paths with an HTML document, so it needs a separate wire layer rather than a patch to the existing client.

Prebuilt APKs are published on the [releases page](https://github.com/mhs003/edgecode/releases). Follow the [changelog](CHANGELOG.md) for what changed in each version.

## Contributing

Contributions, bug reports, and suggestions are welcome.

- Browse the [existing issues](https://github.com/mhs003/edgecode/issues).
- Open an issue to discuss a bug or proposed feature.
- Submit a pull request with a clear description of the changes.

Please review the project's architecture and design documentation before making substantial changes.

## License

See the repository for license information.

## Links

- **Repository:** [mhs003/edgecode](https://github.com/mhs003/edgecode)
- **OpenCode:** [opencode.ai](https://opencode.ai)
- **Issues:** [Report a bug or request a feature](https://github.com/mhs003/edgecode/issues)
- **Releases:** [Download EdgeCode](https://github.com/mhs003/edgecode/releases)

