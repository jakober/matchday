-- Vollstaendiges Schema von Matchday.
--
-- Diese Datei baut die Datenbank von null auf. Sie ersetzt das Schema, das
-- seinerzeit von Hand im SQL-Editor angelegt wurde und nirgends versioniert
-- war - und die drei Nachtragsmigrationen, die darauf aufsetzten. Ab hier
-- gilt: Jede Aenderung am Schema ist eine Datei in diesem Ordner. Nie wieder
-- von Hand im Editor.
--
-- Leitgedanke: Wer was darf, entscheidet die Datenbank, nicht die App. Eine
-- Pruefung in der Oberflaeche waere eine Attrappe - die App liegt auf fremden
-- Geraeten und laesst sich veraendern.
--
-- Der Laeufer (db-setup) fuehrt jede Datei genau einmal aus und merkt sich
-- das. Diese Datei ist deshalb bewusst NICHT wiederholbar geschrieben: Sie
-- beginnt mit dem Verwerfen des Bestehenden.

-- ===========================================================================
-- 0. Neuanfang
-- ===========================================================================

-- Alte Tabellen samt allem, was daran haengt. Die anonymen Nutzer gleich mit:
-- Ihre Kennungen gehoeren zu Geraeten, nicht zu Menschen, und werden durch
-- echte Konten abgeloest.
drop table if exists
  public.important_matches,
  public.invites,
  public.device_tokens,
  public.rsvps,
  public.calendars,
  public.members,
  public.groups,
  public.app_config
cascade;

drop function if exists public.create_group(text, text, bigint);
drop function if exists public.create_group(text, text, bigint, text);
drop function if exists public.create_invite(uuid, text);
drop function if exists public.join_group(text, text, bigint);
drop function if exists public.remove_member(uuid);
drop function if exists public.is_admin(uuid);
drop function if exists public.is_member(uuid);
drop function if exists public.my_member_id(uuid);

delete from auth.users;

create extension if not exists pgcrypto with schema extensions;

-- ===========================================================================
-- 1. Tabellen
-- ===========================================================================

create table public.groups (
  id          uuid primary key default gen_random_uuid(),
  name        text not null,
  -- Historisch der dauerhafte Beitrittscode. Beitritt laeuft heute ueber
  -- einmalige Einladungen; die Spalte bleibt, bis die App sie nicht mehr liest.
  invite_code text not null unique,
  created_at  timestamptz not null default now()
);

create table public.members (
  id           uuid primary key default gen_random_uuid(),
  group_id     uuid not null references public.groups(id) on delete cascade,
  user_id      uuid not null references auth.users(id) on delete cascade,
  display_name text not null,
  color        bigint not null,
  avatar_url   text,
  -- 'all' sieht den vollen Spielplan, 'important' nur die markierten Spiele -
  -- und wird auch nur zu diesen benachrichtigt.
  scope        text not null default 'all' check (scope in ('all', 'important')),
  created_at   timestamptz not null default now(),
  unique (group_id, user_id)
);

-- Erst jetzt moeglich, weil members auf groups verweist und umgekehrt.
alter table public.groups
  add column admin_member_id uuid references public.members(id) on delete set null;

-- Die Kalender-Abos einer Gruppe. Sie sind die gemeinsame Wahrheit: Die
-- Spiel-Ids auf allen Geraeten leiten sich aus calendars.id ab, damit sich
-- Zusagen geraeteuebergreifend zuordnen lassen.
create table public.calendars (
  id         uuid primary key default gen_random_uuid(),
  group_id   uuid not null references public.groups(id) on delete cascade,
  name       text not null,
  url        text not null,
  color      bigint not null,
  -- Optionales Abzeichen fuer die Kalenderliste. Die Wappen einzelner
  -- Mannschaften kommen nicht von hier, sondern aus team_logos.
  logo_url   text,
  created_by uuid references public.members(id) on delete set null,
  sort_order int not null default 0,
  created_at timestamptz not null default now(),
  -- Ein Kalender nur einmal je Gruppe - auch wenn zwei Geraete ihn
  -- gleichzeitig anlegen wollen.
  unique (group_id, url)
);

