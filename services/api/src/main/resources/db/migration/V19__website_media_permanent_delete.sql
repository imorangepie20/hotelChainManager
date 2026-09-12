ALTER TABLE website_media_asset
    ADD COLUMN archived_at TIMESTAMPTZ;

UPDATE website_media_asset
   SET archived_at = updated_at
 WHERE status = 'ARCHIVED'
   AND archived_at IS NULL;
