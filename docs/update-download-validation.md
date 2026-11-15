# Update download validation

Validated 2026-11-15 for GitHub issue #4, the verified download and retention
slice of parent issue #2. Installation remains a later ticket.

## Implementation and contract

After discovery offers a signed stable release, Application updates shows the
download size and a Download update action. A tap starts one foreground-only
transfer over the available Wi-Fi or mobile connection. While transferring, the
screen shows determinate progress in bytes, percentage and megabytes, plus
Cancel. Leaving the app cancels the transfer; rotation and in-app navigation
retain it because the transfer lives in `UpdateViewModel`, and activity
`ON_STOP` cancels only when the activity is not changing configurations. A
second tap while downloading or verifying coalesces into the running transfer,
including a tap that arrives before the first progress update. Retry after
cancellation always restarts from byte zero. There is no background service,
scheduled retry, notification or automatic installation.

`UpdateHttp.download` streams the APK body into `filesDir/app-updates/update.partial`
with the same 10-second connect/read timeouts as discovery, a 256 MiB body cap
and a declared-length check. Cancellation disconnects the actual socket and the
partial file is deleted. The completed file is promoted to `update.apk` only
after every check passes:

- exact declared and received size;
- SHA-256 matching the published checksum;
- package ID equal to the installed app;
- minimum API and actual APK `versionCode`/`versionName` agreeing with metadata;
- strictly higher actual `versionCode` than the installed app;
- compatible minimum API;
- signer certificate digests equal to the installed app's signer set. A file
  signed by the installed key plus an unrelated extra certificate is rejected,
  matching Android's refusal to install it.

The APK identity and signer digests come from
`PackageManager.getPackageArchiveInfo(..., GET_SIGNING_CERTIFICATES)` through
`PackageManagerApkInspector`; the installed signers come from the same
`PackageManager` call for this package. Failures map to specific retryable
messages for connection, timeout, storage, integrity, identity, signing and
unknown errors. No partial or unverified file is ever exposed as ready.

A verified APK and its release identity are retained in the private
`app-updates` directory. On reopen the file is revalidated with the same checks
and against the installed version; damaged, superseded or orphaned files are
deleted. Discovery reconciles retention: a retained APK or stale failure is
cleared when the offered release no longer matches. `app-updates/` is excluded
from both cloud backup and device transfer in `backup_rules.xml` and
`data_extraction_rules.xml`; shop persistence, schemas and local/Drive shop
backups are unchanged.

## Checks

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:compileReleaseKotlin --console=plain
./gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.nadim.bikepart.UpdateApkInspectionTest" --console=plain
./gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.nadim.bikepart.UpdateDiscoveryTest" --console=plain
./gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.nadim.bikepart.UpdateWorkflowTest" --console=plain
./gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.nadim.bikepart.ShopWorkflowTest" --console=plain
```

All build, unit, lint, theme and update-focused instrumentation checks passed.
The full `ShopWorkflowTest` class retains one pre-existing fixture failure,
described under Limits.

- 20 JVM unit tests passed, including 11 `UpdateDownloadTest` cases. They cover
  successful transfer and retention across reopen, duplicate taps including a
  tap before the first progress update, cancellation and retry from byte zero,
  checksum/corruption/truncation/oversize rejection, identity, API, version and
  signing rejection including an extra certificate, network and storage recovery
  including a write that fails partway, cleanup of partial and damaged retained
  files, stale-failure cleanup and the backup exclusion contract.
- 3 `UpdateApkInspectionTest` cases passed on the device. They verify the real
  `PackageManager` inspector against the genuinely signed installed APK and the
  signed test APK, retain a downloaded real APK across a fresh
  `UpdateDownloads` instance, and reject real identity and signing mismatches.
- 7 existing `UpdateDiscoveryTest` cases and 3 `UpdateWorkflowTest` cases passed
  against the updated updater boundary and download action.
- Theme validation passed all 94 checks with minimum normal-text contrast 4.55:1.
- Android lint reports 0 errors and 32 warnings in both debug and release; the
  warnings are the existing starter/dependency set with no new update findings.
- Debug and release Kotlin compilation and both APK builds pass.

The device run used disposable API 37 AVD `Medium_Phone_API_37.0`
(`emulator-5554`) after a wipe. Downloads in tests use a loopback server
serving real signed APK bytes or controlled bodies; no real GitHub release or
signing secret was used. Only debug builds allow cleartext loopback.

## Limits and deliberate omissions

New Compose workflow and appearance tests for the download progress, cancel,
retry and ready surfaces were not added, by explicit direction for this slice.
For the same reason no download-state screenshots were captured in light/dark at
normal and 200% font scale, so those new surfaces were not visually inspected;
the existing update action and appearance coverage was kept passing. Existing
update and shop workflow tests were kept passing, including changing the
available-release action assertion from the discovery-only Check for updates
label to Download update.

`ShopWorkflowTest` passes 9 of 10 cases in its accumulated fixture state; the
pre-existing `stockCorrectionsValidatePersistAndRemainUsableAcrossAppearances`
case reported its Save correction control as not displayed. It passes when run
alone after reinstalling the disposable app. This matches the fixture-coupling
recorded for issue #3, and no shop code or shop test was changed here.

A completed APK is never installed in this slice. Manual physical-phone,
TalkBack, Accessibility Scanner and upgrade-over-install checks were not
performed; the signed two-APK install acceptance path belongs to the
installation ticket.