create table public.rsvps (
  id          uuid primary key default gen_random_uuid(),
  group_id    uuid not null references public.groups(id) on delete cascade,
  member_id   uuid not null references public.members(id) on delete cascade,
  calendar_id uuid not null references public.calendars(id) on delete cascade,
  match_uid   text not null,
  status      text not null check (status in ('IN', 'OUT')),
  comment     text,
  -- Titel der Begegnung, mitgeschickt statt nachgeschlagen: Die Datenbank
  -- kennt keine Spielplaene, und die Benachrichtigung soll sagen, um welches
  -- Spiel es geht.
  match_title text,
  updated_at  timestamptz not null default now(),
  unique (member_id, calendar_id, match_uid)
);

create index rsvps_group_match_idx
  on public.rsvps (group_id, calendar_id, match_uid);

-- Adresse, an die ein Geraet Benachrichtigungen empfangen kann.
create table public.device_tokens (
  id         uuid primary key default gen_random_uuid(),
  group_id   uuid not null references public.groups(id) on delete cascade,
  member_id  uuid not null references public.members(id) on delete cascade,
  platform   text not null check (platform in ('android', 'ios')),
  token      text not null unique,
  updated_at timestamptz not null default now()
);

create index device_tokens_group_idx on public.device_tokens (group_id);

-- Einmalige Einladungen. Bringen die Sichtbarkeit mit, die der Admin beim
-- Erstellen festlegt.
create table public.invites (
  code       text primary key,
  group_id   uuid not null references public.groups(id) on delete cascade,
  scope      text not null check (scope in ('all', 'important')),
  created_by uuid not null references public.members(id) on delete cascade,
  used_by    uuid references public.members(id) on delete set null,
  used_at    timestamptz,
  created_at timestamptz not null default now()
);

create index invites_group_idx on public.invites (group_id);

-- Vom Admin hervorgehobene Spiele.
create table public.important_matches (
  id          uuid primary key default gen_random_uuid(),
  group_id    uuid not null references public.groups(id) on delete cascade,
  calendar_id uuid not null references public.calendars(id) on delete cascade,
  match_uid   text not null,
  match_title text,
  created_at  timestamptz not null default now(),
  unique (group_id, calendar_id, match_uid)
);

create index important_matches_group_idx on public.important_matches (group_id);

-- ===========================================================================
-- 2. Hilfsfunktionen fuer die Zugriffsregeln
-- ===========================================================================

-- security definer, damit die Pruefung nicht selbst durch RLS laeuft und
-- sich rekursiv aufhaengt.

create function public.is_member(g uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.members m
    where m.group_id = g and m.user_id = auth.uid()
  );
$$;

create function public.is_admin(g uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1
    from public.groups gr
    join public.members m on m.id = gr.admin_member_id
    where gr.id = g and m.user_id = auth.uid()
  );
$$;

create function public.my_member_id(g uuid)
returns uuid language sql stable security definer set search_path = public as $$
  select id from public.members where group_id = g and user_id = auth.uid();
$$;

-- ===========================================================================
-- 3. Zugriffsregeln
-- ===========================================================================

alter table public.groups            enable row level security;
alter table public.members           enable row level security;
alter table public.calendars         enable row level security;
alter table public.rsvps             enable row level security;
alter table public.device_tokens     enable row level security;
alter table public.invites           enable row level security;
alter table public.important_matches enable row level security;

-- Gruppen: lesen duerfen Mitglieder. Angelegt wird nur ueber create_group.
create policy "gruppe lesen" on public.groups
  for select using (public.is_member(id));

-- Mitglieder: alle in der Gruppe sehen einander; jeder aendert nur sich
-- selbst. Aufnahme und Entfernen laufen ueber Funktionen.
create policy "mitglieder lesen" on public.members
  for select using (public.is_member(group_id));
create policy "eigenes profil aendern" on public.members
  for update using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Kalender: lesen duerfen alle Mitglieder. Anlegen, aendern und loeschen darf
-- nur der Admin - er bestimmt, was die Gruppe gemeinsam schaut. Vorher durfte
-- das jedes Mitglied; "nur der Ersteller importiert" waere damit nur in der
-- Oberflaeche wahr gewesen.
create policy "kalender lesen" on public.calendars
  for select using (public.is_member(group_id));
