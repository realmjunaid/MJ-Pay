#!/usr/bin/env node
/*
 * Dependency-free mock server for MJ Pay.
 *
 *   SECRET=my-secret PORT=8080 node tools/mock_server.js
 *   SECRET=my-secret FAIL=500 node tools/mock_server.js   // force retryable failure
 *   SECRET=my-secret FAIL=401 node tools/mock_server.js   // force auth failure
 *
 * Verifies X-Signature (HMAC-SHA256 over "<timestamp>.<rawBody>") and the 5-minute
 * timestamp window. Prints only metadata: never the SMS body.
 */
'use strict';

const http = require('http');
const crypto = require('crypto');

const SECRET = process.env.SECRET;
if (!SECRET) {
  console.error('SECRET env var is required');
  process.exit(1);
}

const PORT = Number.parseInt(process.env.PORT || '8080', 10);
const FAIL = process.env.FAIL || '';
const MAX_BODY = 64 * 1024;
const seenDeliveryIds = new Set();

function hmacHex(secret, message) {
  return crypto.createHmac('sha256', secret).update(message, 'utf8').digest('hex');
}

function safeEqual(a, b) {
  const bufA = Buffer.from(String(a));
  const bufB = Buffer.from(String(b));
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

function parseAmount(smsBody) {
  if (typeof smsBody !== 'string') return null;
  const match = /(?:tk|bdt|taka)\.?\s*:?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)/i.exec(smsBody);
  return match ? match[1] : null;
}

function reply(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(body));
}

const server = http.createServer((req, res) => {
  if (req.method !== 'POST') {
    reply(res, 405, { ok: false, error: 'method not allowed' });
    return;
  }

  const chunks = [];
  let size = 0;
  let tooLarge = false;
  req.on('data', (chunk) => {
    size += chunk.length;
    if (size <= MAX_BODY) chunks.push(chunk);
    else tooLarge = true;
  });

  req.on('end', () => {
    if (tooLarge) {
      reply(res, 413, { ok: false, error: 'body too large' });
      return;
    }

    const raw = Buffer.concat(chunks).toString('utf8');
    const timestampHeader = req.headers['x-timestamp'];
    const signature = req.headers['x-signature'];
    const deviceId = req.headers['x-device-id'] || '';
    const nowSeconds = Math.floor(Date.now() / 1000);

    let verified = false;
    let reason = '';

    if (!timestampHeader || !signature) {
      reason = 'missing headers';
    } else if (!/^\d+$/.test(String(timestampHeader))) {
      reason = 'bad timestamp';
    } else if (Math.abs(nowSeconds - Number(timestampHeader)) > 300) {
      reason = 'stale timestamp';
    } else {
      const expected = hmacHex(SECRET, `${timestampHeader}.${raw}`);
      verified = safeEqual(expected, signature);
      if (!verified) reason = 'bad signature';
    }

    let payload = {};
    try {
      payload = JSON.parse(raw);
    } catch (e) {
      reason = reason || 'bad json';
      verified = false;
    }

    const smsBody = payload.type === 'sms' ? payload.body : undefined;
    const deliveryId = payload.delivery_id;
    const duplicate = Boolean(deliveryId && seenDeliveryIds.has(deliveryId));
    if (deliveryId && verified) seenDeliveryIds.add(deliveryId);

    console.log(JSON.stringify({
      time: new Date().toISOString(),
      type: payload.type,
      delivery_id: deliveryId,
      from: payload.from,
      amount: parseAmount(smsBody),
      device: deviceId,
      verified,
      reason: verified ? undefined : reason,
      duplicate,
    }));

    if (!verified) {
      reply(res, 401, { ok: false, error: 'unauthorized' });
      return;
    }
    if (FAIL === '401') {
      reply(res, 401, { ok: false, error: 'forced 401' });
      return;
    }
    if (FAIL === '500') {
      reply(res, 500, { ok: false, error: 'forced 500' });
      return;
    }
    reply(res, 200, { ok: true });
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`MJ Pay mock server listening on http://0.0.0.0:${PORT}${FAIL ? ` (FAIL=${FAIL})` : ''}`);
  console.log('Only metadata is printed; SMS bodies are never logged.');
});
