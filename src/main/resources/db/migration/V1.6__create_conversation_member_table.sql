CREATE TABLE conversation_members
(
    joined_at       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_read_at    TIMESTAMP WITHOUT TIME ZONE,
    conversation_id BIGINT                      NOT NULL,
    user_id         BIGINT                      NOT NULL,
    CONSTRAINT pk_conversation_members PRIMARY KEY (conversation_id, user_id)
);

ALTER TABLE conversation_members
    ADD CONSTRAINT FK_CONVERSATION_MEMBERS_ON_CONVERSATION FOREIGN KEY (conversation_id) REFERENCES conversations (conversation_id);

ALTER TABLE conversation_members
    ADD CONSTRAINT FK_CONVERSATION_MEMBERS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);