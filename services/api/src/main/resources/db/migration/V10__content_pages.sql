ALTER TABLE website_page
    DROP CONSTRAINT website_page_page_type_check,
    DROP CONSTRAINT website_page_check2;

ALTER TABLE website_page
    ADD CONSTRAINT website_page_type_check
        CHECK (page_type IN ('SECTION', 'HOTEL_LANDING', 'CONTENT_PAGE')),
    ADD CONSTRAINT website_page_owner_check
        CHECK (
            (page_type = 'SECTION' AND hotel_id IS NULL AND parent_id IS NULL)
            OR (page_type = 'HOTEL_LANDING' AND hotel_id IS NOT NULL AND parent_id IS NOT NULL)
            OR (page_type = 'CONTENT_PAGE' AND hotel_id IS NULL AND parent_id IS NOT NULL)
        );

ALTER TABLE website_page_audit
    DROP CONSTRAINT website_page_audit_action_check,
    ADD CONSTRAINT website_page_audit_action_check
        CHECK (action IN ('CREATED', 'DRAFT_SAVED', 'PUBLISHED', 'MIGRATED'));

INSERT INTO website_page (
    id, hotel_id, parent_id, page_type,
    draft_slug, published_slug, draft_path, published_path,
    draft_menu_label, published_menu_label, draft_menu_visible, published_menu_visible,
    draft_menu_order, published_menu_order, draft_content, published_content,
    draft_version, published_version, published_from_draft_version
) VALUES (
    '12000000-0000-0000-0000-000000000005',
    NULL, NULL, 'SECTION',
    'brand', 'brand', '/brand', '/brand',
    '브랜드', '브랜드', TRUE, TRUE,
    10, 10, '{}'::jsonb, '{}'::jsonb,
    1, 1, 1
) ON CONFLICT (id) DO NOTHING;
