-- Treffpunkt: Wo die Gruppe gemeinsam schaut.
--
-- Ein Standard je Gruppe (Wohnzimmer, Kneipe), den der Admin setzt, und
-- je Spiel eine Abweichung (Finale beim Nachbarn). Beides nur vom Admin
-- aenderbar, fuer alle lesbar. Die App zeigt: Abweichung, sonst Standard,
-- sonst der Ort aus dem Kalender (meist das Stadion).

alter table public.groups add column if not exists watch_location text;

-- Bisher durfte niemand eine Gruppe aendern - es gab nichts zu aendern.
create policy "gruppe aendern" on public.groups
  for update using (public.is_admin(id)) with check (public.is_admin(id));

create table public.match_locations (
  id          uuid primary key default gen_random_uuid(),
  group_id    uuid not null references public.groups(id) on delete cascade,
  calendar_id uuid not null references public.calendars(id) on delete cascade,
  match_uid   text not null,
  location    text not null,
  updated_at  timestamptz not null default now(),
  unique (group_id, calendar_id, match_uid)
);

create index match_locations_group_idx on public.match_locations (group_id);

alter table public.match_locations enable row level security;

create policy "treffpunkt lesen" on public.match_locations
  for select using (public.is_member(group_id));
create policy "treffpunkt setzen" on public.match_locations
  for insert with check (public.is_admin(group_id));
create policy "treffpunkt aendern" on public.match_locations
  for update using (public.is_admin(group_id)) with check (public.is_admin(group_id));
create policy "treffpunkt entfernen" on public.match_locations
  for delete using (public.is_admin(group_id));
