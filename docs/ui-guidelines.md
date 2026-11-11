# Android UI guidelines

Approved 2026-11-11: adapt Now in Android design-system patterns to the existing
shop screens and adopt the reference's colors and appearance behavior. This
supersedes the earlier fixed Shop teal visual decision. Shop workflows retain
their existing behavior.

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

## Component and screen patterns

- Use centered 22/28 bold app titles. Preserve labelled Home, Stock, Carts and
  More navigation with 24dp icons, filled/outlined selection variants, a tonal
  selected indicator and 12/16 bold labels. Navigation uses the surface role.
- Phone content uses 16dp margins, 8–16dp internal spacing and 24dp between
  sections. Forms constrain width on larger windows. Content scrolls safely
  above the keyboard and system bars.
- Use 16dp rounded grouped surfaces and 8dp compact stock rows. Settings/form
  groups use the reference's primary-at-8%-over-surface treatment. Group titles
  use title-medium, supporting text uses body-medium, and important stock/price
  values use title or headline roles.
- Use the reference's neutral inverse-surface filled button for the main action;
  outlined buttons are secondary. Buttons grow for wrapped text and have at
  least 48dp targets. Reuse `ShopPrimaryButton`, `ShopSection` and shared field
  patterns in `ui/ShopScreens.kt`.
- Category chips wrap and retain the required single-category selection. Use
  labelled fields, useful keyboards, typed errors, announced feedback and
  automatic scrolling to validation errors. Disable edits during a pending save.
  Autosaved cart text uses the [buffered draft pattern](quoted-carts.md#persistence-and-save-behavior)
  to preserve typing and the keyboard session; structural actions and leaving the
  cart wait for all pending drafts to commit.
- Product rows show title, category, combined quantity/price, stock status and
  permanent SKU. Detail screens distinguish current stock/price from descriptive
  information. Empty states use truthful shop copy and bundled shop icons.

The shop keeps its domain-specific destinations, inventory controls and bundled
Material Symbols. Reference news illustrations, avatars, account data, content
photos and news actions are not shop content. There are no runtime Figma asset
URLs. For new applicable static assets, download the exact assets supplied by
Figma and verify their native size, placement and local file before shipping.

## Verification

Run Kotlin compilation during changes and the focused `ShopWorkflowTest` through
the actual app with real Room storage. Keep create/edit/fresh-launch, validation,
identity and stock reconciliation behavior green. For visual changes inspect
actual app screenshots in both brightness modes, at normal and 200% system font
scale, including long names, large prices, empty states and keyboard insets.
Check labelled controls, selected semantics, 48dp targets and announced errors.

Run `node tools/theme/validate.mjs` for normal-text contrast of at least 4.5:1
and essential outlines of at least 3:1. Token checks complement rendered-screen
inspection. Keep UI inspection helpers idle while instrumentation tests run;
Android's UI automation connection is shared. Finish with unit/instrumentation
tests and Android lint, and report physical-device or screen-reader checks
separately when they were not performed.
