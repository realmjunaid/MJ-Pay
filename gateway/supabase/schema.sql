-- MJ Pay Gateway schema.
-- Run in the Supabase SQL editor, or as the first migration.
--
-- Tables are not exposed to the Data API unless service_role is granted
-- explicitly (Supabase default since 2026-10-30). anon and authenticated
-- get no grants and no RLS policies, so the public API cannot read payments.

create extension if not exists pgcrypto;

create table if not exists public.projects (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  api_key_hash text not null unique,
  created_at timestamptz not null default now(),
  constraint projects_name_len check (char_length(name) between 1 and 80),
  constraint projects_key_hash_hex check (api_key_hash ~ '^[0-9a-f]{64}$')
);

create table if not exists public.devices (
  device_id text primary key,
  last_heartbeat_at timestamptz,
  battery integer,
  charging boolean,
  pending integer,
  last_success_at timestamptz,
  battery_opt_ignored boolean,
  constraint devices_id_len check (char_length(device_id) between 1 and 64)
);

create table if not exists public.sms_payments (
  id uuid primary key default gen_random_uuid(),
  delivery_id text not null unique,
  device_id text,
  sender text,
  sender_number text,
  amount numeric(14, 2),
  trx_id text,
  raw_body text not null,
  sim integer,
  claimed_project_id uuid references public.projects (id),
  order_ref text,
  claimed_at timestamptz,
  received_at timestamptz not null,
  created_at timestamptz not null default now(),
  constraint sms_payments_delivery_len check (char_length(delivery_id) between 1 and 80),
  constraint sms_payments_amount_nonneg check (amount is null or amount >= 0),
  constraint sms_payments_claim_pair check (
    (claimed_at is null and claimed_project_id is null and order_ref is null)
    or (claimed_at is not null and claimed_project_id is not null and order_ref is not null)
  )
);

create index if not exists sms_payments_unclaimed_match_idx
  on public.sms_payments (sender_number, amount, received_at)
  where claimed_at is null;

create unique index if not exists sms_payments_project_order_uidx
  on public.sms_payments (claimed_project_id, order_ref)
  where order_ref is not null;

alter table public.projects enable row level security;
alter table public.devices enable row level security;
alter table public.sms_payments enable row level security;

revoke all on table public.projects from public, anon, authenticated;
revoke all on table public.devices from public, anon, authenticated;
revoke all on table public.sms_payments from public, anon, authenticated;

grant select, insert, update, delete on table public.projects to service_role;
grant select, insert, update, delete on table public.devices to service_role;
grant select, insert, update, delete on table public.sms_payments to service_role;

-- Atomic claim. Same project + order_ref is idempotent.
-- Concurrent claims of one payment are serialized with FOR UPDATE SKIP LOCKED.
create or replace function public.claim_sms_payment(
  p_project_id uuid,
  p_sender_number text,
  p_amount numeric,
  p_since timestamptz,
  p_until timestamptz,
  p_order_ref text
) returns jsonb
language plpgsql
security invoker
set search_path = public
as $$
declare
  existing public.sms_payments%rowtype;
  target public.sms_payments%rowtype;
begin
  if p_order_ref is null or length(btrim(p_order_ref)) = 0 or length(p_order_ref) > 128 then
    return jsonb_build_object('status', 'invalid', 'error', 'order_ref required');
  end if;

  perform pg_advisory_xact_lock(hashtext(p_project_id::text), hashtext(p_order_ref));

  select * into existing
  from public.sms_payments
  where claimed_project_id = p_project_id
    and order_ref = p_order_ref
  limit 1;

  if found then
    return jsonb_build_object(
      'status', 'matched',
      'delivery_id', existing.delivery_id,
      'sender', existing.sender,
      'sender_number', existing.sender_number,
      'amount', existing.amount,
      'trx_id', existing.trx_id,
      'received_at', existing.received_at,
      'order_ref', existing.order_ref,
      'claimed_at', existing.claimed_at,
      'idempotent', true
    );
  end if;

  select * into target
  from public.sms_payments
  where claimed_at is null
    and sender_number = p_sender_number
    and amount = p_amount
    and received_at >= p_since
    and received_at <= p_until
  order by received_at
  for update skip locked
  limit 1;

  if not found then
    return jsonb_build_object('status', 'not_found');
  end if;

  update public.sms_payments
  set claimed_project_id = p_project_id,
      order_ref = p_order_ref,
      claimed_at = now()
  where id = target.id
    and claimed_at is null
  returning * into target;

  if not found then
    return jsonb_build_object('status', 'not_found');
  end if;

  return jsonb_build_object(
    'status', 'matched',
    'delivery_id', target.delivery_id,
    'sender', target.sender,
    'sender_number', target.sender_number,
    'amount', target.amount,
    'trx_id', target.trx_id,
    'received_at', target.received_at,
    'order_ref', target.order_ref,
    'claimed_at', target.claimed_at,
    'idempotent', false
  );
exception
  when unique_violation then
    select * into existing
    from public.sms_payments
    where claimed_project_id = p_project_id
      and order_ref = p_order_ref
    limit 1;
    if found then
      return jsonb_build_object(
        'status', 'matched',
        'delivery_id', existing.delivery_id,
        'sender', existing.sender,
        'sender_number', existing.sender_number,
        'amount', existing.amount,
        'trx_id', existing.trx_id,
        'received_at', existing.received_at,
        'order_ref', existing.order_ref,
        'claimed_at', existing.claimed_at,
        'idempotent', true
      );
    end if;
    return jsonb_build_object('status', 'not_found');
end;
$$;

revoke all on function public.claim_sms_payment(uuid, text, numeric, timestamptz, timestamptz, text)
  from public, anon, authenticated;
grant execute on function public.claim_sms_payment(uuid, text, numeric, timestamptz, timestamptz, text)
  to service_role;
