-- Zwischenspeicher der Wappen einmalig leeren.
--
-- Die erste Fassung der Suche nahm den ersten Teiltreffer: "Deutschland"
-- ergab "Deutschlandsberger", einen oesterreichischen Regionalligisten, und
-- der Fehlgriff wurde als gueltig gespeichert - mit Bild, also ohne erneuten
-- Versuch. Die Suche bewertet jetzt exakte Treffer vor Teiltreffern; was die
-- alte Fassung eingetragen hat, muss weg. Von Hand gesetzte Eintraege
-- ('manual') bleiben.

delete from public.team_logos where source = 'thesportsdb';
