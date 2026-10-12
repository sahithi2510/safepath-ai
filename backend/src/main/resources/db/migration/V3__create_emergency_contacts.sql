-- ============================================================
-- V3: Emergency contacts
-- ============================================================

CREATE TABLE emergency_contacts (
    id           UUID        NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name         VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20)  NOT NULL,
    relationship VARCHAR(100),
    is_primary   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_emergency_contacts_user_id ON emergency_contacts(user_id);
