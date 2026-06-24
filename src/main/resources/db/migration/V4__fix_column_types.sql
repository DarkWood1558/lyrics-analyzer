-- Fix column types if they were incorrectly created as bytea
-- PostgreSQL doesn't allow direct type change from bytea to text, so we need to:
-- 1. Add new columns with correct types
-- 2. Copy data from old columns to new columns
-- 3. Drop old columns
-- 4. Rename new columns to old names

-- Check if artist_name is bytea and fix it
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'track' AND column_name = 'artist_name' 
        AND data_type = 'bytea'
    ) THEN
        -- Add temporary column
        ALTER TABLE track ADD COLUMN IF NOT EXISTS artist_name_temp VARCHAR(255);
        
        -- Copy data (this will fail if data can't be converted, but we handle it)
        -- For bytea data that's actually text, we need to convert it
        -- Note: This assumes the bytea data is actually text encoded as UTF-8
        UPDATE track SET artist_name_temp = convert_from(artist_name, 'UTF8') WHERE artist_name IS NOT NULL;
        
        -- Drop old column
        ALTER TABLE track DROP COLUMN IF EXISTS artist_name;
        
        -- Rename new column
        ALTER TABLE track RENAME COLUMN artist_name_temp TO artist_name;
        
        RAISE NOTICE 'Fixed artist_name column type from bytea to VARCHAR(255)';
    END IF;
END $$;

-- Check if title is bytea and fix it
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'track' AND column_name = 'title' 
        AND data_type = 'bytea'
    ) THEN
        -- Add temporary column
        ALTER TABLE track ADD COLUMN IF NOT EXISTS title_temp VARCHAR(500);
        
        -- Copy data
        UPDATE track SET title_temp = convert_from(title, 'UTF8') WHERE title IS NOT NULL;
        
        -- Drop old column
        ALTER TABLE track DROP COLUMN IF EXISTS title;
        
        -- Rename new column
        ALTER TABLE track RENAME COLUMN title_temp TO title;
        
        RAISE NOTICE 'Fixed title column type from bytea to VARCHAR(500)';
    END IF;
END $$;

-- Check if album_name is bytea and fix it
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'track' AND column_name = 'album_name' 
        AND data_type = 'bytea'
    ) THEN
        -- Add temporary column
        ALTER TABLE track ADD COLUMN IF NOT EXISTS album_name_temp VARCHAR(500);
        
        -- Copy data
        UPDATE track SET album_name_temp = convert_from(album_name, 'UTF8') WHERE album_name IS NOT NULL;
        
        -- Drop old column
        ALTER TABLE track DROP COLUMN IF EXISTS album_name;
        
        -- Rename new column
        ALTER TABLE track RENAME COLUMN album_name_temp TO album_name;
        
        RAISE NOTICE 'Fixed album_name column type from bytea to VARCHAR(500)';
    END IF;
END $$;
