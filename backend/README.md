# CivSetu backend

Spring Boot 4.1 API for CivSetu, a civic issue reporting and resolution system. Citizens report
problems with a location and photos, Admins route them to departments, and Authorities resolve them.

**Stack:** Java 17 · Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation, Actuator) ·
PostgreSQL 17 + PostGIS · Flyway · JWT (OAuth2 Resource Server) · Testcontainers · Maven

## Run it locally

Prerequisites: JDK 17, Docker Desktop. Maven is not needed (use the wrapper).

```powershell
cd backend
copy .env.example .env      # then fill in the values (see comments in the file)
.\mvnw.cmd spring-boot:run  # starts PostGIS + pgAdmin via Docker Compose, then the API
```

| What | Where |
|---|---|
| API | http://localhost:8080/api/v1 |
| Swagger UI (dev only) | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| pgAdmin | http://localhost:5050 (server "CivSetu (local)", password = `POSTGRES_PASSWORD`) |
| Database (IntelliJ / psql) | `localhost:5433`, database `civsetu` |

In IntelliJ IDEA, open the repository root and use the shared run configurations:
**CivSetu API (dev)** and **Backend tests (mvnw verify)**. Ready-made requests live in `http/`.

## Tests

```powershell
.\mvnw.cmd verify
```

Integration tests start a throwaway PostGIS container (Docker must be running).
`ArchitectureTest` fails the build if the package rules below are broken.

## Project structure

Code is organised **by feature first, then by role**, following Spring Boot's recommended layout
(main class in the root package, one package per feature).

```
src/main/java/com/civsetu/
├── CivSetuApplication.java      entry point
├── common/                      shared building blocks, no feature logic
│   ├── error/                   ApiException, global RFC 9457 error handler
│   ├── persistence/             BaseEntity (UUID id, created/updated timestamps)
│   ├── domain/                  shared vocabulary (Priority)
│   ├── geo/                     GeoPoints (WGS 84 points for PostGIS)
│   ├── security/                Caller, CurrentUser, TokenClaims (who is calling)
│   ├── validation/              shared validation patterns
│   └── web/                     PageResponse
├── config/                      app-wide Spring config: security, CORS, OpenAPI, clock
├── analytics/                   read-only, role-scoped reporting (SQL aggregates)
├── audit/                       read-only log of Admin actions and system decisions
├── auth/                        register, login, refresh tokens, JWT
├── user/                        accounts, roles, admin user management
├── department/                  departments and categories (category → department routing)
├── engagement/                  votes (one per citizen) and comments, with moderation
├── issue/                       reporting, search, map, workflow (plan/resolve/reject/return/reopen), history
├── media/                       photo/voice uploads, EXIF stripping, storage (local disk or S3/R2)
├── notification/                in-app notifications and the live Server-Sent Events stream
├── sla/                         fixed deadlines per priority, SLA states (On Track/At Risk/Breached/Met/Missed)
├── survey/                      area surveys for the reporters of clustered issues
└── triage/                      automatic routing: AI advisor (Gemini) with rule-engine fallback

src/main/resources/
├── application.yml              shared settings; application-dev.yml / application-prod.yml per profile
└── db/migration/                Flyway migrations: V1__baseline.sql, V2__..., never edited once merged
```

Every feature uses the same sub-packages (only the ones it needs):

| Sub-package | Contains | Example |
|---|---|---|
| `controller/` | REST endpoints; thin, no business logic | `AuthController` |
| `dto/` | Request and response records with validation annotations | `RegisterRequest`, `UserResponse` |
| `service/` | Business logic and transactions; the feature's public API | `UserService` |
| `repository/` | Spring Data repositories | `UserRepository` |
| `entity/` | JPA entities and their enums | `User`, `Role` |
| `config/` | Feature settings (`@ConfigurationProperties`) and beans | `AuthProperties` |
| `security/` | Feature-specific security filters | `AuthRateLimitFilter` |
| `storage/` | Pluggable storage implementations | `LocalDiskStorage`, `S3MediaStorage` |

Tests mirror the same packages under `src/test/java`; shared test helpers live in `support/`.

### Rules (enforced by `ArchitectureTest`)

1. Controllers call services, never repositories.
2. Repositories are used only by services.
3. Controllers return DTOs, never entities.
4. A feature uses another feature only through its `service` and `dto` packages (enums excepted).
   When a lower-level feature needs information from a higher one, it defines an interface in its
   `service` package that the higher feature implements (e.g. `MediaAccessPolicy`).
