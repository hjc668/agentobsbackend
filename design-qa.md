# Tracing Design and Functional QA

> **历史验收快照**：本页记录早期 Vite/旧表阶段的截图与检查结果，不是 2026-09-22 当前前端（Vue 2.6 + Webpack 4）和 AgentObs 表的验收证明。现行接续状态请先读 [`PROJECT_PROGRESS.md`](PROJECT_PROGRESS.md)；其中的旧路径、模块数、历史 Trace ID 和实时行数不可作为当前环境断言。

- Source visual truth: `/Users/mac/Desktop/icbc/Aiops/langfuse/tracing-function-audit/01-source-default.png`
- Final implementation screenshot: `/Users/mac/Desktop/icbc/Aiops/langfuse/tracing-function-audit/13-tracing-steps-1-4-final.png`
- Viewport: 1280 × 720 CSS px
- State: desktop Tracing, 30-day range, Table view, live ClickHouse data through Spring Boot + MyBatis

## Visual comparison

The source and migrated screen were compared together at the same 1280 × 720 viewport. The migrated page preserves the source hierarchy and density: application navigation, search and preset toolbar, facet rail, Pulse trend strip, observation table, column controls and bottom pagination. The light palette is deliberate because the user explicitly requested the light version; it is not treated as a fidelity defect against the captured dark source.

## Step 1 — filter system

- Search completion replaces an unfinished draft: entering `tra` and choosing Trace ID produces only `traceId:`.
- Leading whitespace pasted into the Trace ID sidebar input is removed before querying.
- Known Trace ID `4172ee06996c2e3f86d01d869c9949fe` produced `traceId:=4172…` in the top bar, persisted in the URL and returned exactly one observation.
- Top search and sidebar facets share one source of truth. Unchecking SPAN produced `type:=GENERATION`, kept GENERATION checked and narrowed the live result; rechecking SPAN removed the generated token and restored the full result.
- Environment, Type, Root, Status, Name, Trace Name, Model, Prompt, Session, User and Tags use live ClickHouse facets. Trace ID and numeric/text filters support editable values and operators.
- Numeric sidebar verification: entering `>1` under Latency produced `latency:>1` above and returned 2,421 live rows at the time of the check.
- Search, sorting, direction, view and page state persist in the URL. Saved views persist the effective filter, columns, sort and view locally.

## Step 2 — main table

- Configurable 17-column table includes selection, Start Time, Type, Name, Trace Name, Input, Output, Metadata, Level, Latency, Cost, TTFT, Model, Prompt, Environment, Tags and Scores.
- Start Time, Latency and Cost sorting is interactive. Start Time was switched to ascending and persisted as `direction=ASC` in the URL.
- Column visibility updates immediately and is persisted; hiding Metadata changed the counter from 17/17 to 16/17 and removed its header, then Reset restored it.
- Row selection displays a batch bar with Export selected and Clear. Current-page export remains available independently.
- Page sizes 25/50/100/200, previous/next navigation, total estimate, dense-row toggle, Table/Chart switch and live Pulse trend are operational.
- My Views supports save, apply and delete; views persist filter, sort, direction, table/chart mode and visible columns.

## Step 3 — Trace detail

- Clicking the exact table record opens the matching Trace detail.
- Desktop keeps the navigation/detail split; the narrow layout now matches the original full-width drawer instead of leaving a clipped table visible behind a thin side panel.
- Narrow layout exposes the source-compatible Tree, Timeline, optional Graph and Data navigation. Desktop navigation exposes Tree, Timeline and optional Graph.
- The inspector intentionally exposes only Preview and Log View. Scores and Comments were removed from this migration slice at the user's request.
- Preview supports Formatted and JSON modes. Formatted data uses distinct Input/Output surfaces, a source-style Path/Value metadata table, and conditional model parameter/usage/cost sections.
- Timestamp precision, latency, TTFT, environment, version, release, model, token, cost and status attributes are surfaced when present. Header previous/next controls move through observations and Escape closes the drawer.
- Backend detail loading now uses one `/traces/{traceId}/view` read model containing the trace and ordered observations, replacing four frontend requests including the deferred score/comment calls.

## Step 4 — data consistency

- Both pages use project `cmt2am51r0006pa07ghcbt7vi` and showed the same live Codex telemetry names, timestamps, trace names, environment and metadata shape.
- The database is actively ingesting data, so aggregate totals changed during the check (roughly 62K → 98K in the 30-day window). This is expected live-data drift, not a source mismatch.
- The stable identity check used Trace ID `4172ee06996c2e3f86d01d869c9949fe`: the Spring Boot observation endpoint and migrated UI both returned exactly one row with that same Trace ID.
- Spring Boot detail, observations, scores and comments endpoints for a sampled Trace all returned HTTP 200.
- Pulse, table and facets all receive the same effective filter and time window.

## Regression checklist

