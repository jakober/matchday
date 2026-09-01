-- Admin-Rolle, Einladungen mit Sichtbarkeit und wichtige Spiele.
--
-- Leitgedanke: Wer was darf, entscheidet die Datenbank, nicht die App. Eine
-- Pruefung in der Oberflaeche waere eine Attrappe - die App liegt auf fremden
-- Geraeten und laesst sich veraendern.

-- ===========================================================================
-- 1. Admin-Passwort
-- ===========================================================================

create extension if not exists pgcrypto with schema extensions;

-- Serverseitige Einstellungen. Bewusst ohne jede Zugriffsregel: Aus der App
-- ist die Tabelle unsichtbar, nur die Funktionen weiter unten lesen daraus.
create table if not exists public.app_config (
  key text primary key,
  value text not null
);

alter table public.app_config enable row level security;

-- Das Passwort selbst wird hier NICHT gesetzt. Es steht in einer eigenen,
-- nicht eingecheckten Migration - siehe README. Ein Klartextpasswort in einer
-- versionierten Datei bliebe fuer immer in der Git-Historie, auch nach dem
-- Loeschen.

-- ===========================================================================
-- 2. Sichtbarkeit je Mitglied, Admin je Gruppe
-- ===========================================================================

-- 'all' sieht den vollen Spielplan, 'important' nur die markierten Spiele -
-- und wird auch nur zu diesen benachrichtigt.
alter table public.members
  add column if not exists scope text not null default 'all';

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'members_scope_check'
  ) then
    alter table public.members
      add constraint members_scope_check check (scope in ('all', 'important'));
  end if;
end $$;

alter table public.groups
  add column if not exists admin_member_id uuid
  references public.members(id) on delete set null;

-- Bestehende Gruppen: Das aelteste Mitglied ist der Ersteller und damit Admin.
update public.groups g
set admin_member_id = (
  select m.id from public.members m
  where m.group_id = g.id
  order by m.created_at
  limit 1
)
where g.admin_member_id is null;

-- security definer, damit die Pruefung nicht selbst durch RLS laeuft und
-- sich rekursiv aufhaengt.
create or replace function public.is_admin(g uuid)
returns boolean language sql security definer set search_path = public stable as $$
  select exists (
    select 1
    from public.groups gr
    join public.members m on m.id = gr.admin_member_id
    where gr.id = g and m.user_id = auth.uid()
  );
$$;

-- ===========================================================================
-- 3. Einladungen
-- ===========================================================================

-- Loest den dauerhaften Gruppencode ab: Eine Einladung gilt einmal und bringt
-- die Sichtbarkeit mit, die der Admin beim Erstellen festlegt.
create table if not exists public.invites (
  code text primary key,
  group_id uuid not null references public.groups(id) on delete cascade,
  scope text not null check (scope in ('all', 'important')),
  created_by uuid not null references public.members(id) on delete cascade,
  used_by uuid references public.members(id) on delete set null,
  used_at timestamptz,
  created_at timestamptz not null default now()
);

create index if not exists invites_group_idx on public.invites (group_id);

alter table public.invites enable row level security;

drop policy if exists "einladungen lesen" on public.invites;
create policy "einladungen lesen" on public.invites
  for select using (public.is_member(group_id));

-- Angelegt wird nur ueber create_invite; die Funktion prueft die Adminrolle.

-- ===========================================================================
-- 4. Wichtige Spiele
-- ===========================================================================

create table if not exists public.important_matches (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.groups(id) on delete cascade,
  calendar_id uuid not null references public.calendars(id) on delete cascade,
  match_uid text not null,
  match_title text,
  created_at timestamptz not null default now(),
  unique (group_id, calendar_id, match_uid)
);

create index if not exists important_matches_group_idx
  on public.important_matches (group_id);

alter table public.important_matches enable row level security;

drop policy if exists "wichtige spiele lesen" on public.important_matches;
create policy "wichtige spiele lesen" on public.important_matches
  for select using (public.is_member(group_id));

