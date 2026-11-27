# P2C_X control center: source audit and adaptation plan

Scope: read Full_lock source and compare with the current P2C_X source. No production endpoint was called, no deployed configuration was changed, and no APK was modified for this audit.

## Reference implementation

- Full_lock/control-center/backend.cjs: Vercel access/admin/count endpoints, device bearer credentials, Google admin identity validation, signed Sheets bridge. Firestore is retained as an alternate backend.
- Full_lock/control-center/apps-script/Code.gs: Devices, Settings and AuditLogs tables; setup; signed requests; short-lived caches; registration/check-in; device/global permission changes.
- Full_lock/control-center/frontend/index.html: Google admin login, totals/active/blocked counters, device list, model/version/mode, labels/notes, filters, exact device-code search, global/device switches and recent audit history. Refreshes every 60 seconds when idle and not searching.
- Full_lock/app/src/main/java/com/fulllock/app/RemoteAccess.java: app-scoped SHA-256 Android ID, automatic registration, stored device token, randomized 12-18 minute checks while the process runs, persisted next deadline and last explicit permission decision.
- Full_lock/tools/package-website.mjs and vercel.json: package only public web assets and dashboard; expose server endpoints through Vercel Functions.

The Sheets branch stores operational data in Sheets but still uses Firebase Authentication for administrator Google sign-in. Copying it does not remove the Firebase Auth dependency. DEPLOY.md still describes the earlier Firestore setup; the Apps Script setup guide describes the Sheets migration. Source inspection does not establish which backend production currently uses.

## What registration and usage mean today

New devices are automatically registered as enabled. There is no end-user account creation, mandatory admin approval, license expiry, subscription plan or user-to-device relationship in this schema. Admin-assigned labels are names for devices, not authenticated user identities.

The active counter means a check-in within 24 hours. It is not a concurrent-online counter, a count of people, a count of camera replacements, or measured time using the module. The lastSeen write is throttled to roughly 15 minutes on the Sheets path; cache hits can skip writes.

If a request fails, Full_lock preserves the last explicit decision. A blocked device remains blocked until an explicit successful allowance. Previously allowed offline devices can continue; this is not immediate remote revocation. The Android timer runs only while its process exists and can be delayed by Android.

## P2C_X adaptation

1. Create a dedicated Sheet, Apps Script deployment, Vercel project and P2C_X-prefixed environment variables/secrets. Keep Full_lock data, device hash prefix, credentials, admin settings and branding isolated. Do not copy its Sheet ID or secrets.
2. Reuse the Vercel -> signed Apps Script -> Sheets architecture. Keep Firebase only for the same admin Google login pattern; remove unused Firestore paths from the P2C_X implementation.
3. Add a network client in the P2C_X application process, INTERNET permission, device code/status in MainActivity, and a manual retry button. Trigger initial registration on first application/gallery launch and coalesce later checks. Keep networking off the UI thread and out of target-app hook processes.
4. Add a separate license_allowed value to ModuleSettings and framework RemotePrefs. RuntimeState combines the user switch and permission decision. Tile/boot change only the user switch. Preserve the user's switch preference when remote permission changes. Do not put the device bearer token in framework-shared preferences.
5. Apply the permission gate consistently to camera-intent interception, CameraX interception, selection and final delivery. Handle a denial during an in-flight selection with the existing cancellation contract. A permission-disabled module must not break the target's ordinary camera flow; new calls should pass through without substitution.
6. Retain Devices, Settings and AuditLogs for an exact Full_lock-equivalent first phase. If authenticated end-user registration is desired, add Users/Licenses and a device relationship explicitly; that is additional functionality.
7. If actual feature usage is desired, add daily aggregated UsageDaily records: date, deviceId, gallery-open/confirmed-image/cancel/error counts and app version. Send compact batches with stable batch IDs for deduplication. Count an editor confirmation separately from successful delivery to the target; a confirmed selection is not proof of a target save. A reliable delivery counter needs an additional authenticated local acknowledgement design, since delivery completes in the target process.
8. Collect operational metadata only. No image uploads, image content, filenames, local paths, content URIs, location or target-app personal data are needed for this control center. Clearly disclose operational telemetry in the app.

## Items to improve when reusing the reference

- Document the automatic-approval and offline policy explicitly. Full_lock is currently device registration plus remote permission control, not a complete user licensing service.
- Add registration rate limiting on the Sheets route. The existing per-IP registration limiter is inside the Firestore branch and is bypassed when the Sheets branch is selected.
- A self-reported device hash and server-issued bearer token do not attest a genuine, unmodified APK. This is operational access control, not strong anti-tamper enforcement on rooted devices or an open-source client.
- Do not append one Sheet row per photo. Existing operations scan device rows and serialize signed requests with a script lock. Prefer aggregated counters, caching and gradual load validation.
- At a 15-minute mean interval, 100 devices continuously running for 24 hours imply about 9,600 check-in requests/day, before dashboard traffic. This is a traffic estimate, not a platform quota or a capacity guarantee.
- Audit text encoding before copying the admin UI. Several Thai literals in the current reference files appear corrupted when decoded as UTF-8; use fresh UTF-8 strings in P2C_X.
- Initial registration/offline behavior and check frequency should be covered by tests. No server outage or malformed response should silently become an explicit block.

## Validation performed

Ran the existing in-memory Node fixtures from Full_lock without contacting production:

- node control-center/sheets-checks.cjs: PASS (migration, permissions, cache invalidation, authorization, counts and legacy identity).
- node control-center/sheets-bridge-checks.cjs: PASS (signed bridge, device tokens, Sheets path independence from Firestore and failure semantics).

These fixtures validate reference server logic; they do not validate a P2C_X implementation, live Google deployment, current production secrets or production capacity.

## Suggested implementation sequence

1. Dedicated Sheets/Apps Script + Vercel API and admin dashboard, with test fixtures.
2. P2C_X registration/check-in + permission mirroring through LSPosed RemotePrefs.
3. Per-device/global permission tests, offline/recovery tests, Tile/boot tests and capture cancellation regression tests.
4. Daily usage aggregation after defining exactly which events are needed.

No implementation, deployment, push or release was performed as part of this audit.
