CREATE TABLE website_preview_grant (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (char_length(token_hash) = 64),
    page_id UUID NOT NULL REFERENCES website_page(id) ON DELETE CASCADE,
    locale VARCHAR(2) NOT NULL CHECK (locale IN ('ko', 'en')),
    draft_version INTEGER NOT NULL CHECK (draft_version > 0),
    preview_path VARCHAR(255) NOT NULL,
    issued_by UUID NOT NULL REFERENCES staff_member(id),
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID REFERENCES staff_member(id),
    CHECK (expires_at > issued_at),
    CHECK (revoked_at IS NULL OR revoked_at >= issued_at)
);

CREATE INDEX website_preview_grant_issuer_page_locale_idx
    ON website_preview_grant (issued_by, page_id, locale, expires_at);

CREATE INDEX website_preview_grant_cleanup_idx
    ON website_preview_grant (expires_at, revoked_at);
