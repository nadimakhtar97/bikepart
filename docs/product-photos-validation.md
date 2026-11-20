# Product photos

Ticket 05 adds optional staff-selected photos to product creation and editing, with previews on
forms, details and compact stock rows. The photo field opens the Android image-only photo picker,
without broad media permissions. Tapping an added photo replaces it; its close control removes it.
Removal clears the product association on save, while cancelling a form leaves its saved photo
alone. The selected URI is a draft, while committed photos are private app-owned copies.

The [schema history](../app/schemas/README.md) records version 5, file publication,
hash/size identities and retention. Import finishes before the product transaction;
photo references, descriptive fields and the business revision commit together.
Stock, SKU, price and opening history remain unchanged by photo edits. Completed
unreferenced files remain available for future snapshot pins. Backup/restore and
dependency-aware blob collection are later slices.

The existing Now in Android theme and grouped form/button patterns are retained.
The deliberate shop adaptation uses the bundled stock vector on a neutral Material
surface for absent, corrupt and undecodable photos. Photo colors come from staff content. The form
preview shrinks its frame to the photo's aspect ratio, within the available width and 160dp height,
so no background bar frames the photo and the overlaid close control sits on its corner; detail and
row previews fit the whole photo. Decoding is sampled off the main thread and respects encoded
orientation. No decorative catalog photos or runtime asset URLs are shipped.

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
detail fallback, the tappable photo with its remove control and the empty photo field in light/dark
at normal and 200% font size. Long titles and the maximum whole-rupee price wrap; controls remain
labelled, scrollable and at least 48dp. Existing theme validation passes 94 contrast checks with
minimum normal-text contrast 4.55:1.

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

## Final verification

On 2026-11-11 the complete command passed on the API 37 emulator:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug
node tools/theme/validate.mjs
```

Results: 2 JVM tests, all 36 instrumentation tests, zero lint errors with the
12 pre-existing warnings, and all 94 theme contrast checks. No UI inspection
helper was active during instrumentation. Afterward, installed the final debug
APK and used the actual Choose photo control to open the native system picker;
cancelling returned to the unchanged optional-photo form.

The first full run passed 35 of 36 instrumentation tests. An existing typed-field
workflow timed out after its final Save tap with the keyboard active, then passed
unchanged in isolation. The test now closes the keyboard and checks Save is
enabled before the same touch click; its durable-result assertions are retained.
The following complete run passed. Both reviewers checked this follow-up and the
URI style cleanup and found no actionable Standards or Spec issues.
