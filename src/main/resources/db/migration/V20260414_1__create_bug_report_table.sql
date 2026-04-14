CREATE TABLE bug_reports (
                             id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                             title VARCHAR(255) NOT NULL,
                             description TEXT NOT NULL,
                             user_id BIGINT NOT NULL REFERENCES users(id)
);

CREATE INDEX idx_bug_reports_user_id ON bug_reports(user_id);