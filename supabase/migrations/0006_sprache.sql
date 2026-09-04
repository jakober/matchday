-- Sprache je Mitglied, fuer Texte, die der Server schreibt.
--
-- Push-Meldungen und Einladungsmails entstehen auf dem Server, der die
-- Geraetesprache nicht kennt. Die App traegt sie deshalb beim Start ein
-- (eigene Zeile, erlaubt durch "eigenes profil aendern"). 'de' als
-- Voreinstellung, weil die Nutzer Deutsch lesen.

alter table public.members
  add column locale text not null default 'de' check (locale in ('de', 'en'));
