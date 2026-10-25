# CivSetu

Crowdsourced civic issue reporting and resolution: citizens report local problems with a location
and photos, issues are routed to the responsible department, and authorities update and resolve
them with proof, all tracked transparently.

| Folder | Contents |
|---|---|
| [`backend/`](backend/README.md) | Spring Boot API (Java 17, PostgreSQL + PostGIS). Setup, structure and conventions are in its README. |
| `docs/` | Requirements and implementation plan |
| `.github/workflows/` | CI: builds and tests the backend on every push |
| `.run/` | Shared IntelliJ IDEA run configurations |

The web frontend will be added under `frontend/` in a later phase.
