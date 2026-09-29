-- Upgrade for environments that already created the local _all views.
--
-- WHY THIS EXISTS
--   A plain ClickHouse VIEW silently ignores "FROM view FINAL". When the local schema first
--   created the _all views as "AS SELECT * FROM table", every "…_all FINAL" query became a
--   no-op and replayed rows were counted more than once, so the list, the count, the trend
--   and the facets all disagreed with the base tables. The cluster does not have this
--   problem: there _all is a Distributed table, which does honour FINAL.
--
--   The fix is to define each ReplacingMergeTree-backed view with FINAL inside it. But
--   agentobs-clickhouse-schema-local.sql uses "CREATE VIEW IF NOT EXISTS", so re-running that
--   script against an existing environment leaves the old definitions in place and the fix
--   never takes effect. This file performs the actual replacement.
--
-- WHEN TO RUN
--   Once, on any environment created before the FINAL fix. Safe to re-run: CREATE OR REPLACE
--   is idempotent, and dropping/recreating a view never touches the underlying data.
--
-- AFTER RUNNING, VERIFY
--   Every ReplacingMergeTree-backed view below must end with "FINAL". The locator view is
--   deliberately excluded - it is an AggregatingMergeTree whose readers use minMerge/maxMerge.
--
--   SHOW CREATE VIEW default.hmp_agentobs_observations_all;
--   -- expect: AS SELECT * FROM default.hmp_agentobs_observations FINAL

CREATE OR REPLACE VIEW default.hmp_agentobs_traces_all AS
SELECT * FROM default.hmp_agentobs_traces FINAL;

CREATE OR REPLACE VIEW default.hmp_agentobs_observations_all AS
SELECT * FROM default.hmp_agentobs_observations FINAL;

CREATE OR REPLACE VIEW default.hmp_agentobs_scores_all AS
SELECT * FROM default.hmp_agentobs_scores FINAL;

CREATE OR REPLACE VIEW default.hmp_agentobs_span_events_all AS
SELECT * FROM default.hmp_agentobs_span_events FINAL;

CREATE OR REPLACE VIEW default.hmp_agentobs_span_links_all AS
SELECT * FROM default.hmp_agentobs_span_links FINAL;

CREATE OR REPLACE VIEW default.hmp_agentobs_logs_all AS
SELECT * FROM default.hmp_agentobs_logs FINAL;

CREATE OR REPLACE VIEW default.hmp_agentobs_metric_points_all AS
SELECT * FROM default.hmp_agentobs_metric_points FINAL;

-- The locator is an AggregatingMergeTree: it stores aggregate STATES and its readers collapse
-- them with minMerge/maxMerge. It is intentionally left without FINAL.
CREATE OR REPLACE VIEW default.hmp_agentobs_trace_locator_all AS
SELECT * FROM default.hmp_agentobs_trace_locator;

-- Verification: every row below must report has_final = 1, except the locator row.
SELECT
    name AS view_name,
    position(create_table_query, ' FINAL') > 0 AS has_final
FROM system.tables
WHERE database = 'default' AND engine = 'View' AND name LIKE 'hmp_agentobs%'
ORDER BY name;
