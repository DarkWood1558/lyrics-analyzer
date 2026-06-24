-- Speichert die Deezer-Album-ID pro Track, damit Genre/Erscheinungsjahr auch nachträglich
-- (Backfill) über GET /album/{id} nachgeladen werden können, ohne erneut bei Deezer suchen
-- zu müssen. Für Tracks, die vor dieser Migration angelegt wurden, bleibt die Spalte NULL -
-- für diese ist kein Backfill über die Album-API möglich (siehe README).
ALTER TABLE track ADD COLUMN deezer_album_id BIGINT;

CREATE INDEX idx_track_deezer_album_id ON track (deezer_album_id);
