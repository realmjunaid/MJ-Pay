# Real SMS samples (masked)

Paste real, **masked** samples below. Never commit real phone numbers, balances,
transaction ids or personal names. Replace digits with `X`.

These samples are documentation only; the app does not read from this file. When you
add a sample, add a matching unit test in
`app/src/test/java/me/mjunaid/mjpay/sms/SmsFilterTest.kt`.

## bKash — payment received

```
<paste masked bKash received SMS>
```

## bKash — OTP (must be dropped)

```
<paste masked bKash OTP SMS>
```

## Nagad — payment received

```
<paste masked Nagad received SMS>
```

## Nagad — OTP (must be dropped)

```
<paste masked Nagad OTP SMS>
```

## Notes

- The app only accepts senders in the configured allowlist.
- A message must contain `received` and an amount like `Tk 1,250.00` to be forwarded.
- Any message containing `otp`, `pin`, `verification code`, `password`, `one time` or
  `one-time` is dropped and never stored.
