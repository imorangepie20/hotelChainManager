ALTER TABLE website_media_asset
    DROP CONSTRAINT IF EXISTS website_media_asset_status_check;

ALTER TABLE website_media_asset
    ADD CONSTRAINT website_media_asset_status_check
    CHECK (status IN ('ACTIVE', 'ARCHIVED'));
