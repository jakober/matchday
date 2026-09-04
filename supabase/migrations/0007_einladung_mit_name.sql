-- Einladung mit Name: Wer per Mail eingeladen wird, muss sich nicht mehr
-- registrieren. Name und Adresse stehen in der Einladung; beim Annehmen
-- waehlt die Person nur noch ein Passwort. Die Adresse gilt als bestaetigt,
-- weil der Code aus genau dieser Mail stammt.

alter table public.invites
  add column invitee_name text;
