ALTER TABLE stored_files
    ADD COLUMN message_id BIGINT REFERENCES messages(message_id) ON DELETE SET NULL;

CREATE INDEX idx_stored_files_message
    ON stored_files (message_id)
    WHERE message_id IS NOT NULL AND deleted_at IS NULL;
