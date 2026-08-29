CREATE TABLE stored_files (
    file_id              BIGSERIAL PRIMARY KEY,
    scope                VARCHAR(16) NOT NULL,
    margin_id            BIGINT NOT NULL REFERENCES margins(margin_id),
    channel_id           BIGINT REFERENCES channels(channel_id),
    file_name            VARCHAR(512) NOT NULL,
    content_type         VARCHAR(255) NOT NULL,
    size_bytes           BIGINT NOT NULL,
    storage_url          VARCHAR(1024) NOT NULL,
    uploaded_by_user_id  BIGINT NOT NULL REFERENCES users(id),
    uploaded_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ,

    CONSTRAINT stored_files_channel_scope_chk
        CHECK ((scope = 'CHANNEL' AND channel_id IS NOT NULL)
            OR (scope = 'MARGIN'  AND channel_id IS NULL))
);

CREATE INDEX idx_stored_files_margin_alive
    ON stored_files (margin_id, uploaded_at DESC)
    WHERE deleted_at IS NULL AND scope = 'MARGIN';

CREATE INDEX idx_stored_files_channel_alive
    ON stored_files (channel_id, uploaded_at DESC)
    WHERE deleted_at IS NULL AND scope = 'CHANNEL';
