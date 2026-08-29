CREATE TABLE subscriptions
(
    id                   BIGSERIAL PRIMARY KEY,
    margins              BIGINT      NOT NULL UNIQUE REFERENCES margins (margin_id),
    tier                 VARCHAR(20) NOT NULL DEFAULT 'FREE',
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    trial_ends_at        TIMESTAMP,
    current_period_start TIMESTAMP,
    current_period_end   TIMESTAMP,
    subscription_id      VARCHAR(255),
    created_at           TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_margins ON subscriptions (margins);
CREATE INDEX idx_subscriptions_status ON subscriptions (status);
CREATE INDEX idx_subscriptions_subscription_id ON subscriptions (subscription_id);

CREATE TABLE subscription_limits
(
    id                    BIGSERIAL PRIMARY KEY,
    subscription_id       BIGINT  NOT NULL UNIQUE REFERENCES subscriptions (id) ON DELETE CASCADE,
    max_members           INTEGER NOT NULL DEFAULT 25,
    max_storage_gb        INTEGER NOT NULL DEFAULT 5,
    max_call_participants INTEGER NOT NULL DEFAULT 10
);