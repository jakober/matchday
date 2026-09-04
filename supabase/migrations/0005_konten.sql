-- Umstieg auf echte Konten: Neuanfang bei null.
--
-- Alle bisherigen Nutzer waren anonyme Kennungen von Geraeten, nicht
-- Menschen. Mit ihnen gehen Gruppen, Mitgliedschaften und Zusagen
-- (Fremdschluessel mit on delete cascade). Das ist ausdruecklich gewuenscht:
-- Der Test der Konten beginnt bei einer leeren Datenbank.
--
-- Im Dashboard gehoert dazu: anonyme Anmeldung aus, Bestaetigung der
-- Adresse an, Brevo als SMTP - siehe README.

delete from auth.users;
