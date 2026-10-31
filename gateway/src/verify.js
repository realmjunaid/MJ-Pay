/** Five-minute window, matching the Android app contract. */
export const TIMESTAMP_WINDOW_SECONDS = 300;

/** Hard cap so a signed body cannot exhaust Worker memory. */
export const MAX_BODY_BYTES = 64 * 1024;

const encoder = new TextEncoder();

/**
 * Read the exact request bytes, capped. HMAC must cover these bytes, not a
 * re-serialized JSON object.
 */
export async function readRawBody(request, maxBytes = MAX_BODY_BYTES) {
  if (!request.body) return { ok: false, status: 400, error: "empty body" };

  const reader = request.body.getReader();
  const chunks = [];
  let size = 0;

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > maxBytes) {
        await reader.cancel();
        return { ok: false, status: 413, error: "body too large" };
      }
      chunks.push(value);
    }
  } catch (e) {
    return { ok: false, status: 400, error: "unreadable body" };
  }

  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return { ok: true, bytes, text: new TextDecoder("utf-8", { fatal: false }).decode(bytes) };
}

/** SHA-256 both sides, then compare, so length never short-circuits. */
export async function timingSafeEqualText(left, right) {
  const [leftHash, rightHash] = await Promise.all([
    crypto.subtle.digest("SHA-256", encoder.encode(String(left))),
    crypto.subtle.digest("SHA-256", encoder.encode(String(right))),
  ]);
  if (typeof crypto.subtle.timingSafeEqual === "function") {
    return crypto.subtle.timingSafeEqual(leftHash, rightHash);
  }
  const a = new Uint8Array(leftHash);
  const b = new Uint8Array(rightHash);
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}

export async function hmacSha256Hex(secret, messageBytes) {
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", key, messageBytes);
  const bytes = new Uint8Array(signature);
  let hex = "";
  for (const byte of bytes) hex += byte.toString(16).padStart(2, "0");
  return hex;
}

export async function sha256Hex(text) {
  const digest = await crypto.subtle.digest("SHA-256", encoder.encode(text));
  let hex = "";
  for (const byte of new Uint8Array(digest)) hex += byte.toString(16).padStart(2, "0");
  return hex;
}

/**
 * Contract: HMAC-SHA256(secret, X-Timestamp + "." + rawBodyBytes), lowercase hex.
 * Returns a reason string, or null when the request is fresh and signed.
 */
export async function verifySignedRequest(headers, rawBytes, secret, nowSeconds = Math.floor(Date.now() / 1000)) {
  const timestamp = headers.get("x-timestamp") || "";
  const provided = (headers.get("x-signature") || "").trim().toLowerCase();

  if (!/^\d{1,12}$/.test(timestamp) || !/^[0-9a-f]{64}$/.test(provided)) {
    return "unauthorized";
  }

  const skew = Math.abs(nowSeconds - Number(timestamp));
  if (skew > TIMESTAMP_WINDOW_SECONDS) return "stale";

  const prefix = encoder.encode(`${timestamp}.`);
  const message = new Uint8Array(prefix.length + rawBytes.length);
  message.set(prefix, 0);
  message.set(rawBytes, prefix.length);

  const expected = await hmacSha256Hex(secret, message);
  const matches = await timingSafeEqualText(expected, provided);
  return matches ? null : "bad-signature";
}
