CREATE TABLE website_page (
    id UUID PRIMARY KEY,
    hotel_id UUID REFERENCES hotel(id) ON DELETE RESTRICT,
    parent_id UUID REFERENCES website_page(id) ON DELETE RESTRICT,
    page_type VARCHAR(30) NOT NULL CHECK (page_type IN ('SECTION', 'HOTEL_LANDING')),
    draft_slug VARCHAR(120) NOT NULL,
    published_slug VARCHAR(120) NOT NULL,
    draft_path VARCHAR(255) NOT NULL,
    published_path VARCHAR(255) NOT NULL,
    draft_menu_label VARCHAR(100) NOT NULL,
    published_menu_label VARCHAR(100) NOT NULL,
    draft_menu_visible BOOLEAN NOT NULL DEFAULT FALSE,
    published_menu_visible BOOLEAN NOT NULL DEFAULT FALSE,
    draft_menu_order INTEGER NOT NULL DEFAULT 0 CHECK (draft_menu_order >= 0),
    published_menu_order INTEGER NOT NULL DEFAULT 0 CHECK (published_menu_order >= 0),
    draft_content JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(draft_content) = 'object'),
    published_content JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(published_content) = 'object'),
    draft_version INTEGER NOT NULL DEFAULT 1 CHECK (draft_version > 0),
    published_version INTEGER NOT NULL DEFAULT 1 CHECK (published_version > 0),
    published_from_draft_version INTEGER CHECK (
        published_from_draft_version IS NULL
        OR (published_from_draft_version > 0 AND published_from_draft_version <= draft_version)
    ),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES staff_member(id) ON DELETE SET NULL,
    CHECK (parent_id IS NULL OR parent_id <> id),
    CHECK (
        (page_type = 'SECTION' AND hotel_id IS NULL AND parent_id IS NULL)
        OR (page_type = 'HOTEL_LANDING' AND hotel_id IS NOT NULL AND parent_id IS NOT NULL)
    ),
    CHECK (draft_slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CHECK (published_slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CHECK (draft_path ~ '^/[a-z0-9]+(-[a-z0-9]+)*(?:/[a-z0-9]+(-[a-z0-9]+)*)*$'),
    CHECK (published_path ~ '^/[a-z0-9]+(-[a-z0-9]+)*(?:/[a-z0-9]+(-[a-z0-9]+)*)*$'),
    UNIQUE (draft_path),
    UNIQUE (published_path)
);

CREATE UNIQUE INDEX website_page_hotel_landing_hotel_id_uk
    ON website_page (hotel_id)
    WHERE page_type = 'HOTEL_LANDING';

CREATE INDEX website_page_draft_menu_idx
    ON website_page (parent_id, draft_menu_visible, draft_menu_order, id);

CREATE INDEX website_page_published_menu_idx
    ON website_page (parent_id, published_menu_visible, published_menu_order, id);

CREATE TABLE website_page_version (
    id BIGSERIAL PRIMARY KEY,
    page_id UUID NOT NULL REFERENCES website_page(id) ON DELETE RESTRICT,
    version INTEGER NOT NULL CHECK (version > 0),
    page_snapshot JSONB NOT NULL CHECK (jsonb_typeof(page_snapshot) = 'object'),
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_by UUID REFERENCES staff_member(id) ON DELETE SET NULL,
    UNIQUE (page_id, version)
);

CREATE INDEX website_page_version_page_published_idx
    ON website_page_version (page_id, published_at DESC);

CREATE TABLE website_page_audit (
    id BIGSERIAL PRIMARY KEY,
    page_id UUID NOT NULL REFERENCES website_page(id) ON DELETE RESTRICT,
    action VARCHAR(30) NOT NULL CHECK (action IN ('DRAFT_SAVED', 'PUBLISHED', 'MIGRATED')),
    actor_id UUID REFERENCES staff_member(id) ON DELETE SET NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(details) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX website_page_audit_page_created_idx
    ON website_page_audit (page_id, created_at DESC);

-- Stable identifiers keep the initial page tree addressable without a UUID extension.
INSERT INTO website_page (
    id,
    hotel_id,
    parent_id,
    page_type,
    draft_slug,
    published_slug,
    draft_path,
    published_path,
    draft_menu_label,
    published_menu_label,
    draft_menu_visible,
    published_menu_visible,
    draft_menu_order,
    published_menu_order,
    draft_content,
    published_content,
    draft_version,
    published_version,
    published_from_draft_version
) VALUES (
    '12000000-0000-0000-0000-000000000001',
    NULL,
    NULL,
    'SECTION',
    'stays',
    'stays',
    '/stays',
    '/stays',
    '숙소',
    '숙소',
    TRUE,
    TRUE,
    0,
    0,
    '{}'::jsonb,
    '{}'::jsonb,
    1,
    1,
    1
);

-- Only existing legacy content is copied. New databases receive the section above;
-- a repeatable development page seed must add landing pages after it inserts content.
WITH landing_source(page_id, hotel_id, slug, menu_order) AS (
    VALUES
        ('12000000-0000-0000-0000-000000000002'::uuid, '11000000-0000-0000-0000-000000000001'::uuid, 'sokcho', 10),
        ('12000000-0000-0000-0000-000000000003'::uuid, '11000000-0000-0000-0000-000000000002'::uuid, 'seoraksan', 20),
        ('12000000-0000-0000-0000-000000000004'::uuid, '11000000-0000-0000-0000-000000000003'::uuid, 'jeju', 30)
)
INSERT INTO website_page (
    id,
    hotel_id,
    parent_id,
    page_type,
    draft_slug,
    published_slug,
    draft_path,
    published_path,
    draft_menu_label,
    published_menu_label,
    draft_menu_visible,
    published_menu_visible,
    draft_menu_order,
    published_menu_order,
    draft_content,
    published_content,
    draft_version,
    published_version,
    published_from_draft_version,
    updated_at,
    updated_by
)
SELECT source.page_id,
       source.hotel_id,
       '12000000-0000-0000-0000-000000000001'::uuid,
       'HOTEL_LANDING',
       source.slug,
       source.slug,
       '/stays/' || source.slug,
       '/stays/' || source.slug,
       hotel.name,
       hotel.name,
       TRUE,
       TRUE,
       source.menu_order,
       source.menu_order,
       legacy.draft_content,
       legacy.published_content,
       legacy.draft_version,
       legacy.published_version,
       CASE
           WHEN legacy.draft_content = legacy.published_content THEN legacy.draft_version
           ELSE NULL
       END,
       legacy.updated_at,
       legacy.updated_by
  FROM landing_source source
  JOIN hotel_web_content legacy ON legacy.hotel_id = source.hotel_id
  JOIN hotel ON hotel.id = source.hotel_id;

-- Preserve legacy immutable versions when they exist. Historical page metadata did
-- not exist before this migration, so the initial path/menu metadata is used here.
INSERT INTO website_page_version (page_id, version, page_snapshot, published_at, published_by)
SELECT page.id,
       legacy.version,
       jsonb_build_object(
           'pageType', page.page_type,
           'hotelId', page.hotel_id,
           'parentId', page.parent_id,
           'slug', page.published_slug,
           'path', page.published_path,
           'menu', jsonb_build_object(
               'label', page.published_menu_label,
               'visible', page.published_menu_visible,
               'order', page.published_menu_order
           ),
           'publishedFromDraftVersion', page.published_from_draft_version,
           'content', legacy.content
       ),
       legacy.published_at,
       legacy.published_by
  FROM hotel_web_content_version legacy
  JOIN website_page page
    ON page.hotel_id = legacy.hotel_id
   AND page.page_type = 'HOTEL_LANDING';

-- The initial CMS seed has no legacy version row. Retain its current published
-- document as a baseline without inventing a publisher identity.
INSERT INTO website_page_version (page_id, version, page_snapshot, published_at, published_by)
SELECT page.id,
       page.published_version,
       jsonb_build_object(
           'pageType', page.page_type,
           'hotelId', page.hotel_id,
           'parentId', page.parent_id,
           'slug', page.published_slug,
           'path', page.published_path,
           'menu', jsonb_build_object(
               'label', page.published_menu_label,
               'visible', page.published_menu_visible,
               'order', page.published_menu_order
           ),
           'publishedFromDraftVersion', page.published_from_draft_version,
           'content', page.published_content
       ),
       page.updated_at,
       NULL
  FROM website_page page
 WHERE page.page_type = 'HOTEL_LANDING'
   AND NOT EXISTS (
       SELECT 1
         FROM website_page_version version
        WHERE version.page_id = page.id
          AND version.version = page.published_version
   );

-- Copy actionable legacy audit records with their original actor and timestamp.
INSERT INTO website_page_audit (page_id, action, actor_id, details, created_at)
SELECT page.id,
       legacy.action,
       legacy.actor_id,
       jsonb_build_object(
           'source', 'hotel_web_content_audit',
           'legacyHotelId', legacy.hotel_id
       ),
       legacy.created_at
  FROM hotel_web_content_audit legacy
  JOIN website_page page
    ON page.hotel_id = legacy.hotel_id
   AND page.page_type = 'HOTEL_LANDING';

-- Make the source of the new page records explicit without fabricating a staff actor.
INSERT INTO website_page_audit (page_id, action, actor_id, details, created_at)
SELECT page.id,
       'MIGRATED',
       NULL,
       jsonb_build_object(
           'source', CASE
               WHEN page.page_type = 'SECTION' THEN 'V9__website_pages.sql'
               ELSE 'hotel_web_content'
           END,
           'legacyHotelId', page.hotel_id
       ),
       page.updated_at
  FROM website_page page;
