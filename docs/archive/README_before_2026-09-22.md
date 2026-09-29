# Langfuse Web migration

Standalone migration of the Langfuse v4.15.0 Web application:

- `frontend`: independently started Vue 2.7 + Element UI 2.15 + Vite application
- `backend`: independently started Spring Boot 2.7.18 + Java 8 service
- MyBatis annotation mappers only; no mapper XML files
- ClickHouse for observability events and PolarDB-X for transactional CRUD
- Existing Langfuse Redis, worker, and object storage stay available while
  their workflows are migrated incrementally

Start with `PROJECT_PROGRESS.md` when resuming after a context reset. See
`MIGRATION_MATRIX.md` for the complete page/API/operation inventory and the
current migration status.

## Current architecture

```text
Browser (Vue 2 + Element UI frontend :5173)
        |
        | REST/JSON
        v
Spring Boot (backend :8080)
        |
        +-- ClickHouse SqlSessionFactory
        |      `-- observability @Mapper/@SelectProvider
        |
        `-- PolarDB-X SqlSessionFactory + transactions
               |-- Prompt CRUD @Mapper annotations
               `-- Dashboard CRUD @Mapper annotations

Existing Langfuse worker :3030, Redis :6379 and MinIO :9090
remain online for ingestion and not-yet-migrated async workflows.
```

## Start with mock data

Requirements: JDK 8+ (not a JRE), Maven 3.6+, Node.js 20+, pnpm 9+.

```bash
# terminal 1
bash scripts/start-backend-mock.sh

# terminal 2
bash scripts/start-frontend.sh
```

The backend launcher prefers an installed JDK 8, but can run with JDK 17 or
another newer JDK because Maven still compiles with Java 8 release compatibility.
It also stops an existing backend from this project on `SERVER_PORT` (8080 by
default) before starting the replacement. It refuses to stop an unrelated
process that happens to use the same port.

Open `http://localhost:5173`. `start-backend-mock.sh` explicitly selects the
`mock` profile, stops an older backend from this project first, and does not
connect to PolarDB-X or ClickHouse. `start-backend.sh` uses the default
`mybatis` profile and connects to the local databases.

## Start with the local databases

All local ClickHouse and PolarDB-X connection settings are stored in
`backend/src/main/resources/application-mybatis.yml`. The default Spring profile
is `mybatis`, so IDEA needs no profile or database environment variables.

```bash
# Deployment/script launch: selects Java 8 and stops the old backend first
bash scripts/start-backend.sh

# Frontend
bash scripts/start-frontend-local-langfuse.sh
```

In IDEA, select JDK 8 and run
`com.icbc.aiops.langfuse.LangfuseQueryApplication` directly. Initialize a new
local container once with `backend/sql/polardbx-schema.sql`; subsequent starts
do not perform database administration from the launch script.

The frontend launcher uses `pnpm` when available and falls back to `npm`.
Existing `node_modules` are reused, which supports offline startup without an
unnecessary install. Before Vite starts, the script stops this project's old
frontend on `FRONTEND_PORT` (5173 by default), while refusing to stop an
unrelated process on that port.

`scripts/start-backend.sh` and the compatibility launcher
`scripts/start-backend-local-langfuse.sh` now contain no database variables;
both use the same resource configuration. The original Langfuse UI can continue
running on port 3000.

## Implemented API surface

Observability endpoints are scoped below `/api/v1/observability`; they read the
dedicated ClickHouse trace store globally after AAM authentication. PolarDB-X
compatibility CRUD is scoped below `/api/v1/workspace` using a server-side
workspace configuration, not a browser-supplied project id.

Observability:

- `GET /summary` and `GET /summary/timeseries`
- `GET /traces`, `GET /traces/{traceId}` and trace observations
- `GET /observations`
- `GET /sessions` and `GET /sessions/{sessionId}`
- `GET /users`

Prompt management:

- `GET /prompts`, `GET /prompts/{id}`, and versions by name
- `POST /prompts` creates an immutable new version
- `PUT /prompts/{id}/labels` moves deployment labels transactionally
- `PUT /prompts/{id}/tags` updates tags across every version
- `DELETE /prompts/{id}` and `DELETE /prompts?name=...`

