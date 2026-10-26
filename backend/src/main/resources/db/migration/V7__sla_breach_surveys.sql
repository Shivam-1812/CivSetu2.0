-- Deadline breach tracking and cluster surveys.

-- Set once when the breach alert goes out; a restart or a second server can never send it twice
ALTER TABLE issues ADD COLUMN breach_notified_at timestamptz;
CREATE INDEX issues_open_deadline_idx ON issues (sla_due_at)
    WHERE status IN ('REPORTED', 'ASSIGNED', 'IN_PROGRESS') AND breach_notified_at IS NULL;

ALTER TABLE issue_updates DROP CONSTRAINT issue_updates_action_chk;
ALTER TABLE issue_updates ADD CONSTRAINT issue_updates_action_chk CHECK (action IN
    ('REPORTED', 'ASSIGNED', 'PLANNED', 'RESOLVED', 'REJECTED', 'RETURNED', 'REOPENED', 'REASSIGNED',
     'PRIORITY_RAISED', 'MARKED_DUPLICATE', 'SLA_BREACHED'));

-- A short questionnaire sent to the people who reported issues in one area
CREATE TABLE surveys (
    id              uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    title           varchar(150)   NOT NULL,
    description     varchar(2000),
    created_by      uuid           NOT NULL REFERENCES users (id),
    -- Set when an Authority creates it: the survey covers that department's issues only
    department_id   uuid           REFERENCES departments (id),
    center          geometry(Point, 4326) NOT NULL,
    radius_m        integer        NOT NULL CONSTRAINT surveys_radius_chk CHECK (radius_m BETWEEN 50 AND 5000),
    status          varchar(10)    NOT NULL DEFAULT 'OPEN' CONSTRAINT surveys_status_chk CHECK (status IN ('OPEN', 'CLOSED')),
    closes_at       timestamptz    NOT NULL,
    created_at      timestamptz    NOT NULL DEFAULT now(),
    updated_at      timestamptz    NOT NULL DEFAULT now()
);

CREATE TABLE survey_questions (
    id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    survey_id   uuid          NOT NULL REFERENCES surveys (id) ON DELETE CASCADE,
    position    integer       NOT NULL,
    text        varchar(300)  NOT NULL,
    type        varchar(10)   NOT NULL CONSTRAINT survey_questions_type_chk CHECK (type IN ('RATING', 'YES_NO', 'TEXT')),
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT survey_questions_position_unique UNIQUE (survey_id, position)
);

-- Who was invited; only they can answer
CREATE TABLE survey_targets (
    survey_id  uuid  NOT NULL REFERENCES surveys (id) ON DELETE CASCADE,
    user_id    uuid  NOT NULL REFERENCES users (id),
    PRIMARY KEY (survey_id, user_id)
);

CREATE INDEX survey_targets_user_idx ON survey_targets (user_id);

-- One response per person per survey
CREATE TABLE survey_responses (
    id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    survey_id   uuid          NOT NULL REFERENCES surveys (id) ON DELETE CASCADE,
    user_id     uuid          NOT NULL REFERENCES users (id),
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT survey_responses_one_per_user UNIQUE (survey_id, user_id)
);

CREATE TABLE survey_answers (
    id           uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    response_id  uuid          NOT NULL REFERENCES survey_responses (id) ON DELETE CASCADE,
    question_id  uuid          NOT NULL REFERENCES survey_questions (id) ON DELETE CASCADE,
    rating       integer       CONSTRAINT survey_answers_rating_chk CHECK (rating BETWEEN 1 AND 5),
    yes_no       boolean,
    text         varchar(1000),
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT survey_answers_one_per_question UNIQUE (response_id, question_id)
);
