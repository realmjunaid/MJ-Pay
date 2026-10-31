# MJ Pay — Master Build Prompt

You are an AI coding agent. Build the Android app **MJ Pay** exactly as specified in this file. Read the whole file first, then work through the build steps in order.

---

## 0. Working protocol

1. Work **step by step** (Section 7). Finish a step completely before starting the next one.
2. After each step: build (`gradlew assembleDebug`, use `gradlew.bat` on Windows), run unit tests, fix failures, then make a git commit named `step-N: <title>`.
3. Keep a file `DECISIONS.md`. Whenever the spec is ambiguous, pick the **safest and lightest** option, do not stop to ask, and write the decision there in one or two lines.
4. Do not add features, libraries, permissions or screens that are not in this file. If you believe something is missing, add it to `DECISIONS.md` under "Suggestions" instead of building it.
5. If the Android SDK, JDK or Gradle is missing on this machine, stop and tell me the exact install steps. Never pretend a build passed.
6. Use the latest stable versions of Gradle, Android Gradle Plugin, Kotlin and AndroidX at build time, and make sure they are compatible with each other.
7. Never put real URLs, secrets, phone numbers or SMS text in the repo. All configuration is entered at runtime inside the app.
8. Final answer of each step: a short report (what was built, files changed, how it was tested). No long explanations.

---

## 1. Goal and context

**MJ Pay** is a tiny Android app that runs on a dedicated phone holding the SIM of my bKash/Nagad number. When a **payment-received SMS** arrives, the app securely forwards it to my server over HTTPS. My server (a Telegram bot and later websites) then uses it to verify customer payments automatically.

The app does only this:
1. Receive SMS (event-driven, no polling).
2. Keep only bKash/Nagad **money-received** messages and drop everything else, especially OTP/PIN messages.
3. Send them signed to my server, retry until delivered.
4. Show a small history and health screen so I can see what was sent, what failed, and resend.
5. Send a periodic heartbeat so my server can alert me if the phone goes offline.

The phone will stay on 24 hours a day. The app must be so light that the owner sees no battery or performance effect.

---

## 2. Non-negotiables

- **Lightweight:** idle app uses nothing. No foreground service, no wake locks, no exact alarms, no polling loops, no long-lived threads.
- **Privacy:** read only the SMS that arrive through the SMS broadcast. Never read the inbox, contacts, location or phone state. Drop non-payment SMS immediately: never store, log or send them.
- **Security:** HTTPS only, signed requests, secret stored encrypted, SMS body encrypted at rest, no backups, no screenshots of sensitive screens, app lock.
- **Reliability:** an SMS that passed the filter must never be lost. It is saved locally **before** any network call and retried until delivered or manually resent.
- **Small:** release APK target under 1.5 MB.
- **No third-party SDKs:** no Firebase, analytics, ads, crash reporters.

---

## 3. Tech stack and project layout

- Language: **Kotlin**. Build: Gradle Kotlin DSL.
- `applicationId`: `me.mjunaid.mjpay` (keep it in one place so it can be changed). App name: **MJ Pay**.
- `minSdk` 26. `compileSdk` and `targetSdk`: latest stable. Handle window insets correctly for the latest target SDK (edge-to-edge).
- UI: plain Android Views with XML layouts. **No Compose, no AppCompat, no Material library, no RecyclerView** (use `ListView` with a simple adapter and a hard row limit, e.g. 300 newest rows with "load more").
- Theme: framework `android:Theme.Material.Light` in `values/` and `android:Theme.Material` in `values-night/` so dark mode follows the system.
- Allowed dependencies (only these): `androidx.core:core-ktx` (FileProvider), `androidx.work:work-runtime` (plain `Worker`, no coroutines needed). Everything else uses the Android framework (`HttpURLConnection`, `SQLiteOpenHelper`, `org.json`, `javax.crypto`, Android Keystore, `KeyguardManager`).
- Release build: R8 minify and resource shrinking on. ProGuard rules must also strip `android.util.Log` calls in release (`-assumenosideeffects`).
- Test deps allowed: JUnit for unit tests.

Package layout (adjust names if needed, keep the idea):

