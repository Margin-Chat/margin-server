ALTER TABLE users ADD COLUMN account_type VARCHAR(16) NOT NULL DEFAULT 'FULL';
ALTER TABLE users ADD COLUMN guest_expires_at TIMESTAMPTZ;

CREATE INDEX idx_users_guest_expires_at ON users (guest_expires_at)
    WHERE account_type = 'GUEST';
