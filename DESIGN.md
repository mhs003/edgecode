Product UI Design Standard

Version: 2.0

1. Design Identity

Direction: Compact Monochrome
Personality: Quiet, precise, engineered
Composition: One canvas, soft objects, floating chrome
Surface Treatment: Warm neutral fills and hairline edges

The default interface is deliberately monochrome: warm greys, near-black and
near-white text, and no decorative accent. Colour enters only when the user
chooses a theme family or when a state is genuinely semantic. The product should
still feel designed with every hue removed.

The interface is compact, but not cramped. Controls, cards and type are sized to
the task rather than to the platform default. A screen should read as one
composition, not a stack of Material components.

Design principles

1. Compactness is a design decision, not a missing margin.
2. Hierarchy comes from scale, weight and tone before it comes from colour.
3. One canvas; surfaces are used to group an object, not to frame every region.
4. Every control should look like what it does. A bare action is a bare icon;
   a primary action is a filled button; a send control is a circle.
5. Use space to separate. Use hairlines only where an edge carries meaning.
6. Motion explains a state change; it never decorates one.
7. A theme changes the palette, not the layout or the hierarchy.

2. Composition and Visual Hierarchy

Every screen must have one clear focus.

- The transcript is the document. The user's prompts are annotations.
- The composer is the primary control surface in a chat.
- A session or saved server is an object and may sit on its own rounded card.
- A settings section is a group of rows, not a card.
- Floating chrome is allowed only where it preserves the canvas: the chat
  header and composer.
- Do not add a header band when a title can float over content safely.
- Keep related controls close and unrelated groups separated by at least one
  spacing step.

3. Adaptive Density

Density follows the task.

- Chat body: 14sp/22sp, generous line height, one reading measure.
- Metadata, labels and paths: 10–12sp, still at AA contrast.
- Session and connection cards: 8–10dp internal padding, 36dp leading tile,
  16dp outer radius.
- Screen headers: 50dp. App-bar icons use a 40dp bare control.
- Buttons: 44dp high. Icon-only controls: 40dp in bars, 32dp inside rows. The
  send control: 38dp. Text fields: 44dp.
- Do not inflate every control to 48dp. The visible control is the hit target;
  keep it large enough for a confident thumb and leave space around it.
- Rows in lists are separated by space, not rules. A card may contain a
  divider only where the items inside are genuinely separate choices.

4. Surface System

There are three working tones, not an elevation ladder:

- **Canvas** — `background == surface`; the page.
- **Raised** — `surfaceContainerLow`; session cards, connection cards, the
  composer, inline code.
- **Selected / inset** — `surfaceContainerHigh` (and `surfaceContainer` for a
  middle step); user turns, selected picker rows, pressed states.
- Hairline borders use `outlineVariant`. Use them on inputs, the composer, user
  turns and selected rows. Do not outline every card.
- Shadows and elevation are not part of the visual language.
- The two chat scrims are the only gradients: they dissolve the transcript into
  the floating header and composer. They are functional, not decorative.

Component geometry:

- Cards: 16dp.
- Composer: 20dp.
- User turn: 16dp, with the bottom-end corner reduced to 6dp.
- Inputs and search: 12dp.
- Sheets: 24dp top profile.
- Circular controls: full radius.

5. Typography

Typography is the primary design tool.

The app ships one typeface: **Fira Code 6.2** (variable, SIL OFL), at
`res/font/fira_code_vf.ttf`. Programming ligatures are enabled through `calt`
and `liga`; tracking stays `0` so the ligature grid holds. Weights 300–700 come
from the single variable file. The interface is deliberately monospaced,
including prose, headings and labels.

Semantic scale:

- Chat body: 14sp / 22sp.
- Body: 14sp / 21sp.
- Secondary body: 13sp / 20sp.
- Small body: 12sp / 18sp.
- Card and row title: 13sp / 18sp, medium.
- Chat title: 15sp / 21sp.
- Screen title: 16–17sp / 23sp.
- Labels: 10–13sp.
- Code: 12–13sp / 18–20sp.

Rules:

- Do not use oversized headings or uniformly bold text.
- Metadata is small and quiet, but never below 4.5:1.
- Inline code inherits the surrounding family; it is not a second type system.
- No letter spacing in feature code.

6. Color and Themes

The default **Edge** family is monochrome:

| Role | Dark | Light |
|---|---|---|
| Canvas | `#0D0D0D` | `#F8F6F6` |
| Raised | `#141414` | `#F2EFEF` |
| Fill | `#181818` | `#ECE9E9` |
| High fill | `#1F1F1F` | `#E6E3E3` |
| Text | `#F1ECEC` | `#211E1E` |
| Secondary text | `#B7B1B1` | `#656363` |
| Faint text | `#8E8B8B` | `#6B6868` |
| Border | `#2A2727` | `#CFCECD` |

Optional families: Catppuccin, Tokyo Night, Gruvbox, Nord, Solarized, Rosé
Pine, Everforest and One. Each has a light and a dark variant, so System mode
switches between them without changing the family.

Rules:

- Every colour comes from the active `EdgePalette`; feature code never contains
  a hex value.
