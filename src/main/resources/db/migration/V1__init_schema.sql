-- ============================
-- Init Notification Schema (PostgreSQL + TIMESTAMPTZ for Instant)
-- ============================

-- 1) TABLES
-- (Nếu là DB mới, cho phép DROP để tái khởi tạo; comment 3 dòng dưới khi áp dụng prod)
DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS notification_templates CASCADE;
DROP TABLE IF EXISTS notification_preferences CASCADE;

-- notification_templates  (gốc từ V1, đổi TIMESTAMP -> TIMESTAMPTZ)
CREATE TABLE notification_templates (
                                        id          SERIAL PRIMARY KEY,
                                        name        VARCHAR(100) UNIQUE NOT NULL,
                                        type        VARCHAR(20)  NOT NULL,
                                        subject     VARCHAR(255),
                                        body        TEXT         NOT NULL,
                                        variables   JSONB,
                                        created_at  TIMESTAMPTZ  DEFAULT NOW(),
                                        updated_at  TIMESTAMPTZ  DEFAULT NOW()
);

-- notifications  (gốc từ V1, đổi các cột thời gian sang TIMESTAMPTZ)
CREATE TABLE notifications (
                               id               SERIAL PRIMARY KEY,
                               recipient_id     INTEGER,
                               recipient_email  VARCHAR(255),
                               recipient_phone  VARCHAR(20),
                               type             VARCHAR(50)  NOT NULL,
                               channel          VARCHAR(20)  NOT NULL,
                               subject          VARCHAR(255),
                               content          TEXT         NOT NULL,
                               template_id      INTEGER,
                               status           VARCHAR(20)  DEFAULT 'pending',
                               sent_at          TIMESTAMPTZ,
                               delivered_at     TIMESTAMPTZ,
                               error_message    TEXT,
                               retry_count      INTEGER      DEFAULT 0,
                               created_at       TIMESTAMPTZ  DEFAULT NOW(),
                               updated_at       TIMESTAMPTZ  DEFAULT NOW(),
                               CONSTRAINT fk_template FOREIGN KEY (template_id) REFERENCES notification_templates(id)
);

-- notification_preferences  (gốc từ V1; chọn cột sse_enabled luôn sẵn, bỏ websocket/sms)
CREATE TABLE notification_preferences (
                                          id               SERIAL PRIMARY KEY,
                                          user_id          INTEGER UNIQUE NOT NULL,
                                          email_enabled    BOOLEAN     DEFAULT TRUE,
                                          push_enabled     BOOLEAN     DEFAULT TRUE,
                                          sse_enabled      BOOLEAN     DEFAULT TRUE,
                                          email_frequency  VARCHAR(20) DEFAULT 'immediate',
                                          categories       JSONB,
                                          created_at       TIMESTAMPTZ DEFAULT NOW(),
                                          updated_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 2) INDEXES
CREATE INDEX idx_notification_templates_name ON notification_templates(name);
CREATE INDEX idx_notification_templates_type ON notification_templates(type);

CREATE INDEX idx_notifications_recipient_id ON notifications(recipient_id);
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_type ON notifications(type);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);
CREATE INDEX idx_notifications_channel ON notifications(channel);

CREATE INDEX idx_notification_preferences_user_id ON notification_preferences(user_id);

-- (Tuỳ chọn) Composite index cho worker/poller thường dùng:
-- CREATE INDEX idx_notifications_status_retry_created ON notifications(status, retry_count, created_at);

-- 3) updated_at TRIGGERS (gốc từ V1)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notification_templates_updated_at
    BEFORE UPDATE ON notification_templates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_notifications_updated_at
    BEFORE UPDATE ON notifications
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_notification_preferences_updated_at
    BEFORE UPDATE ON notification_preferences
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 4) COMMENTS / CHANNEL NOTES (gốc từ V3)
COMMENT ON COLUMN notifications.channel IS 'Notification channel: EMAIL, SSE, or PUSH';
COMMENT ON COLUMN notification_preferences.email_enabled IS 'Enable/disable email notifications';
COMMENT ON COLUMN notification_preferences.push_enabled  IS 'Enable/disable push notifications';
COMMENT ON COLUMN notification_preferences.sse_enabled   IS 'Enable/disable Server-Sent Events notifications for real-time updates';

-- 5) SEED DEFAULT TEMPLATES (gốc từ V4, giữ ON CONFLICT để idempotent)
INSERT INTO notification_templates (name, type, subject, body, variables)
VALUES
    ('welcome_user', 'email', 'Welcome to Our Platform, {{firstName}}!',
     '<html>
      <body>
        <h2>Welcome {{firstName}} {{lastName}}!</h2>
        <p>Thank you for registering with us. Your username is: <strong>{{username}}</strong></p>
        <p>You can now access all features of our platform.</p>
        <p>Best regards,<br>The Platform Team</p>
      </body>
     </html>',
     '{"firstName": "string", "lastName": "string", "username": "string", "email": "string"}'::jsonb),

    ('session_completion', 'email', 'Assessment Completed - {{assessmentName}}',
     '<html>
      <body>
        <h2>Congratulations {{username}}!</h2>
        <p>You have successfully completed the assessment: <strong>{{assessmentName}}</strong></p>
        <p>Completion Time: {{completionTime}}</p>
        <p>Score: {{score}}</p>
        <p>Status: {{status}}</p>
        <p>Thank you for your participation.</p>
        <p>Best regards,<br>The Assessment Team</p>
      </body>
     </html>',
     '{"username": "string", "assessmentName": "string", "completionTime": "string", "score": "number", "status": "string"}'::jsonb),

    ('proctoring_alert', 'email', 'Proctoring Alert - Session {{sessionId}}',
     '<html>
      <body>
        <h2>Proctoring Violation Detected</h2>
        <p>A proctoring violation has been detected for session: <strong>{{sessionId}}</strong></p>
        <p>User: {{username}}</p>
        <p>Violation Type: {{violationType}}</p>
        <p>Severity: {{severity}}</p>
        <p>Timestamp: {{timestamp}}</p>
        <p>Please review this incident.</p>
        <p>Best regards,<br>The Proctoring Team</p>
      </body>
     </html>',
     '{"username": "string", "sessionId": "string", "violationType": "string", "severity": "string", "timestamp": "string"}'::jsonb),

    ('new_assessment_assigned', 'email', 'New Assessment Available - {{assessmentName}}',
     '<html>
      <body>
        <h2>Hello {{username}}!</h2>
        <p>A new assessment has been assigned to you: <strong>{{assessmentName}}</strong></p>
        <p>Duration: {{duration}} minutes</p>
        <p>Due Date: {{dueDate}}</p>
        <p>Please complete it before the deadline.</p>
        <p>Best regards,<br>The Assessment Team</p>
      </body>
     </html>',
     '{"username": "string", "assessmentName": "string", "duration": "number", "dueDate": "string"}'::jsonb)
    ON CONFLICT (name) DO UPDATE
                              SET type = EXCLUDED.type,
                              subject = EXCLUDED.subject,
                              body = EXCLUDED.body,
                              variables = EXCLUDED.variables,
                              updated_at = NOW();
