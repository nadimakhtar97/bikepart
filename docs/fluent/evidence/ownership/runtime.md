# Ownership extraction runtime validation

Date: 2026-11-15. Current extraction: e4bdd54 (integration feature/fluent-spec-22). Prior baseline: e779ddd.

Validation uses disposable source copies `/tmp/bikepart-ownership-current` and `/tmp/bikepart-ownership-baseline`. Only applicationId is changed to `com.nadim.bikepart.ownershipverify`; namespace and original actual-app/Room instrumentation tests remain unchanged. Existing user app/package data is untouched.

Device allocation: compatibility explicitly released emulator-5556 before ownership tests; ownership later explicitly released it to theme_pilot. No overlapping automation sessions. Emulator-5554 reserved for demo screenshots.

Required differential loop: separate `am instrument -w -r -e class com.nadim.bikepart.TESTCLASS#METHOD com.nadim.bikepart.ownershipverify.test/androidx.test.runner.AndroidJUnitRunner` session for each historical failure, current then e779ddd baseline on same emulator. Capture raw instrumentation result logs and app-generated screenshots in separate ownership directories.

Historical failures to reproduce without changing assertions:
- stockCorrectionsValidatePersistAndRemainUsableAcrossAppearances: Save correction not displayed (line 68).
- findingSelectorsValidateAndRemainReadableAcrossThemesAndFontScales: Retire action absent (line 161).
- fitmentAndLocationsSurviveDraftCancellationAndDurableReopen: 12 pieces timeout (line 204).

Build logs: `/tmp/bikepart-ownership-current-build.log`, `/tmp/bikepart-ownership-baseline-build.log`.

## Results
Current debug + instrumentation APK compile: PASS (5m08s; 68 tasks). Baseline debug + instrumentation APK compile: PASS (3m03s; 68 tasks). Build warning is unchanged pre-existing DriveBackup data-class copy visibility.

Runtime initially awaited exclusive API37 device handoff; final outcomes and handoff appear below. Both emulators exited externally during other validation; compatibility restarted API37 preserving its data. Only ownership idle Gradle daemon79964 terminated after its successful build to relieve host memory; no global stop.

### API37 environment interruptions

- First unchanged current correction test reached line73 (after Save correction 48dp/display assertion and keyboard screenshot) and failed `RootViewWithoutFocusException`; post-run `dumpsys window` identified blocking `Application Not Responding: com.android.systemui`. Screen captured as `/tmp/bikepart-spec22/ownership-systemui-anr.png`. Raw first attempt retained in `ownership/current/logs/stockCorrection-environment-systemui-anr.txt`. This is not the historical line68 failure.
- Finding attempt aborted after confirming the same system dialog; no behavioral verdict.
- After dismissing System UI dialog, second correction attempt disconnected (instrument shell exit255; no result). API37 emulator process had disappeared; log `/tmp/fluent-api37-restarted.log` ended with ColorBuffer errors, no graceful-shutdown message.
- Restarted preserved API37 AVD in durable detached Python Popen process PID88276, `start_new_session=True`, stdin DEVNULL, output `/tmp/bikepart-spec22/ownership-api37-detached.log`. No wipe, package clear, user-app install, or data deletion performed.

### Durable API37 current results

- `createEditAndReopenProductThroughTheApp`: **PASS**,51.547s. The actual MainActivity and Room shop were exercised unchanged.
- `stockCorrectionsValidatePersistAndRemainUsableAcrossAppearances`: fails at line73 `Espresso.closeSoftKeyboard` with `RootViewWithoutFocusException` after the line68 Save correction display/48dp assertion succeeds. SystemUI ANR remains foreground; baseline reproduced the same line73 failure (43.153s).
- `findingSelectorsValidateAndRemainReadableAcrossThemesAndFontScales`: fails at line161 missing text `Retire: <make>` after all Light/Dark×1/2 selector iterations;76.191s. Baseline also fails; see final comparison below.
- `fitmentAndLocationsSurviveDraftCancellationAndDurableReopen`: passes historical line204 `12 pieces`, reaches durable reopen, fails line217 missing `Location: Shelf A <suffix>`;87.882s. Baseline also fails; see final comparison below.