drop policy if exists "wichtige spiele markieren" on public.important_matches;
create policy "wichtige spiele markieren" on public.important_matches
  for insert with check (public.is_admin(group_id));

drop policy if exists "markierung entfernen" on public.important_matches;
create policy "markierung entfernen" on public.important_matches
  for delete using (public.is_admin(group_id));

-- ===========================================================================
-- 5. Funktionen
-- ===========================================================================

-- Die alte Fassung ohne Passwort faellt weg, sonst blieben beide nebeneinander
-- bestehen und die ungeschuetzte waere weiter aufrufbar.
drop function if exists public.create_group(text, text, bigint);

create or replace function public.create_group(
  p_group_name text,
  p_display_name text,
  p_color bigint,
  p_password text
)
returns table (group_id uuid, invite_code text)
language plpgsql security definer set search_path = public, extensions as $$
declare
  v_id uuid;
  v_member uuid;
  v_code text;
  v_hash text;
  v_try int := 0;
begin
  select value into v_hash from public.app_config where key = 'admin_password';
  if v_hash is null or v_hash <> extensions.crypt(p_password, v_hash) then
    raise exception 'Falsches Admin-Passwort';
  end if;

  loop
    v_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6));
    exit when not exists (select 1 from public.groups g where g.invite_code = v_code);
    v_try := v_try + 1;
    if v_try > 10 then raise exception 'Kein freier Code gefunden'; end if;
  end loop;

  insert into public.groups (name, invite_code)
    values (p_group_name, v_code)
    returning id into v_id;

  insert into public.members (group_id, user_id, display_name, color, scope)
    values (v_id, auth.uid(), p_display_name, p_color, 'all')
    returning id into v_member;

  -- Wer die Gruppe anlegt, ist ihr Admin.
  update public.groups set admin_member_id = v_member where id = v_id;

  return query select v_id, v_code;
end;
$$;

create or replace function public.create_invite(p_group_id uuid, p_scope text)
returns text
language plpgsql security definer set search_path = public as $$
declare
  v_code text;
  v_admin uuid;
  v_try int := 0;
begin
  if not public.is_admin(p_group_id) then
    raise exception 'Nur der Admin kann einladen';
  end if;
  if p_scope not in ('all', 'important') then
    raise exception 'Unbekannte Sichtbarkeit';
  end if;

  select id into v_admin from public.members
    where group_id = p_group_id and user_id = auth.uid();

  loop
    v_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6));
    exit when not exists (select 1 from public.invites i where i.code = v_code);
    v_try := v_try + 1;
    if v_try > 10 then raise exception 'Kein freier Code gefunden'; end if;
  end loop;

  insert into public.invites (code, group_id, scope, created_by)
    values (v_code, p_group_id, p_scope, v_admin);

  return v_code;
end;
$$;

-- Beitritt nur noch ueber eine Einladung; der dauerhafte Gruppencode zieht
-- nicht mehr. Die Einladung bestimmt zugleich, was der Neue zu sehen bekommt.
create or replace function public.join_group(
  p_code text,
  p_display_name text,
  p_color bigint
)
returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_invite public.invites%rowtype;
  v_member uuid;
begin
  select * into v_invite from public.invites
    where code = upper(trim(p_code))
    for update;

  if v_invite.code is null then
    raise exception 'Ungültiger Einladungscode';
  end if;
  if v_invite.used_by is not null then
    raise exception 'Diese Einladung wurde bereits verwendet';
  end if;

  insert into public.members (group_id, user_id, display_name, color, scope)
    values (v_invite.group_id, auth.uid(), p_display_name, p_color, v_invite.scope)
    on conflict (group_id, user_id) do update
      set display_name = excluded.display_name,
          color = excluded.color,
          scope = excluded.scope
    returning id into v_member;

  update public.invites
    set used_by = v_member, used_at = now()
    where code = v_invite.code;

  return v_invite.group_id;
end;
$$;
