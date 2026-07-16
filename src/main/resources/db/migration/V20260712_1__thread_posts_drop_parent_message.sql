-- Threads are forum-style posts anchored to a thread channel's conversation
-- (parent_conversation_id); the earlier message-anchored design shipped to dev
-- in V20260711_1 and is rolled back here. Message-anchored THREAD conversations
-- only ever existed on dev databases — purge them before dropping their anchor.
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
