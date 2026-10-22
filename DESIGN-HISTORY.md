# Design History

A reference log of design decisions and tastes. `DESIGN.md` remains the binding
standard and `ARCHITECTURE.md` holds the current rationale; this file records
what was tried, what was rejected, and how the shipped UI reads — so a redesign
can build on it rather than relearn it.

---

## Current build (1.0.x)

The shipped v1 interface:

- **One canvas, one fill.** `background == surface`; `surfaceContainer` is the
  single fill for the composer, user turns and control circles;
  `surfaceContainerHigh` is the pressed/inset step.
- **Chat has no app bar.** A floating row of circular controls sits at the top,
  a floating composer pill at the bottom, and the transcript runs edge to edge
  beneath both.
- **Assistant turns are bare text.** User turns use a tonal bubble; tool calls
  stay one line until expanded; reasoning is dimmed and separate.
- **Dark by default**, one desaturated teal accent, semantic colours kept off
  the accent hue.
- **4dp spacing step**, radii `6 / 10 / 14 / 20 / 28`, 16sp body text, and
  medium/semibold weights carrying hierarchy.

**Known critique (2026-10):** the interface currently reads too bold and too
large at the component level. The next design pass should reduce weight and
scale across controls, labels, chips, headers and chat spacing. Treat this as a
direction to explore, not a defect in the implementation.

## Question and tool surfaces (1.1)

- A pending question is a compact pinned card, not a modal that opens on its
  own. The multi-question form lives in a sheet with a fixed footer and a
  scrollable body, so it does not push the transcript away.
- The answered exchange is rendered in the transcript as the `question` tool's
  output — prompts and chosen labels — not only in the transient card.
- A tool call's raw input can be wrapped on demand with an icon beside copy;
  fenced code keeps horizontal scroll. The toggle is an action, not a switch.
- A blocked agent while the app is backgrounded raises one alert per request,
  with the session as the destination. It is a notification, not a full-screen
  intent: the decision can wait until the user returns.

---

## Tastes that produced the current design

- Balance over extremes; clarity over decoration; consistency over isolated
  flourishes; function over convention.
- One canvas, not a ladder of surfaces. Nothing outlined, nothing divided;
  separate by space, not rules.
- The assistant's output is the document; the user's prompts are annotations.
- Tool calls are one line until asked to open — never a card per call.
- Circular controls, text-only composer chips, and no icon where a word is
  clearer.
- Floating chrome over an edge-to-edge transcript; the bars paint the canvas
  colour while the individual controls keep their own fill.
- At scroll extremes nothing is obscured: reserve the full chrome height,
  including the status-bar inset.
- Prefer platform conventions and the reference clients (ChatGPT, Claude, Grok)
  over invented patterns.
- Neutral base, one restrained accent; status never relies on colour alone.
- Metadata (timestamps, paths, tokens) lives in the faint tier and still clears
  4.5:1.
- Motion is short and purposeful, and respects reduced-motion.

---

## Decisions tried and rejected

| Decision | Why it was rejected |
|---|---|
| Five-surface ladder (`background` → `surfaceContainerHighest`) | Turned every region into a card and made the app feel boxed in. Collapsed to one canvas, one fill, one pressed step |
| App bar on chat | Cost ~56dp of transcript for a title and directory the header did not need to carry |
| Four-field per-turn footer | Repeated session facts under every reply and turned the conversation into a log. Replaced by one line: model, output tokens, rate |
| A card around every section | `DESIGN.md` §10: grouping comes from space and alignment, not containers |
| Pills for everything | `DESIGN.md` §10: radii are systematic; only genuine pills are pills |
| Gradients, glows, blur, shadows | `DESIGN.md` §10: reserved for hierarchy or interaction, never decoration |
| `material-icons-extended` | Thousands of generated classes to dex on a phone. Replaced by the hand-authored `EdgeIcons` |
| Global model selection | A model picked in one chat leaked into every other. Replaced by pending / committed / recent per chat |
| Status dot on every session row | A column of noise when almost every row is idle. Status moved into the title colour |
| Swipe-to-rename/delete | Not built; the row overflow menu is the single, discoverable path |
| Per-session `SessionStore` | Split into `AppRepository` (transport state) and pure `ChatReducer` (list state) |

---

## Superseded plan records

Kept verbatim from the pre-v1 plan. These describe intent at the time, not the
current build.

### Original design system

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

### Original screen sketches

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

### Original dependency choices

All were later rejected in favour of AndroidX + Compose only. The reasons were
on-device build cost and the requirement that wire mapping stay JVM-testable.

| Need | Original choice | Outcome |
|---|---|---|
| HTTP + SSE | OkHttp + `okhttp-sse` | Replaced by `HttpURLConnection` and the `Sse` parser |
| JSON | kotlinx.serialization | Replaced by the hand-written `Json` parser |
| DI | Hilt (or manual) | Manual construction in `AppViewModel` |
| Persistence | DataStore + `security-crypto` | `SharedPreferences`; passwords are memory-only, never at rest |
| Navigation | `navigation-compose` | Hand-rolled stack in `AppViewModel` |
| Markdown | Own Compose renderer | Kept — still hand-rolled |
| Highlighting | Lightweight Maven highlighter | Hand-rolled `SyntaxHighlighter` |

---

## Decisions revised during the 1.0.3 docs pass

These rows changed because the build moved past them. The old wording is kept
here for reference.

| # | Old wording | Current wording |
|---|---|---|
| 6 | "The object graph is a ViewModel, a repository and a settings store." | "…and two preference stores." |
| 7 | "Settings are six scalars read once at startup." | "Settings and saved servers are a handful of values read once at startup." |
| 9 | "The demo and the **future** v1 client implement the same `OpenCodeApi`." | "The demo and the **shipped** v1 client…" |
| 10 | "Optimistic writes go through the event stream." | "A sent turn is optimistic, then reconciled by its echo." |
| 15 | "Per-turn meta footer removed… exactly one action (copy)." | "One action and one meta line: copy, model, output tokens, rate." |

---

## Redesign checklist

1. Keep `DESIGN.md` as the standard; change it only when the direction itself
   changes.
2. Preserve the behavioural contracts in `ARCHITECTURE.md`: one inset owner per
   screen, one weighted child per `Row`, 4.5:1 contrast, explicit scroll follow.
3. Re-run `ContrastTest` after any palette change.
4. Verify on-device against a long conversation, in both themes, at the top,
   middle and bottom.
5. Record the new decision here, including what was rejected and why.