- The default family has no brand accent. Emphasis is tone and weight.
- A themed family may use its published accent for primary actions, selection
  and focus.
- Semantic error, warning, success and info colours are not the accent.
- Every information-bearing text tier must clear 4.5:1 against the canvas, the
  raised surface and the inset fill. The palette builder normalises them.
- Theme names and variants are shown in the picker; never rely on the swatch
  alone.
- Do not reintroduce teal as a default accent.

7. Components

- **Top bar** — flat, canvas-coloured, 50dp. Back, search and overflow are bare
  `BarIcon` controls with a 40dp target. A brand mark is optional; the session list uses the
  wordmark alone.
- **Cards** — session and saved-server cards use `surfaceContainerLow`, a 32dp
  circular leading tile and a bare overflow icon. No resting border.
- **Composer** — a rounded card on the canvas with one hairline, inset 14dp from
  the sides and 12dp from the bottom. Text sits on 16dp of padding; the toolbar
  below it has 8dp of breathing room around attach, model, agent and send. Model
  and agent are text controls with small icons, not filled pills. The send
  control is the only filled circle.
- **Chat chrome** — a floating row of bare controls over a gradient scrim at the
  top, and the floating composer over a mirrored scrim at the bottom. Both
  reserve their height in the transcript.
- **User turns** — tonal, hairline, 16/6 radius, compact 14sp body, right
  aligned, never wider than 86%.
- **Assistant turns** — bare text. A settled turn shows only Copy. Session
  statistics live in the overflow menu, not under every reply.
- **Reasoning and tools** — one-line rows. Expanded bodies use a left rule and
  plain text, not another card.
- **Code blocks** — rounded tonal block, quiet language/copy chrome, horizontal
  scroll, and a small floating chevron to reveal a long block.
- **Sheets** — canvas-coloured, 28dp top profile, compact rows, no divider per
  row. Selected rows use the high fill.
- **Dialogs** — custom `ConfirmDialog` in the product geometry. Do not ship a
  stock Material alert.
- **Switches** — `EdgeSwitch`; the track is monochrome and the state is also
  communicated by position.
- **Theme picker** — family rows with a four-dot preview and a mode selector.
  The layout never changes between families.

8. Interaction and Motion

- Use short fades and small position changes. `Motion` is the source.
- Tapping a control paints a short tonal overlay inside the control's own shape.
  There is no ripple; a circular control must never reveal a square press
  surface.
- Keep the composer anchored above the keyboard; never let the window pan.
- Preserve scroll position and follow streaming text only while the reader is at
  the end.
- Respect reduced motion and platform back behaviour.

9. Platform-Aware Implementation

- Jetpack Compose, Material 3 only as infrastructure. The product owns surfaces,
  controls, type, shape and colour.
- Do not use default `AlertDialog`, `Switch`, `FilterChip`, `TopAppBar` or
  filled control treatments when they conflict with this standard.
- Respect insets, keyboard behaviour, touch spacing and accessibility. A
  compact visual control still needs a clear label and a predictable target.
- Use `EdgeIcons`; do not add `material-icons-extended`.
- No XML layouts beyond themes and strings.

10. Explicit Anti-Patterns

Avoid these unless a specific requirement justifies them:

- Filled circles around every app-bar action.
- Oversized buttons, chips or headings that dominate the content.
- Square ripples on circular controls.
- A card around every section or every transcript part.
- A persistent app bar on chat when floating chrome is clearer.
- Default Material surfaces, dialogs, switches or accent colours.
- Gradients, glows, blur or shadows used as decoration.
- Teal as a default accent.
- Repeating session statistics under every assistant reply.
- A status chip that is larger than the action it describes.
- Placeholder screens presented as finished work.

11. Required Implementation Workflow

Before coding:

1. Inspect the existing codebase and conventions.
2. Identify the task, hierarchy and required states.
3. Review the current screens and the target visual direction.
4. Confirm tokens, theme roles and component geometry.
5. Build one representative screen before spreading a new pattern.

During implementation:

1. Use tokens and shared components.
2. Implement real content, loading, empty and error states.
3. Check light, dark, System and at least one themed family.
4. Verify touch spacing, contrast, truncation and keyboard behaviour.

Visual validation:

1. Run the app on the device.
2. Capture the actual screens and states.
3. Inspect alignment, density, type rhythm and control size.
4. Fix the most consequential defects and inspect again.
5. Never claim a visual check without rendering the interface.

12. Design Decision Protocol

Prefer the solution that:

1. Makes the next action or piece of information clearer.
2. Keeps the composition calm and the control footprint small.
3. Uses fewer surfaces and fewer decorative elements.
4. Preserves readable density and AA contrast.
5. Fits the platform's touch and back behaviour.
6. Reinforces the monochrome identity first, the chosen theme second.

Do not add a new component when an existing one can be adjusted. Do not change
the established geometry for a single screen.

13. Definition of Done

- One coherent visual system across Connect, Sessions, Chat, Settings, sheets and
  pickers.
- Compact, consistent controls with shape-correct feedback.
- All text legible in every theme variant.
- No stock Material surface left uncustomised.
- No source-project references, copied assets or borrowed branding.
- Verified on-device in light, dark and at least one accent family.
