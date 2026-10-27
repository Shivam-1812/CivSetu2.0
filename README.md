# CivSetu

Crowdsourced civic issue reporting and resolution: citizens report local problems with a location
and photos, issues are routed to the responsible department, and authorities update and resolve
them with proof, all tracked transparently.

| Folder | Contents |
|---|---|
| [`backend/`](backend/README.md) | Spring Boot API (Java 17, PostgreSQL + PostGIS). Setup, structure and conventions are in its README. |
| [`frontend/`](frontend/README.md) | React web app (Vite, TypeScript, Tailwind, Leaflet) for citizens, authorities and admins |
| `docs/` | Requirements and implementation plan |
| `.github/workflows/` | CI: builds and tests the backend on every push |
| `.run/` | Shared IntelliJ IDEA run configurations |

Start the backend first (see its README), then the frontend: `cd frontend && npm install && npm run dev`,
and open http://localhost:5173.
