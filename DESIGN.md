Product UI Design Standard

Version: 1.0

1. Design Identity

Direction: Balanced Minimalism
Personality: Quiet Sophistication
Composition: Adaptive Density
Surface Treatment: Subtle Layering

Create interfaces that feel polished, calm, coherent, and deliberately composed.

Use ChatGPT, Grok, and Claude's native applications as references for product quality, interaction design, and visual restraint. Do not copy their branding or assume that their individual design decisions suit every application.

The goal is to create a distinct, consistent product identity—not a generic template.

Design principles

1. Balance over extremes.
2. Clarity over decoration.
3. Consistency over isolated visual flourishes.
4. Function over convention.
5. Refinement over complexity.
6. Appropriate density over universal spaciousness.
7. Intentional decisions over default component styling.

2. Composition and Visual Hierarchy

Every screen must have a clear visual hierarchy.

- Establish one obvious primary focus for each screen or major region.
- Keep secondary actions and supporting information visually subordinate.
- Align related content consistently.
- Group elements through spacing, alignment, and subtle surface differences.
- Avoid unnecessary containers around content that can stand on its own.
- Maintain balanced proportions between navigation, content, and controls.
- Use whitespace to communicate relationships, not as decoration.

Do not interpret minimalism as removing useful functionality or making everything small.

3. Adaptive Density

Density must follow the task and content.

- Reading and conversational interfaces should prioritize comfortable reading.
- Data-heavy interfaces should make efficient use of available space.
- Forms should group related fields and minimize unnecessary vertical travel.
- Navigation should remain compact without sacrificing discoverability.
- Mobile interfaces should prioritize the primary workflow and use contextual disclosure for secondary actions.
- Desktop interfaces may use additional space for simultaneous information and workflows.

Avoid both excessive whitespace and unnecessary information density.

4. Surface System

Use subtle surfaces rather than a heavily layered card-based interface.

Define consistent tokens for:

- Base background
- Secondary background
- Elevated surface
- Primary and secondary text
- Borders and separators
- Accent color
- Success, warning, and error states
- Focus, hover, pressed, selected, and disabled states

Prefer tonal differences and delicate separators over strong outlines or shadows.

Use elevation only when it communicates hierarchy or interaction. Reserve cards for content that genuinely benefits from grouping.

Corner radii must be systematic and appropriate to the component. Do not round every element into a pill.

5. Typography

Typography is a primary design tool.

The app ships one typeface: **Fira Code 6.2** (variable, SIL OFL), bundled at
`res/font/fira_code_vf.ttf`. Programming ligatures are enabled through `calt`
and `liga`, and tracking is always `0` — letter spacing breaks the monospace
ligature grid. Weights 300–700 come from the single variable file. The
interface is deliberately monospaced, including prose, headings and labels.

- Choose a readable, platform-appropriate typeface.
- Establish semantic text styles for headings, body text, labels, metadata, and controls.
- Use regular and medium weights as the foundation.
- Reserve heavy weights for cases where they improve hierarchy.
- Control line height, line length, wrapping, and paragraph spacing.
- Avoid oversized headings and excessive boldness.
- Maintain readability across screen sizes, display densities, and accessibility settings.

Do not use typography as decoration at the expense of usability.

6. Color

Use a restrained, coherent palette.

- Start with neutral backgrounds and readable foreground colors.
- Choose one primary accent direction appropriate to the product.
- Use semantic colors consistently.
- Avoid gratuitous gradients, neon colors, and decorative multicolor accents.
- Ensure sufficient contrast in both light and dark themes when supported.
- Never rely on color alone to communicate status.

The product may have a distinctive accent, but the interface should not depend on accent color to feel designed.

7. Components and Design Tokens

Build a reusable design system before implementing numerous screens.

Define tokens for:

- Colors
- Typography
- Spacing
- Corner radii
- Borders
- Elevation
- Icon sizes
- Component dimensions
- Animation durations and easing

Create reusable components for buttons, text fields, menus, navigation, dialogs, lists, tabs, notifications, loading states, and empty states as required by the product.

