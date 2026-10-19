# EdgeCode

**A native Android client for [OpenCode](https://opencode.ai).**

EdgeCode brings your OpenCode coding sessions to your Android device. Connect to an OpenCode server, browse sessions, follow live responses, interact with agents, and manage tool permissions through a mobile-first interface built with Kotlin and Jetpack Compose.

## Features

- **Server connections** — Connect to an OpenCode server over HTTP, save connections, and optionally use Basic Authentication.
- **Session management** — Browse existing sessions and monitor their activity.
- **Live streaming** — Follow assistant responses as they arrive, including reasoning, tool calls, diffs, and notes.
- **Model selection** — Choose models from connected providers, with search and recently used models.
- **Agent selection** — Switch agents and use slash commands.
- **Tool permissions** — Review and respond to permission requests from agents.
- **Attachments** — Include supported attachments in your prompts.
- **Markdown rendering** — Read formatted responses and syntax-highlighted code.
- **Demo mode** — Explore the interface using sample data without connecting to a server.
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

Download the latest APK from the repository's [Releases](https://github.com/mhs003/edgecode/releases) page, when available.

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
| `core/util/`     | Formatting, diffs, clipboard, images, and JSON utilities |
| `data/`          | Repository, connection storage, and API abstractions     |
| `data/net/`      | HTTP transport, OpenCode API integration, and SSE        |
| `data/demo/`     | Demo implementation and sample fixtures                  |

## Project Status

EdgeCode is currently under active development. The focus is on polishing the application and making the overall experience stable, reliable, and fully usable before the first public release.

The initial release will target an OpenCode v1 server. Compatibility with other server versions and additional improvements may be introduced in future updates.

Prebuilt APKs and official releases will be made available once the application is ready for public use.

Follow the [changelog](CHANGELOG.md) for development updates and check the [releases page](https://github.com/mhs003/edgecode/releases) for future releases.

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

