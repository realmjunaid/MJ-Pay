import assert from "node:assert/strict";
import test from "node:test";
import { parseAmountPaisa, parseAmountToCanonical, parseSms, parseTime, normalizeSenderNumber } from "../src/parser.js";
import { hmacSha256Hex, verifySignedRequest } from "../src/verify.js";

test("parses a bKash received SMS the same way the phone does", () => {
  const parsed = parseSms(
    "bKash",
    "You have received Tk 1,250.00 from 01700000000. TrxID ABC123XYZ."
  );
  assert.equal(parsed.sender, "bKash");
  assert.equal(parsed.senderNumber, "01700000000");
  assert.equal(parsed.amount, "1250.00");
  assert.equal(parsed.trxId, "ABC123XYZ");
  assert.equal(parseAmountPaisa("Tk 750.50"), 75050);
});

test("normalizes +880 numbers and rejects non-mobiles", () => {
  assert.equal(normalizeSenderNumber("+8801712345678"), "01712345678");
  assert.equal(normalizeSenderNumber("8801812345678"), "01812345678");
  assert.equal(normalizeSenderNumber("12345"), null);
});

test("amount match input is canonical to the paisa", () => {
  assert.equal(parseAmountToCanonical("1,250.5"), "1250.50");
  assert.equal(parseAmountToCanonical(1250), "1250.00");
  assert.equal(parseAmountToCanonical("12.345"), null);
});

test("accepts unix ms and ISO times", () => {
  assert.equal(parseTime(1760000000000), "2025-10-09T08:53:20.000Z");
  assert.equal(parseTime("2025-10-09T08:53:20.000Z"), "2025-10-09T08:53:20.000Z");
  assert.equal(parseTime("nope"), null);
});

test("HMAC covers timestamp dot raw bytes and rejects a stale clock", async () => {
  const secret = "dev-secret";
  const body = '{"type":"test","ts":1}';
  const timestamp = "1760000000";
  const prefix = new TextEncoder().encode(`${timestamp}.`);
  const raw = new TextEncoder().encode(body);
  const message = new Uint8Array(prefix.length + raw.length);
  message.set(prefix, 0);
  message.set(raw, prefix.length);
  const signature = await hmacSha256Hex(secret, message);

  const headers = new Headers({
    "x-timestamp": timestamp,
    "x-signature": signature,
  });
  assert.equal(await verifySignedRequest(headers, raw, secret, 1760000000), null);
  assert.equal(await verifySignedRequest(headers, raw, secret, 1760000000 + 301), "stale");

  const tampered = new Headers({
    "x-timestamp": timestamp,
    "x-signature": signature.replace(/^./, signature[0] === "a" ? "b" : "a"),
  });
  assert.equal(await verifySignedRequest(tampered, raw, secret, 1760000000), "bad-signature");
});
