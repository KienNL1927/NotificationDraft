-- Migration script to update from WebSocket to SSE
-- This migration is only needed if you have an existing database with websocket_enabled column

-- Check if websocket_enabled column exists and rename it to sse_enabled
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'notification_preferences'
        AND column_name = 'websocket_enabled'
    ) THEN
ALTER TABLE notification_preferences
    RENAME COLUMN websocket_enabled TO sse_enabled;

RAISE NOTICE 'Column websocket_enabled renamed to sse_enabled';
ELSE
        RAISE NOTICE 'Column websocket_enabled does not exist, skipping rename';
END IF;
END $$;

-- Update any websocket templates to use SSE type
UPDATE notification_templates
SET type = 'sse'
WHERE type = 'websocket';

-- Add comment to clarify the column purpose
COMMENT ON COLUMN notification_preferences.sse_enabled IS 'Enable/disable Server-Sent Events notifications for real-time updates';

-- Log the migration
DO $$
DECLARE
updated_count INTEGER;
BEGIN
SELECT COUNT(*) INTO updated_count
FROM notification_templates
WHERE type = 'sse';

RAISE NOTICE 'Migration complete. Updated % templates to use SSE', updated_count;
END $$;