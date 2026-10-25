-- CivSetu baseline: extensions plus the core reference tables.
-- Conventions: UUID keys, timestamptz in UTC, foreign keys on every relation.

CREATE EXTENSION IF NOT EXISTS postgis;   -- issue locations, distance, clustering
CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- text search and duplicate matching
CREATE EXTENSION IF NOT EXISTS citext;    -- case-insensitive email

CREATE TABLE departments (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        varchar(50)  NOT NULL UNIQUE,
    name        varchar(150) NOT NULL,
    area        varchar(150),
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE categories (
    id                     uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    code                   varchar(50)  NOT NULL UNIQUE,
    label                  varchar(100) NOT NULL,
    default_department_id  uuid         NOT NULL REFERENCES departments (id),
    default_priority       varchar(10)  NOT NULL DEFAULT 'MEDIUM'
        CONSTRAINT categories_priority_chk CHECK (default_priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    active                 boolean      NOT NULL DEFAULT true,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    updated_at             timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX categories_department_idx ON categories (default_department_id);

CREATE TABLE users (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name           varchar(120) NOT NULL,
    email          citext       NOT NULL UNIQUE,
    phone          varchar(20),
    -- Nullable so an optional external sign-in can exist without a local password
    password_hash  varchar(100),
    role           varchar(20)  NOT NULL
        CONSTRAINT users_role_chk CHECK (role IN ('CITIZEN', 'AUTHORITY', 'ADMIN')),
    department_id  uuid         REFERENCES departments (id),
    active         boolean      NOT NULL DEFAULT true,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    -- One department per authority
    CONSTRAINT users_authority_department_chk CHECK (role <> 'AUTHORITY' OR department_id IS NOT NULL)
);

CREATE INDEX users_department_idx ON users (department_id);
