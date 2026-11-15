# Finding label spacing validation

Ticket: [#48](https://github.com/nadimakhtar97/bikepart/issues/48), discovered during parent #22 ownership validation. Date: 2026-11-15. Integration baseline: a26d62e.

## Cause and bounded change

Android resource compilation removes unquoted XML trailing whitespace. The old `location` and `retire` resources compiled as `Location:` and `Retire:`. Their two consumers in FindingDetails.kt concatenated names directly, losing the intended space. Repository-wide resource-reference search found exactly those two consumers. Both now pass the name as a format argument to a complete label. The retired location uses a complete `Location: %1$s (retired)` resource so the suffix remains in the same visible/accessibility text node. No persistence, retirement callback, selection, or stock behavior changed.

## Real app/Room red and green

The focused FindingLabelWorkflowTest launches the actual MainActivity, seeds unique records through Shop backed by Room, and observes the Compose UI. It verifies active location spacing; retirement actions for make, model and location; retained retired names; and retired location/fitment labels with unchanged 12-piece stock after closing and relaunching the activity. Historical ShopWorkflowTest source and assertions remain unchanged.

Device: physical Samsung SM-S911B, Android API 37. Tests ran serially in an isolated disposable source copy at `/tmp/bikepart-finding-verify`, with only applicationId changed to `com.nadim.bikepart.findingverify`; namespace remained unchanged. The existing `com.nadim.bikepart` user app and its data were untouched. Synthetic Room records persist in the isolated package and use unique names. UI inspection helpers remained idle throughout instrumentation.

Repeatable command (after installing the isolated debug/test APKs):

```sh
adb.exe -s RZCX90RE7PP shell am instrument -w -r \
  -e class com.nadim.bikepart.FindingLabelWorkflowTest \
  com.nadim.bikepart.findingverify.test/androidx.test.runner.AndroidJUnitRunner
```

| Gate | Result |
| --- | --- |
| Focused scenario before fix | RED: missing `Location: Shelf A <suffix>` after product/stock loaded; [raw result](logs/red.txt) |
| Focused scenario after fix | GREEN: OK (1 test), 9.251s; [raw result](logs/green.txt) |
| Original findingSelectorsValidateAndRemainReadableAcrossThemesAndFontScales | OK (1 test), 27.406s; [raw result](logs/original-selectors.txt) |
| Original fitmentAndLocationsSurviveDraftCancellationAndDurableReopen | OK (1 test), 14.597s; [raw result](logs/original-reopen.txt) |
| Compiled resource table | [Before](logs/bikepart-finding-red-resources.txt): trimmed prefixes; [after](logs/bikepart-finding-green-resources.txt): complete formatted labels |

Both original workflows were rerun unmodified. The selector workflow exercises Light/Dark at normal/double font scale and the original spaced retirement assertions. The durable-reopen workflow exercises the original spaced location assertion. These Samsung runs encountered no SystemUI or keyboard interruption. Earlier emulator SystemUI ANR/root-focus failures remain independently documented in [ownership validation](../ownership/runtime.md); these results do not erase those environment findings.

## Build and visual checks

The isolated copy compiled debug and test APKs before the fix (BUILD SUCCESSFUL, 1m43s). After the fix, the following passed in 2m26s:

```sh
ANDROID_HOME=/home/nadimakhtar97/Android/Sdk ./gradlew \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:testDebugUnitTest :app:lintDebug --console=plain --max-workers=2
```

Unit tests: 30 passed, zero failures/errors/skips. Theme validator: all 94 generated contrast checks passed, minimum normal-text contrast 4.55:1. `git diff --check` passed. Existing DriveBackup data-class copy-visibility warning remains unrelated.

Inspected test-generated Samsung screenshots show clean, unobscured [active location](screenshots/active.png), [retired lookup names](screenshots/retirement.png), and [retired location after relaunch](screenshots/retired.png). Exact Retire action text is verified by the focused test and unchanged original selectors test. TalkBack was not run; the retired suffix is asserted in the same Compose text node as its location label.

After validation, font scale was restored to the original 1.0, night mode to the original NO, and the isolated package was force-stopped. Neither emulator was used. No production package installation, clear, uninstall, schema edit, or user-data operation occurred.
