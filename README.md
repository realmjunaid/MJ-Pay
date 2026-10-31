# MJ Pay

MJ Pay is a tiny Android app for a **dedicated phone** that holds the SIM of the
bKash/Nagad number. When a **payment-received SMS** arrives it securely forwards the
message to your own server over HTTPS, signed with HMAC-SHA256, retries until delivery,
and keeps a small local history.

সার্ভারের সহজ সেটআপ (Supabase + Cloudflare Worker) নিচে। বিস্তারিত: [`gateway/README.md`](gateway/README.md)।

## গেটওয়ে চালু করা — সংক্ষেপে

1. Supabase-এ নতুন প্রজেক্ট খুলে **SQL Editor**-এ [`gateway/supabase/schema.sql`](gateway/supabase/schema.sql) রান করুন।
2. একটি লম্বা র্যান্ডম কী বানিয়ে তার SHA-256 হ্যাশ `projects` টেবিলে ঢুকান। কাঁচা কী শুধু বটে রাখুন।
3. Cloudflare → Workers → এই GitHub রিপো ইমপোর্ট করুন। **Root directory = `gateway`**। Deploy command: `npx wrangler deploy`।
4. এই তিনটি সিক্রেট দিন: `SMS_WEBHOOK_SECRET`, `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`।
5. ফোনের Settings-এ URL দিন: `https://<worker-host>/sms-webhook` এবং সেই একই সিক্রেট।
6. বট থেকে `POST /v1/match` করুন, হেডার `X-Project-Key`।

`service_role` কী আর প্রজেক্ট কী কখনো ফোন বা ওয়েবসাইটের ফ্রন্টএন্ডে দেবেন না।

It never reads the SMS inbox, contacts, location or phone state. Non-payment SMS
(OTP, PIN, other senders, anything else) is dropped immediately and is never stored,
logged or sent.

- Kotlin, plain Android Views, no Compose/AppCompat/Material, no third-party SDKs
- `minSdk` 26, `targetSdk` 36
- Only permissions: `RECEIVE_SMS`, `INTERNET`, `POST_NOTIFICATIONS` (plus WorkManager's
  merged permissions)
- SMS bodies encrypted at rest (AES-256-GCM, Android Keystore), secret stored encrypted
- `FLAG_SECURE` on every screen, optional device-credential app lock, no backups
- One 15-minute heartbeat and no foreground service, wake locks or polling

---

## Requirements

- JDK 17
- Android SDK Platform 36 + Build-Tools 36
- No installed Gradle needed: the Gradle wrapper is committed (`gradlew.bat` on
  Windows, `./gradlew` elsewhere)

If the SDK lives somewhere other than the default, create `local.properties` in the
project root (it is git-ignored):

```properties
sdk.dir=C:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

## Build

```bash
# debug (local testing, cleartext allowed only for 10.0.2.2 / localhost / 127.0.0.1)
gradlew.bat assembleDebug

# release (R8 + resource shrinking; unsigned unless keystore.properties exists)
gradlew.bat assembleRelease

# unit tests
gradlew.bat testDebugUnitTest
```

Outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk` (or `app-release-unsigned.apk`)

## Signing the release build

1. Create a keystore (keep it offline and backed up):

   ```bash
   keytool -genkeypair -v -keystore release.jks -alias mjpay \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Create `keystore.properties` in the **project root** (git-ignored):

   ```properties
   storeFile=release.jks
   storePassword=your-store-password
   keyAlias=mjpay
   keyPassword=your-key-password
   ```

3. `gradlew.bat assembleRelease`. The APK is signed automatically.

## Install

Download the signed release APK from the repo:

- [releases/MJ-Pay-1.0.0.apk](releases/MJ-Pay-1.0.0.apk) (about 270 KB)

On the phone, open that file and allow install from the browser or file manager. Or from a computer:

```bash
adb install -r releases/MJ-Pay-1.0.0.apk
```

## First-run setup (important)

1. Open MJ Pay once. **The SMS receiver only works after the app has been opened once
   after install** (Android runtime permission model).
2. Grant **SMS permission** on the main screen.
3. Set **Battery optimization → Unrestricted** from the main screen.
4. Optional: grant **Notifications** (used only for the "deliveries are failing" alert).
5. Open **Settings** and enter:
   - Webhook URL (must be `https://` in release)
   - Shared secret (write-only; it is encrypted and never shown again)
   - Device id (default `phone-1`)
   - Sender allowlist (default `bKash,NAGAD`)
   - Retention days (default 30)
6. Press **Send test** and check that your server returns 2xx.

### OEM autostart / battery notes

WorkManager restores scheduled work after reboot, but aggressive OEM software may still
kill background work. On the dedicated phone:

- **Xiaomi/Redmi/POCO**: Settings → Apps → MJ Pay → Autostart ON; Battery saver → No
  restrictions; lock the app in Recents.
- **Oppo/Realme/OnePlus**: App management → MJ Pay → Allow auto-launch; Battery
  optimization → Don't optimize; lock in Recents.
