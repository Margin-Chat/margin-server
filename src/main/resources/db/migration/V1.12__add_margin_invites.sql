CREATE TABLE margin_invites
(
    id                 BIGSERIAL PRIMARY KEY,
    margin_id          BIGINT      NOT NULL REFERENCES margins (margin_id),
    invited_by_user_id BIGINT      NOT NULL REFERENCES users (id),
    invited_user_id    BIGINT REFERENCES users (id),
    invite_code        VARCHAR(36) NOT NULL UNIQUE,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    type               VARCHAR(20) NOT NULL,
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    expires_at         TIMESTAMP   NOT NULL,
    max_uses           INTEGER,
    current_uses       INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX idx_margin_invites_margin_id ON margin_invites (margin_id);
CREATE INDEX idx_margin_invites_invited_user_id ON margin_invites (invited_user_id);
CREATE INDEX idx_margin_invites_invite_code ON margin_invites (invite_code);
CREATE INDEX idx_margin_invites_status ON margin_invites (status);