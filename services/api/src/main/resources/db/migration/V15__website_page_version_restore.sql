ALTER TABLE website_page_audit
    DROP CONSTRAINT website_page_audit_action_check,
    ADD CONSTRAINT website_page_audit_action_check
        CHECK (action IN ('CREATED', 'DRAFT_SAVED', 'PUBLISHED', 'MIGRATED', 'ARCHIVED', 'RESTORED', 'VERSION_RESTORED'));
