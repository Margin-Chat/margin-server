CREATE INDEX idx_conversation_members_user_id
    ON conversation_members (user_id);
CREATE INDEX idx_messages_conversation_created
    ON messages (conversation_id, created_at DESC);
CREATE INDEX idx_messages_from_user
    ON messages (from_user_id);
CREATE UNIQUE INDEX idx_margin_members_margin_user
    ON margin_members (margin_id, user_id);
CREATE INDEX idx_margin_members_user_id
    ON margin_members (user_id);
CREATE UNIQUE INDEX idx_space_members_space_user
    ON space_members (space_id, user_id);
CREATE INDEX idx_space_members_user_id
    ON space_members (user_id);
CREATE INDEX idx_channels_space_id
    ON channels (space_id);
CREATE INDEX idx_spaces_margin_id
    ON spaces (margin_id);
CREATE INDEX idx_conversations_channel_id
    ON conversations (channel_id);
CREATE INDEX idx_calls_caller_id
    ON calls (caller_id);
CREATE INDEX idx_calls_receiver_id
    ON calls (receiver_id);