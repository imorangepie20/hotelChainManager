-- Expand only: legacy Korean documents, versions and redirect rows are untouched.
CREATE TABLE website_page_translation (
    page_id UUID NOT NULL REFERENCES website_page(id) ON DELETE CASCADE,
    locale VARCHAR(2) NOT NULL CHECK (locale = 'en'),
    draft_content JSONB NOT NULL CHECK (jsonb_typeof(draft_content) = 'object'),
    published_content JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(published_content) = 'object'),
    draft_connections JSONB NOT NULL DEFAULT '{}'::jsonb,
    published_connections JSONB NOT NULL DEFAULT '{}'::jsonb,
    draft_path VARCHAR(255) NOT NULL UNIQUE CHECK (draft_path ~ '^/en(?:/[a-z0-9]+(?:-[a-z0-9]+)*)*$'),
    published_path VARCHAR(255) UNIQUE,
    draft_menu_label VARCHAR(100) NOT NULL,
    published_menu_label VARCHAR(100),
    draft_menu_visible BOOLEAN NOT NULL DEFAULT FALSE,
    published_menu_visible BOOLEAN NOT NULL DEFAULT FALSE,
    draft_menu_order INTEGER NOT NULL DEFAULT 0 CHECK (draft_menu_order >= 0),
    published_menu_order INTEGER NOT NULL DEFAULT 0 CHECK (published_menu_order >= 0),
    draft_version INTEGER NOT NULL DEFAULT 1 CHECK (draft_version > 0),
    published_version INTEGER NOT NULL DEFAULT 0 CHECK (published_version >= 0),
    published_from_draft_version INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    updated_by UUID REFERENCES staff_member(id) ON DELETE SET NULL,
    PRIMARY KEY (page_id, locale)
);

CREATE TABLE website_page_translation_version (
    page_id UUID NOT NULL,
    locale VARCHAR(2) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    page_snapshot JSONB NOT NULL CHECK (jsonb_typeof(page_snapshot) = 'object'),
    published_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    published_by UUID REFERENCES staff_member(id) ON DELETE SET NULL,
    PRIMARY KEY (page_id, locale, version),
    FOREIGN KEY (page_id, locale) REFERENCES website_page_translation(page_id, locale) ON DELETE CASCADE
);

ALTER TABLE website_media_usage ADD COLUMN locale VARCHAR(2) NOT NULL DEFAULT 'ko' CHECK (locale IN ('ko', 'en'));
ALTER TABLE website_media_usage DROP CONSTRAINT website_media_usage_pkey,
    ADD PRIMARY KEY (page_id, locale, document_state, field_path);

CREATE TABLE website_translation_redirect (
    source_path VARCHAR(255) PRIMARY KEY CHECK (source_path LIKE '/en/%'),
    target_path VARCHAR(255) NOT NULL UNIQUE CHECK (target_path LIKE '/en/%'),
    page_id UUID NOT NULL REFERENCES website_page(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    created_by UUID REFERENCES staff_member(id) ON DELETE SET NULL,
    CHECK (source_path <> target_path)
);
