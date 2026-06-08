UPDATE users
SET profile_picture_url = REPLACE(profile_picture_url, 'https://margin.chat/api/files', 'https://files.margin.chat')
WHERE profile_picture_url LIKE 'https://margin.chat/api/files%';

UPDATE margins
SET icon_url = REPLACE(icon_url, 'https://margin.chat/api/files', 'https://files.margin.chat')
WHERE icon_url LIKE 'https://margin.chat/api/files%';

UPDATE stored_files
SET storage_url = REPLACE(storage_url, 'https://margin.chat/api/files', 'https://files.margin.chat')
WHERE storage_url LIKE 'https://margin.chat/api/files%';