5. `common` never depends on a feature, and features have no dependency cycles.
6. The database schema changes only through a new Flyway migration (`ddl-auto: validate`).

### Adding a feature

Create `com/civsetu/<feature>/` with the sub-packages above, add a migration
`V<n>__<description>.sql`, write the test next to it, and expose only the service to other features.

## Configuration

Secrets and URLs come from environment variables; locally they are read from `backend/.env`
(git-ignored) on the `dev` profile. See `.env.example` for every variable.

| Profile | Used for |
|---|---|
| `dev` (default) | Local work: Docker Compose database, Swagger UI, debug logging |
| `demo` | Add to dev (`dev,demo`) to fill an empty database with 40 sample issues around Pune |
| `test` | Automated tests (`src/test/resources/application-test.yml`) |
| (any) | `civsetu.scheduling.enabled=false` turns off background jobs (tests do this) |
| `prod` | Deployment: database from `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`, no Swagger |

## API conventions

- Base path `/api/v1`; JSON in and out; lists are paginated (`page`, `size`, max 50).
- Errors are `application/problem+json` with a stable `code` (e.g. `EMAIL_TAKEN`) and, for
  validation errors, an `errors` list of `{field, message}`.
- Authentication: `Authorization: Bearer <access token>` (15 minutes). The refresh token is an
  httpOnly cookie used only by `/api/v1/auth/refresh` and `/logout`.
- Roles: `CITIZEN` (self sign-up), `AUTHORITY` and `ADMIN` (created by an Admin).
- Public without a token: issue list, map and detail, categories, departments. Reporter contact
  details are shown only to the reporter, Admins, and Authorities of the issue's department.
- Locations are WGS 84 `latitude`/`longitude`; map viewports are `bbox=minLng,minLat,maxLng,maxLat`.
- **No one routes issues by hand.** After a report is saved, triage (AI if `CIVSETU_GEMINI_API_KEY` is set,
  otherwise rules) picks department, category and priority; the system sets the deadline and assigns the
  least-loaded active Authority as the lead. Admins have no endpoint to assign, prioritise or reschedule an issue.
- Workflow: any Authority of the issue's department (not only the lead) plans (`ASSIGNED → IN_PROGRESS`), resolves with proof photos or rejects
  with a reason, or returns a misrouted issue for automatic re-routing; the reporter may reopen. Each request
  carries the issue `version` from the last read; a stale version gets 409 `STALE_ISSUE`.
- `GET /api/v1/issues/{id}/history` is the public, append-only timeline of every step.
- Community: citizens vote AGREE/DISAGREE (one vote each, not on their own report) and comment (3 per
  person per issue). When net agree votes reach `civsetu.engagement.escalation-votes` (default 10) the
  system raises an open issue's priority one level, once, and records it in the history.
- Duplicates: `GET /api/v1/issues/similar?latitude=&longitude=&category=&title=` before reporting, and
  `GET /api/v1/issues/{id}/similar`; an Authority of the department can close an issue as a duplicate.
- Issue lists (`GET /api/v1/issues`, `/mine`, `/assigned`) return feed cards: description, cover photo,
  photo count, voice-note flag, agree/disagree and comment counts, and the viewer's own vote.
  `overdue=true` keeps only open issues past their deadline.
- SLA: every issue shows `slaState`. Every 15 minutes the system alerts each newly breached issue
  exactly once (reporter, Authority, Admins), recorded in the history. Targets are fixed, not editable.
- Surveys: Authorities (own department) and Admins find clusters (`GET /api/v1/surveys/clusters`) and
  survey everyone who reported an open issue there; each person answers once.
- Analytics (`/api/v1/analytics/summary|trend|hotspots|overdue`) are scoped to the caller: Admin the city,
  Authority the department, Citizen their own reports.
- Live notifications: `POST /api/v1/notifications/stream-ticket`, then `new EventSource(streamUrl)`.
  Events are named `notification`; the browser's automatic `Last-Event-ID` replays missed ones.
- Uploads: `POST /api/v1/issues` as `multipart/form-data` with parts `data` (JSON, Content-Type
  `application/json`), `images` (up to 5 JPEG/PNG/WebP, 10 MB each) and `voice` (one WebM/Ogg/MP4/MP3/WAV).
  Types are checked by file content; photos are re-encoded, which removes EXIF data such as GPS.
  Files are served by `GET /api/v1/media/{id}`: report photos are public, voice notes are not.
