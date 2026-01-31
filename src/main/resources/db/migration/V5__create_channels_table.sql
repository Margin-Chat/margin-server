CREATE TABLE channels
(
    channel_id  BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    space_id    BIGINT       NOT NULL
);

ALTER TABLE channels
    ADD CONSTRAINT FK_CHANNELS_ON_SPACE FOREIGN KEY (space_id) REFERENCES spaces (space_id);