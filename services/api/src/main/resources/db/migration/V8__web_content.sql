CREATE TABLE hotel_web_content (
    hotel_id UUID PRIMARY KEY REFERENCES hotel(id),
    draft_content JSONB NOT NULL DEFAULT '{}'::jsonb,
    published_content JSONB NOT NULL DEFAULT '{}'::jsonb,
    draft_version INTEGER NOT NULL DEFAULT 1 CHECK (draft_version > 0),
    published_version INTEGER NOT NULL DEFAULT 1 CHECK (published_version > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES staff_member(id)
);

CREATE TABLE hotel_web_content_version (
    id BIGSERIAL PRIMARY KEY,
    hotel_id UUID NOT NULL REFERENCES hotel(id),
    version INTEGER NOT NULL,
    content JSONB NOT NULL,
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_by UUID NOT NULL REFERENCES staff_member(id),
    UNIQUE (hotel_id, version)
);

CREATE TABLE hotel_web_content_audit (
    id BIGSERIAL PRIMARY KEY,
    hotel_id UUID NOT NULL REFERENCES hotel(id),
    action VARCHAR(30) NOT NULL CHECK (action IN ('DRAFT_SAVED', 'PUBLISHED')),
    actor_id UUID NOT NULL REFERENCES staff_member(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO hotel_web_content (hotel_id)
SELECT id FROM hotel;
