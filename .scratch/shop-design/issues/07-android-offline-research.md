# Verify Android libraries and offline hardware constraints

Parent: ../map.md
Labels: wayfinder:research
Type: research
Status: resolved
Assignee: codex
Blocked by: none

## Question

Which primary-source-supported Android stack can implement the requested offline-first app with Jetpack Compose, Room/SQLite, persistent carts, WorkManager sync, offline barcode scanning and printable QR generation? Compare bundled ML Kit/CameraX with scanning options requiring downloaded components; identify first-use offline constraints. Verify transactions, Flow observation, WorkManager persistent retry/constraints and its scheduling limits, QR encoding options and Android printing versus printer-specific integrations. Recommend architecture boundaries and money/quantity representations without silently choosing user policies. Inspect the starter Gradle configuration and identify what remains unverified. Cite authoritative URLs for each factual finding. Record a context pointer and any inability to create/push a research branch truthfully; there is no configured remote in the initial inspection.

## Answer

Resolved by primary-source research on 2026-11-04; refreshed on 2026-11-11. See [Android offline stack research](../research/android-offline-stack.md) and [primary-source verification](../research/android-offline-source-verification.md) for verified capabilities, authoritative links, current Gradle evidence, recommended boundaries and unverified items.

The supported candidate is Compose with Room/SQLite as the local source of truth, transactional checkout and durable carts, WorkManager for constrained persistent backup retries, bundled ML Kit with CameraX for scanning without a first-use model download, and ZXing for local QR encoding. Android PDF printing is feasible; the selected TE244 USB/OTG adapter still needs actual phone/firmware validation. Follow the subsequently resolved policies: checked whole-rupee money, counted-piece quantities and Drive snapshot backup jobs with transactional revision tracking, rather than the original generic per-record sync outbox.

Publication/context pointer: findings are in this checkout at `C:/Users/somyp/AndroidStudioProjects/bikepart/.scratch/shop-design/research/android-offline-stack.md`. The initial investigation created no commit. The refresh is prepared for a local commit on the existing `master` branch; no separate research branch, push or PR is claimed, and no Git remote is configured. Current debug Kotlin compilation, JVM tests and lint pass offline. No app implementation, shop-phone scanning or physical printer validation was performed by this research task; see the report for remaining acceptance checks.
