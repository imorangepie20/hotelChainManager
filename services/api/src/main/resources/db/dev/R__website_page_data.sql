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
    published_from_draft_version
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
       CASE WHEN legacy.draft_content = legacy.published_content THEN legacy.draft_version ELSE NULL END
  FROM landing_source source
  JOIN hotel ON hotel.id = source.hotel_id
  JOIN hotel_web_content legacy ON legacy.hotel_id = source.hotel_id
ON CONFLICT (hotel_id) WHERE page_type = 'HOTEL_LANDING' DO UPDATE
SET draft_content = EXCLUDED.draft_content,
    published_content = EXCLUDED.published_content,
    draft_version = EXCLUDED.draft_version,
    published_version = EXCLUDED.published_version,
    published_from_draft_version = EXCLUDED.published_from_draft_version
WHERE website_page.draft_content = '{}'::jsonb
  AND website_page.published_content = '{}'::jsonb;

INSERT INTO website_page_version (page_id, version, page_snapshot, published_by)
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
       NULL
  FROM website_page page
 WHERE page.page_type = 'HOTEL_LANDING'
   AND page.published_content <> '{}'::jsonb
   AND NOT EXISTS (
       SELECT 1
         FROM website_page_version version
         WHERE version.page_id = page.id
           AND version.version = page.published_version
    );

-- Seed landing documents are media-backed. Refresh only these three development
-- pages so a fresh database has the same catalog usage view as an upgraded one.
DELETE FROM website_media_usage usage
 USING website_page page
 WHERE usage.page_id = page.id
   AND page.page_type = 'HOTEL_LANDING'
   AND page.hotel_id IN (
       '11000000-0000-0000-0000-000000000001'::uuid,
       '11000000-0000-0000-0000-000000000002'::uuid,
       '11000000-0000-0000-0000-000000000003'::uuid
   );

INSERT INTO website_media_usage (asset_id, page_id, document_state, field_path, alt_text)
SELECT '14000000-0000-0000-0000-000000000001'::uuid,
       page.id,
       state.document_state,
       'heroAssetId',
       CASE WHEN state.document_state = 'DRAFT' THEN page.draft_content ->> 'heroAlt'
            ELSE page.published_content ->> 'heroAlt' END
  FROM website_page page
 CROSS JOIN (VALUES ('DRAFT'), ('PUBLISHED')) AS state(document_state)
 WHERE page.page_type = 'HOTEL_LANDING'
   AND page.hotel_id IN (
       '11000000-0000-0000-0000-000000000001'::uuid,
       '11000000-0000-0000-0000-000000000002'::uuid,
       '11000000-0000-0000-0000-000000000003'::uuid
   )
   AND (CASE WHEN state.document_state = 'DRAFT' THEN page.draft_content ELSE page.published_content END) ? 'heroAssetId';
