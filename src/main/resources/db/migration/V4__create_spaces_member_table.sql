CREATE TABLE space_members
(
    space_id        BIGINT NOT NULL,
    user_id   BIGINT NOT NULL,
    joined_at TIMESTAMP WITHOUT TIME ZONE,
    role      VARCHAR(255),
    CONSTRAINT pk_space_members PRIMARY KEY (space_id)
);

ALTER TABLE space_members
    ADD CONSTRAINT FK_SPACE_MEMBERS_ON_ID FOREIGN KEY (space_id) REFERENCES spaces (space_id);

ALTER TABLE space_members
    ADD CONSTRAINT FK_SPACE_MEMBERS_ON_USER FOREIGN KEY (user_id) REFERENCES users (user_id);