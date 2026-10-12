-- ============================================================
-- V12: Seed default roles
-- ============================================================
-- Insert the three default application roles.
-- These must exist before any user can register.

INSERT INTO roles (name) VALUES
    ('ROLE_USER'),
    ('ROLE_MODERATOR'),
    ('ROLE_ADMIN')
ON CONFLICT (name) DO NOTHING;
