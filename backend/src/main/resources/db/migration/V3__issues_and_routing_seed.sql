-- Issues, SLA targets and the starter routing data (departments and categories).
-- Admins can edit departments, categories and SLA targets later without a deploy.

CREATE TABLE sla_policies (
    priority       varchar(10)   PRIMARY KEY
        CONSTRAINT sla_policies_priority_chk CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    target_hours   integer       NOT NULL CONSTRAINT sla_policies_target_chk CHECK (target_hours > 0),
    -- "At Risk" starts once this share of the target time has passed
    at_risk_ratio  numeric(3, 2) NOT NULL DEFAULT 0.50
        CONSTRAINT sla_policies_ratio_chk CHECK (at_risk_ratio > 0 AND at_risk_ratio < 1),
    updated_at     timestamptz   NOT NULL DEFAULT now()
);

INSERT INTO sla_policies (priority, target_hours) VALUES
    ('CRITICAL', 24),
    ('HIGH', 48),
    ('MEDIUM', 96),
    ('LOW', 168);

INSERT INTO departments (code, name) VALUES
    ('ROADS',       'Roads and Infrastructure'),
    ('SANITATION',  'Solid Waste Management'),
    ('WATER',       'Water Supply'),
    ('DRAINAGE',    'Drainage and Sewerage'),
    ('ELECTRICAL',  'Street Lighting and Electrical'),
    ('PARKS',       'Parks and Gardens'),
    ('HEALTH',      'Public Health'),
    ('TRAFFIC',     'Traffic and Transport'),
    ('GENERAL',     'General Administration');

INSERT INTO categories (code, label, default_department_id, default_priority)
SELECT c.code, c.label, d.id, c.priority
FROM (VALUES
    ('POTHOLE',          'Pothole or damaged road',        'ROADS',      'MEDIUM'),
    ('FOOTPATH',         'Broken footpath',                'ROADS',      'LOW'),
    ('GARBAGE',          'Garbage not collected',          'SANITATION', 'MEDIUM'),
    ('ILLEGAL_DUMPING',  'Illegal dumping',                'SANITATION', 'MEDIUM'),
    ('WATER_LEAK',       'Water leakage or burst pipe',    'WATER',      'HIGH'),
    ('NO_WATER',         'No water supply',                'WATER',      'HIGH'),
    ('DRAIN_BLOCKAGE',   'Blocked or overflowing drain',   'DRAINAGE',   'HIGH'),
    ('WATERLOGGING',     'Waterlogging',                   'DRAINAGE',   'HIGH'),
    ('STREETLIGHT',      'Streetlight not working',        'ELECTRICAL', 'MEDIUM'),
    ('EXPOSED_WIRE',     'Exposed or fallen electric wire','ELECTRICAL', 'CRITICAL'),
    ('TREE_FALL',        'Fallen tree or branch',          'PARKS',      'HIGH'),
    ('PARK_MAINTENANCE', 'Park maintenance',               'PARKS',      'LOW'),
    ('STRAY_ANIMALS',    'Stray animals',                  'HEALTH',     'MEDIUM'),
    ('MOSQUITO',         'Mosquito breeding',              'HEALTH',     'MEDIUM'),
    ('TRAFFIC_SIGNAL',   'Traffic signal fault',           'TRAFFIC',    'HIGH'),
    ('OTHER',            'Other',                          'GENERAL',    'LOW')
) AS c (code, label, department_code, priority)
JOIN departments d ON d.code = c.department_code;

CREATE TABLE issues (
    id                     uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id            uuid          NOT NULL REFERENCES users (id),
    title                  varchar(150)  NOT NULL,
    description            text          NOT NULL CONSTRAINT issues_description_len_chk CHECK (char_length(description) <= 4000),
    category_id            uuid          NOT NULL REFERENCES categories (id),
    department_id          uuid          NOT NULL REFERENCES departments (id),
    assigned_authority_id  uuid          REFERENCES users (id),
    status                 varchar(20)   NOT NULL DEFAULT 'REPORTED'
        CONSTRAINT issues_status_chk CHECK (status IN ('REPORTED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED', 'DUPLICATE')),
    priority               varchar(10)   NOT NULL
        CONSTRAINT issues_priority_chk CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    priority_source        varchar(10)   NOT NULL DEFAULT 'RULE'
        CONSTRAINT issues_priority_source_chk CHECK (priority_source IN ('RULE', 'AI', 'ADMIN')),
    -- WGS 84 longitude/latitude
    location               geometry(Point, 4326) NOT NULL,
    address                varchar(300)  NOT NULL,
    sla_due_at             timestamptz   NOT NULL,
    resolved_at            timestamptz,
    duplicate_of_id        uuid          REFERENCES issues (id),
    -- Optimistic locking: concurrent updates get 409 instead of silently overwriting
    version                bigint        NOT NULL DEFAULT 0,
    created_at             timestamptz   NOT NULL DEFAULT now(),
    updated_at             timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX issues_location_gist_idx      ON issues USING gist (location);
CREATE INDEX issues_created_idx            ON issues (created_at DESC);
CREATE INDEX issues_status_created_idx     ON issues (status, created_at DESC);
CREATE INDEX issues_department_status_idx  ON issues (department_id, status);
CREATE INDEX issues_reporter_created_idx   ON issues (reporter_id, created_at DESC);
CREATE INDEX issues_category_idx           ON issues (category_id);
-- Case-insensitive substring search (lower(x) LIKE '%term%') and later duplicate matching
CREATE INDEX issues_title_trgm_idx         ON issues USING gin (lower(title) gin_trgm_ops);
CREATE INDEX issues_description_trgm_idx   ON issues USING gin (lower(description) gin_trgm_ops);
