# UI ownership extraction validation

Issue #24 under specification #22. Checked 2026-11-15 on branch
`feature/fluent-ownership`, based on `develop` / integration commit `e779ddd`.
See [ownership and contracts](ui-ownership.md) for the feature entry points.

## Performed checks

- `ANDROID_HOME=/home/nadimakhtar97/Android/Sdk ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`
  passed after the final Stock/product/archive/threshold split. The app and
  existing actual-app instrumentation suites compile against the extracted APIs.
- `git diff --check` passed.
- Compared the multiset of nonblank, non-import/package source lines against
  `e779ddd`: no added or removed implementation lines. All changes move existing
  declarations without editing bodies, signatures, callbacks or semantics.
- No dependency, resource, persistence, service or shared workflow-test changes.
  No Fluent adoption or new visual fallback is introduced by this extraction.

## Runtime evidence and open acceptance

The worktree contains an earlier API 33 emulator report dated
2026-11-15T12:57:51, at
`app/build/outputs/androidTest-results/connected/debug/TEST-bikepart-api33(AVD) - 13.xml`.
It records 10 `ShopWorkflowTest` cases with 7 passes and 3 failures:

- `fitmentAndLocationsSurviveDraftCancellationAndDurableReopen`: timeout waiting
  for the saved product's stock quantity, test line 204.
- `findingSelectorsValidateAndRemainReadableAcrossThemesAndFontScales`: retire
  action label absent, test line 161.
- `stockCorrectionsValidatePersistAndRemainUsableAcrossAppearances`: Save
  correction action not displayed, test line 68.

This historical report does not establish the final extraction's runtime state.
Keep every behavioral assertion and rerun failed cases in separate automation
sessions. Run the same cases on `e779ddd` before attributing a reproducible
failure to extraction. Device access is coordinated with the compatibility
owner; no device or instrumentation commands were run during this follow-up.

Runtime acceptance remains open until focused actual-app/Room tests run on the
final commit. Cover product creation/edit/reopen, cart/checkout/receipt/history,
manual picker/arrival and backup/restore/replacement entry points. Reuse the
existing feature suites; preserve `ShopWorkflowTest` scenarios. Save new
ownership evidence under a distinct ownership directory. Record light/dark at
100%/200%, focused-field/keyboard visibility, accessible actions and relevant
48dp targets. Compilation and source-movement checks do not replace these
rendering and behavior checks. TalkBack/assistive-input checks were not performed.
