INSERT INTO stored_files (scope, margin_id, channel_id, file_name, content_type, size_bytes,
                          storage_url, uploaded_by_user_id, uploaded_at, message_id)
SELECT
    'CHANNEL',
    m_marg.margin_id,
    ch.channel_id,
    COALESCE(NULLIF(regexp_replace(m.image_address, '.*/', ''), ''), 'attachment'),
    CASE
        WHEN m.image_address ~* '\.(png)$'  THEN 'image/png'
        WHEN m.image_address ~* '\.(jpe?g)$' THEN 'image/jpeg'
        WHEN m.image_address ~* '\.(gif)$'  THEN 'image/gif'
        WHEN m.image_address ~* '\.(webp)$' THEN 'image/webp'
        ELSE 'application/octet-stream'
    END,
    0,
    m.image_address,
    m.from_user_id,
    m.created_at,
    m.message_id
FROM messages m
JOIN conversations c   ON c.conversation_id = m.conversation_id
JOIN channels ch       ON ch.channel_id     = c.channel_id
JOIN spaces s          ON s.space_id        = ch.space_id
JOIN margins m_marg    ON m_marg.margin_id  = s.margin_id
WHERE m.image_address IS NOT NULL;

ALTER TABLE messages DROP COLUMN image_address;
