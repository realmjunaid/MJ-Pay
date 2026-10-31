# MJ Pay Gateway — সেটআপ

ফোনের bKash/Nagad SMS এখানে আসবে। Cloudflare Worker সিগনেচার চেক করে Supabase-এ রাখবে। Telegram বট বা ওয়েবসাইটের ব্যাকএন্ড একবার ম্যাচ করে পেমেন্ট ক্লেম করবে।

Worker-এর ফোল্ডার এই `gateway/`। Android অ্যাপের রুট থেকে ডিপ্লয় করবেন না।

---

## যা লাগবে

1. একটি Supabase প্রজেক্ট (বিনামূল্যের প্ল্যান চলবে)
2. একটি Cloudflare অ্যাকাউন্ট
3. এই রিপো GitHub-এ (ইতিমধ্যে আছে)
4. ফোনের অ্যাপে যে সিক্রেট দেবেন, ঠিক সেই সিক্রেট

---

## ধাপ ১ — Supabase-এ টেবিল বানান

1. [supabase.com](https://supabase.com) → **New project**।
2. প্রজেক্ট খুললে বাম পাশে **SQL Editor** → **New query**।
3. এই ফাইলের সব SQL কপি করে রান করুন: [`supabase/schema.sql`](supabase/schema.sql)।
4. নিচে `Success` দেখলে টেবিল তৈরি হয়েছে: `projects`, `sms_payments`, `devices`।

এতে পাবলিক API থেকে টেবিল পড়া বন্ধ থাকে। শুধু Worker-এর service role key লিখতে পারে।

---

## ধাপ ২ — বটের জন্য একটি কী বানান

কী নিজে রাখুন। ডাটাবেসে শুধু তার হ্যাশ যাবে।

PowerShell-এ (কীটা নিজের লম্বা র্যান্ডম স্ট্রিং দিন):

```powershell
node --input-type=module -e "const k=process.argv[1]; const d=await crypto.subtle.digest('SHA-256', new TextEncoder().encode(k)); console.log([...new Uint8Array(d)].map(b=>b.toString(16).padStart(2,'0')).join(''))" "এখানে-লম্বা-র্যান্ডম-কী"
```

যে ৬৪ অক্ষরের হ্যাশ আসবে, SQL Editor-এ দিন:

```sql
insert into public.projects (name, api_key_hash)
values ('shop', 'এখানে-৬৪-অক্ষরের-হ্যাশ');
```

কাঁচা কী Telegram বট বা সাইটের সার্ভারে রাখুন। ব্রাউজারের জাভাস্ক্রিপ্টে রাখবেন না।

---

## ধাপ ৩ — Cloudflare-এ GitHub থেকে ডিপ্লয়

1. [dash.cloudflare.com](https://dash.cloudflare.com) → **Workers & Pages** → **Create** → **Import a repository**।
2. GitHub রিপো `realmjunaid/MJ-Pay` সিলেক্ট করুন।
3. **Root directory** অবশ্যই `gateway` দিন। খালি রাখলে বিল্ড ফেল করবে।
4. Build command: `npm install`
5. Deploy command: `npx wrangler deploy`
6. Save and Deploy।

প্রথম ডিপ্লয় সিক্রেট ছাড়া উঠতে পারে। পরের ধাপে সিক্রেট দিলে আবার ডিপ্লয় হবে।

নিজের পিসি থেকেও ডিপ্লয় করা যায়:

```powershell
cd gateway
npm install
npx wrangler login
npx wrangler deploy
```

---

## ধাপ ৪ — ডিপ্লয় সফল হওয়ার পর তিনটি সিক্রেট দিন

প্রথম ডিপ্লয় সিক্রেট ছাড়াই উঠবে। তারপর Cloudflare → **mj-pay** → **Settings** → **Variables and Secrets** → **Add** → **Encrypt** চেক করে এই তিনটি দিন। সেভ করলে Worker আবার ডিপ্লয় হবে।

অথবা পাওয়ারশেল:

```powershell
cd gateway
npx wrangler secret put SMS_WEBHOOK_SECRET
npx wrangler secret put SUPABASE_URL
npx wrangler secret put SUPABASE_SERVICE_ROLE_KEY
```

| নাম | কী দেবেন |
|---|---|
| `SMS_WEBHOOK_SECRET` | ফোনের অ্যাপে যে শেয়ার্ড সিক্রেট দেবেন, হুবহু সেটা |
| `SUPABASE_URL` | `https://xxxx.supabase.co` (Supabase → Project Settings → API) |
| `SUPABASE_SERVICE_ROLE_KEY` | সেই পেজের **service_role** কী। `anon` কী নয় |

`service_role` কী কখনো ফোন, বট ক্লায়েন্ট, বা ওয়েবসাইটের ফ্রন্টএন্ডে দেবেন না।

লোকাল টেস্টের জন্য `.dev.vars.example` কপি করে `.dev.vars` বানান এবং একই তিনটি মান বসান। `.dev.vars` git-এ যাবে না।

---

## ধাপ ৫ — ফোনের অ্যাপ সেট করুন

অ্যাপ → Settings:

1. **Webhook URL:** `https://mj-pay.<আপনার-সাবডোমেইন>.workers.dev/sms-webhook`  
   শেষে `/sms-webhook` থাকতেই হবে। `https` ছাড়া রিলিজ অ্যাপ পাঠাবে না।
2. **Shared secret:** ধাপ ৪-এর `SMS_WEBHOOK_SECRET`।
3. **Send test** চাপুন। সফল হলে Supabase-এ কিছু লেখা হবে না; এটা শুধু সিগনেচার চেক।
4. এরপর একটি টেস্ট পেমেন্ট SMS আসলে `sms_payments` টেবিলে একটা রো দেখা যাবে।

ফোন ভুল ঘড়িতে থাকলে `401` আসবে। ফোনের তারিখ-সময় অটো রাখুন।

---

## ধাপ ৬ — বট বা সাইট থেকে ম্যাচ

একই নম্বর + ঠিক একই টাকা + সময়ের ভিতরে যে SMS আছে, সেটা একবার ক্লেম হবে।

```http
POST /v1/match
X-Project-Key: ধাপ-২-এর-কাঁচা-কী
Content-Type: application/json

{
  "sender_number": "01700000000",
  "amount": "1250.00",
  "since": 1760000000000,
  "until": 1760003600000,
  "order_ref": "order-1842"
}
```

- মিললে: `{ "status": "matched", ... }`
- না মিললে: `{ "status": "not_found" }`
- একই `order_ref` আবার পাঠালে আগের ক্লেমই ফেরে, দ্বিতীয় SMS কেড়ে নেয় না।

`since` / `until` মিলিসেকেন্ড, সেকেন্ড, অথবা ISO সময় হতে পারে। `amount` স্ট্রিং বা সংখ্যা — পয়সায় মিলবে, ফ্লোট দিয়ে নয়।

কলটা বটের সার্ভার থেকে করুন, ব্রাউজার থেকে নয়।

---

## যা যা হয়

| ফোন যা পাঠায় | সার্ভার |
|---|---|
| `type: "sms"` | `sms_payments`-এ সেভ। একই `delivery_id` আবার আসলে উপেক্ষা। ঠিক সই থাকলে `200 {"ok":true}` |
| `type: "heartbeat"` | `devices` আপডেট। `200` |
| `type: "test"` | ডাটাবেসে লেখে না। `200` |

খারাপ সিগনেচার বা ৫ মিনিটের বেশি পুরনো সময় = `401`। ডাটাবেস ডাউন = `500`, ফোন আবার চেষ্টা করে।

লগে SMS-এর লেখা, সিক্রেট, বা সিগনেচার থাকে না।

ম্যাচের উত্তরেও SMS-এর পুরো লেখা থাকে না। থাকে নম্বর, টাকা, trx id, আর `order_ref`।

---

## লোকাল চেক

```powershell
cd gateway
npm install
npm test
```

`npm test` পার্সার আর HMAC চেক করে। Supabase লাগে না।

---

## ফোল্ডার

```
gateway/
├── src/index.js       # রাউট
├── src/verify.js      # HMAC + সময়
├── src/parser.js      # bKash / Nagad পার্স
├── src/handlers.js    # /sms-webhook এবং /v1/match
├── supabase/schema.sql
├── package.json
└── wrangler.toml
```