```
app/src/main/java/me/mjunaid/mjpay/
  App.kt                  // Application, lifecycle callbacks for app lock
  receiver/SmsReceiver.kt
  sms/SmsFilter.kt        // pure Kotlin, unit tested
  sms/SmsParserLite.kt    // best-effort amount / trx id / method for UI only
  data/Db.kt              // SQLiteOpenHelper + DAO functions
  data/Prefs.kt
  security/KeyStoreCrypto.kt
  security/Signer.kt      // pure Kotlin HMAC-SHA256, unit tested
  security/SecretStore.kt
  net/Uploader.kt         // HttpURLConnection POST, result classification
  work/DeliveryWorker.kt
  work/HeartbeatWorker.kt
  work/Scheduler.kt
  ui/MainActivity.kt
  ui/HistoryActivity.kt
  ui/DetailActivity.kt
  ui/SettingsActivity.kt
  ui/LockManager.kt
  util/CsvExporter.kt
docs/server-contract.md   // copy of Section 4 of this file
docs/samples.md           // real masked SMS samples (I will fill this later)
tools/mock_server.js      // dependency-free Node script for local testing
README.md  DECISIONS.md  .gitignore
```

---

## 4. Server contract (must be followed exactly)

**Request**

```
POST <configured webhook URL>        // must be https://
Content-Type: application/json; charset=utf-8
X-Timestamp: <unix seconds, string>
X-Signature: <lowercase hex of HMAC-SHA256(secret, X-Timestamp + "." + rawBodyString)>
X-Device-Id: <device label set in settings, default "phone-1">
```

- `rawBodyString` is exactly the UTF-8 JSON text sent as the body. Build the JSON string once, sign that exact string, send that exact string.
- `secret` is the shared secret from settings (UTF-8 bytes).
- The server rejects timestamps older than 5 minutes. A `401` means bad signature **or** bad timestamp.

**Body types**

SMS delivery:
```json
{
  "type": "sms",
  "delivery_id": "<uuid v4, stable across retries>",
  "from": "bKash",
  "body": "<full sms text>",
  "received_at": 1760000000000,
  "sim": 1,
  "app_version": "1.0.0"
}
```
(`sim` is the subscription id from the broadcast extras if present, otherwise `null`.)

Heartbeat:
```json
{
  "type": "heartbeat",
  "ts": 1760000000000,
  "battery": 87,
  "charging": true,
  "pending": 0,
  "last_success_at": 1760000000000,
  "battery_opt_ignored": true,
  "app_version": "1.0.0"
}
```

Test (from the Test button):
```json
{ "type": "test", "ts": 1760000000000, "app_version": "1.0.0" }
```

**Responses and how the app reacts**

| HTTP result | App action |
|---|---|
| 2xx | success, mark SENT |
| 429, 5xx, timeout, no network, TLS/IO error | retryable, keep PENDING, retry with backoff |
| 401, 403 | permanent for now: mark FAILED with error `auth` (hint: check secret and phone clock), stop automatic retries, allow manual resend |
| other 4xx | permanent: mark FAILED with the status code, allow manual resend |

The server de-duplicates by `delivery_id`, so retries and resends are always safe. Never follow redirects (`setInstanceFollowRedirects(false)`); treat 3xx as permanent failure.

---

## 5. SMS filter rules (`SmsFilter`)

Input: sender address string, full message body. Output: `Accept` or `Drop(reason)`.

1. **Multipart:** combine all parts from the same broadcast into one body (`Telephony.Sms.Intents.getMessagesFromIntent`).
2. **Sender allowlist:** default `bKash,NAGAD`, case-insensitive exact match, editable in settings (comma separated). Anything else: drop.
3. **Never-forward words:** if the body contains (case-insensitive) any of `otp`, `pin`, `verification code`, `password`, `one time`, `one-time`: drop.
4. **Payment-received rule:** accept only if the body contains the keyword `received` (case-insensitive) **and** matches an amount pattern like `Tk 1,250.00` / `Tk1250` / `Amount: Tk 100` (regex must accept optional space, thousands commas, optional decimals).
5. Everything that is dropped: not stored, not logged, not forwarded.
6. All keywords and regexes live in one file/object so they are easy to adjust. I will provide real masked samples in `docs/samples.md` later; write the unit tests using clearly fake placeholder SMS text for now (bKash style and Nagad style) and structure the tests so adding real samples is trivial.

`SmsParserLite` (UI only, the server does the real parsing): best-effort extract `amount` (as integer paisa), `trx_id` (token after `TrxID` or `TxnID`, optional colon), and `method` (`bKash`/`Nagad`/`Other` from sender). Parser failure must never block forwarding: store nulls and forward anyway.

---

## 6. Data model

SQLite via `SQLiteOpenHelper`, table `deliveries`:

