UPDATE messages m
SET message = CASE
        WHEN m.message IS NULL OR m.message = '' THEN sf.storage_url
        ELSE m.message || E'\n' || sf.storage_url
    END
FROM stored_files sf
JOIN conversations c ON c.conversation_id = (
    SELECT conversation_id FROM messages WHERE message_id = sf.message_id
)
WHERE sf.message_id = m.message_id
  AND c.type = 'CHANNEL'
  AND sf.storage_url NOT LIKE '/api/files/%'
  AND sf.storage_url NOT LIKE '%/user-profiles/%'
  AND sf.storage_url NOT LIKE '%/margin-icons/%'
  AND sf.storage_url NOT LIKE '%/conversation-images/%'
  AND sf.storage_url NOT LIKE '%/stored-files/%';

DELETE FROM stored_files
WHERE storage_url NOT LIKE '/api/files/%'
  AND storage_url NOT LIKE '%/user-profiles/%'
  AND storage_url NOT LIKE '%/margin-icons/%'
  AND storage_url NOT LIKE '%/conversation-images/%'
  AND storage_url NOT LIKE '%/stored-files/%';