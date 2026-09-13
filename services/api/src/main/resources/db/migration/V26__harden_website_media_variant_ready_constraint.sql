ALTER TABLE website_media_variant
    ADD CONSTRAINT website_media_variant_ready_metadata_complete_check
    CHECK (
        status <> 'READY'
        OR COALESCE(
            storage_key IS NOT NULL
            AND mime_type = 'image/webp'
            AND byte_size > 0
            AND width = target_width
            AND height > 0,
            FALSE
        )
    );
