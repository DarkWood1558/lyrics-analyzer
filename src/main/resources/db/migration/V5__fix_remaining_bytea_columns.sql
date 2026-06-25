-- Fix remaining column types if they were incorrectly created as bytea
-- This migration handles columns that may have been missed by V4
-- PostgreSQL doesn't allow direct type change from bytea to text, so we need to:
-- 1. Add new columns with correct types
-- 2. Copy data from old columns to new columns (with error handling)
-- 3. Drop old columns
-- 4. Rename new columns to old names

-- Check if genre is bytea and fix it
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'track' AND column_name = 'genre' 
        AND data_type = 'bytea'
    ) THEN
        RAISE NOTICE 'Fixing genre column type from bytea to VARCHAR(255)...';
        
        ALTER TABLE track ADD COLUMN IF NOT EXISTS genre_temp VARCHAR(255);
        
        UPDATE track SET genre_temp = 
            CASE 
                WHEN genre IS NULL THEN NULL
                ELSE convert_from(genre, 'UTF8')
            END 
        WHERE genre IS NOT NULL;
        
        ALTER TABLE track DROP COLUMN IF EXISTS genre;
        ALTER TABLE track RENAME COLUMN genre_temp TO genre;
        
        RAISE NOTICE 'Fixed genre column type from bytea to VARCHAR(255)';
    END IF;
END $$;

-- Check if lyrics is bytea and fix it
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'track' AND column_name = 'lyrics' 
        AND data_type = 'bytea'
    ) THEN
        RAISE NOTICE 'Fixing lyrics column type from bytea to TEXT...';
        
        ALTER TABLE track ADD COLUMN IF NOT EXISTS lyrics_temp TEXT;
        
        UPDATE track SET lyrics_temp = 
            CASE 
                WHEN lyrics IS NULL THEN NULL
                ELSE convert_from(lyrics, 'UTF8')
            END 
        WHERE lyrics IS NOT NULL;
        
        ALTER TABLE track DROP COLUMN IF EXISTS lyrics;
        ALTER TABLE track RENAME COLUMN lyrics_temp TO lyrics;
        
        RAISE NOTICE 'Fixed lyrics column type from bytea to TEXT';
    END IF;
END $$;

-- Check if lyrics_status is bytea and fix it
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'track' AND column_name = 'lyrics_status' 
        AND data_type = 'bytea'
    ) THEN
        RAISE NOTICE 'Fixing lyrics_status column type from bytea to VARCHAR(20)...';
        
        ALTER TABLE track ADD COLUMN IF NOT EXISTS lyrics_status_temp VARCHAR(20);
        
        UPDATE track SET lyrics_status_temp = 
            CASE 
                WHEN lyrics_status IS NULL THEN 'PENDING'
                ELSE convert_from(lyrics_status, 'UTF8')
            END;
        
        ALTER TABLE track DROP COLUMN IF EXISTS lyrics_status;
        ALTER TABLE track RENAME COLUMN lyrics_status_temp TO lyrics_status;
        
        -- Set default value
        ALTER TABLE track ALTER COLUMN lyrics_status SET DEFAULT 'PENDING';
        
        RAISE NOTICE 'Fixed lyrics_status column type from bytea to VARCHAR(20)';
    END IF;
END $$;

-- Check if sentiment_label is bytea and fix it
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'track' AND column_name = 'sentiment_label' 
        AND data_type = 'bytea'
    ) THEN
        RAISE NOTICE 'Fixing sentiment_label column type from bytea to VARCHAR(20)...';
        
        ALTER TABLE track ADD COLUMN IF NOT EXISTS sentiment_label_temp VARCHAR(20);
        
        UPDATE track SET sentiment_label_temp = 
            CASE 
                WHEN sentiment_label IS NULL THEN NULL
                ELSE convert_from(sentiment_label, 'UTF8')
            END 
        WHERE sentiment_label IS NOT NULL;
        
        ALTER TABLE track DROP COLUMN IF EXISTS sentiment_label;
        ALTER TABLE track RENAME COLUMN sentiment_label_temp TO sentiment_label;
        
        RAISE NOTICE 'Fixed sentiment_label column type from bytea to VARCHAR(20)';
    END IF;
END $$;
