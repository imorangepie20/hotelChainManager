CREATE TABLE website_redirect (
    source_path VARCHAR(255) PRIMARY KEY,
    target_path VARCHAR(255) NOT NULL UNIQUE,
    page_id UUID NOT NULL REFERENCES website_page(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    created_by UUID REFERENCES staff_member(id) ON DELETE SET NULL,
    CHECK (source_path <> target_path)
);

ALTER TABLE website_page_audit
    DROP CONSTRAINT website_page_audit_action_check,
    ADD CONSTRAINT website_page_audit_action_check
        CHECK (action IN ('CREATED', 'DRAFT_SAVED', 'PUBLISHED', 'MIGRATED', 'ARCHIVED', 'RESTORED',
            'VERSION_RESTORED', 'PAGE_MOVED', 'PAGE_MOVED_WITH_REDIRECT'));
