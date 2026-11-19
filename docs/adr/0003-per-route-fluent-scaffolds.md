# Give each route its own Fluent scaffold

Each screen route owns and calls its own Fluent Compose Scaffold. The route owns its app bar, screen
actions, content window insets, snackbar and floating action button configuration. Shared route
helpers may provide consistent app-bar and content behavior, but must not wrap or own the route
Scaffold. This keeps routes independently composable while each screen sets the configuration it
needs.

The app shell owns only primary destination navigation: the bottom navigation or side rail for Home,
Stock, Carts and More. It does not own route app bars, screen actions or route content insets.
Preserve the existing destination visibility and shop navigation behavior.

The pinned published Fluent artifacts do not provide the Scaffold API shown in FluentUI.Demo's
V2ScaffoldActivity. Vendor the upstream Scaffold implementation from Fluent UI Android master at
commit 95205e33c8067d922bb45bd07490acc18c50755 under its source package, preserving the source
license and provenance. Keep the existing published Fluent dependency pins. Track the vendored file
and license locations in third_party/fluent-android-sources.md.

This decision adopts direct per-route Scaffold ownership, navigation-only shell responsibilities and
the vendoring boundary.