Pulled14 current screenshot files to `/tmp/bikepart-spec22/ownership/current/screenshots/files`. Inspected correction-keyboard-light1, finding-light2, fitment-dark2: all obscured by the SystemUI ANR dialog. These screenshots **do not establish clean rendering, focused-keyboard visibility, or visual accessibility acceptance**. They are retained as environment evidence only.

Current APK SHA256 `7ac96d0d6f24bc275f33ae8524e2d73f9f0074202b23af089fcb67ba65d5db7c`; baseline APK `c57665876a5ce70354d299327db9aed7f3c895816586a9080a860fc3dd4c4991`.


## Final comparison and handoff

| Original unchanged ShopWorkflowTest method | Current extraction e4bdd54 | Baseline e779ddd |
| --- | --- | --- |
| createEditAndReopenProductThroughTheApp | PASS,51.547s | Not rerun; current focused Room gate passed |
| stockCorrectionsValidatePersistAndRemainUsableAcrossAppearances | FAIL48.191s, line73 Espresso.closeSoftKeyboard root focus | FAIL43.153s, same line73/root focus |
| findingSelectorsValidateAndRemainReadableAcrossThemesAndFontScales | FAIL76.191s, line161 missing Retire label | FAIL98.454s, same line161 missing Retire label |
| fitmentAndLocationsSurviveDraftCancellationAndDurableReopen | FAIL87.882s, line217 missing Location:Shelf A expectation after durable reopen | FAIL85.623s, line216 missing Fitment:Unknown expectation after durable reopen |

Historical stock-correction line68 and fitment line204 symptoms were not reproduced in the final current attempt: both methods progressed beyond those assertions. The finding line161 failure reproduces before and after extraction. No current-only behavioral regression was demonstrated. This is **bounded extraction evidence**, not an all-green ShopWorkflowTest result or complete visual/accessibility acceptance.

The baseline/current fitment failure differs by one assertion after durable reopen. Do not claim an exact matching failure or a proven persistence regression. Both runs passed creation, initial stock display, edit, initial Unknown fitment wait, and app relaunch. Tests retain existing immediate post-reopen selectors; asynchronous navigation/state or inherited label spacing need separate investigation.

Compiled resource evidence supports inherited prefix-spacing defects: in BOTH APKs `aapt2 dump resources` reports string/location as `"Location:"` and string/retire as `"Retire:"`; source FindingDetails.kt directly concatenates these prefixes with names. Original assertions expect a separating space. Prefix dump files are saved in each variant evidence directory. XML source trailing spaces do not survive resource compilation. Root will assign any repair to a fresh implementer; validator edited no repository code, resources or tests.

Environment: same API37 x86_64 AVD,1080×2400,420dpi; same isolated applicationId, namespace and signing setup. Baseline installed over current with `adb install -r`; synthetic test data was retained and names use nanoTime suffixes. Thus device/runtime settings and real Room boundary match, but the database is not a reset pristine fixture. Existing production/user package data was never opened, cleared or replaced. The physical Samsung was not used.

Raw results: `/tmp/bikepart-spec22/ownership/current/logs` and `/tmp/bikepart-spec22/ownership/baseline/logs`. Repeatable loop: `/tmp/bikepart-spec22/run-ownership-test.sh VARIANT 'ShopWorkflowTest#METHOD'`. Android instrumentation can return shell exit0 while reporting failures; verdicts above use JUnit `OK`/`FAILURES` output, not shell exit status.

Handoff: emulator5556 released exclusively to theme_pilot after all tests/pulls, isolated ownership app force-stopped, system font_scale restored1.0. Persistent SystemUI ANR remains and was reported to the next owner. No further ownership device commands after handoff. Durable emulator PID88276 remains running; no wipe/reset/uninstall performed.

Not performed: broad Cart/Checkout/Scan/Backup/Restore/PhoneReplacement suite reruns (root narrowed scope to historical differential and minimal product gate), clean light/dark1/2 rendering acceptance, TalkBack/assistive-input checks, physical-device checks. Those remain broader migration/release gates; obscured screenshots cannot substitute for them.
