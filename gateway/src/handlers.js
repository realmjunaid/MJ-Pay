import { createClient } from "@supabase/supabase-js";
import { parseSms, parseTime, parseAmountToCanonical, normalizeSenderNumber, normalizeTrxId } from "./parser.js";
import { readRawBody, sha256Hex, verifySignedRequest } from "./verify.js";

const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };

export function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}

export function clientFor(env) {
  return createClient(env.SUPABASE_URL, env.SUPABASE_SERVICE_ROLE_KEY, {
    auth: {
      persistSession: false,
      autoRefreshToken: false,
      detectSessionInUrl: false,
    },
    global: { fetch },
  });
}

function deviceId(request) {
  const value = (request.headers.get("x-device-id") || "").trim();
  if (!value || value.length > 64) return null;
  return value;
}

export async function handleSmsWebhook(request, env) {
  if (!env.SMS_WEBHOOK_SECRET || !env.SUPABASE_URL || !env.SUPABASE_SERVICE_ROLE_KEY) {
    console.log(JSON.stringify({ event: "webhook", error: "missing-config" }));
    return json({ ok: false, error: "server misconfigured" }, 500);
  }

  const raw = await readRawBody(request);
  if (!raw.ok) return json({ ok: false, error: raw.error }, raw.status);

  const reason = await verifySignedRequest(request.headers, raw.bytes, env.SMS_WEBHOOK_SECRET);
  if (reason) {
    console.log(JSON.stringify({ event: "webhook", verified: false, reason }));
    return json({ ok: false, error: "unauthorized" }, 401);
  }

  let payload;
  try {
    payload = JSON.parse(raw.text);
  } catch (e) {
    return json({ ok: false, error: "bad json" }, 400);
  }
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    return json({ ok: false, error: "bad json" }, 400);
  }

  const type = payload.type;
  const device = deviceId(request);

  if (type === "test") {
    console.log(JSON.stringify({ event: "webhook", type: "test", device, verified: true }));
    return json({ ok: true });
  }

  if (type === "heartbeat") {
    return handleHeartbeat(env, payload, device);
  }

  if (type === "sms") {
    return handleSms(env, payload, device);
  }

  return json({ ok: false, error: "unknown type" }, 400);
}

async function handleHeartbeat(env, payload, device) {
  console.log(JSON.stringify({
    event: "webhook",
    type: "heartbeat",
    device,
    verified: true,
    pending: Number.isInteger(payload.pending) ? payload.pending : null,
  }));

  if (!device) return json({ ok: true });

  const row = {
    device_id: device,
    last_heartbeat_at: new Date().toISOString(),
    battery: Number.isInteger(payload.battery) ? payload.battery : null,
    charging: typeof payload.charging === "boolean" ? payload.charging : null,
    pending: Number.isInteger(payload.pending) ? payload.pending : null,
    last_success_at: payload.last_success_at > 0 ? new Date(payload.last_success_at).toISOString() : null,
    battery_opt_ignored: typeof payload.battery_opt_ignored === "boolean" ? payload.battery_opt_ignored : null,
  };

  const { error } = await clientFor(env).from("devices").upsert(row, { onConflict: "device_id" });
  if (error) {
    console.log(JSON.stringify({ event: "heartbeat", error: error.code || "db" }));
    return json({ ok: false, error: "store failed" }, 500);
  }
  return json({ ok: true });
}

async function handleSms(env, payload, device) {
  const deliveryId = typeof payload.delivery_id === "string" ? payload.delivery_id.trim() : "";
  const body = typeof payload.body === "string" ? payload.body : "";
  const from = typeof payload.from === "string" ? payload.from.trim() : "";
  const receivedAt = Number.isFinite(payload.received_at) ? new Date(payload.received_at) : null;

  if (!deliveryId || deliveryId.length > 80 || !body || !receivedAt || Number.isNaN(receivedAt.getTime())) {
    return json({ ok: false, error: "invalid sms" }, 400);
  }

  const parsed = parseSms(from, body);
  const row = {
    delivery_id: deliveryId,
    device_id: device,
    sender: parsed.sender === "Other" && from ? from.slice(0, 64) : parsed.sender,
    sender_number: parsed.senderNumber,
    amount: parsed.amount,
    trx_id: parsed.trxId,
    raw_body: body,
    sim: Number.isInteger(payload.sim) ? payload.sim : null,
    received_at: receivedAt.toISOString(),
  };

  const { error } = await clientFor(env)
    .from("sms_payments")
    .upsert(row, { onConflict: "delivery_id", ignoreDuplicates: true });

  if (error && error.code !== "23505") {
    console.log(JSON.stringify({ event: "sms", delivery_id: deliveryId, error: error.code || "db" }));
    return json({ ok: false, error: "store failed" }, 500);
  }

  console.log(JSON.stringify({
    event: "sms",
    delivery_id: deliveryId,
    device,
    sender: row.sender,
    amount: parsed.amount,
    duplicate: error?.code === "23505",
    verified: true,
  }));
  return json({ ok: true });
}

