# CivSetu on Spring Boot — Implementation Plan

Oct 25, 2026 · @Swaraj

Ten phases (0 to 9) take CivSetu from an empty branch to a deployed Spring Boot backend in about 43 working days, an estimate for one developer: roughly nine weeks full-time, or seventeen at 20 hours a week. Phase 5 ends with the full citizen → admin → authority loop working through the API, and every phase has an exit gate you can test.

## Roadmap

&#91;embedded content: roadmap · 5 stages, 5 gates, days 1 to 43\]

Each gate is a test you run, not a feeling: a stage is finished only when its gate criteria pass, and Gate 2 is the point where the backend is already useful on its own.

## Ground rules

Build in vertical slices (migration, entity, service, controller, test, OpenAPI) inside one repository with `backend/` and `frontend/` folders, and keep each feature in its own package.

```
backend/src/main/java/com/civsetu/
  common/        error handling (ProblemDetail), paging, clock, security helpers
  config/        security, CORS, OpenAPI, scheduling
  auth/          register, login, refresh tokens, JWT
  user/          users and admin user management
  department/    departments, categories, routing rules
  issue/         entity, repository, search, DTOs, controller
  workflow/      status rules, assignment, history, audit
  media/         MediaStorage (local disk, S3), upload checks
  engagement/    votes and comments
  notification/  stored notifications and the SSE hub
  sla/           policies, scheduler, escalation
  survey/        surveys, clustering, responses
  analytics/     read-only reporting queries
  ai/            PriorityAdvisor (rules first, optional providers)
```

- Packages talk to each other through service interfaces and domain events, never through another package's repository.
- Controllers return DTOs, never JPA entities.
- The schema changes only through Flyway; `spring.jpa.hibernate.ddl-auto` stays on `validate`.
- Secrets and URLs come from environment variables; `.env.example` is committed, `.env` is not.
- One Git branch per phase, merged only when its exit gate passes and CI is green.
- Keep the old Node code in a `legacy-node/` folder until Phase 8 is done, as the reference for behaviour.

## Phase 0: Decide and prepare (2 days)

Lock the open decisions and tooling so that Phase 1 is pure coding.

- [ ] Answer the decisions table in the "What We Will Build" doc and save the answers as `docs/decisions.md`
- [ ] Install JDK 21, Maven, Docker Desktop, Node 20 and an IDE (IntelliJ IDEA Community or VS Code with the Java extensions)
- [ ] Create the branch `spring-backend`; move the Node backend to `legacy-node/` untouched
- [ ] Write the status-transition table and the category → department table into `docs/` as the single source of truth
- [ ] Copy the category list from `ReportIssue.tsx` and the department data from `authorities.ts` into seed files
- [ ] Generate the project at start.spring.io: Maven, Java 21, Spring Boot 4.1.x, with Web, Validation, Security, OAuth2 Resource Server, Data JPA, PostgreSQL Driver, Flyway, Actuator and Testcontainers
- [ ] Check each extra library you plan to add (springdoc, ShedLock, Bucket4j, MapStruct, Hibernate Spatial, AWS SDK v2) for Spring Boot 4 support

**Exit gate:** `./mvnw -v` and `docker compose version` work, and the decisions file exists.

## Phase 1: Foundation (3 days)

Get an empty but production-shaped application running with its database, error format, tests and CI.

- [ ] `docker-compose.yml` with the `postgis/postgis` image and a named volume, plus a committed `.env.example`
- [ ] `application.yml`, `application-dev.yml` and `application-prod.yml`; every secret read from environment variables
- [ ] Flyway `V1__baseline.sql`: enable `postgis`, `pg_trgm` and `citext`; create `users`, `departments` and `categories`
- [ ] Base entity with UUID id and `created_at` / `updated_at` auditing in UTC
- [ ] Global exception handler returning RFC 9457 problem JSON with a stable `code` and field errors
- [ ] Paging helper with a maximum page size of 50
- [ ] Swagger UI enabled on the dev profile only; Actuator health with liveness and readiness probes
- [ ] Testcontainers base class using the PostGIS image; first test proves the context loads and migrations run on an empty database
- [ ] GitHub Actions workflow that builds and tests on every push
- [ ] Multi-stage Dockerfile that runs as a non-root user

**Exit gate:** `docker compose up` plus `./mvnw spring-boot:run` starts the app, `/actuator/health` reports UP, CI is green, and the clean-database migration test passes.

