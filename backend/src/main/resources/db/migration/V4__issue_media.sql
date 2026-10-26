-- Files attached to issues. Only metadata lives here; the bytes are in MediaStorage
-- (local disk in development, S3-compatible object storage in production).

CREATE TABLE issue_media (
    id            uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id      uuid          NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    uploaded_by   uuid          NOT NULL REFERENCES users (id),
    kind          varchar(20)   NOT NULL
        CONSTRAINT issue_media_kind_chk CHECK (kind IN ('IMAGE', 'VOICE', 'PLAN_DOC', 'RESOLUTION_PROOF', 'REJECTION_PROOF')),
    -- Server-generated; never derived from the uploaded file name
    storage_key   varchar(200)  NOT NULL UNIQUE,
    content_type  varchar(100)  NOT NULL,
    size_bytes    bigint        NOT NULL CONSTRAINT issue_media_size_chk CHECK (size_bytes > 0),
    created_at    timestamptz   NOT NULL DEFAULT now(),
    updated_at    timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX issue_media_issue_idx ON issue_media (issue_id, created_at);
