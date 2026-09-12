ALTER TABLE website_page_translation
    DROP CONSTRAINT website_page_translation_review_version_check,
    ADD CONSTRAINT website_page_translation_review_version_check CHECK (
        (review_status = 'DRAFT' AND reviewed_draft_version IS NULL) OR
        (review_status <> 'DRAFT'
            AND reviewed_draft_version IS NOT NULL
            AND reviewed_draft_version > 0
            AND reviewed_draft_version <= draft_version)
    );