- **Vivo/iQOO**: Auto-start manager → allow MJ Pay; background power saving → high.
- **Samsung**: Settings → Battery → Background usage limits → make sure MJ Pay is not
  in "Sleeping apps"; turn off "Put unused apps to sleep" for it.
- Lock MJ Pay in the recent-apps list so the system does not remove it.

Do **not** force-stop the app: force-stop cancels broadcasts and scheduled work until
the next manual launch. Swiping it away is fine.

## How it works

```
SMS broadcast → filter (allowlist, OTP/PIN block, "received" + amount)
              → encrypt + store PENDING (SQLite)  ← nothing is sent before this
              → one immediate upload (≤ ~8 s)
              → on failure: WorkManager retry with exponential backoff (30 s base)
              → permanent failure (401/403/other 4xx/3xx) → FAILED, manual resend
```

- A PENDING row older than 24 hours is marked FAILED with error `expired`.
- Deduplication: `sha256(sender|body|smsTimestamp)` prevents the same broadcast from
  being stored twice. The server also deduplicates by `delivery_id`.
- The heartbeat (15 min, 5 min flex) also purges old rows, re-queues PENDING rows older
  than 5 minutes, and raises one low-importance notification when the oldest PENDING
  row is older than 30 minutes.

The full wire contract is in [`docs/server-contract.md`](docs/server-contract.md).

## Testing with the mock server

`tools/mock_server.js` is dependency-free Node.js (18+). It verifies the signature and
the 5-minute timestamp window and prints only metadata (never the SMS body).

```bash
# terminal 1
SECRET=dev-secret PORT=8080 node tools/mock_server.js

# failure modes
SECRET=dev-secret PORT=8080 FAIL=500 node tools/mock_server.js
SECRET=dev-secret PORT=8080 FAIL=401 node tools/mock_server.js
```

- **Emulator**: set the webhook URL to `http://10.0.2.2:8080/hook` in a **debug** build
  (release builds never allow cleartext).
- **Real phone**: expose the mock server with a tunnel, e.g.
  `cloudflared tunnel --url http://localhost:8080` or `ngrok http 8080`, and use the
  returned `https://…/hook` URL.

### Simulate an SMS without a SIM

Debug builds only, long-press the **MJ Pay** title on the main screen → "Simulate SMS"
screen. Fill sender/body or use the sample buttons, then **Run pipeline**. This uses the
same filter, encryption, database and upload path as the real receiver.

Alternative from adb (debug build only; the activity is exported in the debug
manifest and is not present in release):

```bash
adb shell am start -n me.mjunaid.mjpay/me.mjunaid.mjpay.debug.SimulateSmsActivity
```

You can also send a real SMS to the phone (the classic end-to-end test).

## Battery measurement (after 24 hours)

1. Charge the phone, then leave it on the SIM with the screen off for 24 hours.
2. Settings → Battery → Battery usage → MJ Pay. Expect a very small share (well under
   1–2%). Check that there is no long "CPU wake" bar.
3. Settings → Battery → Battery usage → "Mobile data" / "Wi-Fi" if available; the only
   traffic should be one tiny heartbeat every 15 minutes plus SMS deliveries.
4. If usage is high, check for OEM background restrictions and for failing rows in
   History (retries every 30 s while a row is PENDING).

## Troubleshooting

| Symptom | Check |
|---|---|
| `401` / error `auth` | Secret mismatch between app and server, or the phone clock is wrong (enable automatic date/time). Both cause 401. |
| Nothing arrives at the server | App opened at least once after install, SMS permission granted, sender allowlist matches exactly, message contains `received` and an amount like `Tk 100`. |
| SMS arrives but nothing is stored | The message was dropped by design (non-allowlisted sender, OTP/PIN words, no `received`/amount). Dropped messages are never stored. |
| Rows stay PENDING | No network, wrong URL, TLS error, or server 5xx/429. Open a row's detail screen for the short error code. |
| Heartbeat missing | It runs every 15 minutes; check the "Last heartbeat" line on the main screen, notification permission is not required for it. OEM battery restrictions are the usual cause. |
| App lock loop | The device must have a PIN/pattern/password. If the device lock is removed, MJ Pay disables its own lock instead of locking you out. |
| Export fails | Choose an app that accepts `text/csv` from the Android share sheet. The CSV is written to the cache directory and deleted on the next export. |

## Security notes

- HTTPS only in release; redirects are not followed; the app only contacts the
  configured host.
- Request bodies are signed with HMAC-SHA256 over `timestamp.rawBody`; there is no
  static secret header.
- The secret is AES-256-GCM encrypted with a non-exportable Android Keystore key; SMS
  bodies use the same scheme. Nothing is logged.
- `allowBackup=false`, and both backup/extraction rule files exclude all data.
- All activities use `FLAG_SECURE`: screenshots and Recents snapshots are blocked.
- CSV export contains time, method, amount, trx id, status, attempts — **never SMS
  bodies**. Cells starting with `= + - @` (and tab/CR) are prefixed with `'` to block
  formula injection, and export requires device-credential authentication.
