ALTER TABLE conversations
    ADD COLUMN parent_message_id BIGINT REFERENCES messages (message_id),
    ADD COLUMN parent_conversation_id BIGINT REFERENCES conversations (conversation_id);

CREATE UNIQUE INDEX uq_conversations_parent_message_id
    ON conversations (parent_message_id)
    WHERE parent_message_id IS NOT NULL;

CREATE INDEX idx_conversations_parent_conversation_id
    ON conversations (parent_conversation_id)
    WHERE parent_conversation_id IS NOT NULL;
