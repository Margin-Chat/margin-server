-- Rolls back V20260711_1's message-anchored threads (dev-only) in favour of posts anchored by parent_conversation_id.
DELETE FROM message_reactions
WHERE message_id IN (SELECT message_id
                     FROM messages
                     WHERE conversation_id IN (SELECT conversation_id
                                               FROM conversations
                                               WHERE type = 'THREAD'
                                                 AND parent_message_id IS NOT NULL));

DELETE FROM messages
WHERE conversation_id IN (SELECT conversation_id
                          FROM conversations
                          WHERE type = 'THREAD'
                            AND parent_message_id IS NOT NULL);

DELETE FROM notification
WHERE conversation_id IN (SELECT conversation_id
                          FROM conversations
                          WHERE type = 'THREAD'
                            AND parent_message_id IS NOT NULL);

DELETE FROM conversation_members
WHERE conversation_id IN (SELECT conversation_id
                          FROM conversations
                          WHERE type = 'THREAD'
                            AND parent_message_id IS NOT NULL);

DELETE FROM conversations
WHERE type = 'THREAD'
  AND parent_message_id IS NOT NULL;

DROP INDEX IF EXISTS uq_conversations_parent_message_id;

ALTER TABLE conversations
    DROP COLUMN IF EXISTS parent_message_id;
