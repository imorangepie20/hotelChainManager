-- Keep every existing non-root URL form unchanged while allowing the fixed
-- homepage record to use the root path.
ALTER TABLE website_page
    DROP CONSTRAINT website_page_type_check,
    DROP CONSTRAINT website_page_owner_check,
    DROP CONSTRAINT website_page_draft_path_check,
    DROP CONSTRAINT website_page_published_path_check;

ALTER TABLE website_page
    ADD CONSTRAINT website_page_type_check
        CHECK (page_type IN ('SECTION', 'HOTEL_LANDING', 'CONTENT_PAGE', 'HOME_PAGE')),
    ADD CONSTRAINT website_page_owner_check
        CHECK (
            (page_type = 'SECTION' AND hotel_id IS NULL AND parent_id IS NULL)
            OR (page_type = 'HOTEL_LANDING' AND hotel_id IS NOT NULL AND parent_id IS NOT NULL)
            OR (page_type = 'CONTENT_PAGE' AND hotel_id IS NULL AND parent_id IS NOT NULL)
            OR (page_type = 'HOME_PAGE' AND hotel_id IS NULL AND parent_id IS NULL)
        ),
    ADD CONSTRAINT website_page_draft_path_check
        CHECK (
            draft_path = '/'
            OR draft_path ~ '^/[a-z0-9]+(-[a-z0-9]+)*(?:/[a-z0-9]+(-[a-z0-9]+)*)*$'
        ),
    ADD CONSTRAINT website_page_published_path_check
        CHECK (
            published_path = '/'
            OR published_path ~ '^/[a-z0-9]+(-[a-z0-9]+)*(?:/[a-z0-9]+(-[a-z0-9]+)*)*$'
        );

-- A partial unique index makes HOME_PAGE a fixed singleton without changing
-- uniqueness or ownership rules for any other page type.
CREATE UNIQUE INDEX website_page_home_page_singleton_uk
    ON website_page (page_type)
    WHERE page_type = 'HOME_PAGE';

WITH home_document AS (
    SELECT $$
    {
      "seo": {
        "title": "STAY HANEUL | 가장 느린 하루를 위한 여정",
        "description": "속초, 설악산, 제주에서 자연의 리듬에 맞춘 휴식과 객실 예약을 만나보세요."
      },
      "blocks": [
        {
          "type": "HERO",
          "imageSrc": "/images/sokcho-coast-hero.png",
          "imageAlt": "동해와 설악산을 바라보는 가상의 속초 해안 호텔",
          "eyebrow": "STAY HANEUL",
          "title": "파도와 설악의 사이에서 가장 느린 하루를",
          "description": "동해의 수평선과 설악의 능선을 한눈에 담는 휴식.",
          "cta": {
            "label": "객실 예약하기",
            "href": "/#booking"
          }
        },
        {
          "type": "TEXT",
          "eyebrow": "A STAY TO REMEMBER",
          "title": "머무는 시간에 따라 장소는 더 깊어집니다.",
          "paragraphs": [
            "속초의 바다, 설악산의 능선, 제주의 바람을 따라 각기 다른 하루를 준비했습니다.",
            "객실을 고르고 싶은 순간에는 실제 판매 가능 객실과 요금만 확인할 수 있습니다."
          ]
        },
        {
          "type": "CTA",
          "eyebrow": "BOOK YOUR STAY",
          "title": "다음 여정을 예약하세요",
          "description": "날짜와 인원을 입력하면 현재 판매 가능한 객실을 바로 찾아드립니다.",
          "cta": {
            "label": "객실 검색하기",
            "href": "/#booking"
          }
        }
      ]
    }
    $$::jsonb AS content
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
SELECT
    '12000000-0000-0000-0000-000000000006'::uuid,
    NULL,
    NULL,
    'HOME_PAGE',
    'home',
    'home',
    '/',
    '/',
    '홈',
    '홈',
    FALSE,
    FALSE,
    0,
    0,
    content,
    content,
    1,
    1,
    1
FROM home_document;

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
 WHERE page.id = '12000000-0000-0000-0000-000000000006'::uuid;

INSERT INTO website_page_audit (page_id, action, actor_id, details)
SELECT page.id,
       'MIGRATED',
       NULL,
       jsonb_build_object(
           'source', 'V11__home_page.sql',
           'path', page.published_path
       )
  FROM website_page page
 WHERE page.id = '12000000-0000-0000-0000-000000000006'::uuid;
