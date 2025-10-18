-- Migration to remove SMS column if it exists
DO $$
BEGIN
    -- Remove sms_enabled column if it exists
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'notification_preferences'
        AND column_name = 'sms_enabled'
    ) THEN
ALTER TABLE notification_preferences DROP COLUMN sms_enabled;
RAISE NOTICE 'Column sms_enabled removed from notification_preferences';
ELSE
        RAISE NOTICE 'Column sms_enabled does not exist, skipping';
END IF;

    -- Also clean up any SMS-related data in notifications
UPDATE notifications
SET channel = 'EMAIL'
WHERE channel = 'SMS';

RAISE NOTICE 'Updated any SMS notifications to use EMAIL channel instead';
END $$;

-- Add comment to clarify available channels
COMMENT ON COLUMN notifications.channel IS 'Notification channel: EMAIL, SSE, or PUSH';
COMMENT ON COLUMN notification_preferences.email_enabled IS 'Enable/disable email notifications';
COMMENT ON COLUMN notification_preferences.push_enabled IS 'Enable/disable push notifications';
COMMENT ON COLUMN notification_preferences.sse_enabled IS 'Enable/disable Server-Sent Events notifications for real-time updates';