/**
 * Authoritative SMS parse for MJ Pay. The phone parser is best-effort only.
 * Amounts are integer paisa so matching never uses binary floats.
 */

const AMOUNT = /(?:^|[^\d])(?:tk|bdt|taka)\.?\s*:?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)/i;
const TRX_ID = /\b(?:trx\s*id|trxid|txn\s*id|txnid)\s*:?\s*([A-Za-z0-9]{4,})/i;
const FROM_NUMBER = /\bfrom\s*:?\s*((?:\+?880|88)?0?1[3-9]\d{8})\b/i;
const ANY_NUMBER = /(?:\+?880|88)?0?1[3-9]\d{8}/g;

export function methodFromSender(sender) {
  const value = String(sender || "").toLowerCase();
  if (value.includes("bkash")) return "bKash";
  if (value.includes("nagad")) return "Nagad";
  return "Other";
}

/** 11-digit BD mobile, or null. */
export function normalizeSenderNumber(value) {
  if (value == null) return null;
  let digits = String(value).replace(/[^\d]/g, "");
  if (digits.startsWith("880")) digits = `0${digits.slice(3)}`;
  if (/^1[3-9]\d{8}$/.test(digits)) digits = `0${digits}`;
  return /^01[3-9]\d{8}$/.test(digits) ? digits : null;
}

export function parseAmountPaisa(body) {
  const match = AMOUNT.exec(String(body || ""));
  if (!match) return null;
  const raw = match[1].replace(/,/g, "");
  if (!/^\d+(\.\d{1,2})?$/.test(raw)) return null;
  const [whole, fraction = ""] = raw.split(".");
  const paisa = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
  if (paisa > 9_999_999_999_99n) return null;
  return Number(paisa);
}

export function formatAmount(paisa) {
  if (paisa == null || !Number.isSafeInteger(paisa) || paisa < 0) return null;
  const whole = Math.floor(paisa / 100);
  const fraction = String(paisa % 100).padStart(2, "0");
  return `${whole}.${fraction}`;
}

export function parseAmountToCanonical(value) {
  if (typeof value === "number") {
    if (!Number.isFinite(value) || value < 0) return null;
    return formatAmount(Math.round(value * 100));
  }
  if (typeof value !== "string") return null;
  const trimmed = value.trim().replace(/,/g, "");
  if (!/^\d+(\.\d{1,2})?$/.test(trimmed)) return null;
  return formatAmount(parseAmountPaisa(`Tk ${trimmed}`));
}

export function parseTrxId(body) {
  const match = TRX_ID.exec(String(body || ""));
  return match ? match[1] : null;
}

export function parseSenderNumber(body) {
  const text = String(body || "");
  const fromMatch = FROM_NUMBER.exec(text);
  if (fromMatch) return normalizeSenderNumber(fromMatch[1]);
  const any = text.match(ANY_NUMBER);
  if (!any) return null;
  return normalizeSenderNumber(any[0]);
}

export function parseSms(sender, body) {
  const amountPaisa = parseAmountPaisa(body);
  return {
    sender: methodFromSender(sender),
    senderNumber: parseSenderNumber(body),
    amount: formatAmount(amountPaisa),
    trxId: parseTrxId(body),
  };
}

/** Unix ms, unix seconds, or ISO-8601. */
export function parseTime(value) {
  if (typeof value === "number" && Number.isFinite(value)) {
    const ms = value < 1e12 ? value * 1000 : value;
    const date = new Date(ms);
    return Number.isNaN(date.getTime()) ? null : date.toISOString();
  }
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (/^\d{10,13}$/.test(trimmed)) return parseTime(Number(trimmed));
    const date = new Date(trimmed);
    return Number.isNaN(date.getTime()) ? null : date.toISOString();
  }
  return null;
}
