# Langfuse Web v4.15.0 migration matrix

This project targets a standalone Vue 2 + Element UI frontend and a standalone Spring Boot
backend. The Java backend replaces the original Next.js/tRPC server surface;
the original worker and data stores remain compatible during the migration.

Status legend: `done`, `in progress`, `planned`, `deferred`.

## Current scope decision

The active migration scope is Observability Web: Home, Dashboards, Tracing,
Sessions, Users, project context, and their synchronous Web operations.
Prompt Management and Evaluation surfaces are deferred by product decision.
Existing Prompt code is preserved for possible reuse, but it is frozen and is
not part of current delivery or parity acceptance.

Tracing is the current delivery priority. Dashboard placement/layout work is
paused until Trace and Observation list/detail parity, synchronous operations,
exports, and real-data/browser verification are complete.

| Product surface | Frontend | Java API | Primary stores | Required operations | Status |
| --- | --- | --- | --- | --- | --- |
| Application shell and navigation | Vue 2 + Element UI | bootstrap/config | PolarDB-X | read project/org/session context | in progress |
| Home | Vue 2 + Element UI | dashboard | ClickHouse, PolarDB-X | read metrics/widgets | in progress |
| Dashboards and widgets | Vue 2 + Element UI | dashboards/widgets | PolarDB-X, ClickHouse | dashboard/widget CRUD and initial observability metric execution done; placement and advanced filters pending | in progress |
| Tracing / events | Vue 2 + Element UI | traces/observations/events | ClickHouse, S3 | list/detail, trend, Tree/Graph, complex filter and page export done; delete, writable score/comment operations and full export pending | in progress |
| Sessions | Vue 2 + Element UI | sessions | ClickHouse | list, detail, filter, export, delete | in progress |
| Users | Vue 2 + Element UI | users | ClickHouse | list, detail, filter | in progress |
| Alerts | Vue 2 + Element UI | alerts/monitors | PolarDB-X, ClickHouse, worker | CRUD, evaluate, notify | planned |
| Prompts | Vue 2 + Element UI | prompts | PolarDB-X, Redis | existing version CRUD preserved; no further work in current scope | deferred |
| Playground | Vue 2 + Element UI | playground | PolarDB-X, provider APIs | connections/tools/schemas CRUD, execute | planned |
| Scores | Vue 2 + Element UI | scores/score-configs | PolarDB-X, ClickHouse | excluded with Evaluation | deferred |
| Evaluators | Vue 2 + Element UI | evaluators/evaluation-rules | PolarDB-X, worker | excluded with Evaluation | deferred |
| Human annotation | Vue 2 + Element UI | annotation queues/items/assignments | PolarDB-X | excluded with Evaluation | deferred |
| Datasets | Vue 2 + Element UI | datasets/items/runs | PolarDB-X, ClickHouse, worker | excluded with Evaluation | deferred |
| Experiments | Vue 2 + Element UI | experiments | PolarDB-X, ClickHouse, worker | excluded with Evaluation | deferred |
| Project settings | Vue 2 + Element UI | projects/api-keys/members/models | PolarDB-X | CRUD, rotate/revoke, membership | planned |
| Organization settings | Vue 2 + Element UI | organizations/members/SSO | PolarDB-X | CRUD, roles, domains, SSO | planned |
| Integrations | Vue 2 + Element UI | integrations | PolarDB-X, S3, external APIs | CRUD, test, OAuth/webhooks | planned |
| Media and batch exports | Vue 2 + Element UI | media/exports | S3, Redis, worker | upload/download/delete, async export | planned |
| Authentication and RBAC | Vue 2 + Element UI | auth/session/RBAC | PolarDB-X, Redis | sign-in/out, password, scopes, audit | planned |
| Ingestion and public API | SDK/API | public REST/OTLP | ClickHouse, PolarDB-X, Redis, S3 | ingest and public CRUD compatibility | planned |

## Compatibility rules

- API contracts are versioned under `/api/v1`; compatibility adapters may
  mirror original public REST routes where external SDKs depend on them.
- ClickHouse event rows are append/version based. Destructive operations must
  follow Langfuse's deletion semantics and must not use ad-hoc table mutations.
- PolarDB-X mutations use Spring transactions. Cross-store workflows use an
  outbox plus worker jobs so PolarDB-X, ClickHouse, Redis, and S3 changes can
  be retried safely.
- Every migrated surface requires controller tests, mapper/transaction tests,
  and a browser comparison against Langfuse v4.15.0 before being marked done.

## Current vertical slice

- Standalone Vite/Vue 2 + Element UI frontend and Spring Boot service start independently.
- Spring Boot/MyBatis reads two ClickHouse models during the transition. Tracing
  reads the AgentObs tables (`hmp_agentobs_traces`, `hmp_agentobs_observations`,
  `hmp_agentobs_trace_locator`) through `TracingMapper`/`TracingSqlProvider`.
  Sessions, Users, Trace Scores and the Dashboard metrics still read the original
  Langfuse v4 `events_core`/`events_full` tables and migrate separately.
  A single request never mixes the two models.
- Summary, trend, traces, trace detail, observations, sessions, session detail,
  and users are the first compatibility slice.
