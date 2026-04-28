ALTER TABLE margins
    ADD COLUMN deleted_at TIMESTAMP;

ALTER TABLE spaces
    ADD COLUMN deleted_at TIMESTAMP;

ALTER TABLE conversations
    ADD COLUMN deleted_at TIMESTAMP;

ALTER TABLE channels
    ADD COLUMN deleted_at TIMESTAMP;
