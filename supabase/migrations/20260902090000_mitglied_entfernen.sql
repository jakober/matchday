-- Der Admin kann Mitglieder aus seiner Gruppe entfernen.
--
-- Als Funktion statt als Zugriffsregel, weil zwei Bedingungen zusammenkommen,
-- die sich in einer Regel schlecht ausdruecken lassen: Nur der Admin darf es,
-- und er darf sich nicht selbst entfernen - sonst bliebe eine Gruppe ohne
-- jeden Verantwortlichen zurueck.

create or replace function public.remove_member(p_member_id uuid)
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