- The release build has R8 and resource shrinking on and strips all `android.util.Log`
  calls via `proguard-rules.pro`.

### Optional certificate pinning (build time only)

No runtime pinning is implemented. To pin your server certificate, add a
`<domain-config>` with a **backup pin** to
`app/src/main/res/xml/network_security_config.xml`:

```xml
<domain-config>
    <domain includeSubdomains="true">payments.example.com</domain>
    <pin-set expiration="2027-12-31">
        <pin digest="SHA-256">REPLACE_WITH_PRIMARY_SPKI_BASE64</pin>
        <pin digest="SHA-256">REPLACE_WITH_BACKUP_SPKI_BASE64</pin>
    </pin-set>
</domain-config>
```

Compute pins from the SPKI with:
`openssl s_client -connect host:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64`.
Always keep a valid backup pin and an expiration date, or app updates can lock
themselves out.

## Project layout

```
app/src/main/java/me/mjunaid/mjpay/
  App.kt, receiver/SmsReceiver.kt, sms/ (filter, parser, intake, dedupe)
  data/ (SQLite Db, Prefs), security/ (Keystore crypto, Signer, SecretStore)
  net/ (Uploader, payloads, rules), work/ (delivery, heartbeat, scheduler)
  ui/ (main, history, detail, settings, lock, CSV helper)
app/src/debug/          debug-only "Simulate SMS" screen and local cleartext config
app/src/test/           JUnit tests (signer, filter, parser, rules, CSV, DB helpers)
docs/server-contract.md wire contract
docs/samples.md         masked real SMS samples (fill in later)
tools/mock_server.js    dependency-free local server
DECISIONS.md            spec decisions and suggestions
```

## Manual test checklist

Run on an Android 16 (API 36) emulator against `tools/mock_server.js` on 2026-11-01.
Unit tests (signer RFC 4231 vectors, filter, parser, HTTP classification, CSV escaping,
DB query helpers) pass.

| # | Check | Status |
|---|---|---|
| 1 | Simulated bKash SMS reaches the mock server with a valid signature | passed. Debug pipeline and `adb emu sms send` both posted `type:sms` with `verified:true`. |
| 2 | Simulated OTP/PIN SMS is dropped (nothing in DB/server) | passed. An OTP from bKash was not stored and did not reach the server. |
| 3 | Non-allowlisted sender is dropped | passed. Sender `GP` was not stored and did not reach the server. |
| 4 | Airplane mode → PENDING; network back → delivered once, no duplicates | passed. With Wi-Fi and mobile data off the row stayed PENDING; after reconnect it was SENT once. |
| 5 | `FAIL=500` retries with backoff; `FAIL=401` marks FAILED `auth`; Resend delivers | passed. `http-500` stayed PENDING then SENT on the worker retry. `auth` stayed FAILED until "Resend all failed". |
| 6 | Same broadcast twice creates one row | passed. Replaying the same PDU twice left a single row and did not post again. |
| 7 | Wrong phone clock gives 401 and the UI hints at checking time | passed. A +3 hour clock produced `stale timestamp` and the detail line "401/403: check the secret and the phone clock". |
| 8 | App locked/swiped away: next SMS is still delivered | passed. After the process was killed (not force-stopped) the next SMS was delivered. |
| 9 | Heartbeat arrives; purge removes rows older than retention | passed. Heartbeat posted `verified:true`. A row older than retention was deleted; newer rows remained. |
| 10 | Dark mode and 360dp look fine; screenshots are blocked | passed. Light and night mode rendered. At 360dp width the main screen scrolls and the actions stay reachable. `screencap` of the app was a solid black frame. |

Also checked on that emulator: app lock prompts after 60 seconds in the background and
not during the grace period; with a device PIN set, CSV export asks for that PIN after
the warning. The CSV had amounts and trx ids and no SMS text.

Before using the phone for real payments, run this checklist once more against your
real server.

## Verified build

Checked after `assembleRelease` and `testDebugUnitTest` on 2026-11-01:

- Release APK: `app-release-unsigned.apk`, **266.1 KB**, `debuggable` not set, R8 and resource shrinking on.
- Unit tests: 62 passed, 0 failed.
- Direct dependencies: `androidx.core:core-ktx:1.16.0`, `androidx.work:work-runtime:2.10.0`, JUnit `4.13.2` (tests only).
- App permissions: `RECEIVE_SMS`, `INTERNET`, `POST_NOTIFICATIONS`.
- Merged by WorkManager (not requested in the app manifest): `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, plus `me.mjunaid.mjpay.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- Release components: launcher activity exported; `SmsReceiver` exported and protected by `BROADCAST_SMS`; history, detail and settings not exported. The debug simulate screen is absent. WorkManager also merges its own receivers; the exported ones require `BIND_JOB_SERVICE` or `DUMP`.

Still manual before a real phone: create the release keystore (`keystore.properties`), paste masked real SMS samples into `docs/samples.md`, and set battery unrestricted plus recents lock on the device.
