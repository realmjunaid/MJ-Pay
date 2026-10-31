# Server contract (MJ Pay)

This is the exact contract the app implements. The server should reject anything that
does not match.

## Request

```
POST <configured webhook URL>        // must be https://
Content-Type: application/json; charset=utf-8
X-Timestamp: <unix seconds, string>
X-Signature: <lowercase hex of HMAC-SHA256(secret, X-Timestamp + "." + rawBodyString)>
X-Device-Id: <device label set in settings, default "phone-1">
```

- `rawBodyString` is exactly the UTF-8 JSON text sent as the body. The app builds the
  JSON once, signs that exact string and sends that exact string.
- `secret` is the shared secret from settings (UTF-8 bytes).
- The server rejects timestamps older than 5 minutes. A `401` means bad signature
  **or** bad timestamp.

## Body types

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

`sim` is the subscription id from the broadcast extras if present, otherwise `null`.

Heartbeat (every 15 minutes):

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

## Responses and app behaviour

| HTTP result | App action |
|---|---|
| 2xx | success, mark SENT |
| 429, 5xx, timeout, no network, TLS/IO error | retryable, keep PENDING, retry with backoff |
| 401, 403 | permanent for now: mark FAILED with error `auth` (hint: check secret and phone clock), stop automatic retries, allow manual resend |
| other 4xx | permanent: mark FAILED with the status code, allow manual resend |

The server de-duplicates by `delivery_id`, so retries and resends are safe. The app
never follows redirects (`setInstanceFollowRedirects(false)`); a 3xx is a permanent
failure.

## Local mock server

`tools/mock_server.js` implements this contract (signature and time-window checks) and
prints only metadata. See the README for usage.
