# Decisions

Safest and lightest option chosen whenever the spec was ambiguous.

## Build and versions
- Gradle 8.14.2, AGP 8.13.2, Kotlin 2.2.21, compile/target SDK 36, minSdk 26.
- `core-ktx 1.16.0`, `work-runtime 2.10.0`, JUnit 4.13.2 only. Newer core/work
  versions require an SDK platform that is not installed on the build machine.
- Theme uses `android:Theme.Material.Light.NoActionBar` / `android:Theme.Material.NoActionBar`
  (children of the required Material themes) so screens draw their own header and no
  AppCompat/Material dependency is needed.
- Launcher icon is an adaptive vector (minSdk 26), so no PNG variants are needed.
- `local.properties` is machine-specific and git-ignored.
- Debug builds keep the same `applicationId` as release (no suffix) so the documented
  `adb` command and settings are identical; debug and release cannot be installed side
  by side on one phone. This is acceptable for a single dedicated device.
- Debug cleartext is additionally allowed for `127.0.0.1` (besides the two hosts named
  in the spec) because it is the same loopback mock server.

## SMS intake
- Filter rules are ordered: sender → blocked words → `received` → amount. A dropped
  message returns from the receiver before any storage or network work.
- Blocked words `otp` and `pin` use word boundaries so "shopping" is not dropped;
  multi-word phrases use case-insensitive substring matching.
- The amount regex accepts `Tk`, `BDT` and `Taka`, optional dot/colon/space, thousands
  commas and one or two decimals.
- `SmsParserLite` stores the first amount it finds (best effort for the UI); the server
  remains authoritative.
- Multipart parts are joined in broadcast order. The SMS timestamp of the first part is
  used for `received_at` and for the dedupe key.
- The immediate upload uses a 3-second connect/read timeout so the receiver can respect
  its ~8-second hard stop. The worker uses the full 10-second timeout.
- The receiver saves the row before starting the upload thread; if the hard stop fires,
  WorkManager is enqueued and the server de-duplicates by `delivery_id`.

## Data and delivery
- `insertPending` returns null on a dedupe-key conflict (broadcast replay), and the
  receiver then does nothing.
- Bodies are encrypted before insert and only decrypted for the detail screen and the
  upload payload. Decryption failure shows "message body unavailable"; it never blocks
  forwarding (but the row is marked FAILED with error `body` if the body cannot be read
  at delivery time).
- Local configuration problems (missing URL/secret) are classified as retryable so a
  later settings fix delivers the queued SMS; only server responses are classified per
  the contract.
- A PENDING row older than 24 hours is marked FAILED with `expired` and not retried.
- `last_success_at` in the heartbeat is `0` when no delivery has ever succeeded.
- Unknown battery level is sent as `-1`, charging `false` (the server should tolerate it).
- History UI is capped at the newest 300 rows, loaded 50 at a time (the list never holds
  more than that). CSV export is a separate query capped at 5000 rows.
- CSV export uses the current history filters and contains no SMS bodies.

## UI / security
- App lock is process-session based: it prompts on cold start and again after more than
  60 seconds in the background. It never touches SMS intake or delivery.
- `KeyguardManager.createConfirmDeviceCredentialIntent` is used instead of a biometric
  library because no extra dependency is allowed. The deprecation warning is expected.
- If the device credential is removed, enabling the lock is blocked; if it disappears
  later, the app disables its own lock instead of locking the user out.
- Export always shows a warning that the file contains payment data. If the device
  has a secure screen lock, Continue then requires that credential. If it does not,
  the warning is the only gate — there is no credential prompt to show, and blocking
  export would make the data unreachable.
- Settings save is gated by the app lock when it is enabled.
- The notification uses the framework `Notification.Builder` (no AndroidX notification
  dependency); the channel is low importance and contains no SMS content.
- The debug "Simulate SMS" screen is reached by long-pressing the main title in a debug
  build, or by `adb shell am start` because the debug manifest exports that activity.
  Release has no such activity; the class only exists in `src/debug`.
- `SmsReceiver` is the only exported component besides the launcher activity and is
  protected by `android.permission.BROADCAST_SMS`.

## Testing
- No SQLite-for-JVM dependency is allowed (only JUnit). Automated tests cover the pure
  helpers (signer, filter, parser, HTTP classification, CSV, dedupe key, query helpers).
  SQLite transitions were checked on an API 36 emulator; results are in the README
  manual checklist.
- The emulator AVD lives under `E:\Android\avd`, so `ANDROID_AVD_HOME` must be set
  before `emulator -avd mjpay_e`. That path is machine-specific and not part of the app.

## Suggestions (not built, per the protocol)
- Add a server-side "device offline" alert driven by the existing heartbeat; the app
  already reports pending count, battery and last success time.
- Add certificate pinning with a backup pin before distributing the release APK
  (instructions are in the README).
- Replace the placeholder parser regexes with real masked samples in `docs/samples.md`
  and add them to the filter/parser tests.
- If a second SIM/device is ever needed, the `X-Device-Id` setting already supports it.
