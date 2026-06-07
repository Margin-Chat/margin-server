ALTER TABLE stored_files ADD COLUMN conversation_id BIGINT REFERENCES conversations(conversation_id);

ALTER TABLE stored_files ALTER COLUMN margin_id DROP NOT NULL;

ALTER TABLE stored_files DROP CONSTRAINT stored_files_channel_scope_chk;

ALTER TABLE stored_files ADD CONSTRAINT stored_files_scope_chk
    CHECK (
        (scope = 'CHANNEL'      AND channel_id IS NOT NULL      AND margin_id IS NOT NULL AND conversation_id IS NULL)
     OR (scope = 'MARGIN'       AND channel_id IS NULL          AND margin_id IS NOT NULL AND conversation_id IS NULL)
     OR (scope = 'CONVERSATION' AND conversation_id IS NOT NULL AND channel_id IS NULL    AND margin_id IS NULL)
    );

CREATE INDEX idx_stored_files_conversation_alive
    ON stored_files (conversation_id, uploaded_at DESC)
    WHERE deleted_at IS NULL AND scope = 'CONVERSATION';