- Trace list filters cover text, status, environment, user, session, exact tag,
  half-open time range, whitelisted sort fields, direction, and pagination.
- Observation list filters now cover text, environment, type/status, model,
  trace ID, exact tag, half-open time range, whitelisted sort fields,
  direction, and pagination. Trace and Observation lists provide current-page
  CSV export; full filter-aware batch export remains on Worker/MinIO.
- Trace/Span detail now uses one Spring Boot aggregate read model for the Trace
  and ordered observations. The responsive drawer provides Tree, Timeline,
  optional Graph, Data, Preview/Log View, Formatted/JSON, source-style I/O and
  flattened metadata. Scores and Comments are intentionally deferred by the
  current scope and are no longer requested by the Tracing page.
- Observation Type handling is source-complete for all ten Langfuse types:
  SPAN, GENERATION, EVENT, AGENT, TOOL, CHAIN, RETRIEVER, EVALUATOR, EMBEDDING,
  and GUARDRAIL. The Type facet intentionally mirrors Langfuse by returning only
  values present in the effective filtered dataset. The live 30-day project
  facet on 2026-09-07 contained SPAN and GENERATION only.
- Log View now mirrors the source four-column chronological layout, relative
  time/duration formatters, depth and short-ID labels, search, indentation and
  millisecond preferences, row/all expansion, copy action, non-null I/O detail,
  and a collapsible syntax-colored JSON observation array. Scores and Comments
  remain deferred.
- Trace Graph now renders parent-child edges from Observation relationships.
  Aggregated mode groups repeated step names and Expanded mode renders every
  call; both support selection, zoom, and fit-to-canvas.
- Observation search now supports bound and whitelisted numeric/date operators,
  negation, tag AND/OR groups, arbitrary metadata keys (`JSONExtractString` on the
  AgentObs JSON column), and `has:`/null-style predicates. Invalid incomplete
  tokens are not sent to the query backend. Score filters (`scores.<name>`) and
  fields with no AgentObs column (`toolCalls`, `modelId`, `release`, `tps`,
  `inputCost`, `outputCost`, `sdkName`, `sdkVersion`) are dropped rather than
  silently producing wrong results; they are also no longer offered in the UI.
- Source-compatible Quality, Slow, and Cost preset menus now apply the original
  preset queries and sort order. Search, facets, exact Trace ID, and presets
  share full-expression filter chips rendered directly inside the search
  composer, with per-chip removal, one-click clear-all, and URL/list/pulse
  synchronization. `toolDefinitions` is backed by a bound predicate over the
  AgentObs tool_definitions JSON column; the Missed tool calls preset is
  currently disabled because AgentObs has no `tool_calls` column.
- Project-scoped APIs require a matching `X-Project-Id` header, with CORS
  preflight handled separately. Validation errors return HTTP 400 and scope
  mismatches return HTTP 403.
- The 2026-09-11 regression run passed 52 backend tests and the Vite production
  build. Tracing was benchmarked on 455,249 AgentObs-shaped rows: list p95 104 ms,
  count p95 32 ms, Pulse p95 44 ms, Facet p95 24 ms, peak memory under 9 MiB.
  Filtering by `service_name` prunes the primary key from 58/58 to 5/58 granules;
  without it the query reads every granule in the time range, so the remaining
  performance work is production-scale validation of the unfiltered path.
  Sessions and Users still run on `events_core FINAL` and need the same
  optimization before release.
- Observation detail also exposes hierarchy depth, TTFT, status message,
  model parameters, usage/cost breakdowns, prompt reference, and expanded
  metadata, served from AgentObs `hmp_agentobs_observations`. TTFT comes from
  `time_to_first_chunk_ms` (a duration); AgentObs has no absolute
  `completion_start_time`, so that DTO field stays null.
- Users support environment filtering and a detail drawer with associated
  traces and sessions. Sessions and Users provide an explicitly page-scoped
  synchronous CSV export; filter-aware full batch export remains on the
  original worker/MinIO path.
- Real-stack parity check for project `cmt2am51r0006pa07ghcbt7vi` currently
  returns 597 unique traces from both ClickHouse and the Spring Boot API.
  Observability pagination is aligned at 50 rows per page. Hikari keepalive
  and bounded connection lifetimes protect the Java service from stale local
  Docker Desktop connections; unavailable stores return HTTP 503.
- Prompt list and version CRUD run through PolarDB-X compatibility tables.
  Version/label/tag transaction semantics and dependency guards are present;
  RBAC, audit events, Redis cache epochs, webhooks, folders, import/export,
  composability, and metrics remain before this surface can be marked done.
- Dashboard list, detail, create, metadata/definition/filter update, clone, and
  delete now run through Spring transactions and MyBatis annotations. Built-in
  Langfuse dashboards are read-only.
- Dashboard Widget list, detail, create, update, clone, and delete now use the
  PolarDB-X `dashboard_widgets` table through MyBatis annotations.
  New or cloned widgets are deliberately limited to Trace and Observation
  views while Evaluation remains deferred.
- Trace and Observation widgets can execute one validated metric through a
  whitelist-only MyBatis ClickHouse query and display a 30-day frontend
  preview. Time-series and common categorical dimensions are supported;
  advanced widget filters, additional measures, and dashboard placement/layout
  editing remain.

