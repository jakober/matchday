-- Gemerkte Fehlschlaege der Wappensuche verwerfen.
--
-- Der DFB-Kalender nennt die Nationalmannschaft "DFB"; dafuer gab es keinen
-- Alias, und der Fehlschlag wurde fuer 90 Tage gespeichert. Mit dem Alias
-- muss der Eintrag weg, sonst bleibt das Wappen bis Dezember leer.

delete from public.team_logos where badge_url is null and source = 'thesportsdb';
