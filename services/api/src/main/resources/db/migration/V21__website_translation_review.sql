ALTER TABLE website_page_translation
  ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  ADD COLUMN reviewed_draft_version INTEGER,
  ADD CONSTRAINT website_page_translation_review_status_check
    CHECK (review_status IN ('DRAFT','IN_REVIEW','APPROVED','PUBLISHED')),
  ADD CONSTRAINT website_page_translation_review_version_check CHECK (
    (review_status = 'DRAFT' AND reviewed_draft_version IS NULL) OR
    (review_status <> 'DRAFT' AND reviewed_draft_version > 0 AND reviewed_draft_version <= draft_version)
  );

UPDATE website_page_translation
SET review_status='PUBLISHED', reviewed_draft_version=published_from_draft_version
WHERE published_content <> '{}'::jsonb
  AND published_from_draft_version = draft_version;

CREATE TABLE website_translation_review_event (
  id BIGSERIAL PRIMARY KEY,
  page_id UUID NOT NULL,
  locale VARCHAR(2) NOT NULL DEFAULT 'en' CHECK (locale='en'),
  action VARCHAR(30) NOT NULL CHECK (action IN
    ('REVIEW_REQUESTED','APPROVED','REJECTED','APPROVAL_INVALIDATED','PUBLISHED')),
  draft_version INTEGER NOT NULL CHECK (draft_version > 0),
  actor_id UUID REFERENCES staff_member(id) ON DELETE SET NULL,
  comment VARCHAR(2000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
  FOREIGN KEY (page_id, locale)
    REFERENCES website_page_translation(page_id, locale) ON DELETE CASCADE
);
CREATE INDEX website_translation_review_event_page_created_idx
  ON website_translation_review_event(page_id, locale,created_at DESC,id DESC);
