-- Refresh tokens for login sessions. Only a SHA-256 hash of the token is stored.
-- Each login starts a family; every refresh rotates to a new token in the same family.
-- Presenting an already-rotated token revokes the whole family (token theft signal).

CREATE TABLE refresh_tokens (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id       uuid         NOT NULL,
    token_hash      varchar(64)  NOT NULL UNIQUE,
    expires_at      timestamptz  NOT NULL,
    revoked_at      timestamptz,
    replaced_by_id  uuid         REFERENCES refresh_tokens (id),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
