# Product photos

Ticket 05 adds optional staff-selected photos to product creation and editing,
with previews on forms, details and compact stock rows. Choose/replace uses the
Android image-only photo picker, without broad media permissions. Remove clears
the product association on save; cancelling a form leaves its saved photo alone.
The selected URI is a draft, while committed photos are private app-owned copies.

The [schema history](../app/schemas/README.md) records version 5, file publication,
hash/size identities and retention. Import finishes before the product transaction;
photo references, descriptive fields and the business revision commit together.
Stock, SKU, price and opening history remain unchanged by photo edits. Completed
unreferenced files remain available for future snapshot pins. Backup/restore and
dependency-aware blob collection are later slices.

The existing Now in Android theme and grouped form/button patterns are retained.
The deliberate shop adaptation uses the bundled stock vector on a neutral Material
surface for absent, corrupt and undecodable photos. Photo colors come from staff
content. Decoding is sampled off the main thread and respects encoded orientation.
No decorative catalog photos or runtime asset URLs are shipped.

Focused real-persistence checks cover known SHA-256/size metadata, source deletion,
replacement/removal, retained old bytes, close/reopen, revision changes, interrupted
streams, empty/oversized input, cancellation, rejected revision commits, and
missing/corrupt files. Photo reads leave the business snapshot unchanged. App tests
exercise real Room storage, activity recreation with a selected draft, replacement,
source deletion and removal through the actual product screens. Only picker
transport is stubbed with synthetic image files; production storage is real.
Historical file-backed schemas 1–4 upgrade through explicit migrations and retain
catalog identity, values, balances, history, settings, dataset and revision, with
SQLite integrity and foreign-key checks.

Inspected all twelve API 37 emulator captures in `.scratch/product-photos-visual/`:
detail fallback, replace/remove controls and the empty photo field in light/dark
at normal and 200% font size. Long titles and the maximum whole-rupee price wrap;
controls remain labelled, scrollable and at least 48dp. Existing theme validation
passes 94 contrast checks with minimum normal-text contrast 4.55:1.

Manual TalkBack traversal and physical-device checks have not been performed.

## Standards

The independent code-review Standards review against starting commit `f559fcc`
found no documented-rule violations. Its one optional judgment was that the
selected URI and removal flag could become a sealed photo-draft type. Current
selection/removal callbacks keep the pair mutually consistent, and activity
recreation coverage verifies the saved draft. This is a future maintainability
option, with no demonstrated behavior defect.

## Spec

The independent Spec review found no missing requirements, scope creep or
incorrect implementation. Deterministic interruption coverage uses stream failure,
cancellation and rejected database commits; it does not kill the process during
filesystem publication. The reviewed write ordering publishes and syncs complete
bytes before a product can commit their reference.

Review totals: one optional Standards observation, no hard Standards violations,
and zero Spec findings.
