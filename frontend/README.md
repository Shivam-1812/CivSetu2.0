# CivSetu web app

The original CivSetu interface (shadcn/ui components on Radix, Tailwind 3, the civic blue theme), rebuilt
on React 19 + TypeScript and connected to the Spring Boot API in [`../backend`](../backend/README.md).
Firebase, Google Maps and the old Node API are gone.

| Concern | Choice |
|---|---|
| UI kit | shadcn/ui components in `src/components/ui` (Radix primitives), `lucide-react` icons |
| Styling | Tailwind CSS 3 with the original theme (`tailwind.config.ts`, `src/index.css`) |
| Routing | React Router 7; portal pages lazy-loaded |
| Server data | TanStack Query (caching, feed paging, refetch on live events) |
| Maps | Leaflet + OpenStreetMap tiles, no API key |
| Toasts | `sonner` and the shadcn toaster |
| API types | Generated from the backend's OpenAPI document |
| Tests | Vitest + Testing Library (unit), Playwright (end to end) |

## Screens

| Who | Where | What |
|---|---|---|
| Everyone | `/` | Home: hero, live statistics, features, how it works |
| Everyone | `/map` | Issue Map: map plus the community feed (like, dislike, comment), filters, SLA-breached toggle |
| Everyone | `/issues/:id` | One issue: photos and voice note, timeline, votes, comments, official responses |
| Citizen | `/report` | Report with photos, voice (with live transcription), location picker, duplicate warning |
| Citizen | `/dashboard` | Own reports and their progress; surveys to answer |
| Department official | `/authority` | Department Portal: overview, **Manage Issues** (all of the department's issues: plan, resolve, reject, not our department, duplicate), Map & Surveys, Analytics |
| Admin | `/admin` | Admin Portal: overview, all issues (read-only), Map & Surveys, Analytics, Users, Departments, Audit log |

Routing, priority and deadlines are automatic; no screen lets anyone change them. Sign-up creates
citizen accounts only; officials and admins get accounts from an Admin.

## Run it

Needs Node 20+ and the backend running on port 8080 (`docker compose up -d` and the
**CivSetu API (dev)** run configuration).

```bash
npm install
npm run dev        # http://localhost:5173, /api is proxied to :8080
```

The dev proxy keeps one origin, so the refresh-token cookie and the live notification stream
work without CORS. For a separate API host, copy `.env.example` to `.env` and set `VITE_API_BASE_URL`.

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Dev server with hot reload |
| `npm run build` | Type-check, then production build into `dist/` |
| `npm run typecheck` | `tsc` only |
| `npm test` | Unit and component tests (Vitest) |
| `npm run api:types` | Regenerate `src/api/schema.d.ts` from a running backend; run after any API change |
| `npm run e2e` | Playwright: the full citizen → system → community → official loop in a real browser |

### End-to-end tests

`npm run e2e` expects:

- the backend running on the dev profile
- the Compose database container `backend-postgres-1` (override with `E2E_DB_CONTAINER`)
- Java on the PATH, and the backend built once with `mvnw` (the tests reuse Spring Security's password hasher from `~/.m2`)

Each run creates a throwaway Admin, its own department and category, and users with a random password.
When it finishes it deletes all of them, plus their issues and uploaded files. The test uses its own
department so the background sweeper never hands demo issues to a test account.
First run only: `npx playwright install chromium`.

## Layout

```
src/
  api/            client.ts (fetch, token refresh, errors), endpoints.ts, generated schema + readable types
  auth/           AuthProvider (session restore), RequireRole
  components/
    ui/           shadcn/ui components (kept as generated, small fixes only)
    home/         landing page sections
    layout/       Navbar (notifications), Footer, MainLayout, PortalLayout (sidebar)
    issues/       IssueCard, IssueDetailModal, workflow dialogs, filters, vote hook
    surveys/      survey list/answer dialog, survey creation
    CivicMap.tsx  Leaflet issue map with survey clusters
    shared.tsx    form fields, cards and alerts used by the admin pages
  hooks/          live notification stream, toast, debounce
  lib/            formatting, issue badges and labels, cn()
  pages/          public and citizen pages; portal/ and admin/ for staff
e2e/              Playwright specs, setup and teardown
```

## Conventions

- Every API call goes through `src/api/endpoints.ts`; components never call `fetch` directly
  (the one exception is `useMediaUrl`, which downloads files with the token into blob URLs).
- Access tokens live in memory only. The refresh token is an httpOnly cookie the app can't read.
  Refreshes are single-flight and serialised across tabs.
- Server errors arrive as problem JSON; forms show `fieldErrors` next to the matching field.
- Response fields are typed as possibly missing (springdoc marks them optional), so handle `undefined`.
- Pages are guarded by role in `App.tsx`; the backend still enforces every rule on its own.
