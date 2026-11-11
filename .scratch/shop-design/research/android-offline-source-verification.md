# Android offline stack: primary-source verification

Verified on 2026-11-11 for [research ticket 07](../issues/07-android-offline-research.md), against the capability claims in [the existing stack report](android-offline-stack.md). This supplement changes no implementation or business policy. URLs below were opened during this verification; library release versions, dependency compatibility and actual devices were not validated.

The generic outbox and scaled-number alternatives below describe capabilities, not the selected shop policy. The current handoff in the stack report governs: ticket 05 selects Drive snapshots and a durable backup-job journal, ticket 08 selects whole-rupee money and counted pieces, and ticket 10 selects TE244 USB/OTG. Checkout must atomically advance the shop revision/backup-dirty state rather than implement the generic server outbox described here.

## Room and local transactions

Room documents `suspend` one-shot operations and `Flow` observable reads. Observable queries can rerun following changes anywhere in an observed table; `distinctUntilChanged` suppresses equivalent results. Room ordinarily disallows main-thread access. The current guide includes Room 3 APIs and `androidx.room3` artifacts, so its dependency examples must not be copied blindly into a Room 2 setup. [Room asynchronous queries](https://developer.android.com/training/data-storage/room/async-queries).

The separate `androidx.room.Transaction` reference says a concrete DAO method can group its body into one transaction, marked successful unless an exception escapes. Generated insert/update/delete operations already have transactions, but separate operations do not thereby become a single checkout transaction. Multi-query relation reads can require a transaction for consistency. This reference identifies `androidx.room:room-common`; it does not establish Room 3 API compatibility. [Room Transaction reference](https://developer.android.com/reference/androidx/room/Transaction).

Design inference: checkout needs one local transaction spanning stock validation, persisted sale/line snapshots, stock movements, cart completion and the durable shop revision/backup-dirty marker. Validation failures must escape/roll back rather than be swallowed inside the transaction. Idempotency identifiers and local uniqueness constraints should protect repeated submission. Network requests and printing remain outside this transaction. These are proposed application contracts, not guarantees supplied by the annotation alone.

## WorkManager: persistence with scheduling limits

WorkManager stores scheduled requests in its own SQLite database and reschedules work across device reboot; scheduled work persists across app restarts. Named/tagged work supports grouping and uniqueness. Its scheduling persistence does not persist shop business operations automatically. [WorkManager overview](https://developer.android.com/develop/background-work/background-tasks/persistent).

All requested constraints must hold before execution. Losing a constraint stops a running worker, with retry when constraints are met again. `Result.retry()` uses linear or exponential backoff with a minimum initial delay of 10 seconds. Periodic work has a minimum repeat interval of 15 minutes; system optimization and constraints affect timing, and a periodic run can be skipped. Expedited work is quota limited. Therefore neither network reconnection nor a periodic interval implies an exact execution deadline. [Define work requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).

Generic alternative, superseded here by snapshot backup jobs: durable outbox rows would commit with the sale and use stable IDs that the server deduplicates. A startup reconciliation pass should recover the gap between a committed outbox row and enqueueing its worker. Acknowledgement after a lost response must remain safe to repeat. WorkManager does not establish exactly-once delivery or complete a backup/restore design.

## Barcode first-use offline behavior

| Option | Verified prerequisite | Implication |
| --- | --- | --- |
| Bundled ML Kit barcode model | Statically linked into the app; immediately available | Model download is absent from first-use scanning |
| Play-services ML Kit barcode model | Dynamically downloaded; requests before download finishes produce no results | An unprepared offline installation cannot assume scanning works |
| Google code scanner | Play-services scanner module must be present; otherwise install-time, explicit or first-use download is needed | Permission-free scanner UI does not remove first-use module dependency |

The first two options and the API 23 minimum are stated in the explicit setup section of [ML Kit Android barcode scanning](https://developers.google.com/ml-kit/vision/barcode-scanning/android). Google code scanner delegates camera use to Play services without the app requesting camera permission and processes images on device. Its downloaded-module lifecycle is documented in [Google code scanner](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner). No artifact version is recommended here.

Design inference: bundled ML Kit is the supported candidate for unconditional model availability at first launch. Camera permission, hardware behavior and damaged-label readability still need real-device validation. Test with networking disabled before installation/first launch. Decoding should resolve a local product identifier; it should not directly mutate stock or assume each repeated camera frame is a new physical item.

## CameraX frame ownership

`ImageAnalysis` supplies frames to an analyzer and can integrate with ML Kit. `STRATEGY_KEEP_ONLY_LATEST` permits dropping stale frames; blocking analysis can also block preview. Bind to a lifecycle and release frames using `ImageProxy.close()`, rather than closing the wrapped `Media.Image`. [CameraX image analysis](https://developer.android.com/media/camera/camerax/analyze).

Design inference: close the proxy after asynchronous decoding finishes, including failures. Suppress repeat detections at the application boundary. CameraX compatibility is not proof of autofocus, torch behavior or readable physical label output on the shop device.

## Local QR encoding and printer compatibility

ZXing's `QRCodeWriter` converts contents into a `BitMatrix`; its implementation accepts error-correction and margin hints and defaults to a four-module quiet zone. The source is Apache 2.0 licensed. This local encoder requires no hosted QR generation service. The inspected source is the mutable master branch, not a pinned release or dependency recommendation. [ZXing QRCodeWriter source](https://raw.githubusercontent.com/zxing/zxing/master/core/src/main/java/com/google/zxing/qrcode/QRCodeWriter.java).

Android custom printing uses `PrintManager` and `PrintDocumentAdapter` to lay out and write a PDF for the print framework. PDF canvas measurements use points (1/72 inch), and printers may have unprintable edges. [Android custom printing](https://developer.android.com/training/printing/custom-docs). A `PrintService` discovers printers and communicates using supported protocols. [PrintService reference](https://developer.android.com/reference/android/printservice/PrintService).

For direct output, the adapter must match the actual printer SDK/protocol and model. Epson's ESC/POS documentation enumerates supported models and warns that customized models can have different commands, ranges or defaults. [Epson ESC/POS reference](https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/index.html). Direct Bluetooth access for apps targeting Android 12+ distinguishes runtime `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` permissions. [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions).

Design inference: offline PDF generation is feasible, while physical offline printing remains conditional on the selected printer path. Confirm printer model, service/SDK, transport, paper, DPI, margins and status handling. Printing/reprinting should consume persisted sale/label data; a printer failure should not roll back the cash sale. Preserve QR quiet zones and validate real output with the scanner.

## Exact SQLite numbers

SQLite integer values in the inclusive range -9223372036854775808 through 9223372036854775807 are exact; `REAL` uses approximate binary64 floating point. [SQLite numeric accuracy](https://www.sqlite.org/floatingpoint.html). Ordinary SQLite tables use dynamic typing; numeric affinity can convert inputs, so declaring a decimal-looking type does not provide exact decimal arithmetic. STRICT tables exist, but their availability in this app's runtime has not been checked. [SQLite datatypes](https://www.sqlite.org/datatype3.html).

Additional clarification for the existing report: SQL arithmetic uses integer operations only when both operands are integers and overflow would not occur. Integer overflow can promote an expression to floating point; integer division truncates toward zero. Exact integer storage therefore does not guarantee exact unchecked SQL calculations. [SQLite expression operators](https://www.sqlite.org/lang_expr.html#operators).

Design inference: retain checked scaled integer money and quantity representations, with explicit currency/unit/scale. Validate intermediate multiplication and totals before storage; do not rely on automatic SQL overflow behavior or integer division as a rounding policy. Human decisions in tickets 01 and 08 select counted pieces and whole rupees; reject fractional stock and decimal monetary input without rounding. Descriptive measurements can still use exact decimals.

## Corrections and remaining verification

The existing report's core capability conclusions remain supported. Two wording improvements are warranted: make the Room 2/3 generation split explicit when implementation dependencies are chosen, and add SQLite's overflow-to-floating-point and truncating-division behavior to the exact-arithmetic warning. Its description of Android printing as producing content “commonly a PDF” can be tightened: custom document printing passes PDF content to the Android print framework.

This supplement does not refresh local Gradle observations or repository publication state. It does not claim current library versions, a successful build, hardware compatibility, reliable restore, a chosen server, or settled shop arithmetic/stock policies. Dependency resolution, transactional recovery tests, clean-install offline scans and real-printer trials remain outstanding.
