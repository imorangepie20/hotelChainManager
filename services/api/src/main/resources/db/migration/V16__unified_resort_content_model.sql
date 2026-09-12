ALTER TABLE website_page
    ADD COLUMN content_kind VARCHAR(20);

UPDATE website_page
   SET content_kind = CASE page_type
       WHEN 'HOME_PAGE' THEN 'HOME'
       WHEN 'HOTEL_LANDING' THEN 'DESTINATION'
       WHEN 'CONTENT_PAGE' THEN 'BRAND'
       ELSE NULL
   END;

ALTER TABLE website_page
    DROP CONSTRAINT website_page_owner_check,
    ADD CONSTRAINT website_page_owner_check
        CHECK (
            (page_type = 'SECTION')
            OR (page_type = 'HOME_PAGE' AND hotel_id IS NULL AND parent_id IS NULL)
            OR (page_type = 'HOTEL_LANDING' AND hotel_id IS NOT NULL AND parent_id IS NOT NULL)
            OR (page_type = 'CONTENT_PAGE' AND parent_id IS NOT NULL)
        ),
    ADD CONSTRAINT website_page_content_kind_check
        CHECK (
            (page_type = 'SECTION' AND content_kind IS NULL)
            OR (page_type = 'HOME_PAGE' AND content_kind = 'HOME')
            OR (page_type = 'HOTEL_LANDING' AND content_kind = 'DESTINATION')
            OR (page_type = 'CONTENT_PAGE' AND content_kind IN (
                'ROOM', 'DINING', 'FACILITY', 'EXPERIENCE', 'PROMOTION', 'GUIDE', 'BRAND'
            ))
        );

CREATE INDEX website_page_content_kind_idx
    ON website_page (content_kind, lifecycle_status, published_path);

CREATE TABLE website_page_room_type (
    page_id UUID NOT NULL REFERENCES website_page(id) ON DELETE RESTRICT,
    document_state VARCHAR(12) NOT NULL CHECK (document_state IN ('DRAFT', 'PUBLISHED')),
    room_type_id UUID NOT NULL REFERENCES room_type(id) ON DELETE RESTRICT,
    PRIMARY KEY (page_id, document_state, room_type_id)
);

CREATE INDEX website_page_room_type_lookup_idx
    ON website_page_room_type (room_type_id, document_state, page_id);

CREATE TABLE website_page_hotel (
    page_id UUID NOT NULL REFERENCES website_page(id) ON DELETE RESTRICT,
    document_state VARCHAR(12) NOT NULL CHECK (document_state IN ('DRAFT', 'PUBLISHED')),
    hotel_id UUID NOT NULL REFERENCES hotel(id) ON DELETE RESTRICT,
    PRIMARY KEY (page_id, document_state, hotel_id)
);

CREATE INDEX website_page_hotel_lookup_idx
    ON website_page_hotel (hotel_id, document_state, page_id);

CREATE TABLE website_page_relation (
    page_id UUID NOT NULL REFERENCES website_page(id) ON DELETE RESTRICT,
    document_state VARCHAR(12) NOT NULL CHECK (document_state IN ('DRAFT', 'PUBLISHED')),
    target_page_id UUID NOT NULL REFERENCES website_page(id) ON DELETE RESTRICT,
    relation_type VARCHAR(20) NOT NULL CHECK (relation_type IN ('RELATED', 'MANUAL_CARD')),
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    PRIMARY KEY (page_id, document_state, target_page_id),
    UNIQUE (page_id, document_state, relation_type, display_order),
    CHECK (page_id <> target_page_id)
);

CREATE INDEX website_page_relation_target_idx
    ON website_page_relation (target_page_id, document_state, page_id);
