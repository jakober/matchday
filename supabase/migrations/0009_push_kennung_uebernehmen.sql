-- Push-Kennung einem anderen Konto zuordnen.
--
-- Wer sich auf demselben Geraet ab- und mit einem anderen Konto wieder
-- anmeldet, bringt dieselbe Kennung mit. Die gehoert in device_tokens noch
-- dem alten Mitglied, und die RLS laesst das neue sie weder aendern noch
-- loeschen - der Upsert scheiterte, und die Einstellungen meldeten "nicht
-- erreichbar". Diese Funktion raeumt die alte Zuordnung weg und legt die
-- neue an. Geprueft wird nur, dass der Aufrufer das Mitglied ist, dem die
-- Kennung kuenftig gehoert; eine fremde Kennung zu uebernehmen bringt
-- nichts, weil nur das eigene Geraet sie kennt.

create or replace function public.claim_device_token(
  p_group_id uuid, p_member_id uuid, p_platform text, p_token text
)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if p_member_id is distinct from public.my_member_id(p_group_id) then
    raise exception 'nicht dein Mitglied' using errcode = '42501';
  end if;
  delete from public.device_tokens where token = p_token;
  insert into public.device_tokens (group_id, member_id, platform, token)
  values (p_group_id, p_member_id, p_platform, p_token);
end $$;

grant execute on function public.claim_device_token(uuid, uuid, text, text) to authenticated;
