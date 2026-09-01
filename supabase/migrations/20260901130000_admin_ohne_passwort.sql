-- Das Admin-Passwort entfaellt wieder.
--
-- Begruendung: Es verhinderte nur, dass Fremde eine EIGENE Gruppe anlegen -
-- und eine fremde Gruppe beruehrt die eigene nicht. Wer einladen und Spiele
-- markieren darf, haengt ohnehin an der Adminrolle, und die ist fest an den
-- Ersteller der Gruppe gebunden. Der Einrichtungsschritt kostete also mehr,
-- als er einbrachte.

drop function if exists public.create_group(text, text, bigint, text);

create or replace function public.create_group(
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

-- Wurde nur fuer den Passwort-Hash gebraucht.
drop table if exists public.app_config;
