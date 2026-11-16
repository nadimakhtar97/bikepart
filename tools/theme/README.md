# Effective Fluent theme checks

The active source is the scoped `BikepartTheme` and pinned Fluent Android core
0.3.11 / controls 0.3.3. The old Figma/Material palette generator is retired;
`figma-tokens.json` is historical evidence only. No Node dependencies are needed.

From the repository root, using the toolchain in [SETUP.md](../../SETUP.md):

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nadim.bikepart.ThemeContrastExportTest
adb pull /data/local/tmp/fluent-contrast.json docs/fluent/evidence/theme-pilot/fluent-contrast.json
node tools/theme/validate.mjs
```

Select the intended emulator with `ANDROID_SERIAL` when more than one is running.
The instrumentation measurement resolves the production Fluent scope, Material
bridge, and button state tokens on Android. It records composited foreground /
background colors in both appearances, including disabled-state exemptions.
The validator requires 4.5:1 text and 3:1 meaningful outlines/focus indicators.
It rejects an export whose SHA-256 fingerprint differs from the source list in
`source-files.json`; regenerate after theme/control/dependency changes.

Screenshots, staff workflow checks, keyboard focus, target sizing, and assistive
technology remain separate requirements. Numeric contrast is not proof of a
usable screen. See [pilot evidence](../../docs/fluent/theme-pilot.md).
