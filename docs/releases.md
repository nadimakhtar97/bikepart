# App releases

The `App release` GitHub Actions workflow runs the configured unit tests and lints
the release variant, then builds a signed APK and Android App Bundle using Java 25
and Android SDK 37.
Pushing a version tag publishes both files and SHA-256 checksums to GitHub Releases.
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
Successful tag builds publish `bikepart.apk`, `bikepart.aab` and `SHA256SUMS.txt`.
An existing release is never overwritten. Use a new version tag for changed binaries.

To verify signing before publishing, open Actions → App release → Run workflow,
select `main`, and download the `bikepart-release` artifact after the run succeeds.
