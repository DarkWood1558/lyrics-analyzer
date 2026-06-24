-- Migration für Themenklassifikation
ALTER TABLE track ADD COLUMN IF NOT EXISTS theme VARCHAR(20);

-- Index für schnelle Themen-Suchen
CREATE INDEX IF NOT EXISTS idx_track_theme ON track (theme);
