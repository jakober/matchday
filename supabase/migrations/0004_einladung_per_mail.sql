-- Einladungen per E-Mail: An wen und wann verschickt.
--
-- sent_to dient der Anzeige ("per E-Mail an ... geschickt") und der
-- Nachvollziehbarkeit; sent_at der Zaehlung je Gruppe und Tag, mit der die
-- Function invite-send den Versand begrenzt. Die Zaehlung steht in der
-- Tabelle, nicht im Speicher der Function - die ist zustandslos und laeuft
-- in mehreren Instanzen, eine Zaehlung im Speicher schuetzte nichts.

alter table public.invites
  add column sent_to text,
  add column sent_at timestamptz;

create index invites_group_sent_idx on public.invites (group_id, sent_at);
