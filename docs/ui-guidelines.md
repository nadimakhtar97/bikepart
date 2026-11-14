# Android UI guidelines

Approved 2026-11-11: adapt Now in Android design-system patterns to the existing
shop screens and adopt the reference's colors and appearance behavior. This
supersedes the earlier fixed Shop teal visual decision. Shop workflows retain
their existing behavior.

## Scope of the Android guidance

Reviewed 2026-11-14 against the full [Mobile design guides](https://developer.android.com/design/ui/mobile):
Foundations, Styles, Layout and content, Components, Patterns, Home screen and
Widgets, including nested navigation entries and linked Mobile guide pages.
The sections below paraphrase actionable guidance; linked sources retain the
full examples, diagrams and implementation references. Follow links for the
feature being changed rather than treating example apps as shop requirements.

Android accessibility, safe areas and system behavior apply throughout. The
approved Figma reference supplies the shop's visual tokens and component
adaptations. Where a generic recommendation differs, the shop decisions here
are explicit: centered titles, the existing four destinations, reference button
colors and a static Default color scheme remain intentional adaptations.
Guidance for authentication, onboarding, notifications, live updates, widgets,
media and cross-platform migration applies when those features are designed;
this document does not introduce them into the product.

## Reference and precedence

Use the user's [Now in Android file](https://www.figma.com/design/MapX1cK2WjBKsPS0ZDLIbw/?node-id=544-19305).
The Community listing alone is not sufficient for inspecting components.

| Reference | Node | Project use |
| --- | --- | --- |
| Guide | `544:19305` | Design intent and companion-project links |
| Light / Dark palettes | `2:10800`, `2:10871` | Semantic Material color roles |
| Typography | `12:138` | Roboto role sizes, weights, line heights and spacing |
| Phone onboarding example | `654:31518` | Content hierarchy, rounded surfaces, spacing and current light variables |
| Light / Dark settings | `654:31767`, `218:318102` | Appearance choices, radio rows, tonal settings surface and dark variables |
| Bottom navigation | `49:12083` | Label hierarchy, selected indicator and compact navigation |

Use current bound variables first, then applied styles, then specimen captions.
The file contains older swatches/captions: its light primary-container swatch is
`#8C4190`, while the phone's bound variable is `#FFA9FE` with `#732A78` content.
The title-medium caption says 18sp but the applied style is 16sp; label-small's
caption says 10sp but its applied style is 11sp. Follow the applied values.

For Figma-driven changes, read the Figma design-to-code skill, create a file
session, and inspect the concrete nodes with design context and screenshots.
Use the existing native components where they express the reference accurately.
Record deliberate adaptations rather than claiming the shop is a pixel copy of
the news app. Recheck source nodes when changing an inherited design decision.

## Theme and tokens

The adopted variant is the reference's Default theme: purple primary, orange
secondary, blue tertiary, and neutral light/dark surfaces. System / Light / Dark
controls brightness and remains stored through `Shop.setAppearance`. System
follows device brightness. This adoption does not add the reference's illustrative
account controls or its separate Android brand variant to the shop.

`tools/theme/figma-tokens.json` records inspected roles and source nodes.
`tools/theme/generate.mjs` generates `ui/theme/Theme.kt`; edit the snapshot or
generator and regenerate rather than changing generated Kotlin alone. Newer
Material roles missing from the reference are derived from its neutral/accent
palettes. Wallpaper-derived colors are not enabled for the Default reference
theme. System-bar icon brightness must follow the effective app appearance.

`ui/theme/ShopTokens.kt` owns typography, spacing and shapes. Screens consume
Material roles rather than defining local colors or font sizes. Use system
sans-serif/Roboto without runtime font downloads. Titles and labels use the
reference's bold weights; body text remains regular. Amounts retain Indian
grouping and whole rupees.

## Platform foundations and visual styles

### Accessibility

Source: [Accessibility](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility).

- Use sp for text and support the user's font settings. Body text must be at
  least 12sp; keep the shop's larger body roles where already specified.
- Maintain at least 4.5:1 text contrast and 3:1 for meaningful non-text elements
  against their surfaces. Combine color with text, icons or another affordance
  for links, errors, selection and stock status.
- Provide meaningful Compose semantics for controls, informative images,
  headings and states. Give decorative images a null description. Group related
  content so assistive navigation can skip between useful units.
- Keep touch targets at least 48dp even when the visible icon is smaller. Supply
  accessible actions or visible controls for gesture actions, including deletion.
- Consider haptics as supplementary feedback. Check TalkBack, Accessibility
  Scanner, Voice Access and Switch Access; record which checks were performed.

### System bars

Source: [Android system bars](https://developer.android.com/design/ui/mobile/guides/foundations/system-bars).

- Include status, navigation and window caption bars in designs. Account for
  manufacturer differences, gesture and button navigation, cutouts, keyboard,
  foldables, desktop windows and multi-window mode.
- Draw behind transparent or translucent system bars and use `WindowInsets`
  to keep essential content and controls unobscured. Support edge-to-edge on
  older supported versions with the platform's compatibility approach.
- Match system icon brightness to the content behind it. Keep the gesture
  navigation area transparent and free of conflicting touch targets.
- For three-button navigation, use a transparent bar over non-scrolling or
  bottom-app-bar content; protect scrolling content with a translucent scrim.
  Change contrast enforcement only with a verified replacement protection.
- Coordinate keyboard and content movement through inset animations. Adapt
  navigation components to window size and posture.
- Reserve hidden system bars for justified immersive experiences and allow
  users to reveal system controls again.

### Android terminology and translating designs

Sources: [Android glossary](https://developer.android.com/design/ui/mobile/guides/foundations/glossary)
and [Translate iOS designs to Android](https://developer.android.com/design/ui/mobile/guides/foundations/translate-designs).

Use the Android glossary for platform terms and [the shop glossary](../GLOSSARY.md)
for domain vocabulary. Distinguish the system navigation bar from the app's
destination navigation. A task is a back stack of activities; an intent requests
an action, possibly from another app. Containment describes visual grouping;
canonical layouts are reusable adaptive compositions. Describe color in terms
of hue, chroma and tone, and dimensions in dp or sp.

When adapting an external platform design:

- Map user flows first, duplicate the source design, remove its system chrome,
  resize for Android windows and add Android system bars. Check smaller compact
  widths such as 360dp as well as the 412dp design example and larger classes.
- Use Android navigation, app bars, switches, radio buttons, checkboxes, fields,
  pickers, dialogs and sheets. Keep primary navigation on parent destinations;
  use tabs for sibling content, Up for the parent and close for a modal.
- Use sparse list dividers and avoid redundant disclosure indicators. Apply
  Material Symbols, Roboto, touch feedback and Android transition patterns;
  avoid lateral transitions between primary destinations.
- Review margins, resizing, modal behavior and the rewired prototype before
  handoff. Keep a shared style guide and document platform adaptations.

### Color

Source: [Android color for mobile design](https://developer.android.com/design/ui/mobile/guides/styles/color).

- Use semantic roles and their matching on-colors, not hue-based names or local
  hex values. Distinguish accent, semantic and surface roles; use tonal surfaces,
  container colors and outlines to establish hierarchy without oversaturation.
- Keep meanings consistent across screens. Never rely on red/green or color
  alone to communicate availability, errors or actions. Test foreground and
  background combinations, including opacity and disabled states.
- HCT separates hue, chroma and tone; accessible contrast depends on tone, not
  merely different hues. Generate harmonious palettes with Material tooling
  when deriving roles rather than assuming a source color is the primary role.
- Limit competing color sources and decorative or custom semantic colors.
  Content-derived color, if introduced, should use a clear dominant source.
- Maintain light and dark schemes and assess contrast preferences. Dynamic color
  normally needs a static fallback; this shop intentionally uses its approved
  static reference scheme. Check real displays because calibration and settings
  affect perceived colors.

### Themes

Source: [Themes](https://developer.android.com/design/ui/mobile/guides/styles/themes).

- Centralize color, type and shape in a coherent theme; add shared spacing or
  other theme attributes when needed. Reusable component styles consume it.
- Respect system brightness and scalable type, with the shop's persisted
  System / Light / Dark override. Avoid locking the experience to light mode.
  Verify text and surfaces across appearances and accessibility settings.
- Keep one dominant theme source. Limit content-specific or sub-brand themes
  so individual screens do not become unrelated palettes.
- Use consistent icon families and legible type roles. Customize component
  shapes through shared tokens rather than screen-specific overrides.
- If a WebView is justified, its light/dark behavior must agree with the app.
  Manufacturer themes can affect system UI and should not break legibility.
- Material Theme Builder can produce paired light/dark schemes, but any change
  to the adopted reference must go through the existing token-generation path.

## Layout and content

### Layout basics

Apply [Android Developers: Layout basics](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-basics)
(reviewed 2026-11-14) alongside the shop reference and tokens above.

- Start with [app anatomy](https://developer.android.com/design/ui/mobile/guides/layout-and-content/app-anatomy)
  and [content structure](https://developer.android.com/design/ui/mobile/guides/layout-and-content/content-structure).
  Consult [adaptive guidance](https://developer.android.com/design/ui/mobile/guides/layout-and-content/adapt-layout)
  and [canonical layouts](https://developer.android.com/design/ui/mobile/guides/layout-and-content/common-layouts)
  when choosing a screen structure.
- Adapt to portrait, landscape, window size classes, aspect ratios, resolutions
  and form factors; avoid assuming a fixed phone canvas.
- Account for cutouts, edge displays, edge-to-edge insets, system bars and the
  software keyboard. Keep focused fields visible automatically, moving them
  above or attaching them to the keyboard; do not rely on users discovering
  scrolling to uncover an input.
- Place primary navigation and essential actions within comfortable reach.
  Consider a FAB when a prominent, reachable action suits the workflow.
- Group related content and its actions through containment, such as cards.
  Align comparable elements and apply consistent spacing for readability.
- Prioritize essential actions; avoid crowding a view with competing controls.
- Document custom layouts' alignment, constraints or gravity, plus image sizing,
  scaling, cropping and positioning within containers.

### Parts of an app

Source: [App anatomy](https://developer.android.com/design/ui/mobile/guides/layout-and-content/app-anatomy).

Design the body, app navigation and system bars as related regions. The body
contains the content and its groupings and continues behind navigation and
system-bar regions, with safe insets for important information and interaction.
Choose composition and navigation from how staff use the shop's content and
move through its information hierarchy.

### Grids and units

Source: [Grids and units](https://developer.android.com/design/ui/mobile/guides/layout-and-content/grids-and-units).

- Specify layout dimensions in dp and text in sp, never physical pixels. The
  160dpi baseline maps 1dp to approximately 1px; density and user font settings
  determine rendered dimensions.
- Use an 8dp baseline for major layout spacing and 4dp alignment for smaller
  elements, icons and type, while preserving inspected reference tokens.
- Choose hierarchical grids for unequal emphasis, modular grids for equivalent
  items, and flexible columns for responsive alignment. Reflow, resize or reveal
  cells as space changes rather than imposing a fixed column count.
- Use consistent image/container aspect ratios when appropriate: 16:9, 3:2,
  4:3, 1:1, 3:4 or 2:3. Specify exceptions for content that needs another ratio.
- Supply raster density variants at mdpi 1x, hdpi 1.5x, xhdpi 2x, xxhdpi 3x and
  xxxhdpi 4x. Scalable vectors do not need one copy per density.
- Use current window size classes as design and test breakpoints; do not equate
  a device model or physical screen size with the app's available window.

### Content structure

Source: [Content composition and structure](https://developer.android.com/design/ui/mobile/guides/layout-and-content/content-structure).

- Establish margins and an adaptive grid before filling containers. Compact
  margins begin at 16dp; align content and actions within them and adapt larger
  windows. Incorporate safe insets at this stage.
- Group related information with whitespace, typography and dividers or explicit
  cards. Choose a hierarchy, modular arrangement or columns based on content.
- Use rows, columns, boxes, flow layouts, lazy lists or grids for the content's
  one- or two-dimensional structure. Combine them deliberately and keep repeated
  spacing and alignment consistent; use alignment lines where needed.
- Specify image scale, focal point and cropping at each layout size. Preserve
  necessary source aspect ratios without distorting content; balance remaining
  space. Reconsider image scale and viewing distance on larger screens.
- Use Material component slots and `Scaffold` for shared screen structure.
  Pin critical controls when appropriate and keep focused input visible with
  the keyboard. Respect component states, scrolling and placement behavior.

### Layout and navigation patterns

Source: [Layouts and navigation patterns](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns).

- Pair familiar navigation with the content hierarchy. A navigation bar supports
  three to five peer primary destinations; retain the shop's Home, Stock, Carts
  and More labels. Use tabs for a secondary layer of sibling content.
- Adapt primary navigation to the available window: a rail on larger windows,
  and a rail or horizontal navigation for medium windows. Consider cover-screen
  reachability and cutouts rather than blindly copying phone navigation.
- A drawer can hold more destinations but is less reachable on compact screens;
  do not add one when the existing navigation meets the workflow.
- If using a FAB, give it one highest-priority action, an appropriate size and
  optional label. Place it through `Scaffold` so scrolling does not hide it.
- Put secondary actions in the app bar or near their content; move infrequent
  actions to an overflow menu. Do not give every action equal prominence.

### Common and custom layouts

Sources: [Common layouts](https://developer.android.com/design/ui/mobile/guides/layout-and-content/common-layouts)
and [Relative and custom layouts](https://developer.android.com/design/ui/mobile/guides/layout-and-content/custom-layouts).

- Use list-detail for browsable records with supplementary details, such as
  stocked products. Compact windows show one pane at a time; larger windows can
  show the list and selected detail together.
- Use a feed/list/grid for equivalent items with consistent spacing and clear
  ordering. Lazy lists and grids suit changing or extensive collections.
- Keep supporting content in sheets or dialogs when this focuses the primary
  task; on larger windows, supporting content can become an adjacent pane.
- Custom layouts must define relationships to siblings and parent constraints
  while preserving grouping, columns and spacing. Mix horizontal and vertical
  collections only where the relationship remains understandable.
- Prefer an external browser for ordinary web content. Use a WebView only when
  an in-app web experience is warranted; use full-screen layouts only for an
  appropriate immersive task.

### Adapt your layout

Source: [Adapt layouts](https://developer.android.com/design/ui/mobile/guides/layout-and-content/adapt-layout).

- Start layout decisions with window width class, then account for height.
  Support phones, tablets, foldables, resizing, multitasking and desktop windows.
  Do not lock the app to portrait or rely on letterboxing.
- Design key screens at representative breakpoints and specify how their
  containers constrain, expand, reflow, reveal content or change presentation.
- Bound content, inputs and buttons with useful maximum widths; do not stretch
  a compact layout across the entire larger window.
- Think in panes: compact usually uses one; medium can use one or two; larger
  windows can use multiple panes. Reveal, hide, relocate or constrain panes as
  space permits, while preserving the task.
- Change component presentation when appropriate, such as a bottom sheet
  becoming a side sheet. Adjust content amount, grid density and element scale
  for available space, input methods and viewing distance.

### Postures and orientation

Source: [Foldable postures and orientation](https://developer.android.com/design/ui/mobile/guides/layout-and-content/postures-and-orientation).

- Support rotation and resizable windows. In landscape, account for compact
  height, reflow content and choose navigation that leaves useful working space.
- Maintain understandable scrolling behavior across rotation; redesign content
  placement instead of introducing unnecessary scrolling. Preserve intrinsic
  media ratios in a container when needed.
- Consider flat, folded and tabletop use, without assuming all devices support
  every posture. Maintain continuity when moving between displays or postures.
- Keep meaningful text and controls out of hinges; allow wider pane gutters and
  multiple hinges where needed. A single-pane screen may need no special split.
- Cover screens should be focused, edge-to-edge and safe around camera cutouts.
  Prioritize the active input when the keyboard appears; adapt filter sheets,
  control sizes and navigation orientation to the actual space and reach.

### Immersive content

Source: [Immersive content](https://developer.android.com/design/ui/mobile/guides/layout-and-content/immersive-content).

Hide system bars only for a task that benefits from uninterrupted content or
avoiding accidental exits, such as a dedicated image viewer, reading, video or
game. Extra screen space alone is insufficient justification for shop forms.
Provide an obvious way to reveal controls and system navigation, such as tapping
the content, and never design for permanently hidden bars on personal devices.
Protect overlaid controls and text with a scrim. If media is introduced, consider
PiP or casting for continuity rather than keeping users trapped in full screen.

### Edge-to-edge design

Source: [Edge-to-edge design](https://developer.android.com/design/ui/mobile/guides/layout-and-content/edge-to-edge).

- Draw backgrounds and scrolling content behind system bars. Distinguish system
  bar, system gesture and cutout insets; inset important content and avoid tap or
  drag targets in areas reserved for system gestures.
- Use the Material app bar's supported scroll behavior. A small sticky bar can
  collapse to status-bar height; medium/large bars can collapse to small bars.
  Protect content scrolling under the status bar with Material's built-in
  treatment or a matching gradient, without stacking both protections.
- Match protection to each pane or drawer background. Keep gesture navigation
  transparent without an extra scrim. Provide three-button scrim protection
  when a bottom app bar scrolls away.
- Distinguish `BottomAppBar`, which can collapse, from destination
  `NavigationBar`, which remains visible while scrolling.
- Extend solid app-bar backgrounds and horizontal scrolling imagery into cutout
  areas while keeping critical information and controls safely inset.

### Images and graphics

Source: [Images and graphics](https://developer.android.com/design/ui/mobile/guides/layout-and-content/images-graphics).

- Prefer vectors for simple icons and illustrations; convert SVG designs to
  supported Android Vector Drawables. Use WebP, PNG or JPG for detailed photos
  and complex images, supplying appropriate density variants.
- Keep immutable text out of graphics. Name resources consistently in lowercase,
  without density suffixes; organize raster variants in density directories.
- Preserve consistent optical size and icon padding, with sufficient interactive
  target space. Use adaptive vector launcher artwork and legacy raster assets
  where required; provide monochrome system artwork and the platform splash.
- Specify scale, position, clipping and focal point. Responsive images can keep
  a ratio, switch ratios at breakpoints or use fixed height with flexible width.
  Ensure overlaid text has sufficient contrast through a scrim.
- Prefer tinting a shared asset over duplicated color variants. Use brushes for
  gradients and supported blend modes; check blur support and rendering cost.
  Animated vectors or Compose animation suit small scalable UI motion.

## Shop component and screen patterns

- Use centered 22/28 bold app titles. Preserve labelled Home, Stock, Carts and
  More navigation with 24dp icons, filled/outlined selection variants, a tonal
  selected indicator and 12/16 bold labels. Navigation uses the surface role.
- Phone content uses 16dp margins, 8–16dp internal spacing and 16dp between
  grouped sections. Forms constrain width on larger windows. Content scrolls
  safely above the keyboard and system bars.
- Use 16dp rounded grouped surfaces and 8dp compact stock rows. Settings/form
  groups use the reference's primary-at-8%-over-surface treatment. Group titles
  use title-medium, supporting text uses body-medium, and important stock/price
  values use title or headline roles.
- Use the reference's neutral inverse-surface filled button for the main action;
  outlined buttons are secondary. Buttons grow for wrapped text and have at
  least 48dp targets. Reuse `ShopPrimaryButton` in `ui/ShopScreens.kt` and the
  shared page, group, row and field primitives in `ui/ShopLayout.kt`.
- Sub-screens show their contextual title in the centred app bar with a 48dp
  arrow back button. Generic page headers do not repeat the app bar label; a
  detail screen may repeat the item name it was opened for, because the app bar
  truncates long names. Stat and action tiles reserve two label lines so
  neighbours keep equal heights.
- Category chips wrap and retain the required single-category selection. Use
  labelled fields, useful keyboards, typed errors, announced feedback and
  automatic scrolling to validation errors. Disable edits during a pending save.
  Autosaved cart text uses the [buffered draft pattern](quoted-carts.md#persistence-and-save-behavior)
  to preserve typing and the keyboard session; structural actions and leaving the
  cart wait for all pending drafts to commit.
- Product rows show title, category, combined quantity/price, stock status and
  permanent SKU. Detail screens distinguish current stock/price from descriptive
  information. Empty states use truthful shop copy and bundled shop icons.
  Trailing totals keep at most 45% of a row so long titles stay readable; a
  total that cannot fit that share, or any total at 150%+ system font scale,
  moves under the title instead, and detail headers stack the photo above the
  summary.

The shop keeps its domain-specific destinations, inventory controls and bundled
Material Symbols. Reference news illustrations, avatars, account data, content
photos and news actions are not shop content. There are no runtime Figma asset
URLs. For new applicable static assets, download the exact assets supplied by
Figma and verify their native size, placement and local file before shipping.

## Components

### Material Components

Source: [Material Components](https://developer.android.com/design/ui/mobile/guides/components/material-overview).

- Build the design system from shared color, typography and shape decisions,
  composing these into reusable controls and screen patterns. Prefer code-backed
  Material components and consistent Compose theming when they fit the shop.
- Choose buttons and FABs by action priority; reserve icon buttons for supporting
  actions. Use badges for alerts, progress indicators for ongoing work, and
  snackbars for brief process feedback.
- Group related information and actions with cards, sheets, dialogs, lists and
  dividers as appropriate to the interaction. Navigation bars/drawers express
  primary destinations; tabs and app bars support local navigation and actions.
- Match choice semantics: radios for mutually exclusive options, checkboxes for
  independent choices, chips for filtering, switches for toggles and sliders for
  ranges. Use text fields for text entry.

## Patterns

### Predictive back

Source: [Predictive back design](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back).

- Support preview, commit and cancellation using platform/navigation support;
  preview the actual destination and restore the original state on cancellation.
  Avoid drag targets that compete with system gesture insets.
- Prefer built-in transitions. For genuinely custom full-screen transitions, the
  source specifies exit scale 100% to 90%, enter scale 110% to 100%, fade-through
  at 35% progress, and interpolation `(0.1, 0.1, 0, 1)`.
- Custom shared-element preview should communicate undoing navigation, not
  deleting or dismissing the underlying item. Its illustrated 90% scale preview
  does not apply to Navigation Compose, Navigation Component or FragmentManager;
  follow the supported shared-element return transition for those stacks.
- A custom gesture preview uses decelerated progress and preserves an 8dp screen
  margin; verify both slow cancellation and rapid committed flings. Only the
  system dismisses the app window.

### Settings

Source: [Settings](https://developer.android.com/design/ui/mobile/guides/patterns/settings).

- Store infrequently changed preferences, respect system accessibility choices,
  and choose neutral defaults with low interruption, battery and data costs.
  Frequent actions and contextual filters belong beside the affected feature.
- Keep Settings in secondary navigation unless central to the main journey.
  Group related rows with headings; show current values, match row labels to
  subscreen titles, and constrain width on larger windows. For 15 or more
  settings, introduce grouped subscreens; complex hierarchies may need search.
- Use switches/checkboxes for booleans and radios for one choice among at least
  two alternatives. Explain disabled dependencies below their controlling option
  and link to relevant device settings when needed.
- Write brief, direct, neutral labels with important words first. Supporting
  text should clarify state or consequences without repeating the heading.
  Existing System/Light/Dark shop choices remain the approved appearance control.

### Help and feedback

Source: [Help and Feedback screens](https://developer.android.com/design/ui/mobile/guides/patterns/help-content).

- If help is introduced, place it in secondary navigation such as More,
  Settings, an overflow menu, or the bottom of a drawer. Use consistent familiar
  labels across entry points.
- Lead with frequent questions and link directly to the relevant feature or
  setting. Keep lower-demand information such as legal links less prominent.
- Provide an understandable route to report an issue, ask an unanswered
  question, or give feedback. Support task instructions with relevant visuals
  when these improve understanding.

### Passkeys (conditional)

Source: [User authentication with passkeys](https://developer.android.com/design/ui/mobile/guides/patterns/passkeys).

- If accounts become necessary, use Credential Manager to consolidate passkeys,
  passwords and federated sign-in, with passkeys preferred and alternative paths
  accessible. Do not present a competing button for every method by default.
- Offer creation at account setup, recovery, after password sign-in or account
  management. Explain speed, simplicity and familiar screen-lock authentication
  before launching creation; use the platform terms "Create a passkey" and
  "Sign in", the filled Google passkey icon, and a success confirmation.
- Avoid duplicate passkeys for the same account/password manager. Management
  should show the syncing password manager, creation/last-use times and deletion.
  Do not imply a synced credential lives only on the device that created it.
- Collect only necessary information, explain its purpose and allow optional
  details later.

### Onboarding (conditional)

Source: [Authentication & Onboarding](https://developer.android.com/design/ui/mobile/guides/patterns/onboarding).

- Decide which setup is essential before use and which learning can occur in
  context. Let users experience product value before asking for accounts or
  permissions; explain each permission at the point it becomes relevant.
- Prefer short contextual education over an automatic full walkthrough. If a
  walkthrough is needed, provide persistent skip/sign-in access, resume saved
  progress, and make the consequences of leaving clear.
- Show recognizable progress for multi-step flows. Collect only critical data,
  group related inputs, split genuinely long setup into logical steps and give
  forms a sensible maximum width in landscape/expanded windows.
- Provide visible recovery and clear credential requirements. Mask sensitive
  input and never prefill a password during recovery/reset.

## Home screen (conditional features)

### Notifications

Source: [Notifications](https://developer.android.com/design/ui/mobile/guides/home-screen/notifications).

- Notify only for timely, relevant value. Use concise headlines and supporting
  previews, appropriate system templates, categories and channels, and importance
  proportional to urgency. Group related notifications while keeping children
  understandable alone; remove stale information.
- Ask for non-exempt notification permission after dismissible contextual
  education explains its benefits. Respect dismissal and offer preference access.
- Tapping must open the corresponding actionable destination. Expanded actions
  should add value rather than duplicate the body tap; keep long-form editing
  inside the app. Choose lock-screen sensitivity deliberately.
- Avoid alerts for routine sync, self-recovering failures, rating requests,
  irrelevant greetings, or empty re-engagement. Foreground-service tasks need
  visible status and a stop action. Reserve strong interruption/colorization for
  suitable urgent cases.

### Live updates

Source: [Live update notifications](https://developer.android.com/design/ui/mobile/guides/home-screen/live-updates).

- Use only for a user-initiated, finite, trackable experience with a clear end.
  Keep status glanceable and follow normal notification relevance principles;
  users retain the ability to dismiss/demote the update.
- Alert for meaningful status transitions, with immediate visible evidence of
  the change. Quietly update minor adjustments rather than repeatedly alerting.
- Label discrete phases; for continuous progress make the fill correspond to
  the time/distance semantics. Keep timestamp or duration formats consistent
  between the status chip and expanded card.
- Prefer predictable template fields and the most important information in the
  title. Avoid live updates for recommendations, indefinite processes, merged
  information from unrelated apps, or cases requiring bespoke animation/data
  visualization. Routine stock changes are not a justified live-update use case.

### Widgets overview / Getting started

Sources: [Home-screen Widgets](https://developer.android.com/design/ui/mobile/guides/home-screen/widgets)
redirects to [Widgets](https://developer.android.com/design/ui/mobile/guides/widgets).

- Choose one valuable primary use case for each widget: glance at useful
  content, act directly, or open the relevant app experience. A widget is a
  focused extension of the app rather than a compressed whole screen.
- Start from canonical templates, apply sizing guidance and verify readable,
  usable results at several sizes and supported form factors. Use widget quality
  guidance when implementing.
- A future shop widget needs a stated user goal and proper empty/current-data
  states.

### Picture-in-picture

Source: [Picture-in-picture](https://developer.android.com/design/ui/mobile/guides/home-screen/picture-in-picture).

- Use PiP for a genuine continuing video/navigation experience. Keep video
  playback running, hide ordinary app UI, and use system controls or purposeful
  custom actions because normal screen controls are unusable in the small window.
- Choose an entry trigger that complements the task without surprise. If new
  content is selected while playback is in PiP, reuse the existing playback
  activity in full screen rather than creating another confusing player.
- Provide correct `sourceRectHint` bounds for smooth entry/exit. Supported
  auto-entry improves gesture transitions for appropriate video use cases;
  disable seamless resizing for non-video content.
- Verify return-to-fullscreen, close, resize and transitions. PiP is not a
  substitute for a floating shop cart or inventory window.

## Widgets (conditional implementation details)

### Canonical layouts

Source: [Canonical widget layouts](https://developer.android.com/design/ui/mobile/guides/widgets/layouts).

- Identify core content first. Use text layouts for concise status, text/image
  layouts when the image contributes meaning, toolbars for common tasks, lists
  for scannable multiple items and grids for image-rich collections.
- Keep a search toolbar focused on search. When resizing toolbars, prioritize
  frequent tasks and add a button only when a minimum 48dp target fits.
- Show immediate on/off feedback in action lists and clear completion targets
  in checklists. Use Glance action callbacks and state updates for interactions;
  distinguish no-data and empty-list content.
- Adapt rows and columns to available space. Cars do not support ordinary
  manual list scrolling; supply an appropriate glanceable alternative. The
  source's full-bleed snapping image layout is Android 17+; gate any such choice
  by platform support rather than assuming availability everywhere.

### Sizing

Source: [Sizing](https://developer.android.com/design/ui/mobile/guides/widgets/sizing).

- Design adaptive sizes and content-driven breakpoints instead of fixed squares:
  launcher cells vary by device, orientation and form factor. Test resizing to
  locate where layout changes or removal of supplementary content is necessary.
- Set `targetCellWidth` and `targetCellHeight` for the intended handheld/tablet
  default spans. Ensure essential information and controls remain legible and
  usable throughout the supported size range.
- Fill the allocated grid bounds with the widget container; avoid extra custom
  outer padding that misaligns it with neighboring home-screen content. If using
  a nonrectangular expressive shape, reach the grid boundary on at least one axis.
- Form factors share basic principles but have different input and glanceability
  expectations; verify the actual supported contexts.

### Style

Source: [Style](https://developer.android.com/design/ui/mobile/guides/widgets/style).

- Use semantic Material color roles for readable hierarchy and accessible
  foreground/background contrast. Support light and dark rendering. For a future
  widget, device dynamic colors can integrate it into the launcher; this does not
  change the shop app's approved fixed Default palette.
- Use the system corner-radius property for rectangular widgets to maintain
  consistency and prevent clipping. Expressive whole-widget shapes suit minimal
  content; complex content needs readable hierarchy before decorative shape.
- Establish text hierarchy with system-font sizes, weight, line height and
  letter spacing. Give restricted-space text adequate readability and avoid
  treating the source's illustrative role names as authority to replace the
  inspected shop typography tokens.

### Configuration

Source: [Widget configuration](https://developer.android.com/design/ui/mobile/guides/widgets/configuration).

- Open configuration during placement only when selection is necessary to show
  useful content or customization is central. Otherwise start with a useful
  default and allow later configuration through touch-and-hold.
- Keep configuration to a clear path, ideally one or two screens, ending in
  adding the widget. Do not turn this into a generic settings dead end. If setup
  is unfinished, provide a recoverable unconfigured state with a configuration
  route; show helpful empty/authentication states when relevant.
- Preview appearance changes and progressively disclose advanced controls.
  Showcase distinct valuable use cases instead of every color/shape permutation.
  The source recommends 6–8 picker previews at most, with further customization
  available through configuration.

### Discovery and promotion

Source: [Discovery and promotion](https://developer.android.com/design/ui/mobile/guides/widgets/discovery-promotion).

- Choose default dimensions that demonstrate utility without crowding the
  picker, and adapt spans/minimum sizes to different device grids. Do not publish
  same-purpose duplicates solely for color or shape; expose those in configuration.
- Provide accurate previews matching actual layout, size, shape and supported
  dynamic colors, plus a concise value-focused description. Avoid changing the
  initial shape or size when dropped onto the home screen.
- Offer a pin-widget suggestion at a relevant moment, such as after a related
  successful action or repeated feature use. Keep the suggestion subtle and
  dismissible in effect: it must never block the user's primary task.
- Test picker previews and initial placement on supported form factors as well
  as the running widget itself.

### Adapt across form factors

Source: [Adapt widgets across form factors](https://developer.android.com/design/ui/mobile/guides/widgets/adapt).

- Prefer Jetpack Glance and responsive layouts that fill grid bounds. Verify
  multiple layouts, light/dark rendering and host integration when supporting
  cars, desktop or other widget hosts.
- Make automotive content glanceable without manual scrolling. For a normal
  `LazyColumn`, offer a surface showing all essential information; otherwise
  drivers may see only the first few items.
- If a supported auto segmented collection is used, put the most valuable and
  personalized content on its first page. Do not make critical status depend on
  revealing later items.
- Dynamic theming is recommended for vehicle integration. This is conditional
  host-specific widget guidance; the shop application's adopted theme remains
  the source for its in-app colors.

## Screenshot-inspired refinement

Approved 2026-11-14 in the [UI refinement brief](ui-refinement-brief.md): retain
Now in Android Default tokens while adapting the user's shop-app screenshots
for quieter search surfaces, creation FABs, product overflow actions, filter
sheets and chronological sales logs. The screenshots supplement the existing
reference; their payment/order model does not replace the shop's cart/cash-sale
model.

- Stock uses one labelled Add product FAB; Carts uses one labelled New sale FAB.
  Place these through Scaffold, respect keyboard/safe insets and leave scroll
  clearance so the final row remains reachable above the FAB.
- Stock, cart selection and receiving/catalog search combine manual entry with
  a trailing contextual scan action. Keep manual fallback and scanner return
  state. Primary Save and Checkout actions remain visible.
- Stock rows open details on tap. Their labelled three-dot menu offers edit,
  receiving, copying and label shortcuts without removing full detail actions.
- Stock-health/category chips expose common selections; advanced filters use a
  scrollable sheet. Retain every existing lookup/decimal filter, active selection
  summaries, validation and explicit clearing.
- Sales history groups receipts newest-first by device-local calendar day, with
  Today / Yesterday / date headings, count and exact whole-rupee daily total.
  Totals must support the sum of multiple maximum-value receipts without overflow.
  Date ranges include both endpoints. Search matches receipt, cart or customer.
  Completed cash receipts remain immutable.
- Bundle official Material Symbols locally and record provenance under
  `third_party`; retain a consistent icon family and semantic theme colors.

## Verification

Run Kotlin compilation during changes and the focused `ShopWorkflowTest` through
the actual app with real Room storage. Keep create/edit/fresh-launch, validation,
identity and stock reconciliation behavior green. For visual changes inspect
actual app screenshots in both brightness modes, at normal and 200% system font
scale, including long names, large prices, empty states and keyboard insets.
Check labelled controls, selected semantics, 48dp targets and announced errors.

For layout changes, check portrait/landscape, representative window sizes,
aspect ratios and densities, safe areas, keyboard focus visibility, reachability,
grouping, alignment and action density.

Check gesture and three-button navigation, display cutouts, caption bars and
window resizing where supported. For adaptive changes, check pane transitions,
hinge avoidance and continuity across rotation or posture changes. Verify
predictive-back preview, cancellation and commitment. Include assistive input
checks from Accessibility when controls or navigation change. For optional
system surfaces, verify their actual host, permissions, entry/exit and empty
states; an in-app screenshot alone does not validate a notification or widget.

Run `node tools/theme/validate.mjs` for normal-text contrast of at least 4.5:1
and essential outlines of at least 3:1. Token checks complement rendered-screen
inspection. Keep UI inspection helpers idle while instrumentation tests run;
Android's UI automation connection is shared. Finish with unit/instrumentation
tests and Android lint, and report physical-device or screen-reader checks
separately when they were not performed.

## Source coverage

The recursive review covered every expanded Mobile Guides sidebar entry and
followed links to other Mobile guide pages, deduplicating redirects and cycles.
External Material, Figma, API/developer documentation and other Android
form-factor sites remain supporting references rather than an unbounded crawl.
All 34 distinct Mobile guides below were readable on 2026-11-14 and have linked
guidance in this document.

| Guide group | Sidebar entries | Coverage in this document |
| --- | ---: | --- |
| Foundations | 4 | Accessibility; System bars; Android terminology and translating designs |
| Styles | 2 | Color; Themes, alongside Theme and tokens |
| Layout and content | 11 | Layout basics through Images and graphics |
| Components | 1 | Material Components, alongside Shop component and screen patterns |
| Patterns | 5 | Predictive back; Settings; Help and feedback; Passkeys; Onboarding |
| Home screen | 4 | Notifications; Live updates; Widgets overview; Picture-in-picture |
| Widgets | 7 | Overview/Getting started; Canonical layouts; Sizing; Style; Configuration; Discovery and promotion; Adapt across form factors |
| Additional body-linked guide | 1 | Relative and custom layouts, under Common and custom layouts |

The 34 sidebar entries resolve to 33 distinct pages: Home screen / Widgets
redirects to Widgets / Getting started. The additional Custom layouts page
brings the reviewed canonical-page total to 34.
