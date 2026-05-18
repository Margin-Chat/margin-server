UPDATE users
SET profile_picture_url = '/api/files/user-profiles/' || substring(profile_picture_url FROM '/users/([^/]+)$')
WHERE profile_picture_url IS NOT NULL
  AND profile_picture_url ~ '/users/[^/]+$'
  AND profile_picture_url NOT LIKE '%/api/files/%';

UPDATE margins
SET icon_url = '/api/files/margin-icons/' || substring(icon_url FROM '/margins/([^/]+)$')
WHERE icon_url IS NOT NULL
  AND icon_url ~ '/margins/[^/]+$'
  AND icon_url NOT LIKE '%/api/files/%';

UPDATE messages
SET image_address = '/api/files/conversation-images/' || substring(image_address FROM '/conversations/([^/]+)$')
WHERE image_address IS NOT NULL
  AND image_address ~ '/conversations/[^/]+$'
  AND image_address NOT LIKE '%/api/files/%';
