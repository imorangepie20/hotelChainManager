ALTER TABLE website_page
    ADD COLUMN lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN lifecycle_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD COLUMN archived_by UUID REFERENCES staff_member(id) ON DELETE SET NULL,
    ADD CONSTRAINT website_page_lifecycle_status_check
        CHECK (lifecycle_status IN ('ACTIVE', 'ARCHIVED')),
    ADD CONSTRAINT website_page_lifecycle_version_check
        CHECK (lifecycle_version > 0),
    ADD CONSTRAINT website_page_archived_state_check
        CHECK (
            (lifecycle_status = 'ACTIVE' AND archived_at IS NULL AND archived_by IS NULL)
            OR (lifecycle_status = 'ARCHIVED' AND archived_at IS NOT NULL)
        );

ALTER TABLE website_page_audit
    DROP CONSTRAINT website_page_audit_action_check,
    ADD CONSTRAINT website_page_audit_action_check
        CHECK (action IN ('CREATED', 'DRAFT_SAVED', 'PUBLISHED', 'MIGRATED', 'ARCHIVED', 'RESTORED'));

CREATE INDEX website_page_lifecycle_parent_idx
    ON website_page (lifecycle_status, parent_id, draft_menu_order, id);