- [x] Frontend TypeScript/Vite production build
- [x] Spring Boot query service connected to ClickHouse with the MyBatis profile
- [x] Filter draft replacement and whitespace normalization
- [x] Top/sidebar bidirectional state and reversible checkbox behavior
- [x] Exact Trace ID result and URL persistence
- [x] Numeric operator filter
- [x] Table sorting, column configuration and row selection
- [x] Pagination and page-size controls
- [x] Tree/Timeline/Graph/Data and Preview/Log View detail tabs
- [x] Same-database identity comparison
- [x] 1280 × 720 source/implementation visual comparison
- [x] Source-compatible Quality / Slow / Cost preset dropdowns
- [x] Real-time filter chips, single removal, and clear-all behavior

## Remaining scope

Per the requested stop point, this QA does not begin Sessions, Users, Alerts or any Prompt Management/Evaluation work.

## 2026-09-04 graph/filter/reliability extension

- Browser-verified fixed Trace `668904f3534ff5678327bcf81a3f5814`: exact Trace ID filter returned four rows and persisted in the URL.
- The Graph view visibly renders directed parent-child connections. Aggregated mode reported three unique steps with `auth · 2 calls`; Expanded mode reported four individual calls.
- Browser-verified `metadata.resourceAttributes.env:=local-langfuse` against live ClickHouse data.
- Browser-verified incomplete `scores.accuracy:` validation feedback; the incomplete token is not applied to backend SQL.
- Project scope protection returns 403 for a mismatched header and allows CORS preflight before checking the actual request.
- Full backend suite: 32 tests, zero failures. Frontend production build: passed. Live reliability baseline: 8 requests, concurrency 2, cold p95 3.727s and final warm p95 0.705s; permission and invalid-query probes passed.
- Desktop visual inspection at 1440 × 900 confirmed the light palette, connected graph, readable node labels, detail inspector, and navigation layout. The earlier 1280 × 720 source/implementation pair remains the stored pixel-comparison evidence.

## 2026-09-04 quick-filter and chip extension

- Preset names, descriptions, expressions, ordering, active state, toggle-off behavior and disabled `Low quality`/`Soon` row were matched to the original Langfuse preset catalog and `CategoryPresetChips` interaction.
- Browser verification confirmed `Quality → Errors Only` produced `level:ERROR`, stored `viewId=__langfuse_errors_only`, displayed a removable Status chip and refreshed both trend and list.
- Manual `type:SPAN latency:>1` produced two distinct chips and the same URL/list query. Removing only Type left `latency:>1` active and refreshed the result set; `Clear all` removed every chip and query parameter.
- `Quality → Missed tool calls` produced Type, Tool Definitions and Tool Calls chips, sent `type:GENERATION toolDefinitions:>0 toolCalls:=0`, and completed against the live ClickHouse-backed Spring Boot service without an API error.
- Spring Boot test suite now passes 33 tests with a dedicated SQL regression for tool definition/call count predicates. The Vite production build passes.
- Follow-up browser verification at the in-app narrow viewport confirmed that the search field remains usable, `type:SPAN latency:>1` renders two visible full-expression chips directly inside the outlined search composer, removing Type leaves only `latency:>1` in both URL and list query, and `Clear all` removes every inline chip and the `filter` URL parameter.

## 2026-09-04 inline composer parity correction

- Rechecked the original Langfuse composer with `type:SPAN`: the committed expression is rendered as one pale tag inside the search field, not in a separate active-filter row.
- Migrated UI now follows that structure. Browser verification confirmed `type:SPAN` becomes an inline tag with its own `×`, and the URL changes to `filter=type:SPAN` while Pulse and the observation list refresh.
- A second `level:ERROR` condition creates a second inline tag and narrows the live result. Deleting one tag preserves the other; `Clear all` removes all tags, clears the URL filter, and restores the unfiltered result.
- Keyboard behavior was checked: Enter commits a complete draft, Backspace on an empty draft removes the last tag, and invalid/incomplete drafts stay editable instead of being sent to the backend.

## 2026-09-05 Trace/Span drawer parity pass

