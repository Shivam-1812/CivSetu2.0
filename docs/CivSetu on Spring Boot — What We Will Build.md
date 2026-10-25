# CivSetu on Spring Boot — What We Will Build

Oct 25, 2026 · @Swaraj

CivSetu keeps its React frontend and its citizen → authority → resolution workflow, but the backend moves to Spring Boot 4.1 on PostgreSQL with PostGIS, with no Firebase or Google Cloud dependency, and it fixes the security and scaling gaps found in the Node version.

## How it fits together

&#91;embedded content: architecture · clients, proxy, API modules, data stores\]

Clients reach one API through a reverse proxy, all modules ship in one jar, and the dashed optional services can be switched off without breaking reporting.

## What the old build really did

I read your repo (backend routes, services, middleware and the frontend's API calls), and ten problems should not be carried into Spring Boot; each has a fix built into the design.

| # | In the Node + Firebase build | Fix in the new build |
| --- | --- | --- |
| 1 | `POST /api/auth/sync` accepts `role` from the request body, so any signup can claim to be an official. | Role is set only on the server. Public signup always creates a Citizen; Admin creates Authority and Admin accounts. |
| 2 | Status update, resolve, reject and the survey endpoints only check that a token exists, not role or department. | Role and ownership checks on every protected endpoint, with a security test for each. |
| 3 | `GET /issues/all` and `/issues/user` return every issue unpaginated and write SLA fields back during the read. | Paginated, indexed queries. SLA state is derived from a stored deadline, so reads never write. |
| 4 | The submit handler spreads a `slaDetails` variable it never defines. | Typed services with unit tests around issue creation. |
| 5 | Every file gets a signed URL valid until the year 2500, and uploads are buffered in memory. | Private storage, short-lived access links, streamed uploads, content-type checks. |
| 6 | The AI validity check rejects any text containing "test", which also blocks words like "latest" and "contest". | Rule-based checks plus an optional AI advisor that only recommends; a human decides. |
| 7 | README says SLA is 1/3/7/14 days, code uses 1/2/4/7, and the job runs every 12 hours. | One configurable `sla_policies` table and a scheduler that runs every 15 minutes. |
| 8 | Survey target user comes from a query parameter, responses can repeat, and clustering runs in the browser. | User from the token, one response per user per survey, 500 m clustering in PostGIS. |
| 9 | Comments live in an array on the issue, capped at 3 in total. | A `comments` table with a per-user limit and moderation. |
| 10 | Open CORS, a 50 MB body limit, a `/notifications/test` route, and an in-memory token cache. | Per-environment config, exact-origin CORS, size limits per endpoint, no test routes in production. |

The frontend also knows only two roles (`citizen`, `official`), while your ER diagram has three (Citizen, Authority, Admin); the new build uses three.

## Features and what replaces the Google pieces

Every feature in your README and slides is covered, ordered so a working citizen → admin → authority loop exists first and AI and push stay optional add-ons.

| Module | What it does | Tier | Replaces |
| --- | --- | --- | --- |
| Accounts and login | Email + password, short-lived JWT, rotating refresh token in an httpOnly cookie, optional Google sign-in | MVP | Firebase Auth (phone OTP dropped; SMS is not free) |
| Issue reporting | Title, description, category, address, GPS point, up to 5 images, optional voice note | MVP | Firestore + Cloud Storage |
| Issue discovery | Paginated list, filters (status, category, priority, department, date), text search, map-bounds query, "nearby" | MVP | `GET /issues/all` |
| Department routing | Category → department mapping table; admin can override; authority sees their department queue | MVP | Hard-coded `authorities.ts` |
| Resolution workflow | Plan (action, staff, resources, documents), resolve with proof photos, reject with reason and proof, full status history | MVP | `PUT status`, `resolve`, `reject` |
| Audit log | Who did what and when, for admin and authority actions | MVP | Nothing (new) |
| Notifications | Stored in-app notifications, pushed live over Server-Sent Events, unread count | V1 | Firestore `onSnapshot` + FCM |
| Community engagement | Agree / disagree (one vote per user), comments, "duplicate" flag with admin review | V1 | `engage`, `comment` |
| Priority and SLA | Rule-based priority, SLA deadline per priority, On Track / At Risk / Breached, escalation alerts | V1 | `priorityEngine`, `slaService` |
| Duplicate detection | Same category, within a set distance, recent, similar text → "possible duplicate" suggestion | V1 | Cloud Vision (already disabled) |
| Map | Leaflet or MapLibre with free tiles; admin cluster view | V1 | Google Maps |
| Surveys | Admin picks a 500 m cluster, targets its reporters, one response each, results view | V2 | `surveyRoutes` |
| Analytics | Counts by status, category, department; resolution time; SLA compliance; hotspots | V2 | Client-side counting |
| Optional AI advisor | Suggests priority and flags spam; app works with it switched off | V2 | Gemini (paid quota) |
| Reverse geocoding | Backend proxy to a free geocoder with caching | V2 | Google geocoding |
| Web push, email, translation, APK hosting | Browser push (VAPID), email alerts, Google Translate widget or i18next, APK as a static download | Later | FCM, Translate, Storage |

## Roles and permissions

Three roles, enforced by Spring Security method rules and ownership checks in the services; the React UI only mirrors them.

| Capability | Citizen | Authority | Admin |
| --- | --- | --- | --- |
| Sign up | Yes, as Citizen only | Created by Admin | Created by Admin |
| Report an issue | Yes | No | No |
| Browse the public map and issue details | Yes | Yes | Yes |
| See a reporter's name and contact | No | Own department | Yes |
| Vote and comment | Yes | Yes | Yes |
| Flag a duplicate | Suggest | Own department | Confirm |
| Assign or reassign department and authority | No | No | Yes |
| Plan work and change status | No | Assigned, own department | Yes |
| Resolve with proof, or reject with reason | No | Assigned, own department | Yes |
| Create a survey | No | Own department | Yes |
| Answer a survey | Targeted users only | No | No |
| See survey responses | No | Own surveys | Yes |
| Analytics | Own reports | Own department | Everything |
| Manage users, departments, categories, SLA policy | No | No | Yes |
| Audit log | No | No | Yes |

## Issue lifecycle and SLA

&#91;embedded content: state machine · 4 forward steps, 2 exits, 1 way back\]

The server checks every transition and writes a history row for each one; reassigning an In Progress issue sends it back to Assigned, and "escalated" is an SLA flag rather than a status.

SLA deadlines come from the `sla_policies` table, so Admin can change them without a deploy; the values below are the ones your Node code uses today.

| Priority | Target | At Risk after | Breached after |
| --- | --- | --- | --- |
| Critical | 1 day | 12 hours | 1 day |
| High | 2 days | 1 day | 2 days |
| Medium | 4 days | 2 days | 4 days |
| Low | 7 days | 3.5 days | 7 days |

At Risk starts when half of the target time has passed, as in your current code. A breach creates one notification for the reporter and one for Admin; a database flag makes sure a restart never sends it twice.

## Tech stack

Start on Spring Boot 4.1 (latest patch 4.1.2, released 22 Oct 2026, open-source support until June 2027); the 3.5 line your plan document assumed lost open-source support in June 2026. Source: [VersionLog](https://versionlog.com/spring-boot/), checked 24 Oct 2026.

| Layer | Choice | Replaces | Why |
| --- | --- | --- | --- |
| Runtime | Java 21 LTS (Java 25 LTS also works) | Node.js 18 | Boot 4.1 needs Java 17 or newer; virtual threads help the upload and SSE paths. |
| Framework | Spring Boot 4.1.x, Spring MVC, Bean Validation | Express, multer | Mature, one deployable jar, strong validation and testing. |
| Security | Spring Security + OAuth2 Resource Server (JWT), BCrypt | Firebase Admin `verifyIdToken` | Built-in JWT validation means less hand-written auth code. |
| Database | PostgreSQL 17 + PostGIS | Firestore | Constraints, transactions, aggregation, and real geo queries (distance, clustering). |
| Persistence | Spring Data JPA, Hibernate Spatial, native SQL for analytics | Firestore SDK | Entities for CRUD, SQL where reports need it. |
| Migrations | Flyway | Seed scripts in `/scripts` | Every schema change is versioned and repeatable. |
| DTO mapping | MapStruct | Ad-hoc object spreads | Entities never leave the service layer. |
| File storage | `MediaStorage` interface: local disk in dev, S3-compatible in production | Firebase Storage | Swap providers without touching business code. |
| Live updates | Server-Sent Events, with polling as fallback | Firestore `onSnapshot` | One-way push is all notifications need. |
| Scheduled jobs | `@Scheduled` + ShedLock | node-cron | Lock in Postgres so a restart or second instance never double-runs a job. |
| Rate limiting, cache | Bucket4j, Caffeine | In-memory token Map | Protects login, upload and comment endpoints. |
| API docs | springdoc-openapi (Swagger UI) | README list | Contract the frontend can generate types from. |
| Observability | Actuator, Micrometer, structured logs | `console.log` | Health, metrics and traceable errors. |
| Testing | JUnit 5, Mockito, MockMvc, Testcontainers (PostGIS image) | None | Tests run against the real database engine. |
| Build and ship | Maven, Docker multi-stage, Docker Compose, GitHub Actions, Caddy | Single Dockerfile | One command locally, automatic HTTPS in production. |
| Frontend | Existing React + Vite + TypeScript + shadcn/ui, TanStack Query with a typed API client, Leaflet or MapLibre | Firebase JS SDK, Google Maps | TanStack Query is already in your `package.json`. |
| AI (optional) | `PriorityAdvisor` interface; default rules; Gemini free tier or local Ollama behind it | Gemini hard dependency | Reporting must work with no API key. |

Before adding each library to the project, check that it supports Spring Boot 4 (springdoc, ShedLock, Bucket4j and MapStruct are the ones to check first).

## Data model

Your ER diagram's four entities (User, Department, Issue, Issue\_Update) stay as the core; the other tables exist only because a documented feature needs them.

| Table | Purpose | Key columns and rules |
| --- | --- | --- |
| `users` | Accounts | UUID id, name, email (unique, case-insensitive), phone, password\_hash, role (CITIZEN, AUTHORITY, ADMIN), department\_id (authorities), active, created\_at |
| `refresh_tokens` | Login sessions | user\_id, token\_hash (never the raw token), expires\_at, revoked\_at; rotated on every use |
| `departments` | Who handles what | name, area, active |
| `categories` | Category → department routing | code, label, default\_department\_id, default\_priority |
| `issues` | The report | reporter\_id, title, description, category, department\_id, assigned\_authority\_id, status, priority, priority\_source (RULE, AI, ADMIN), `location` (PostGIS point), address, sla\_due\_at, resolved\_at, duplicate\_of\_id, version (optimistic lock) |
| `issue_updates` | Append-only status history | issue\_id, actor\_id, from\_status, to\_status, remarks, action\_taken, staff\_allocated, resources\_used, created\_at |
| `issue_assignments` | Assignment trail | issue\_id, department\_id, authority\_id, assigned\_by, assigned\_at, ended\_at, reason |
| `issue_media` | Files (metadata only) | issue\_id, update\_id (optional), kind (IMAGE, VOICE, PLAN\_DOC, RESOLUTION\_PROOF, REJECTION\_PROOF), storage\_key, content\_type, size\_bytes |
| `issue_votes` | Agree / disagree | issue\_id, user\_id, vote; unique on (issue\_id, user\_id) |
| `issue_comments` | Discussion | issue\_id, user\_id, body, hidden |
| `sla_policies` | Deadline rules | priority, target\_hours, at\_risk\_ratio |
| `notifications` | In-app alerts | user\_id, type, title, body, issue\_id, read\_at |
| `surveys` | Cluster survey | title, description, created\_by, cluster\_center (PostGIS), radius\_m, status, closes\_at |
| `survey_questions`, `survey_targets` | Questions and who must answer | survey\_id, position, text, type; survey\_id, user\_id |
| `survey_responses` | Answers | survey\_id, user\_id, answers (JSON); unique on (survey\_id, user\_id) |
| `audit_events` | Accountability | actor\_id, action, entity\_type, entity\_id, metadata (JSON) |

Rules that apply everywhere: UUID primary keys, UTC `timestamptz` timestamps, foreign keys on every relation, current status stored on the issue while history is only ever appended, and one Flyway migration per schema change. Indexes: GiST on `issues.location`, plus B-tree on (status, created\_at), (department\_id, status) and (reporter\_id, created\_at), and a trigram index on title and description for search and duplicate matching.

## API surface

The new API lives under `/api/v1`, is paginated, documented in OpenAPI, and returns errors as RFC 9457 problem JSON; every old route has a home.

| Old Node route | New Spring Boot route | Who |
| --- | --- | --- |
| `POST /api/auth/sync` (Firebase) | `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout` | Public |
| `GET /api/auth/me` | `GET /api/v1/auth/me` | Signed in |
| `POST /api/issues/submit` | `POST /api/v1/issues` (multipart) | Citizen |
| `GET /api/issues/user` | `GET /api/v1/issues/mine` | Citizen |
| `GET /api/issues/all` | `GET /api/v1/issues?status=&category=&department=&page=&size=` and `GET /api/v1/issues/map?bbox=` | Public, limited fields |
| (none) | `GET /api/v1/issues/{id}`, `/history`, `/similar` | Public detail; history for staff and reporter |
| (none) | `POST /api/v1/issues/{id}/assign` | Admin |
| `PUT /api/issues/:id/status` | `POST /api/v1/issues/{id}/plan`, `PATCH /api/v1/issues/{id}/status` | Authority, Admin |
| `POST /api/issues/:id/resolve` | `POST /api/v1/issues/{id}/resolve` | Authority, Admin |
| `POST /api/issues/:id/reject` | `POST /api/v1/issues/{id}/reject` | Authority, Admin |
| `POST /api/issues/:id/engage` | `PUT` and `DELETE /api/v1/issues/{id}/vote` | Signed in |
| `POST /api/issues/:id/comment` | `GET` and `POST /api/v1/issues/{id}/comments` | Signed in |
| `GET /api/notifications`, `PATCH .../read` | `GET /api/v1/notifications`, `PATCH /{id}/read`, `POST /read-all`, `GET /stream` (SSE) | Signed in |
| `GET /api/surveys?userId=` | `GET /api/v1/surveys` (user taken from the token) | Signed in |
| `POST /api/surveys/create` | `POST /api/v1/surveys`, `GET /api/v1/surveys/clusters?radius=500` | Authority, Admin |
| `POST /api/surveys/:id/respond` | `POST /api/v1/surveys/{id}/responses` | Targeted citizen |
| `GET /api/surveys/:id/responses` | `GET /api/v1/surveys/{id}/responses` | Owner, Admin |
| `GET /api/apk/*` | Static download link | Public |
| `GET /api/health` | `/actuator/health` | Public (summary only) |
| (none) | `/api/v1/admin/users`, `/departments`, `/categories`, `/sla-policies`, `/analytics/*`, `/audit` | Admin |

To move the frontend one page at a time, the first phase of integration adds a thin mapper that returns the old field names (`coordinates.lat`, `slaStatus`, lowercase statuses) from a compatibility layer, which is deleted once every page uses the new shapes.

## Free resources and hosting

Nothing in the core reporting loop needs a paid service; items marked *verify* have terms that change, so confirm them on the day you deploy.

| Need | Pick | Limit or caution |
| --- | --- | --- |
| Local development | JDK 21, Maven wrapper, Node 20, Docker Desktop, `postgis/postgis` image | All free; Docker Compose starts the database and storage in one command. |
| Production database | PostgreSQL + PostGIS in Docker on your own server | Managed free tiers often sleep or expire; *verify* PostGIS support before choosing one. |
| API hosting | A small Linux VPS, or an Oracle Cloud Always Free VM if your region has capacity | *Verify* eligibility and capacity; local Docker Compose is the guaranteed fallback. |
| HTTPS | Caddy with automatic certificates | Needs a domain name. |
| File storage | Local volume first; Cloudflare R2 when you deploy | R2 free tier: 10 GB storage, 1 million writes and 10 million reads a month, no egress fee ([Cloudflare](https://cloudflare.com/en-ca/products/r2)). |
| Map tiles | Leaflet or MapLibre with a free tile source | The public OpenStreetMap tile server is not meant for heavy production use; *verify* the tile provider's free limits and attribution. |
| Reverse geocoding | Nominatim, called only from the backend with caching | The public instance allows light use only (about one request a second with an identifying User-Agent); *verify* the current policy. |
| Email (optional) | Any SMTP provider's free tier | Not needed for the MVP; in-app notifications come first. |
| AI (optional) | Rules by default; local Ollama or Gemini free tier behind `PriorityAdvisor` | Free-tier quotas change, so the app must run with AI switched off. |
| CI and frontend hosting | GitHub Actions; Cloudflare Pages, Netlify or Vercel free plans | *Verify* build-minute and bandwidth limits. |

## Decisions for you

The plan builds to the defaults below; tell me which ones to change before Phase 1 starts.

| Decision | Default in this plan | Change it if |
| --- | --- | --- |
| Old Firebase data | Start with an empty PostgreSQL database; seed demo data with a script | You have real users or issues to keep. Firebase password hashes do not drop into BCrypt, so accounts would be recreated or imported with custom code. |
| Android APK | Web first; the APK is updated later | The APK calls this backend today and would then need the new login flow too. |
| Phone OTP login | Dropped; email + password, Google sign-in optional | You can pay for an SMS provider. |
| SLA targets | Code values: Critical 1, High 2, Medium 4, Low 7 days, editable by Admin | You prefer the README values (1, 3, 7, 14 days). |
| Status names | Reported, Assigned, In Progress, Resolved, Rejected, Duplicate; "escalated" becomes an SLA flag, not a status | You want Escalated as a visible status. |
| Public visibility | Public map shows title, category, status, area and photos; reporter name and contact stay private | Reports must be fully private or fully public. |
| Authority scope | One department per authority | Staff cover several departments. |
| Spring Boot version | 4.1.x now; move to 4.2 after it is generally available | You want the newest line immediately. |
| Build tool | Maven | You prefer Gradle. |

## Sources

- [CivSetu repository](https://github.com/Sahil-memane/CivSetu): README, backend routes and services, frontend API calls, read 25 Oct 2026
- [VersionLog, Spring Boot lifecycle](https://versionlog.com/spring-boot/): versions and support dates, checked 24 Oct 2026
- [Cloudflare R2](https://cloudflare.com/en-ca/products/r2): free-tier allowances
- Your uploaded plan document, DFD levels 0 and 1, ER diagram and the DevX presentation