## Phase 2: Accounts and security (4 days)

Build login and role enforcement first, because every later endpoint depends on it.

- [ ] `users` entity and repository; migration `V2` adds `refresh_tokens`
- [ ] `POST /auth/register` creates Citizens only, with validated email and password hashed by BCrypt
- [ ] `POST /auth/login` returns a 15-minute access JWT; the refresh token goes in an httpOnly, Secure, SameSite cookie and is stored hashed
- [ ] `POST /auth/refresh` rotates the refresh token and revokes the whole chain if an old token is reused; `POST /auth/logout` revokes it
- [ ] Stateless security filter chain using the OAuth2 resource-server JWT support; the role claim becomes `ROLE_CITIZEN`, `ROLE_AUTHORITY` or `ROLE_ADMIN`; method security switched on
- [ ] CORS limited to the exact frontend origin from configuration
- [ ] Bucket4j rate limits on login and register
- [ ] First Admin created from environment variables on first start (no default password in code); `POST /admin/users` creates Authority and Admin accounts
- [ ] `GET /auth/me` and `PATCH /users/me`
- [ ] Optional: Google sign-in through Spring's OAuth2 client, issuing the same JWT
- [ ] Tests: register → login → me; a signup that tries to set its own role; expired token returns 401; wrong role returns 403

**Exit gate:** a Citizen cannot reach any admin route or choose a role, refresh tokens rotate, and all auth tests pass.

## Phase 3: Departments and issues (5 days)

Create, find and view issues, with routing by category and location stored as a PostGIS point.

- [ ] Migration `V3`: `issues`, seed categories and departments, B-tree indexes, a GiST index on `location`, a trigram index on title and description
- [ ] Admin CRUD for departments and categories; the category's default department drives routing
- [ ] `POST /issues` (JSON only for now): validate title length, known category, latitude in \[-90, 90\], longitude in \[-180, 180\], and address; take the reporter from the token, never the request
- [ ] On creation set department from the category, status Reported, priority from `RulePriorityAdvisor`, and `sla_due_at` from `sla_policies`
- [ ] `GET /issues` with filters for status, category, department, priority, date range, text and bounding box, always paginated
- [ ] `GET /issues/map?bbox=` returning a slim payload (id, point, category, status, priority)
- [ ] `GET /issues/{id}` with field visibility by caller: public, reporter, staff
- [ ] `GET /issues/mine`
- [ ] A `demo` profile that seeds sample issues, replacing `seedIssues.js`
- [ ] Tests: validation failures, bounding-box filter, pagination limits, visibility rules

**Exit gate:** a valid issue is stored with UUID, reporter, status, timestamps and location; invalid coordinates return a 400 problem response; filters return correct pages.

## Phase 4: Media (3 days)

Store images, voice notes and proofs safely behind one storage interface, so local disk and Cloudflare R2 are interchangeable.

- [ ] `MediaStorage` interface with `put`, `open` and `delete`
- [ ] `LocalDiskStorage` for development: generated UUID keys, no user-supplied file names, protected against path traversal
- [ ] `S3Storage` using the AWS SDK v2 with a configurable endpoint (R2 or a local S3-compatible server), chosen by profile
- [ ] Upload rules from configuration: at most 5 images and 1 voice note, 10 MB each as today, allow-listed types checked by file content (magic bytes), not extension
- [ ] Re-encode images on upload to strip EXIF data such as GPS tags, protecting reporter privacy
- [ ] `issue_media` table; media rows saved in the same transaction as the issue, with cleanup of stored files if the transaction fails
- [ ] `GET /media/{id}` streams the file after an access check: public issue media is public, proofs follow role rules; short cache headers
- [ ] `POST /issues` becomes multipart: a `data` JSON part plus `images[]` and `voice`
- [ ] Tests: oversized file, a non-image renamed to `.jpg`, unauthorised proof download

**Exit gate:** a report with photos and a voice note is saved, retrievable only by users allowed to see it, and bad files are rejected with clear errors.

## Phase 5: Workflow (5 days)

Implement assignment, status changes, proofs and history, so the full citizen → admin → authority → citizen loop works.

