ALTER TABLE website_page_audit
    DROP CONSTRAINT website_page_audit_action_check,
    ADD CONSTRAINT website_page_audit_action_check
        CHECK (action IN ('CREATED', 'DRAFT_SAVED', 'PUBLISHED', 'MIGRATED', 'ARCHIVED', 'RESTORED',
            'VERSION_RESTORED', 'PAGE_MOVED'));

CREATE INDEX website_page_parent_draft_path_idx
    ON website_page (parent_id, lifecycle_status, draft_path, id);
