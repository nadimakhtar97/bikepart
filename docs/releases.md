# App releases

The `App release` GitHub Actions workflow runs the configured unit tests and lints
the release variant, then builds a signed APK and Android App Bundle using Java 25
and Android SDK 37.
Pushing a version tag publishes both files, SHA-256 checksums and APK-derived update metadata to GitHub Releases.
Running the workflow manually builds downloadable artifacts without publishing.
This workflow does not upload to Google Play or run device instrumentation tests.

## One-time signing setup

In repository Settings → Secrets and variables → Actions, add these repository secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of the release keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Signing key password |

Use the existing app signing key if the app has already been distributed so updates
can be installed over it. Keep a secure backup of the keystore and passwords.
For a new app, create a key locally with the JDK `keytool`:

```powershell
keytool -genkeypair -v -keystore release.jks -alias bikepart -keyalg RSA -keysize 3072 -validity 10000
```

Copy the Base64 value to the GitHub secret without printing it in logs:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path release.jks))) | Set-Clipboard
```

Keystores and local environment files are ignored by Git. The workflow restores
the key into runner temporary storage and removes it even when the build fails.
Gradle configuration caching is disabled for release builds to avoid caching
signing credentials. Missing secrets stop the workflow before building.

## Publish a version

1. Increase `versionCode` and set `versionName` in `app/build.gradle.kts`.
   Every distributed update must use a larger version code.
2. Commit and push the version change to `main`.
3. Tag that commit using the app version, then push the tag:

```powershell
git tag v1.0
git push origin v1.0
```

For later releases, replace `v1.0` with the new version. The tag labels the release;
the APK's version comes from the Gradle configuration and is not changed by the tag.
Successful tag builds publish `bikepart.apk`, `bikepart.aab`, `SHA256SUMS.txt` and `update.json`.
An existing release is never overwritten. Use a new version tag for changed binaries.

To verify signing before publishing, open Actions → App release → Run workflow,
select `main`, and download the `bikepart-release` artifact after the run succeeds.


## Update discovery contract

`tools/release/metadata.py` reads the actual built APK with Android `aapt dump
badging`, hashes its bytes and writes the version 1 `update.json` contract:

| Field | Source |
| --- | --- |
| `formatVersion` | `1` |
| `packageId` | APK package name (`com.nadim.bikepart`) |
| `versionCode`, `versionName` | APK manifest values; tags do not order Android updates |
| `minSdk` | APK minimum Android API |
| `apkAssetName` | `bikepart.apk` |
| `sizeBytes` | Exact APK file size |
| `sha256` | Lowercase SHA-256 of the APK bytes |

The existing keystore/signing configuration remains unchanged. Preparation verifies
the APK signature with `apksigner`, generates metadata, tests that contract against
the APK and retains the existing APK/App Bundle checksums. A checksum detects byte
changes; it does not establish signing identity. Continue verifying the signing
certificate against the version already distributed to staff before rollout.

Publication creates a **draft** release and uploads all four files. The publisher
then downloads the staged assets, compares every byte by SHA-256 and validates the
metadata/checksum contract again before clearing the draft flag. Failed preparation
creates no release; failed upload/validation leaves an undiscoverable draft. A rerun
never replaces assets in an existing draft or published release. Investigate a
failed draft and use a new version tag for a new publication attempt. Manual builds
remain artifact-only.

More > Application updates shows the installed version and a manual check. The
app anonymously pages public stable releases from `nadimakhtar97/bikepart`, skips
drafts/prereleases and incomplete or inconsistent assets, and selects the highest
compatible Android version code above the installed version. All four named assets
must be uploaded in the same release path; metadata must agree with APK size,
checksums and any GitHub-provided asset digest. Release notes become a short
plain-text summary. GitHub's latest label and version tag text do not decide
eligibility.

After the check offers a release, Download update starts a foreground-only APK
transfer over the available Wi-Fi or mobile connection. The UI shows download
size before transfer, determinate progress, Cancel, and a specific retryable
failure. Duplicate taps coalesce, in-app navigation and rotation retain the
transfer, and true process backgrounding cancels it. Downloads are written to
private temporary storage and promoted only after exact size, SHA-256, package
ID, minimum API, actual APK version code, metadata agreement and signing
compatibility all pass. Completed APKs and their identity live in
`filesDir/app-updates`, are revalidated when the app reopens, and are explicitly
excluded from cloud backup and device transfer. A completed download never installs automatically. Staff can choose Install now or Install later in More > Application updates. Installing may close or restart the app. On first use, Android may ask the user to allow Bikepart as an installation source; return to the app and tap Install now again. Android then asks for confirmation. Denial or cancellation leaves the existing app and retained download available for retry. If the download is missing or invalid, download it again. Do not uninstall the existing app to work around a signing mismatch.

Network responses are limited to 1 MiB for discovery, connect/read timeouts to
10 seconds and a check to 30 seconds, with a separate 256 MiB cap for APK
bodies. Discovery stops safely rather than guessing if more than 20 pages are
required. Concurrent checks coalesce. Leaving the foreground cancels the manual
check and any active download; automatic checks happen at most once per local day on launch or foreground return. Reminders wait for idle Home and can be postponed until the next day; there are no background transfers or system notifications. Failures appear only in update controls and never modify
stock, sales, saved carts, shop schema or shop backups.

Local contract checks (substitute your APK and build-tools paths):

```powershell
python tools/release/test_metadata.py app/build/outputs/apk/release/app-release.apk C:/Android/Sdk/build-tools/36.0.0/aapt.exe
python tools/release/test_publish.py
```

Instrumentation `UpdateDiscoveryTest` uses the production HTTP implementation with
a loopback server for pagination, network failures, timeouts and rate limits.
`UpdateWorkflowTest` exercises Settings with real Room storage, verifies unchanged
shop snapshots, foreground cancellation, and captures light/dark screens at normal
and 200% font scale. Only debug builds allow cleartext loopback traffic for these
tests; release networking remains HTTPS.

## Bootstrap and signing continuity

For the first installation, obtain the verified release APK from the official GitHub release and use Android's installer. For every later update, compare the signing certificate of the new APK with the APK already distributed before rollout (for example, run `apksigner verify --print-certs` on both APKs). Stop rollout if they differ; restoring the original signing key is required. Never ask staff to uninstall, because doing so can remove shop data. Check the downloaded APK's SHA-256 against `SHA256SUMS.txt` and confirm its package, version code, size and hash agree with `update.json`.

Before rollout, exercise a real upgrade on an isolated device using two APKs signed by the same key and increasing version codes. Seed products and stock, a completed sale and a saved cart in the older app; use Android's permission and installer screens to install the newer APK, reopen it, and verify the records and any applicable migration. Record device, versions, signing certificate and results. A simulated installer does not establish upgrade compatibility.
