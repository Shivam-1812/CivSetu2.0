-- Automatic triage, the resolution workflow, the public issue history and the audit log.
-- Routing, priority and deadlines are decided by the system only; there is no admin override.

-- Priority is set by the AI advisor or the rule engine, never by a person
ALTER TABLE issues DROP CONSTRAINT issues_priority_source_chk;
ALTER TABLE issues ADD CONSTRAINT issues_priority_source_chk CHECK (priority_source IN ('RULE', 'AI'));
ALTER TABLE issues ADD COLUMN reopen_count integer NOT NULL DEFAULT 0;
ALTER TABLE issues ADD COLUMN flagged_for_review boolean NOT NULL DEFAULT false;
-- When the issue was last resolved or rejected (start of the reopen window)
ALTER TABLE issues ADD COLUMN closed_at timestamptz;
CREATE INDEX issues_authority_status_idx ON issues (assigned_authority_id, status);

-- One row per step in an issue's life. Rows are only ever added.
CREATE TABLE issue_updates (
    id               uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id         uuid          NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    -- NULL actor means the system (triage, automatic reassignment)
    actor_id         uuid          REFERENCES users (id),
    actor_role       varchar(20)   NOT NULL
        CONSTRAINT issue_updates_actor_chk CHECK (actor_role IN ('SYSTEM', 'CITIZEN', 'AUTHORITY')),
    action           varchar(20)   NOT NULL
        CONSTRAINT issue_updates_action_chk CHECK (action IN
            ('REPORTED', 'ASSIGNED', 'PLANNED', 'RESOLVED', 'REJECTED', 'RETURNED', 'REOPENED', 'REASSIGNED')),
    from_status      varchar(20),
    to_status        varchar(20)   NOT NULL,
    department_id    uuid          REFERENCES departments (id),
    remarks          text,
    action_taken     text,
    staff_allocated  text,
    resources_used   text,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX issue_updates_issue_idx ON issue_updates (issue_id, created_at);

-- History is append-only: refuse edits at the database level too
CREATE FUNCTION issue_updates_no_update() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'issue_updates is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER issue_updates_append_only BEFORE UPDATE ON issue_updates
    FOR EACH ROW EXECUTE FUNCTION issue_updates_no_update();

-- Files attached to a workflow step (plan documents, resolution proof, rejection evidence)
ALTER TABLE issue_media ADD COLUMN update_id uuid REFERENCES issue_updates (id);

-- Which department and Authority handled the issue, and when
CREATE TABLE issue_assignments (
    id              uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id        uuid          NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    department_id   uuid          NOT NULL REFERENCES departments (id),
    -- NULL while the department has no active Authority; filled in automatically later
    authority_id    uuid          REFERENCES users (id),
    reason          varchar(20)   NOT NULL
        CONSTRAINT issue_assignments_reason_chk CHECK (reason IN ('TRIAGE', 'RE_ROUTE', 'REBALANCE', 'REOPEN')),
    ended_at        timestamptz,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX issue_assignments_issue_idx ON issue_assignments (issue_id, created_at);

-- Every routing decision, with its source and reason, for transparency
CREATE TABLE triage_decisions (
    id                  uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id            uuid           NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    source              varchar(10)    NOT NULL CONSTRAINT triage_decisions_source_chk CHECK (source IN ('AI', 'RULE')),
    model               varchar(100),
    category_id         uuid           NOT NULL REFERENCES categories (id),
    department_id       uuid           NOT NULL REFERENCES departments (id),
    priority            varchar(10)    NOT NULL,
    confidence          numeric(3, 2),
    reason              text           NOT NULL,
    flagged_for_review  boolean        NOT NULL DEFAULT false,
    -- Why the AI answer was not used, when the rules decided instead
    fallback_reason     varchar(200),
    latency_ms          integer,
    created_at          timestamptz    NOT NULL DEFAULT now(),
    updated_at          timestamptz    NOT NULL DEFAULT now()
);

CREATE INDEX triage_decisions_issue_idx ON triage_decisions (issue_id, created_at);

-- Who did what: Admin changes to accounts and routing data, and system decisions
CREATE TABLE audit_events (
    id           uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id     uuid          REFERENCES users (id),
    actor_role   varchar(20)   NOT NULL,
    action       varchar(60)   NOT NULL,
    entity_type  varchar(40)   NOT NULL,
    entity_id    uuid,
    details      text,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX audit_events_created_idx ON audit_events (created_at DESC);
CREATE INDEX audit_events_entity_idx ON audit_events (entity_type, entity_id);