- [ ] `IssueStatus` enum and a table-driven `TransitionPolicy`: from-status, to-status, allowed roles, required inputs
- [ ] `POST /issues/{id}/assign` (Admin) writes an `issue_assignments` row, sets department and authority, moves the issue to Assigned
- [ ] `POST /issues/{id}/plan` (assigned Authority) stores action taken, staff, resources and plan documents, and moves the issue to In Progress
- [ ] `POST /issues/{id}/resolve` requires remarks and at least one proof image; records `resolved_at` and whether it met the SLA
- [ ] `POST /issues/{id}/reject` requires a reason; proofs optional
- [ ] Reopen with a reason, and duplicate confirmation by Admin
- [ ] Optimistic locking with `@Version`, returning 409 when two people update the same issue
- [ ] `GET /issues/{id}/history` from `issue_updates`
- [ ] Application events (`IssueStatusChanged`) published after commit; an `audit_events` listener records the actor and action
- [ ] Tests: every allowed and every forbidden transition (parameterised); an Authority cannot touch another department's issue; history only ever grows

**Exit gate:** with Swagger UI or a Postman collection, a citizen reports, an admin assigns, an authority plans and resolves with proof, and the citizen sees the full history.

## Phase 6: Engagement, notifications and priority (5 days)

Add community signals, live notifications and a rule-based priority engine that works with no AI key.

- [ ] Votes: `PUT` and `DELETE /issues/{id}/vote`, idempotent, one row per user per issue enforced by a unique constraint
- [ ] Comments: table, `GET` and `POST`, per-user limit from configuration (default 3, as today), Admin can hide a comment
- [ ] Notifications: `NotificationService` listens to domain events and stores Submission, Status change, Resolution and SLA breach notifications
- [ ] `GET /notifications`, mark one read, read all, unread count
- [ ] `GET /notifications/stream` over Server-Sent Events: a 25-second heartbeat, one emitter per user, `Last-Event-ID` replay, and a short-lived stream ticket because browsers cannot set headers on `EventSource`
- [ ] `PriorityAdvisor` interface and `RulePriorityAdvisor`: category baseline, urgency keywords matched on word boundaries, community votes and issue age
- [ ] Duplicate hints: `GET /issues/{id}/similar`, and candidates returned when an issue is created, using distance (`ST_DWithin`), same category and trigram text similarity
- [ ] Optional `GeminiPriorityAdvisor` and `OllamaPriorityAdvisor`, switched on by a property, with a timeout and automatic fallback to rules; the result is stored as a recommendation with `priority_source`

**Exit gate:** votes cannot be double-counted, a status change appears live in an open browser tab, and with AI switched off everything still works.

## Phase 7: SLA, surveys and analytics (5 days)

Turn deadlines into alerts, cluster issues on the server, and give Admin and Authority real numbers.

- [ ] `sla_policies` seeded with 1, 2, 4 and 7 days and editable by Admin; `sla_due_at` recalculated when priority changes
- [ ] SLA state (On Track, At Risk, Breached) derived from `sla_due_at` and the current time in a query, so reads never write
- [ ] `SlaScheduler` runs every 15 minutes under a ShedLock lock; it sets `breach_notified_at` once and notifies the reporter and Admins
- [ ] Surveys: `GET /surveys/clusters?radius=500` using `ST_ClusterDBSCAN` over open issues; create a survey from a cluster with the cluster's reporters as targets
- [ ] Survey responses with one answer set per user; aggregate results plus the response list for the survey owner and Admin
- [ ] Analytics endpoints scoped by role: counts by status, category and department; average resolution time; SLA compliance; overdue list; hotspots; daily trend
- [ ] Check slow queries with `EXPLAIN` and add indexes; add a materialised view only if a query needs it
- [ ] Tests: SLA boundaries using a fixed `Clock` bean; scheduler run twice sends one alert; cluster query on fixture points

**Exit gate:** restarting the app never repeats an SLA alert, a cluster survey can be created and answered, and dashboard numbers match the test fixtures.

## Phase 8: Frontend switch (7 days)

This is the biggest single phase; do it page by page, with the compatibility mapper from the "What We Will Build" doc if you want pages to keep working while others move.