- Source visual truth: original Langfuse live page at `http://localhost:3000/project/cmt2am51r0006pa07ghcbt7vi/traces?dateRange=30d`, captured in the in-app browser with Trace `243175040f0c1f2b37dbcb426e8d067c` / Span `d8b2e9025d55a8be` open.
- Rendered implementation: `http://127.0.0.1:5173/project/cmt2am51r0006pa07ghcbt7vi/traces?dateRange=30d&filter=type%3ASPAN`, captured in the same in-app browser with a live Span open.
- Evidence paths retained from the broader Tracing comparison: source `/Users/mac/Desktop/icbc/Aiops/langfuse/tracing-function-audit/01-source-default.png`; implementation `/Users/mac/Desktop/icbc/Aiops/langfuse/tracing-function-audit/13-tracing-steps-1-4-final.png`. The focused drawer captures are browser-inline evidence because this browser surface does not expose a screenshot file path.
- Viewport/state normalization: both focused captures used the same narrow 393 × 757 CSS viewport, light theme, Data/Preview/Formatted state, live ClickHouse-backed data. Browser capture density was normalized by the same in-app browser surface.
- Full-view comparison: the migrated drawer is now full-width with the top handle, compact item header, four-view navigation, detail header, attribute chips, detail tabs, format switch and scrolling content in the same visual hierarchy as the original.
- Focused region comparison: header controls, Data header, Input/Output surfaces and metadata table were readable in both captures. The implementation uses the project's Lucide icon set; no image assets were present in this drawer state.
- Typography: compact system sans sizes, weights, truncation and monospace values align with the source density; no actionable P0/P1/P2 mismatch remained in the inspected state.
- Spacing/layout: the earlier P1 clipped narrow panel was fixed with a full-viewport drawer and source-like section rhythm. The metadata table remains vertically scrollable for real long payloads.
- Colors/tokens: light surfaces, lavender active rules, neutral chips, green type/value accents and pale-green Output surface match the observed source palette.
- Copy/content: Tree, Timeline, Graph, Data, Preview, Log View, Formatted, JSON, Input, Output and Metadata match the source vocabulary. Scores and Comments are intentionally absent by scope.
- Interaction evidence: opening a live row produced the expected Trace/Span identity, Data view, Preview/Log View switch, Formatted/JSON switch, previous/next controls and collapsible attributes. The aggregate endpoint returned the same real Trace/Span and metadata.
- Console check: no application console error was observed during the successful render. A later browser automation click caused the in-app browser tab itself to crash; this was outside the rendered application and did not reproduce through build/API checks.

Comparison history:

1. P1 before fix: the narrow implementation occupied only a thin right-hand panel, hid most inspector content and left the table visible. Fix: added a full-width mobile drawer, top view tabs and state-specific panes.
2. P1 before fix: Timeline/Data navigation and source-style metadata were missing. Fix: added relative-duration Timeline, Data default state, Path/Value metadata and source-like I/O surfaces.
3. P2 before fix: opening detail made four calls and surfaced out-of-scope Scores/Comments. Fix: introduced the Spring Boot TraceView aggregate endpoint and reduced the UI to Preview and Log View.
4. Post-fix evidence: the same-width migrated capture visibly shows the full header, Tree/Timeline/Graph/Data navigation, Data details, chips, Preview/Log View, format control and metadata rows without clipping.

Follow-up polish (P3): verify a very deep, long-duration multi-observation Trace at desktop width to tune timeline label collision and graph density.

final result: passed

## 2026-09-07 Type facet and Log View parity pass

- Source captures: original Langfuse at `http://localhost:3000/project/cmt2am51r0006pa07ghcbt7vi/traces?dateRange=30d` with Trace `243175040f0c1f2b37dbcb426e8d067c` open in Log View; Formatted and JSON were captured in the in-app browser at 393 × 757.
- Implementation captures: migrated page at `http://127.0.0.1:5173/project/cmt2am51r0006pa07ghcbt7vi/traces?dateRange=30d&filter=type%3ASPAN` with a live three-observation Trace open; Formatted, JSON, and expanded-row states were captured at the same viewport.
- Type parity evidence: source shared enum, migrated TypeScript union, Spring Boot enum, MyBatis Type predicate and live facet response were checked together. The implementation recognizes all ten current Langfuse Observation Types. The live facet response contained only `SPAN=313570` and `GENERATION=2679`, matching the source behavior of hiding absent values.
- Formatted comparison: both views now have Search observations, indentation, milliseconds, expand/collapse and copy controls above Observation/Depth/Start/Duration columns. Row labels use `name (8-char id)`, depth uses `L{n}`, time is relative to trace start, and row expansion shows only available I/O/metadata.
- JSON comparison: the implementation now renders the observation array directly as an expanded, syntax-colored, collapsible tree with item counts. The earlier extra `traceId` wrapper and plain monospaced preformatted block were removed.
- Typography and spacing: narrow-viewport labels, row height, toolbar height, column density, pale dividers and light surfaces visually align with the captured source. Long JSON content scrolls without expanding the drawer width.
- Interaction evidence: Formatted/JSON switching, one-row expansion, all-row expansion control, indentation toggle, millisecond toggle, search input, JSON-node collapse controls and copy action are exposed with accessible names.
- Regression: frontend TypeScript/Vite production build passed; Spring Boot full suite passed 34 tests with zero failures, including the new exact ten-type enum assertion.

Comparison history:

1. P1 before fix: Formatted rendered each observation as a large Input/Output card with no depth, relative timing or source toolbar. Fix: replaced it with the source-style chronological log table and controls.
2. P1 before fix: JSON rendered a plain `{ traceId, observations }` block. Fix: changed the model to an Observation array and added source-like collapsible, colored JSON nodes.
3. P2 before fix: SPAN and GENERATION shared the only two icon paths and every other type visually fell back to SPAN. Fix: added exhaustive source-aligned icon/color mapping for all ten types and a backend parity test.
4. Post-fix capture: no remaining P0/P1/P2 issue was observed in the normalized Formatted, JSON or expanded-row states. Scores, Comments and JSON Beta remain intentionally outside scope.

final result: passed
