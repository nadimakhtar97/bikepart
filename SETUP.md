# Build setup

Read this before installing build tools or running Gradle or theme commands.

## Required tools

- JDK 25. The Gradle daemon JVM is pinned in `gradle/gradle-daemon-jvm.properties`.
- Android SDK with the API 37 platform and Build Tools 37.0.0. Android Studio's
  SDK Manager or `sdkmanager` can install these packages. The API 37 package is
  currently listed as `platforms;android-37.0`; check `sdkmanager --list` if the
  package name changes.
- Android SDK Platform Tools for `adb` when using a connected device.
- Node.js for the theme contrast validator (no external dependencies).

Point Gradle at the SDK with `ANDROID_HOME` or a local `local.properties` file
containing `sdk.dir=<absolute SDK path>`. `local.properties` is ignored by Git
and is specific to each machine. Accept the Android SDK license in SDK Manager
before building.

## Build the app

Use the checked-in Gradle wrapper from the repository root:

```sh
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Gradle
downloads the wrapper distribution and project dependencies on the first build.

## Theme tooling

The historical Figma/Material generator is retired. Export the effective Fluent
colors on a connected Android runtime, then validate them (Node built-ins only):

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nadim.bikepart.ThemeContrastExportTest
adb pull /data/local/tmp/fluent-contrast.json docs/fluent/evidence/theme-pilot/fluent-contrast.json
node tools/theme/validate.mjs
```

Set `ANDROID_SERIAL` when more than one device is connected. On a WSL setup
with only Windows Node installed, use `node.exe` for the validation command.

See `tools/theme/README.md` and `docs/ui-guidelines.md` for the theme source and design constraints.

## Device tests

Instrumented tests live in `app/src/androidTest`. Running them requires a
connected Android device or an emulator with a compatible system image; they are
not needed to assemble the APK.
