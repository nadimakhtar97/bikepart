# Adopt Fluent UI Android as the shop visual authority

Adopt Microsoft Fluent UI Android progressively as the shop’s shared design system and implement the new appearance using Fluent tokens and components. This supersedes the Now in Android visual reference, palette and component-styling decisions in `docs/ui-guidelines.md`; retaining that appearance through Fluent customization was considered, but the shop chose Fluent’s appearance instead. Preserve the existing shop workflows, Home / Stock / Carts / More destinations, accessibility requirements and persisted System / Light / Dark setting.

Use native Fluent Compose controls through shared shop components, with documented Fluent-styled Compose fallbacks where suitable Fluent controls are unavailable. Preserve app-owned layout and navigation. Migrate through small PRs to `develop`: compatibility check, appearance/buttons pilot, shell/navigation, Stock, Carts/checkout, Home and remaining screens. Hold the production release until the completed appearance is coherent and regression checks pass.

Use Fluent defaults for the first migrated version, while keeping theme, typography, shapes and icon choices centrally replaceable in later versions. Keep the existing System / Light / Dark choices. Try supported dependency alignment against the current app stack; if compatibility remains unresolved, stop and review options before downgrading the toolchain or maintaining a fork.

The adoption direction, visual baseline, integration boundary, migration sequence, release gate and compatibility failure policy are accepted; this decision does not claim that the app has already migrated. The implementation plan must update the UI guidelines and theme tooling as part of introducing the new appearance.