- [ ] Generate TypeScript types from the OpenAPI file and add `lib/api.ts`: base URL from `VITE_API_BASE_URL`, auth header, silent token refresh, error mapping
- [ ] Replace `AuthContext.tsx` (Firebase) with a JWT-based context; update `Login.tsx` and `Signup.tsx`; remove phone OTP and reCAPTCHA; use the roles Citizen, Authority and Admin in `ProtectedRoute.tsx`
- [ ] Move each `fetch("/api/...")` call in this order: auth, issue list and map (`MapView`, `AdminMapView`, `AdminDashboard`), report (`ReportIssue`), citizen dashboard (`/issues/mine`), `ManageIssues` (plan, resolve, reject), votes and comments, surveys, notifications
- [ ] In `Navbar.tsx`, replace the Firestore `onSnapshot` listener with an SSE hook that falls back to polling
- [ ] Replace `@vis.gl/react-google-maps` in `CivicMap.tsx` and the two map pages with react-leaflet or MapLibre; drop `utils/clustering.ts` in favour of the clusters endpoint
- [ ] Switch list pages to paginated TanStack Query hooks
- [ ] Add a Vite dev proxy from `/api` to `http://localhost:8080`
- [ ] Delete `lib/firebase.ts`, the `firebase` dependency and the Firebase, Google Maps and Gemini variables from the env files
- [ ] Decide what happens to the Android app and the APK download button in `HeroSection.tsx` (see the decisions table)
- [ ] Click-through every page as Citizen, Authority and Admin using a written checklist

**Exit gate:** a search of `frontend/src` for "firebase" finds nothing, and all three roles complete their flows in the browser against the Spring Boot API.

## Phase 9: Harden and deploy (4 days)

Prove it is safe, recoverable and reproducible before anyone else uses it.

- [ ] Security pass: dependency scan (Dependabot or OWASP dependency-check), security headers, production CORS, cookie flags, no stack traces in responses
- [ ] A short k6 run against list, map and report endpoints to catch obvious slow queries
- [ ] JSON logs with a request ID on every line and no tokens or personal data
- [ ] `docker-compose.prod.yml` with the API, PostGIS and Caddy, restart policies and memory limits; Caddy configured so SSE responses are not buffered
- [ ] Nightly `pg_dump` to object storage, and one full **restore rehearsal** on a clean machine
- [ ] Deploy to your chosen host with HTTPS, then run a smoke-test script covering register, report, assign, resolve
- [ ] README with prerequisites, setup, environment variables, migrations, tests and troubleshooting; exported OpenAPI file and a Postman collection
- [ ] A feature checklist marking each item implemented, deferred or out of scope

**Exit gate:** acceptance criteria AC-01 to AC-12 from your plan document all pass, and a stranger can run the project from the README alone.

## Testing and definition of done

A feature is done only when it has its migration, code, test, OpenAPI entry and, where relevant, an audit row and a notification.

| Layer | Tools | What it proves | Runs |
| --- | --- | --- | --- |
| Unit | JUnit 5, Mockito, fixed `Clock` | Transition rules, priority rules, SLA maths | Every build |
| Web and security | MockMvc, Spring Security Test | The role matrix: each endpoint allows and refuses the right roles | Every build |
| Integration | Testcontainers with PostGIS | Migrations, constraints, geo queries, pagination | Every build |
| End-to-end API | Scripted run against the running app | Report → assign → plan → resolve → citizen view | Before each merge |
| Frontend | Vitest, Playwright smoke tests | Login, report, resolve, map load | Before each merge from Phase 8 |
| Operations | Restore rehearsal, smoke script | Backups restore; the deployment answers | Phase 9 and each release |

## Risks

The biggest schedule risk is Phase 8, because the frontend has many direct API calls and map code to replace.

| Risk | How the plan handles it |
| --- | --- |
| A library does not support Spring Boot 4 yet | Check in Phase 0; pick an alternative or wait for the patch release before depending on it. |
| Frontend switch takes longer than 7 days | Do it page by page behind the compatibility mapper; the backend phases are not blocked. |
| Scope creep into AI features | AI sits behind `PriorityAdvisor`, is scheduled in Phase 6 as optional, and is cut first if time runs short. |
| SSE connections cut by a proxy | Disable response buffering in Caddy and keep the polling fallback in the frontend. |
| Managed free database lacks PostGIS | Run PostGIS in Docker on your own server; keep the schema portable by isolating geo queries in repository methods. |
| Free hosting limits change | Local Docker Compose is the guaranteed demo path; verify host limits before Phase 9. |
| Android app breaks | Treated as a separate decision; the web app is the delivery target of this plan. |
