-- ============================================================
-- V18: Create user notifications table for report status alerts
-- ============================================================

CREATE TABLE user_notifications (
    id                 UUID         NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id            UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title              VARCHAR(255) NOT NULL,
    message            TEXT         NOT NULL,
    notification_type  VARCHAR(50)  NOT NULL DEFAULT 'REPORT_STATUS_CHANGED',
    reference_id       UUID,
    is_read            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_notifications_user_id   ON user_notifications(user_id);
CREATE INDEX idx_user_notifications_unread    ON user_notifications(user_id, is_read);
CREATE INDEX idx_user_notifications_created   ON user_notifications(created_at DESC);