create policy "kalender anlegen" on public.calendars
  for insert with check (public.is_admin(group_id));
create policy "kalender aendern" on public.calendars
  for update using (public.is_admin(group_id)) with check (public.is_admin(group_id));
create policy "kalender loeschen" on public.calendars
  for delete using (public.is_admin(group_id));

-- Zusagen: alle sehen alle, jeder schreibt nur die eigene.
create policy "zusagen lesen" on public.rsvps
  for select using (public.is_member(group_id));
create policy "eigene zusage setzen" on public.rsvps
  for insert with check (member_id = public.my_member_id(group_id));
create policy "eigene zusage aendern" on public.rsvps
  for update using (member_id = public.my_member_id(group_id))
  with check (member_id = public.my_member_id(group_id));
create policy "eigene zusage loeschen" on public.rsvps
  for delete using (member_id = public.my_member_id(group_id));

-- Push-Kennungen: nur die eigene, in jeder Richtung. Die der anderen liest
-- ausschliesslich der Server beim Versand.
create policy "eigenen token lesen" on public.device_tokens
  for select using (member_id = public.my_member_id(group_id));
create policy "eigenen token setzen" on public.device_tokens
  for insert with check (member_id = public.my_member_id(group_id));
create policy "eigenen token aendern" on public.device_tokens
  for update using (member_id = public.my_member_id(group_id))
  with check (member_id = public.my_member_id(group_id));
create policy "eigenen token loeschen" on public.device_tokens
  for delete using (member_id = public.my_member_id(group_id));

-- Einladungen: lesbar fuer Mitglieder. Angelegt nur ueber create_invite,
-- eingeloest nur ueber join_group.
create policy "einladungen lesen" on public.invites
  for select using (public.is_member(group_id));

-- Wichtige Spiele: lesen alle, markieren nur der Admin.
create policy "wichtige spiele lesen" on public.important_matches
  for select using (public.is_member(group_id));
create policy "wichtige spiele markieren" on public.important_matches
  for insert with check (public.is_admin(group_id));
create policy "markierung entfernen" on public.important_matches
  for delete using (public.is_admin(group_id));

-- ===========================================================================
-- 4. Funktionen fuer die App
-- ===========================================================================

-- Legt eine Gruppe an und traegt den Aufrufer als erstes Mitglied und Admin
-- ein. Beides in einem Rutsch, damit keine Gruppe ohne Mitglied entsteht.
create function public.create_group(
  p_group_name text,
  p_display_name text,
  p_color bigint
)
returns table (group_id uuid, invite_code text)
language plpgsql security definer set search_path = public as $$
declare
  v_id uuid;
  v_member uuid;
  v_code text;
  v_try int := 0;
begin
  if auth.uid() is null then
    raise exception 'Nicht angemeldet';
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

  update public.groups set admin_member_id = v_member where id = v_id;

  return query select v_id, v_code;
end;
$$;

-- Erzeugt eine einmalige Einladung. Nur der Admin darf das.
create function public.create_invite(p_group_id uuid, p_scope text)
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

-- Loest eine Einladung ein. Die Einladung bestimmt, was der Neue sieht.
create function public.join_group(
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
  if auth.uid() is null then
    raise exception 'Nicht angemeldet';
  end if;

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

-- Entfernt ein Mitglied. Nur der Admin, und nicht sich selbst - sonst bliebe
-- eine Gruppe ohne Admin zurueck, in der niemand mehr einladen kann.
create function public.remove_member(p_member_id uuid)
returns void
language plpgsql security definer set search_path = public as $$
declare
  v_group uuid;
  v_admin uuid;
begin
  select group_id into v_group from public.members where id = p_member_id;
  if v_group is null then
    raise exception 'Mitglied nicht gefunden';
  end if;
  if not public.is_admin(v_group) then
    raise exception 'Nur der Admin kann Mitglieder entfernen';
  end if;

  select admin_member_id into v_admin from public.groups where id = v_group;
  if p_member_id = v_admin then
    raise exception 'Du kannst dich nicht selbst entfernen';
  end if;

  -- Zusagen, Push-Kennungen und benutzte Einladungen haengen per
  -- Fremdschluessel daran und verschwinden mit.
  delete from public.members where id = p_member_id;
end;
$$;
