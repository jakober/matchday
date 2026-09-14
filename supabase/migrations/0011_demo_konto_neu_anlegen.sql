-- Demo-Gruppe fuer das Pruefkonto der Stores anlegen.
--
-- Die Pruefer von Apple und Google probieren "Konto loeschen" gern mit
-- dem Demo-Konto aus - und beim naechsten Update scheitert dann ihre
-- Anmeldung. Die Function account-delete legt das Konto deshalb sofort
-- wieder an und ruft diese Funktion, die die Gruppe dazu baut. Nur der
-- Server darf sie aufrufen.

create or replace function public.seed_demo_group(p_user uuid)
returns void
language plpgsql security definer set search_path = public as $$
declare
  v_group uuid;
  v_member uuid;
  v_code text := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6));
begin
  insert into public.groups (name, invite_code, watch_location)
    values ('Demo-Gruppe', v_code, 'Bei Max im Wohnzimmer')
    returning id into v_group;

  insert into public.members (group_id, user_id, display_name, color, scope, locale)
    values (v_group, p_user, 'Demo', 4281852538, 'all', 'de')
    returning id into v_member;

  update public.groups set admin_member_id = v_member where id = v_group;

  insert into public.calendars (group_id, name, url, color, created_by, sort_order) values
    (v_group, 'Bundesliga', 'https://i.cal.to/ical/2699/bundesliga/bundesliga-gesamtspielplan/12345.12345-54321.ics', 4292609325, v_member, 0),
    (v_group, 'Premier League', 'https://ics.fixtur.es/v2/league/premier-league.ics', 4282362357, v_member, 1);
end $$;

revoke all on function public.seed_demo_group(uuid) from public, anon, authenticated;
grant execute on function public.seed_demo_group(uuid) to service_role;
