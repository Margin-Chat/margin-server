CREATE TABLE meeting
(
    meeting_id             BIGSERIAL PRIMARY KEY,
    code                   VARCHAR(32)  NOT NULL,
    host_user_id           BIGINT       NOT NULL REFERENCES users (id),
    margin_id              BIGINT       NOT NULL REFERENCES margins (margin_id) ON DELETE CASCADE,
    title                  VARCHAR(120),
    status                 VARCHAR(16)  NOT NULL,
    scheduled_at           TIMESTAMPTZ,
    duration_minutes       INT,
    organizer_timezone     VARCHAR(64),
    require_admission      BOOLEAN      NOT NULL DEFAULT TRUE,
    max_participants       INT          NOT NULL,
    max_video_height       INT,
    ics_sequence           INT          NOT NULL DEFAULT 0,
    reminder_sent_at       TIMESTAMPTZ,
    started_notice_sent_at TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    started_at             TIMESTAMPTZ,
    ended_at               TIMESTAMPTZ,
    cancelled_at           TIMESTAMPTZ,
    expires_at             TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX uq_meeting_code ON meeting (code);
CREATE INDEX idx_meeting_margin_id ON meeting (margin_id);
CREATE INDEX idx_meeting_host_user_id ON meeting (host_user_id);
CREATE INDEX idx_meeting_scheduled_pending ON meeting (scheduled_at) WHERE status = 'SCHEDULED';

CREATE TABLE meeting_participant
(
    meeting_participant_id BIGSERIAL PRIMARY KEY,
    meeting_id             BIGINT      NOT NULL REFERENCES meeting (meeting_id) ON DELETE CASCADE,
    user_id                BIGINT REFERENCES users (id) ON DELETE SET NULL,
    display_name           VARCHAR(50) NOT NULL,
    is_guest               BOOLEAN     NOT NULL DEFAULT FALSE,
    role                   VARCHAR(16) NOT NULL,
    state                  VARCHAR(16) NOT NULL,
    knocked_at             TIMESTAMPTZ,
    admitted_at            TIMESTAMPTZ,
    joined_at              TIMESTAMPTZ,
    left_at                TIMESTAMPTZ,
    admitted_by            BIGINT
);

CREATE UNIQUE INDEX uq_meeting_participant_meeting_user
    ON meeting_participant (meeting_id, user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_meeting_participant_meeting_state ON meeting_participant (meeting_id, state);

CREATE TABLE meeting_invite
(
    meeting_invite_id BIGSERIAL PRIMARY KEY,
    meeting_id        BIGINT      NOT NULL REFERENCES meeting (meeting_id) ON DELETE CASCADE,
    user_id           BIGINT REFERENCES users (id) ON DELETE CASCADE,
    email             VARCHAR(255),
    invite_token      VARCHAR(64) NOT NULL,
    status            VARCHAR(16) NOT NULL,
    responded_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_meeting_invite_target CHECK (user_id IS NOT NULL OR email IS NOT NULL)
);

CREATE UNIQUE INDEX uq_meeting_invite_token ON meeting_invite (invite_token);
CREATE INDEX idx_meeting_invite_meeting_id ON meeting_invite (meeting_id);
