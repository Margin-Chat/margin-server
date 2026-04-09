ALTER TABLE margin_invites
    DROP CONSTRAINT margin_invites_margin_id_fkey,
    ADD CONSTRAINT margin_invites_margin_id_fkey
        FOREIGN KEY (margin_id) REFERENCES margins (margin_id) ON DELETE CASCADE;