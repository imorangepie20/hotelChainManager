CREATE TABLE website_media_variant (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES website_media_asset(id) ON DELETE CASCADE,
    format VARCHAR(10) NOT NULL CHECK (format = 'WEBP'),
    target_width INTEGER NOT NULL CHECK (target_width IN (640, 1280)),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    storage_key VARCHAR(255),
    mime_type VARCHAR(64),
    byte_size BIGINT,
    width INTEGER,
    height INTEGER,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 3),
    next_attempt_at TIMESTAMPTZ,
    lease_expires_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (asset_id, format, target_width),
    CHECK ((status = 'READY') = COALESCE(storage_key IS NOT NULL AND mime_type = 'image/webp'
        AND byte_size > 0 AND width = target_width AND height > 0, FALSE))
);

CREATE INDEX website_media_variant_work_idx
    ON website_media_variant (status, next_attempt_at, lease_expires_at, created_at)
    WHERE status IN ('PENDING', 'PROCESSING', 'FAILED');

INSERT INTO website_media_variant (id, asset_id, format, target_width, status)
SELECT gen_random_uuid(), asset.id, 'WEBP', target.target_width, 'PENDING'
  FROM website_media_asset asset
 CROSS JOIN (VALUES (640), (1280)) AS target(target_width)
 WHERE asset.origin = 'UPLOADED'
   AND asset.status = 'ACTIVE'
   AND asset.width >= target.target_width
ON CONFLICT (asset_id, format, target_width) DO NOTHING;