Dashboards:

- `GET /dashboards` and `GET /dashboards/{dashboardId}`
- `POST /dashboards` and `POST /dashboards/{dashboardId}/clone`
- `PUT /dashboards/{dashboardId}/{metadata,definition,filters}`
- `DELETE /dashboards/{dashboardId}` for project-owned dashboards

Langfuse-managed dashboards (`project_id IS NULL`) are visible and cloneable,
but remain read-only, matching the original ownership contract.

Prompt creation automatically moves `latest`, enforces type consistency,
serializes concurrent version creation with a PolarDB-X row lock, blocks
dependent deletions/label removal, and respects protected labels.
Redis cache rotation, audit records, webhooks, and full Langfuse
organization/member compatibility are still tracked as required work in the
migration matrix.

## Build and test

```bash
cd backend
mvn test

cd ../frontend
pnpm run build
```

The current suite covers observability endpoints, MyBatis SQL generation,
Prompt CRUD, and Dashboard CRUD in mock mode. Real-stack verification uses
read-only requests unless a human explicitly chooses to exercise a write.

## Configuration

Edit `backend/src/main/resources/application-mybatis.yml` for local ClickHouse,
PolarDB-X and Hikari settings. Database configuration is not assembled by any
start script. Common HTTP, session, CORS and AAM settings remain in
`backend/src/main/resources/application.yml`; mock-only workspace isolation is
in `backend/src/main/resources/application-mock.yml`.

`start-backend-local-langfuse.sh` no longer reads Docker configuration or the
old Langfuse PostgreSQL container.

Langfuse's internal database schemas are not a stable public API. Re-run the
mapper integration suite whenever the original Langfuse version changes.

Tracing 切换至 AgentObs ClickHouse 新表的分阶段实施、查询映射和小批量调试数据平移步骤，见
`TRACING_CLICKHOUSE_REFACTOR_PLAN.md`；旧表与新表的完整结构差异见
`backend/sql/clickhouse/CLICKHOUSE_SCHEMA_DIFF.md`。

After changing Maven dependencies in IntelliJ IDEA, use **Reload All Maven
Projects** before running the application. The resolved runtime intentionally
keeps Spring Boot 2.7.18's Logback 1.2.12/SLF4J 1.7.36; the PolarDB-X
connector's logging artifacts are `provided` and do not replace them.

# Authentication and access model

The frontend first calls `GET /api/v1/auth/me`. Authentication is selected by a single
switch in `backend/src/main/resources/application.yml`:

| `app.auth.mode` | Sign-in | Roles come from |
|---|---|---|
| `mock` (current setting) | `POST /api/v1/auth/login` — any non-empty AAM number and ticket | `app.auth.admin-users` in `application.yml` |
| `aam` | In-house Hermes unified authentication: `/api/aam/login` → portal → `/aam/login/auth` | the `langfuse_user_roles` table |

In mock mode `38971135` and `admin` receive ADMIN; all other accounts receive VIEW. The
PolarDB-X workspace is the internal `app.workspace.project-id` value.

To deploy on the intranet, set `app.auth.mode: aam` and fill in the `aam:` block at the
bottom of `application.yml` with this application's own AAM client credentials. No Spring
profile and no environment variable are involved. `AamConfigurationValidator` refuses to
start if `aam` mode is selected while any required credential is still blank, so an AAM
deployment can never silently fall back to the mock sign-in.

The local development frontend (`5173`) and backend (`8080`) use an HttpOnly session cookie plus a readable CSRF cookie. Do not disable CSRF for deployment; only the AAM callback `/aam/login/auth` is exempt, because the unified authentication service cannot know a pre-login business token.

Use the same hostname for the frontend and backend in development (`127.0.0.1` by default) so session and CSRF cookies remain same-site. Production must use HTTPS and set `SERVER_SESSION_COOKIE_SECURE=true`; the session cookie is HttpOnly, defaults to `SameSite=Lax`, and defaults to a 30-minute timeout. The browser-supplied role is always ignored — VIEW/ADMIN is decided server side, and an unknown or unreachable role source falls back to VIEW rather than escalating.
