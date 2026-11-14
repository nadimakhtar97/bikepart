# Update discovery validation

Validated 2026-11-15 for GitHub issue #3, the manual discovery slice of parent
issue #2. Automatic reminders, downloading and installation remain later tickets.

## Implementation and contract

More > Application updates reuses existing grouped settings surfaces, semantic
Material colors, theme typography and the shared 48dp primary button. Installed
version, checking, available version/size/plain summary, no compatible update and
retryable failure states use Android string resources. Update state and actions
enter through the app boundary; shop persistence and backup models are unchanged.

The signed-release workflow keeps its signing configuration and existing APK,
App Bundle and checksum outputs. APK-derived format-v1 metadata joins those
assets. Publication validates staged files, creates a draft, downloads and checks
all four uploaded assets, then makes the release public. Existing release assets
are never replaced; manual builds remain artifact-only. See [releases](releases.md)
for the public contract and maintainer procedure.

## Checks

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:compileReleaseKotlin --console=plain
python tools/release/test_metadata.py app/build/outputs/apk/debug/app-debug.apk C:/Android/Sdk/build-tools/36.0.0/aapt.exe
python tools/release/test_publish.py
node tools/theme/validate.mjs
```

All checks passed: 9 JVM unit tests, 21 focused instrumentation tests, debug and
release compilation/lint, 4 Python contract tests, workflow lint and theme checks.
The final strict-metadata snapshot also passed a focused rerun of all 7 discovery
tests. Android lint retains existing starter/dependency warnings and reports no
errors or new update-resource warnings.

The metadata contract was also checked against an existing signed release APK,
independently reading package, version and minimum API through Android build-tools
`aapt`, plus exact size/SHA-256. Publication tests cover incomplete preparation,
corrupt staged upload, preserving an existing release and successful publication.
The release workflow passes `actionlint`.

The focused device run uses the real app on disposable API 37 AVD `RetentionFinal`
(`emulator-5570`): `UpdateDiscoveryTest`, `UpdateWorkflowTest`, `ShopWorkflowTest`
and `LayoutWorkflowTest`. The updater boundary exercises production HTTP over a
loopback server, stable/compatible/version ordering, pagination, malformed metadata,
missing assets, checksum/digest contradictions, empty releases, offline/timeout/
rate-limit recovery, concurrent checks and cancellation of a real trickled socket.
Settings tests cover checking/failure/retry, eligible presentation, navigation and
activity recreation, foreground cancellation and unchanged real Room snapshots
containing stock, a completed sale and a saved cart. Seeded saved carts are removed
only after preservation assertions so existing empty-cart tests remain isolated.

Only debug builds allow cleartext loopback for controlled tests. Production URLs
remain HTTPS public GitHub requests without credentials. Connection/read timeouts,
response-size and total-check limits bound requests; cancellation disconnects the
actual socket. No scheduled closed-app work is registered.

Theme validation passes all 94 checks (minimum normal-text contrast 4.55:1). The
validator now normalizes CRLF before parsing generated palettes, allowing a fresh
Windows checkout to produce the same results without changing any theme token.

## Rendered inspection and limits

Actual Settings screenshots were captured in Light and Dark at 100% and 200%
system font scale. The available release has a long version label and multiline
summary; summary and action captures verify readable wrapping, grouped surfaces,
48dp controls and safe clearance above navigation. The root reviewer inspected all
four configurations and found no new visual defects. Captures are local validation
artifacts under `.scratch/issue-3-visual/updates-*.png`, not runtime assets.

Existing layout workflows cover representative compact and expanded windows;
existing shop workflows cover create/edit/reopen, validation, identities and stock
reconciliation. Manual physical-phone, TalkBack, Accessibility Scanner, Voice Access
and Switch Access checks were not performed. No real GitHub release was published
and repository signing secrets were not accessed during local validation. The
signed two-APK upgrade/installer acceptance path belongs to later tickets.
