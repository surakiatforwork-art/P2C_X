# v0.9.1 release validation

- `npm run test:control`: passed signed bridge, token/failure behavior, automatic approval, pending migration and cache, preserved notes, blocked re-registration, global pause, immutable client nickname, admin rename and usage deduplication checks.
- `:app:assembleRelease :app:assembleDebugAndroidTest :app:lintRelease --offline`: passed. Lint reports warnings and no blocking errors.
- Release APK metadata: `com.phantom.p2cx`, versionName `0.9.1`, versionCode `13`, minSdk 26, targetSdk 35. Debugging disabled.
- APK signature verification passed and the certificate matches the previously distributed 0.9.0 test APK.
- Release APK installed successfully over the emulator's existing app. Control-only instrumentation passed permission/offline validation, editor rendering and transforms, crop zoom, image provider, camera recommendation and unsupported fallback checks.
- No physical phone was connected during this release validation. LSPosed and target-app callback tests were skipped on the emulator; those integration paths depend on earlier device testing.
- Public source candidates were checked against local deployment secrets before publishing; private configuration, environment files and signing keys are excluded from Git.