Each component must have coherent default, interactive, disabled, and error states where applicable.

Keep business logic separate from visual styling. Reuse components when behavior and appearance are genuinely shared; do not force unrelated components into a universal abstraction.

Use component libraries as foundations, not as substitutes for product-specific design decisions.

8. Interaction and Motion

Interactions should feel predictable and precise.

- Provide immediate, appropriate feedback.
- Use motion to explain state changes, navigation, and relationships.
- Keep transitions restrained and purposeful.
- Avoid decorative animation that delays work or distracts from content.
- Support keyboard navigation on desktop and comfortable touch interaction on mobile.
- Respect platform accessibility and reduced-motion preferences.
- Prevent unexpected layout shifts and preserve user input when appropriate.

Polish must extend beyond screenshots to real application behavior.

9. Platform-Aware Implementation

First identify the platform, framework, existing architecture, and project constraints.

Native Android

- Use Jetpack Compose and appropriate Android interaction conventions.
- Respect system insets, navigation modes, keyboard behavior, touch targets, and accessibility settings.
- Use Material components where useful, customizing them to match the product's visual system.
- Support light and dark themes when required.
- Avoid reproducing web layouts literally in a native interface.

Web applications

- Use semantic HTML, responsive layouts, accessible controls, and predictable browser behavior.
- Define design tokens with CSS custom properties or the project's existing token system.
- Support mouse, keyboard, touch, and narrow viewports.
- Adapt navigation and content density to the available space.
- Use component libraries selectively and override their defaults intentionally.

Other platforms

Follow the platform's established interaction conventions while preserving the same design identity, semantic tokens, hierarchy, and principles of restraint.

Do not force identical layouts across platforms when their interaction models differ.

10. Explicit Anti-Patterns

Avoid these unless a specific requirement justifies them:

- Generic AI-generated dashboard aesthetics
- A card around every section
- Excessive pills, badges, and rounded containers
- Oversized headings and uniformly bold typography
- Gratuitous gradients, glows, blur, and shadows
- Excessive whitespace that reduces useful information density
- Dense layouts without adequate grouping
- Random icon styles and inconsistent spacing
- Decorative elements with no functional or brand purpose
- Default library components left uncustomized when they conflict with the design direction
- Placeholder screens presented as finished work

11. Required Implementation Workflow

Before coding

1. Inspect the existing codebase and project conventions.
2. Identify the users, primary tasks, and required screens.
3. Review available visual references and existing screenshots.
4. Establish design tokens and component conventions.
5. Define the information hierarchy and responsive behavior.

During implementation

1. Build a representative screen first.
2. Implement real content and interaction states.
3. Reuse the established tokens and components.
4. Verify responsive behavior, accessibility, and platform conventions.
5. Extend the validated design to the remaining screens.

Visual validation

1. Run the actual application.
2. Capture screenshots at relevant viewport sizes and states.
3. Inspect alignment, spacing, typography, contrast, density, and proportions.
4. Compare against the design brief and available reference images.
5. Identify the most consequential visual defects.
6. Fix them and inspect the updated result.
7. Repeat until the interface is consistent and polished.

Never claim to have visually tested an interface without actually rendering and inspecting it.

12. Design Decision Protocol

When multiple solutions are functionally acceptable, prefer the one that:

1. Communicates the intended action or information more clearly.
2. Maintains balanced visual hierarchy.
3. Uses the least unnecessary visual complexity.
4. Preserves appropriate information density.
5. Fits the platform's interaction model.
6. Reinforces the product's established identity.

When a design decision is uncertain, inspect the reference material or existing design system before inventing a new pattern.

Do not redesign established components without a reason.

13. Definition of Done

A finished interface must:

- Feel like one coherent product.
- Maintain consistent typography, spacing, color, and geometry.
- Balance visual clarity with practical information density.
- Have no obvious placeholder styling or arbitrary decoration.
- Work correctly in its actual target environment.
- Handle meaningful interaction and accessibility states.
- Have been visually inspected wherever screenshot or rendering tools are available.

Final principle: Make every design decision intentional. The interface should feel naturally polished, not visibly decorated.
