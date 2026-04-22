ALTER TABLE channels
    ADD COLUMN created_at TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE spaces
    ADD COLUMN created_at TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE space_members
    ADD COLUMN created_at TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE conversation_members
    ADD COLUMN created_at TIMESTAMP;

ALTER TABLE bug_reports
    ADD COLUMN created_at TIMESTAMP;

ALTER TABLE conversations
    ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE users
    ADD COLUMN updated_at TIMESTAMP;
