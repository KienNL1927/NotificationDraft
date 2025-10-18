-- Create notification_templates table
DROP TABLE IF EXISTS notification_templates CASCADE;
CREATE TABLE notification_templates (
                                        id SERIAL PRIMARY KEY,
                                        name VARCHAR(100) UNIQUE NOT NULL,
                                        type VARCHAR(20) NOT NULL,
                                        subject VARCHAR(255),
                                        body TEXT NOT NULL,
                                        variables JSONB,
                                        created_at TIMESTAMP DEFAULT NOW(),
                                        updated_at TIMESTAMP DEFAULT NOW()
);

-- Create index for template name lookup
CREATE INDEX idx_notification_templates_name ON notification_templates(name);
CREATE INDEX idx_notification_templates_type ON notification_templates(type);

-- Create notifications table
DROP TABLE IF EXISTS notifications CASCADE;
CREATE TABLE notifications (
                               id SERIAL PRIMARY KEY,
                               recipient_id INTEGER,
                               recipient_email VARCHAR(255),
                               recipient_phone VARCHAR(20),
                               type VARCHAR(50) NOT NULL,
                               channel VARCHAR(20) NOT NULL,
                               subject VARCHAR(255),
                               content TEXT NOT NULL,
                               template_id INTEGER,
                               status VARCHAR(20) DEFAULT 'pending',
                               sent_at TIMESTAMP,
                               delivered_at TIMESTAMP,
                               error_message TEXT,
                               retry_count INTEGER DEFAULT 0,
                               created_at TIMESTAMP DEFAULT NOW(),
                               updated_at TIMESTAMP DEFAULT NOW(),
                               CONSTRAINT fk_template FOREIGN KEY (template_id) REFERENCES notification_templates(id)
);

-- Create indexes for notifications
CREATE INDEX idx_notifications_recipient_id ON notifications(recipient_id);
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_type ON notifications(type);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);
CREATE INDEX idx_notifications_channel ON notifications(channel);

-- Create notification_preferences table
DROP TABLE IF EXISTS notification_preferences CASCADE;
CREATE TABLE notification_preferences (
                                          id SERIAL PRIMARY KEY,
                                          user_id INTEGER UNIQUE NOT NULL,
                                          email_enabled BOOLEAN DEFAULT true,
                                          push_enabled BOOLEAN DEFAULT true,
                                          sse_enabled BOOLEAN DEFAULT true,
                                          email_frequency VARCHAR(20) DEFAULT 'immediate',
                                          categories JSONB,
                                          created_at TIMESTAMP DEFAULT NOW(),
                                          updated_at TIMESTAMP DEFAULT NOW()
);

-- Create index for user_id lookup
CREATE INDEX idx_notification_preferences_user_id ON notification_preferences(user_id);

-- Create trigger function for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at
CREATE TRIGGER update_notification_templates_updated_at BEFORE UPDATE ON notification_templates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_notifications_updated_at BEFORE UPDATE ON notifications
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_notification_preferences_updated_at BEFORE UPDATE ON notification_preferences
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();