| column | type | notes |
|---|---|---|
| id | INTEGER PK AUTOINCREMENT | |
| delivery_id | TEXT UNIQUE | uuid, sent to server |
| dedupe_key | TEXT UNIQUE | sha256(sender + "\|" + body + "\|" + smsTimestampMillis), prevents duplicate broadcasts |
| received_at | INTEGER | epoch millis |
| sender | TEXT | |
| method | TEXT | bKash / Nagad / Other |
| body_enc | TEXT | AES-GCM encrypted, base64 (iv + ciphertext) |
| trx_id | TEXT NULL | plaintext, for search |
| amount_paisa | INTEGER NULL | for totals |
| status | TEXT | PENDING / SENT / FAILED |
| attempts | INTEGER | |
| last_error | TEXT NULL | short code, never SMS text |
| last_attempt_at | INTEGER NULL | |
| sim | INTEGER NULL | |

Indexes on `status`, `received_at`, `trx_id`. Settings (non-secret) in `SharedPreferences`: webhook URL, device id, sender allowlist, retention days (default 30), lock enabled, last success time, last heartbeat time/result. The secret is stored encrypted (see Section 9).

---

## 7. Build steps

### Step 1 — Scaffold
- Create the Gradle project, manifest, themes (`values` + `values-night`), placeholder vector launcher icon, `.gitignore` (keystore files, `local.properties`, build dirs), `README.md` skeleton, `DECISIONS.md`.
- Manifest permissions: **only** `RECEIVE_SMS`, `INTERNET`, `POST_NOTIFICATIONS`. (WorkManager will merge its own permissions automatically; leave those, add nothing else.)
- `android:allowBackup="false"`, `dataExtractionRules` and `fullBackupContent` XML that exclude everything, `usesCleartextTraffic="false"`, `network_security_config.xml` with `cleartextTrafficPermitted="false"`.
- Debug-only override (`src/debug`): allow cleartext **only** for `10.0.2.2` and `localhost` for emulator testing with the mock server. Release must never allow cleartext.
- Release signing reads from `keystore.properties` (git-ignored); document `keytool` command in README.
- Done when: `assembleDebug` and `assembleRelease` (unsigned is fine) build and the empty app launches.

### Step 2 — Crypto and signing
- `KeyStoreCrypto`: Android Keystore AES-256-GCM key (alias `mjpay_key`), `encrypt(String): String` and `decrypt(String): String` (base64 of iv + ciphertext).
- `SecretStore`: save/load the webhook secret encrypted with `KeyStoreCrypto`; `hasSecret()`; the plaintext secret is never shown again in the UI.
- `Signer`: pure Kotlin `hmacSha256Hex(secret, message)` returning lowercase hex. Unit test with the RFC 4231 HMAC-SHA256 test vectors and with the `timestamp + "." + body` format.
- Done when: unit tests pass.

### Step 3 — SMS filter
- Implement `SmsFilter` and `SmsParserLite` exactly per Section 5, with unit tests: accepts a bKash-style and Nagad-style received SMS (placeholders), drops OTP/PIN SMS, drops non-allowlisted senders, handles amounts with commas/decimals, parser returns nulls without crashing on odd text.
- Done when: unit tests pass.

### Step 4 — Database
- Implement `Db` with the schema above and these functions: `insertPending(...)` (returns null if `dedupe_key` already exists), `getById`, `markSent`, `markFailed(error)`, `incrementAttempt`, `list(statusFilter, methodFilter, trxQuery, limit, offset)`, `todayStats()` (count and total paisa, local timezone day), `pendingCount()`, `oldestPendingAge()`, `purgeOlderThan(days)`, `listPendingIds()`.
- Body is encrypted before insert and decrypted only for the detail screen and for building the upload payload.
- Done when: unit/instrumented tests (or a simple JVM test with an in-memory wrapper) cover insert, dedupe, status transitions, purge, today stats.

### Step 5 — Uploader and delivery worker
- `Uploader.post(json: String)`: validate URL is `https` (debug build also allows the debug cleartext hosts), set `X-Timestamp`, `X-Signature`, `X-Device-Id`, `Content-Type`, connect/read timeout 10 s, no redirects, read at most 1 KB of response, always close streams. Returns `Success`, `Retry(code)` or `Permanent(code)` as in Section 4. Never log URL, headers, body or secret.
- `DeliveryWorker` (plain `Worker`): input `delivery_id`; unique work name `deliver-<id>` with `ExistingWorkPolicy.KEEP`; constraints `NetworkType.CONNECTED`; backoff exponential from 30 s. Loads the row, skips if already SENT, builds the SMS JSON, uploads, updates the row. If the row is older than 24 hours and still failing, mark FAILED and finish without retrying. `Permanent` results mark FAILED and finish.
- `Scheduler`: helper to enqueue delivery work and to schedule the heartbeat.
- Done when: tested against `tools/mock_server.js` (see Step 11) for success, 5xx retry, 401 failure and offline then online recovery.