export async function handleMatch(request, env) {
  if (!env.SUPABASE_URL || !env.SUPABASE_SERVICE_ROLE_KEY) {
    return json({ status: "error", error: "server misconfigured" }, 500);
  }

  const projectKey = (request.headers.get("x-project-key") || "").trim();
  if (!projectKey || projectKey.length > 256) {
    return json({ status: "unauthorized" }, 401);
  }

  let body;
  try {
    body = await request.json();
  } catch (e) {
    return json({ status: "invalid", error: "bad json" }, 400);
  }

  const senderNumber = normalizeSenderNumber(body?.sender_number);
  const trxId = normalizeTrxId(body?.trx_id);
  const matchBy = resolveMatchBy(body?.match_by, senderNumber, trxId);
  const amount = parseAmountToCanonical(body?.amount);
  const since = parseTime(body?.since);
  const until = parseTime(body?.until);
  const orderRef = typeof body?.order_ref === "string" ? body.order_ref.trim() : "";

  if (!matchBy || !amount || !since || !until || !orderRef || orderRef.length > 128) {
    return json({
      status: "invalid",
      error: "amount, since, until, order_ref and a valid match_by are required",
    }, 400);
  }
  if ((matchBy === "phone" || matchBy === "both") && !senderNumber) {
    return json({ status: "invalid", error: `sender_number is required for match_by ${matchBy}` }, 400);
  }
  if ((matchBy === "trx" || matchBy === "both") && !trxId) {
    return json({ status: "invalid", error: `trx_id is required for match_by ${matchBy}` }, 400);
  }
  if (since > until) {
    return json({ status: "invalid", error: "since is after until" }, 400);
  }

  const supabase = clientFor(env);
  const { data: project, error: projectError } = await supabase
    .from("projects")
    .select("id, api_key_hash")
    .eq("api_key_hash", await sha256Hex(projectKey))
    .maybeSingle();

  if (projectError) {
    console.log(JSON.stringify({ event: "match", error: projectError.code || "db" }));
    return json({ status: "error", error: "lookup failed" }, 500);
  }
  if (!project) return json({ status: "unauthorized" }, 401);

  const { data, error } = await supabase.rpc("claim_sms_payment", {
    p_project_id: project.id,
    p_sender_number: senderNumber,
    p_amount: amount,
    p_since: since,
    p_until: until,
    p_order_ref: orderRef,
    p_trx_id: trxId,
    p_match_by: matchBy,
  });

  if (error) {
    console.log(JSON.stringify({ event: "match", project: project.id, error: error.code || "rpc" }));
    return json({ status: "error", error: "match failed" }, 500);
  }

  const result = data && typeof data === "object" ? data : { status: "not_found" };
  console.log(JSON.stringify({
    event: "match",
    project: project.id,
    match_by: matchBy,
    status: result.status || "unknown",
  }));

  if (result.status === "invalid") return json(result, 400);
  return json({ ...result, match_by: matchBy });
}

/**
 * phone = sender number only, trx = transaction ID only, both = the same SMS
 * must match both. When match_by is omitted it is inferred from what was sent,
 * so older callers that only send sender_number keep working.
 */
function resolveMatchBy(requested, senderNumber, trxId) {
  const value = typeof requested === "string" ? requested.trim().toLowerCase() : "";
  if (value) {
    return value === "phone" || value === "trx" || value === "both" ? value : null;
  }
  if (senderNumber && trxId) return "both";
  if (trxId) return "trx";
  return "phone";
}

