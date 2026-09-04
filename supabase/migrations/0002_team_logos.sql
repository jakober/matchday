-- Wappen-Zwischenspeicher, gruppenuebergreifend.
--
-- Ein Wappen ist keine Gruppeninformation: "Bayern Munich" sieht in jeder
-- Gruppe gleich aus. Deshalb eine Tabelle fuer alle, gefuellt von der Edge
-- Function team-logo, die als einzige den Wappendienst (TheSportsDB) fragt.
-- So wird jeder Name genau einmal nachgeschlagen - nicht einmal je Geraet.
--
-- Clients duerfen hier nur lesen. Duerften sie schreiben, koennte jeder
-- beliebige Bildadressen hinterlegen, die dann auf allen Geraeten geladen
-- wuerden - ein Zaehlpixel, verteilt ueber die App.

create table public.team_logos (
  -- Normalisierter Name: klein, ohne Umlaute, ohne Rechtsform. Die
  -- Normalisierung lebt in der Function; hier ist der Schluessel opak.
  key          text primary key,
  display_name text not null,
  -- null heisst: nachgeschlagen, nichts gefunden. Auch das wird gespeichert,
  -- sonst fragt jedes Geraet bei jedem Start erneut nach "Werder Bremen II".
  badge_url    text,
  -- 'thesportsdb' wird nach Ablauf erneut nachgeschlagen, 'manual' nie:
  -- Damit laesst sich ein Fehlgriff des Dienstes einmal fuer alle korrigieren.
  source       text not null default 'thesportsdb' check (source in ('thesportsdb', 'manual')),
  looked_up_at timestamptz not null default now()
);

alter table public.team_logos enable row level security;

create policy "wappen lesen" on public.team_logos
  for select using (true);

-- Kein insert/update/delete fuer Clients: Das macht ausschliesslich die
-- Function mit dem Dienstschluessel.