### Step 6 — SMS receiver
- `SmsReceiver` declared in the manifest for `android.provider.Telephony.SMS_RECEIVED`, `android:exported="true"` with `android:permission="android.permission.BROADCAST_SMS"`. Every other component `exported="false"` (except the launcher activity).
- On receive: join multipart text, run `SmsFilter`. If dropped, return immediately (nothing stored). If accepted: compute `dedupe_key`, `insertPending` (skip if duplicate), then use `goAsync()`, attempt **one immediate upload** on a short-lived thread (hard stop at ~8 seconds), mark SENT on success, otherwise enqueue `DeliveryWorker`. Always call `finish()` on the `PendingResult`.
- Request `RECEIVE_SMS` at runtime from the main screen. Note: the app only starts receiving after it has been opened once after install.
- Done when: a simulated SMS (see debug hook in Step 11) goes through the full path.

### Step 7 — Heartbeat and maintenance
- `HeartbeatWorker`: one `PeriodicWorkRequest` every 15 minutes (flex 5 minutes), `CONNECTED` constraint, unique name `heartbeat` with `KEEP`. It: (a) sends the heartbeat JSON from Section 4, (b) purges rows older than the retention setting, (c) re-enqueues delivery work for any PENDING row older than 5 minutes, (d) shows or clears one low-importance notification "MJ Pay: N deliveries are failing" if the oldest PENDING row is older than 30 minutes (no SMS content in the notification; skip silently if notification permission is not granted).
- Schedule it when `MainActivity` starts. WorkManager restores scheduled work after reboot, so do **not** add a boot receiver or `RECEIVE_BOOT_COMPLETED` of your own.
- Done when: heartbeat appears on the mock server every 15 minutes (use a shorter interval in a debug build only if needed for testing).

