# Contextual offline scanning

Ticket: `.scratch/shop-implementation/issues/13-contextual-offline-scanning.md`.

Shared product labels use exactly
`bikepart:1:<lowercase-dataset-uuid>:product:<lowercase-product-uuid>`.
`ItemQr.product` validates its output with the same strict parser. The complete
payload must match: no trimming, URL decoding, short UUIDs, extra segments,
unknown kinds or versions, or case normalization. The dataset must match the
open shop and the target must be a known active product. Resolution never
looks up a guessed SKU, title or product in another dataset. Explicit external
aliases are described in [barcode aliases](barcode-aliases.md).

Explicit local template identities use the separate
`bikepart:1:<dataset-uuid>:template:<template-uuid>` kind. Only known nonretired
templates resolve. They offer catalog prefill only, never sale or receiving.
External barcode assignment is now implemented by ticket 14; unassigned codes
retain manual fallback. The `bikepart:` namespace remains reserved.

Staff enter scanning from Add to cart, Receive stock, or New product. The scan
screen labels its purpose and explains the shared-label quantity requirement.
Recognition starts explicitly, requests camera permission in context, and uses
CameraX 1.5.3 with bundled ML Kit barcode-scanning 17.3.0. There is no model
download dependency. Ticket 14 enables all bundled barcode formats. Multiple visible code values
produce an error instead of selecting an arbitrary product.

Every result or camera error pauses recognition. The first callback closes the
current attempt; repeated frames, late callbacks from old attempts, navigation
and explicit cancellation cannot act on it again. Resume clears the prior
result and quantity and creates a new attempt. Activity recreation preserves
the result but requires explicit resume. The transport releases its analyzer,
camera use cases, camera-state observer and ML Kit client when disposed.

Sale results start with a blank quantity, show the current stock and retained
quote when applicable, and use the existing transactional cart write. Invalid
quantity input keeps the result available. Receiving results only select a
product; staff must enter quantity and purchase-unit cost and save an arrival.
The missing receiving prerequisite was implemented with the additive version-12
schema; see [schema history](../app/schemas/README.md).

Catalog results offer Open existing or Copy catalog details. Templates require
Use template details. Prefill uses the existing independent-copy boundary and
leaves stock, price and locations for separate entry. Opening a scanner and
returning through manual search preserves an unfinished product form.
Manual search remains available after denial, failure, cancellation and invalid
results. Scanner and receiving views reuse the existing Material theme, grouped
surfaces, fields and primary buttons, without changing inherited Figma tokens.

## Validation

Tests use the public `Shop` boundary with file-backed Room and the full app UI.
`ShopApp` accepts one camera-content boundary so UI tests substitute only camera
transport; product, template, cart and stock operations remain real. Focused
checks cover strict encoding/parsing, contextual routing, quantity confirmation,
templates, repeat suppression, fallback search, independent copy drafts, saved
arrival history, operation retries, interruption rollback and historical upgrades.

`OfflineQrRecognitionTest` encodes the reviewed literal payload as a real QR
using ZXing, disables Wi-Fi/mobile data in airplane mode, and decodes it with
the bundled production recognizer on the API 37 emulator. It restores network
settings afterward. This verifies offline model recognition independently of
the substituted camera workflows.

The production permission-denial dialog and manual fallback passed on the
emulator. Live CameraX preview was visually checked after permission grant;
the preview is clipped to its bounds so camera texture transforms cannot
cover the purpose text or manual/cancel controls. Review also corrected manual
template pagination and added coverage for more than 30 matching templates.

The denial test requires camera permission to be denied before instrumentation
starts (`adb shell pm revoke com.nadim.bikepart android.permission.CAMERA`).
It skips an already-granted installation: revoking that permission inside the
instrumented process causes Android to kill the runner. Stop the Android CLI
inspector before instrumentation, since its automation connection persists
after layout/screenshot commands.

Rendered scanner and receiving screens were inspected in Light and Dark at
100% and 200% font scale, including long names/SKUs, zero stock, empty search,
validation errors and keyboard insets. Screenshots are under
`.scratch/scanning-visual/`; test-generated originals are in `/data/local/tmp/`.
All 94 generated theme contrast checks pass (minimum normal text 4.55:1).

Final Kotlin compilation, all three unit tests and Android lint pass. The full
90-check instrumentation run produced 75 passing cases and 15 failures, then
hit an Android `UiAutomation.disconnect()` shutdown error. A persistent CLI
inspector connection caused automation failures; keyboard animation also made
two test taps miss their targets. The tests now dismiss the search/quantity
keyboard before those taps. All 90 checks have passing results across the full
run and targeted reruns. The global empty-cart assertion and correction check
were verified against a fresh file-backed shop; the saved emulator database
was preserved and restored afterward.

Evidence: `.scratch/scanning-full.log`, `scanning-reruns-summary.txt`,
`scanning-isolated-summary.txt`, `scanning-final-focused.log` and
`scanning-final-verification.log`. Intermediate failure logs remain available
alongside the passing reruns; the initial full run is not claimed as green.

## Standards

No remaining documented-standard violations or material baseline smells.
Review found unreachable manual template results after the first page; the
pagination fix and real-persistence regression test address it. Follow-up review
found no further actionable issue in pagination or camera-preview clipping.

## Spec

No remaining implementation defects or scope creep identified. Receiving and
the version-12 schema are required prerequisite work for contextual receiving.
The ticket's requirement to verify offline scanning on the intended phone
remains an acceptance gap.

Only an emulator is connected. First-use optical scanning on the intended shop
phone, real printed-label readability and TalkBack traversal remain hardware
acceptance checks; emulator bitmap decoding does not establish those results.

Review totals: Standards has zero open findings; Spec has one unverified
acceptance check, scanning on the intended phone.
