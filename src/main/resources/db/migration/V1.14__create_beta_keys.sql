CREATE TABLE beta_keys (
                           id BIGSERIAL PRIMARY KEY,
                           key VARCHAR(255) NOT NULL UNIQUE,
                           used_by BIGINT REFERENCES users(id),
                           used_at TIMESTAMP,
                           created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_beta_keys_key ON beta_keys(key);