# 0.9.0-control-test

- Node fixtures pass: pending approval, immutable app nickname, admin rename, block retained on re-registration, global switch, bridge signatures, token authorization, malformed/upstream-failure semantics, usage deduplication and ASCII-safe wire encoding for Thai names.
- Live signed Apps Script and Vercel API checks pass on the supplied Sheet/deployment: registration, approval, rename, persistent block and rejected missing token.
- Browser login with an allowed Google account succeeded. An actual Thai nickname and note were saved through the admin form and observed on the dashboard.
- Android assembleDebug, assembleDebugAndroidTest and lintDebug pass.
- Installed and ran control-only instrumentation on emulator-5580: approval, denial, switch separation, last-decision retention on absent/malformed responses, editor rendering/rotation and crop zoom passed. LSPosed framework and target callback tests were explicitly skipped on this emulator; the previous physical device e3b20843 was not connected.
- The registration UI requires the real user to choose and confirm a nickname; no nickname was assigned to the owner's physical phone.
- No target check-in/shutter actions, Git push or GitHub release.