### Step 8 — Main screen
- Status card: SMS permission (granted or button to request), battery optimization (ignored or button that opens `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, needs no extra permission), notification permission (optional), last successful send time, last heartbeat time and result, pending count.
- Today card: number of payments and total Tk received today.
- Buttons: History, Settings, Send test.
- Banners (red) when SMS permission is missing, secret/URL not configured, or heartbeat has been failing for over 45 minutes.
- Done when: all states render correctly in light and dark mode.

### Step 9 — History and details
- `HistoryActivity`: newest first. Row shows time, method, amount, trx id (if known), status chip (colored text is fine). Filters: status (All / Pending / Sent / Failed), method (All / bKash / Nagad), search box for trx id. Header shows today's count and total. Buttons: "Resend all failed", "Export CSV".
- `DetailActivity`: full decrypted body, sender, received time, attempts, last error, last attempt time, `Resend` button (resets status to PENDING and enqueues delivery).
- `CsvExporter`: writes CSV to the cache directory, shares via `FileProvider` and `ACTION_SEND`. Columns: time, method, amount, trx_id, status, attempts. **Do not export SMS bodies.** Neutralize CSV injection (cells starting with `=`, `+`, `-`, `@` get a leading `'`). Require app-lock authentication before export and show a warning that the file contains payment data.
- Done when: list, filters, search, resend and export work on a device/emulator with test data.

### Step 10 — Settings and app lock
- Settings fields: webhook URL (must be https), secret (write-only field; show "secret is set" afterwards, with a Replace action), device id, sender allowlist, retention days, app lock switch, Send test button (shows success or the error code).
- `LockManager`: when app lock is on, require device credential through `KeyguardManager.createConfirmDeviceCredentialIntent` when the app opens and again after it has been in the background for more than 60 seconds (track via `Application.ActivityLifecycleCallbacks`). If the device has no secure screen lock, warn and do not enable the switch. SMS receiving and delivery must work even while the app is locked or closed.
- Apply `FLAG_SECURE` to every activity.
- Settings changes require unlock first.
- Done when: lock, test button and saving settings all work, and the receiver still delivers while the app is locked.

### Step 11 — Hardening, tooling, docs
- `tools/mock_server.js`: dependency-free Node script. Env `SECRET` and `PORT`. Verifies `X-Signature` and the 5-minute timestamp window, prints only `type`, `delivery_id`, `from`, parsed amount and the verification result (not the full body), answers `200 {"ok":true}`, supports env `FAIL=500` or `FAIL=401` to test retry and failure behavior.
- Debug-only hook (in `src/debug`, never in release): a hidden "Simulate SMS" screen where I can type sender and body and run it through the same pipeline as the receiver. Add `adb` instructions in the README as an alternative.
- Run the checklists in Sections 9, 10 and 11 and fix everything that fails.
- README: what the app is, build and sign instructions, install (sideload) steps, first-run setup (grant SMS permission, battery optimization unrestricted, autostart on, lock the app in recents, Xiaomi/Oppo/Vivo/Realme/Samsung notes), how to measure battery usage after 24 hours (Settings > Battery > App usage), how to test with the mock server and a tunnel, troubleshooting (401 means wrong secret or wrong phone clock).
- `docs/server-contract.md`: copy of Section 4.
- Done when: all definition-of-done items in Section 12 are true.

---

## 8. UI notes

- English UI text, short and clear. Large tap targets, no animations beyond system defaults.
- Times shown in the phone's local timezone, amounts as `Tk 1,250.00`.
- Every screen must work in light and dark mode and on small phones (360dp width).
- Do not show SMS bodies anywhere except the detail screen. Mask nothing else in the list (I want full trx id and amount for reconciliation).

---

## 9. Security checklist (must all pass)

- [ ] Only permissions: `RECEIVE_SMS`, `INTERNET`, `POST_NOTIFICATIONS` (plus those merged by WorkManager). No `READ_SMS`, contacts, storage, location or phone state.
- [ ] SMS receiver protected with `android:permission="android.permission.BROADCAST_SMS"`; all other components not exported (except the launcher activity).
- [ ] HTTPS only in release; cleartext disabled; no redirects; the app only talks to the configured host.
- [ ] Requests signed with HMAC-SHA256 plus timestamp; no static secret header.
- [ ] Secret stored encrypted with an Android Keystore key; SMS body column encrypted with AES-GCM; neither appears in logs.
- [ ] `allowBackup=false`, backup/extraction rules exclude all data.
- [ ] `FLAG_SECURE` on all activities; app lock works; export requires unlock; CSV has no SMS bodies and is injection-safe.
- [ ] Release build: R8 on, `Log` calls stripped, `debuggable=false`, no third-party SDKs.
- [ ] Dropped SMS (OTP/PIN/other senders) are never stored, logged or sent.
- [ ] Optional certificate pinning: document in README how to add a `pin-set` (with a backup pin) to `network_security_config.xml` at build time. Do not implement runtime pinning.

## 10. Performance checklist (must all pass)

- [ ] No foreground service, wake locks, `AlarmManager` exact alarms, polling loops or long-lived threads.
- [ ] Receiver uses `goAsync()` with a hard 8-second stop and always calls `finish()`.
- [ ] Heartbeat is one 15-minute periodic work with a tiny JSON body; no other recurring work.
- [ ] No observers or listeners active while the UI is closed.
- [ ] Release APK under ~1.5 MB; no unused resources or libraries.
- [ ] History screen never loads more than the visible page of rows.

## 11. Testing

- Unit tests (JUnit): `Signer` (RFC 4231 vectors), `SmsFilter`, `SmsParserLite`, response classification in `Uploader`, CSV escaping, dedupe key.
- Manual test checklist (write it in README and tick it off in your final report):
  1. Simulated bKash-style SMS reaches the mock server with a valid signature.
  2. Simulated OTP/PIN SMS is dropped (nothing in DB, nothing on the server).
  3. Non-allowlisted sender is dropped.
  4. Airplane mode on, SMS arrives, row is PENDING; airplane mode off, it is delivered once, no duplicates on the server.
  5. `FAIL=500` retries with backoff; `FAIL=401` marks FAILED with error `auth`; Resend delivers after fixing the server.
  6. Same broadcast delivered twice creates one row.
  7. Wrong phone clock gives `401`, and the UI hints at checking the phone time.
  8. App locked or swiped away (not force-stopped): next SMS is still delivered.
  9. Heartbeat arrives at the mock server; purge removes rows older than retention.
  10. Dark mode and a 360dp screen look fine; screenshots are blocked.

---

## 12. Definition of done

- All 11 steps committed, `assembleRelease` succeeds, all unit tests pass.
- Sections 9 and 10 checklists fully ticked; manual tests in Section 11 documented as passed (or clearly marked as "could not test: reason").
- `README.md`, `DECISIONS.md`, `docs/server-contract.md`, `tools/mock_server.js` present.
- Final report lists: APK size, permissions in the final merged manifest, dependency list, and anything I must do manually (keystore creation, real SMS samples in `docs/samples.md`, phone battery settings).

## 13. Out of scope (do not build)

Play Store release, iOS, reading the SMS inbox, sending or replying to SMS, OTP handling of any kind, multiple servers, user accounts, in-app payment parsing beyond the best-effort UI fields, analytics, push notifications, widgets.
