CREATE TABLE website_media_asset (
    id UUID PRIMARY KEY,
    origin VARCHAR(20) NOT NULL CHECK (origin IN ('BUNDLED', 'UPLOADED')),
    delivery_path VARCHAR(255) NOT NULL UNIQUE,
    storage_key VARCHAR(255) UNIQUE,
    display_name VARCHAR(160) NOT NULL CHECK (length(btrim(display_name)) BETWEEN 1 AND 160),
    default_alt_text VARCHAR(200) NOT NULL CHECK (length(btrim(default_alt_text)) BETWEEN 1 AND 200),
    mime_type VARCHAR(100) NOT NULL CHECK (mime_type IN ('image/png', 'image/jpeg')),
    byte_size BIGINT NOT NULL CHECK (byte_size > 0 AND byte_size <= 10485760),
    width INTEGER NOT NULL CHECK (width > 0),
    height INTEGER NOT NULL CHECK (height > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE')),
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES staff_member(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES staff_member(id) ON DELETE SET NULL,
    CHECK (
        (origin = 'BUNDLED' AND storage_key IS NULL AND delivery_path ~ '^/images/[A-Za-z0-9][A-Za-z0-9._/-]*$')
        OR
        (origin = 'UPLOADED' AND storage_key IS NOT NULL
            AND delivery_path = '/api/website/media/' || id::text || '/content')
    )
);

CREATE TABLE website_media_usage (
    asset_id UUID NOT NULL REFERENCES website_media_asset(id) ON DELETE RESTRICT,
    page_id UUID NOT NULL REFERENCES website_page(id) ON DELETE RESTRICT,
    document_state VARCHAR(20) NOT NULL CHECK (document_state IN ('DRAFT', 'PUBLISHED')),
    field_path VARCHAR(200) NOT NULL CHECK (length(btrim(field_path)) BETWEEN 1 AND 200),
    alt_text VARCHAR(200) NOT NULL CHECK (length(btrim(alt_text)) BETWEEN 1 AND 200),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (page_id, document_state, field_path)
);

CREATE INDEX website_media_usage_asset_idx
    ON website_media_usage (asset_id, document_state, page_id);

INSERT INTO website_media_asset (
    id, origin, delivery_path, storage_key, display_name, default_alt_text,
    mime_type, byte_size, width, height, status, version
) VALUES (
    '14000000-0000-0000-0000-000000000001',
    'BUNDLED',
    '/images/sokcho-coast-hero.png',
    NULL,
    '속초 해안 메인 이미지',
    '동해와 설악산을 바라보는 속초 해안',
    'image/png',
    2336001,
    1672,
    941,
    'ACTIVE',
    1
) ON CONFLICT (id) DO NOTHING;

-- Current documents become media-backed without mutating immutable page snapshots.
UPDATE website_page page
   SET draft_content = jsonb_set(
           jsonb_set(page.draft_content, '{heroAssetId}', to_jsonb('14000000-0000-0000-0000-000000000001'::text), true),
           '{heroImage}', to_jsonb('/images/sokcho-coast-hero.png'::text), true),
       published_content = jsonb_set(
           jsonb_set(page.published_content, '{heroAssetId}', to_jsonb('14000000-0000-0000-0000-000000000001'::text), true),
           '{heroImage}', to_jsonb('/images/sokcho-coast-hero.png'::text), true)
 WHERE page.page_type = 'HOTEL_LANDING'
   AND (page.draft_content ? 'heroImage' OR page.published_content ? 'heroImage');

UPDATE website_page page
   SET draft_content = jsonb_set(
           page.draft_content,
           '{blocks}',
           (SELECT jsonb_agg(
                   CASE WHEN block.value ->> 'type' = 'HERO' THEN
                       jsonb_set(
                           jsonb_set(block.value, '{imageAssetId}', to_jsonb('14000000-0000-0000-0000-000000000001'::text), true),
                           '{imageSrc}', to_jsonb('/images/sokcho-coast-hero.png'::text), true)
                   ELSE block.value
                   END
                   ORDER BY block.ordinality)
              FROM jsonb_array_elements(page.draft_content -> 'blocks') WITH ORDINALITY AS block(value, ordinality)),
           true),
       published_content = jsonb_set(
           page.published_content,
           '{blocks}',
           (SELECT jsonb_agg(
                   CASE WHEN block.value ->> 'type' = 'HERO' THEN
                       jsonb_set(
                           jsonb_set(block.value, '{imageAssetId}', to_jsonb('14000000-0000-0000-0000-000000000001'::text), true),
                           '{imageSrc}', to_jsonb('/images/sokcho-coast-hero.png'::text), true)
                   ELSE block.value
                   END
                   ORDER BY block.ordinality)
              FROM jsonb_array_elements(page.published_content -> 'blocks') WITH ORDINALITY AS block(value, ordinality)),
           true)
 WHERE page.page_type IN ('HOME_PAGE', 'CONTENT_PAGE')
   AND (jsonb_typeof(page.draft_content -> 'blocks') = 'array'
        OR jsonb_typeof(page.published_content -> 'blocks') = 'array');

INSERT INTO website_media_usage (asset_id, page_id, document_state, field_path, alt_text)
SELECT asset.id,
       page.id,
       'DRAFT',
       'heroAssetId',
       page.draft_content ->> 'heroAlt'
  FROM website_page page
  JOIN website_media_asset asset
    ON asset.id = (page.draft_content ->> 'heroAssetId')::uuid
 WHERE page.page_type = 'HOTEL_LANDING'
   AND page.draft_content ? 'heroAssetId';

INSERT INTO website_media_usage (asset_id, page_id, document_state, field_path, alt_text)
SELECT asset.id,
       page.id,
       'PUBLISHED',
       'heroAssetId',
       page.published_content ->> 'heroAlt'
  FROM website_page page
  JOIN website_media_asset asset
    ON asset.id = (page.published_content ->> 'heroAssetId')::uuid
 WHERE page.page_type = 'HOTEL_LANDING'
   AND page.published_content ? 'heroAssetId';

INSERT INTO website_media_usage (asset_id, page_id, document_state, field_path, alt_text)
SELECT asset.id,
       page.id,
       'DRAFT',
       format('blocks[%s].imageAssetId', block.ordinality - 1),
       block.value ->> 'imageAlt'
  FROM website_page page
 CROSS JOIN LATERAL jsonb_array_elements(page.draft_content -> 'blocks') WITH ORDINALITY AS block(value, ordinality)
  JOIN website_media_asset asset
    ON asset.id = (block.value ->> 'imageAssetId')::uuid
 WHERE page.page_type IN ('HOME_PAGE', 'CONTENT_PAGE')
   AND block.value ->> 'type' = 'HERO'
   AND block.value ? 'imageAssetId';

INSERT INTO website_media_usage (asset_id, page_id, document_state, field_path, alt_text)
SELECT asset.id,
       page.id,
       'PUBLISHED',
       format('blocks[%s].imageAssetId', block.ordinality - 1),
       block.value ->> 'imageAlt'
  FROM website_page page
 CROSS JOIN LATERAL jsonb_array_elements(page.published_content -> 'blocks') WITH ORDINALITY AS block(value, ordinality)
  JOIN website_media_asset asset
    ON asset.id = (block.value ->> 'imageAssetId')::uuid
 WHERE page.page_type IN ('HOME_PAGE', 'CONTENT_PAGE')
   AND block.value ->> 'type' = 'HERO'
   AND block.value ? 'imageAssetId';
