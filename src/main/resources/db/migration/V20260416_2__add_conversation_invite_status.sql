ALTER TABLE conversation_members
    ADD COLUMN invite_status VARCHAR(10) NOT NULL DEFAULT 'ACCEPTED',
    ADD COLUMN invited_at    TIMESTAMP;
