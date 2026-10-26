-- Community engagement (votes, comments), in-app notifications, duplicate handling and
-- community escalation of priority.

-- One vote per citizen per issue
CREATE TABLE issue_votes (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id    uuid         NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    user_id     uuid         NOT NULL REFERENCES users (id),
    value       varchar(10)  NOT NULL CONSTRAINT issue_votes_value_chk CHECK (value IN ('AGREE', 'DISAGREE')),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT issue_votes_one_per_user UNIQUE (issue_id, user_id)
);

CREATE TABLE issue_comments (
    id             uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id       uuid           NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    user_id        uuid           NOT NULL REFERENCES users (id),
    body           varchar(1000)  NOT NULL,
    -- Moderation hides a comment but keeps it, with who hid it and why
    hidden         boolean        NOT NULL DEFAULT false,
    hidden_by      uuid           REFERENCES users (id),
    hidden_reason  varchar(300),
    -- Insertion order; timestamps alone can tie when two comments land in the same clock tick
    seq            bigserial      NOT NULL,
    created_at     timestamptz    NOT NULL DEFAULT now(),
    updated_at     timestamptz    NOT NULL DEFAULT now()
);

CREATE INDEX issue_comments_issue_idx ON issue_comments (issue_id, seq);
CREATE INDEX issue_comments_user_idx ON issue_comments (issue_id, user_id);

CREATE TABLE notifications (
    id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        varchar(30)   NOT NULL,
    title       varchar(150)  NOT NULL,
    body        varchar(1000) NOT NULL,
    issue_id    uuid          REFERENCES issues (id) ON DELETE CASCADE,
    read_at     timestamptz,
    -- Order of creation; also the reference point for replaying missed live events
    seq         bigserial     NOT NULL,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX notifications_user_seq_idx ON notifications (user_id, seq DESC);
CREATE INDEX notifications_unread_idx ON notifications (user_id) WHERE read_at IS NULL;

-- Community escalation happens at most once per issue
ALTER TABLE issues ADD COLUMN priority_raised_by_community boolean NOT NULL DEFAULT false;

-- History in exact insertion order (existing rows are numbered in their stored order)
ALTER TABLE issue_updates ADD COLUMN seq bigserial NOT NULL;
CREATE INDEX issue_updates_issue_seq_idx ON issue_updates (issue_id, seq);

-- New history steps
ALTER TABLE issue_updates DROP CONSTRAINT issue_updates_action_chk;
ALTER TABLE issue_updates ADD CONSTRAINT issue_updates_action_chk CHECK (action IN
    ('REPORTED', 'ASSIGNED', 'PLANNED', 'RESOLVED', 'REJECTED', 'RETURNED', 'REOPENED', 'REASSIGNED',
     'PRIORITY_RAISED', 'MARKED_DUPLICATE'));

-- Distance queries in metres ("issues within 200 m") use this index
CREATE INDEX issues_location_geography_idx ON issues USING gist ((location::geography));
