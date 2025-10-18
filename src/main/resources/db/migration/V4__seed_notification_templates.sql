-- Insert default notification templates
INSERT INTO notification_templates (name, type, subject, body, variables) VALUES
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

    ON CONFLICT (name) DO UPDATE SET
    type = EXCLUDED.type,
                              subject = EXCLUDED.subject,
                              body = EXCLUDED.body,
                              variables = EXCLUDED.variables,
                              updated_at = NOW();