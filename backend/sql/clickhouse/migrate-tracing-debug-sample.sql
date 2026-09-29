-- Small-batch debug data migration: Langfuse events_core/events_full -> AgentObs.
--
-- LOCAL/TEST ONLY. Never run the DELETE statements in section 8 on a shared or
-- production cluster without confirming the batch owns those trace ids.
--
-- Scope of this script:
--   * copies a handful of COMPLETE traces (parent/child closed, exactly one root)
--     so the Tracing page can be developed and verified against the new schema;
--   * does NOT migrate history, does NOT touch the source tables, does NOT run
--     on a schedule. This is a development fixture, not an ingestion path.
--
-- Rules honored (refactor plan section 10.1):
--   * whole traces only, selected by complete trace_id, never a random subset;
--   * candidates must be inside the 7-day TTL window - never rewrite business
--     timestamps to keep data alive;
--   * the batch table records exactly which trace ids were copied;
--   * the locator MV is created before observations are inserted, so it backfills.
--
-- Requires the local schema first:
--   docker exec -i langfuse-clickhouse-1 clickhouse-client \
--     --multiquery < agentobs-clickhouse-schema-local.sql
--
-- OPERATIONAL NOTES (found the hard way on ClickHouse 25.12; verified 2026-09-10)
--
--   1. RUN THIS FILE IN SECTIONS, NOT AS ONE --multiquery SESSION.
--      Feeding the whole file at once accumulates memory across statements and
--      section 6 dies with Code 241 MEMORY_LIMIT_EXCEEDED (~6.9 GiB, the cgroup
--      derived server ceiling), even though each statement passes on its own.
--      Running the sections as separate client invocations works and is what was
--      verified. Re-running a section is safe once section 4 passed.
--
--   2. ClickHouse substitutes aliases TEXTUALLY, including inside aggregate
--      arguments. "min(start_time) AS start_time" makes every later read of
--      start_time expand to min(start_time), so "coalesce(max(end_time),
--      min(start_time))" becomes min(min(...)) -> Code 184 ILLEGAL_AGGREGATION.
--      The same trap applies to derived level/end_time columns. Aggregated or
--      derived columns here are therefore aliased min_start_time / max_end_time /
--      trace_level / resolved_end_time and mapped to their real names in an outer
--      SELECT.
--
--   3. The alias goes BEFORE FINAL: "FROM t AS x FINAL" is valid, "FROM t FINAL
--      AS x" is a syntax error.
--
--   4. FINAL DOES NOT SURVIVE A PLAIN VIEW. ClickHouse silently ignores
--      "FROM view FINAL" when the view is a plain "SELECT * FROM table", so any
--      read through such a view returns pre-merge duplicates. That is why every
--      ReplacingMergeTree-backed "_all" view in agentobs-clickhouse-schema-local.sql
--      is defined with FINAL inside it. Verified by inserting a duplicate span:
--      base table FINAL = 21 rows, the same FINAL through a plain view = 22.
--      Do not "simplify" those view definitions by removing FINAL.

-- ---------------------------------------------------------------------------
-- 1. Batch bookkeeping table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS default.hmp_agentobs_debug_migration_batch
(
    batch_id   String,
    trace_id   String,
    selected_at DateTime DEFAULT now()
)
ENGINE = MergeTree
ORDER BY (batch_id, trace_id);

-- ---------------------------------------------------------------------------
-- 2. Register the batch (fixed sample verified 2026-09-10, 3 days old, in TTL)
--
--    If these ids have aged out of the TTL window, do NOT edit timestamps:
--    use section 3 to re-select a fresh set instead.
-- ---------------------------------------------------------------------------
INSERT INTO default.hmp_agentobs_debug_migration_batch (batch_id, trace_id) VALUES
('tracing-refactor-20260910', 'a874a391240d48d9cfcfe66ea874b9cb'),
('tracing-refactor-20260910', 'c4b4ad2262f4a4893f26d33da248e905'),
('tracing-refactor-20260910', 'ba8298c87681f90ab4ae2f580068e3bb');

-- ---------------------------------------------------------------------------
-- 3. Alternative: dynamically pick 3 fresh candidates (only if section 2 aged out)
--    Uncomment and replace the batch above if needed.
-- ---------------------------------------------------------------------------
-- INSERT INTO default.hmp_agentobs_debug_migration_batch (batch_id, trace_id)
-- SELECT 'tracing-refactor-20260910', trace_id
-- FROM default.events_core FINAL
-- WHERE is_deleted = 0
--   AND length(trace_id) = 32
--   AND length(span_id) = 16
--   AND start_time >= now() - INTERVAL 6 DAY
-- GROUP BY trace_id
-- HAVING countIf(parent_span_id = '') = 1
--    AND countIf(end_time IS NULL) = 0
--    AND count() BETWEEN 2 AND 100
-- ORDER BY countIf(type = 'GENERATION') DESC, count() DESC, min(start_time) DESC
-- LIMIT 3;

-- ---------------------------------------------------------------------------
-- 4. MANDATORY PRE-CHECK - must return zero rows before inserting.
--    If it returns anything, stop: those trace ids already exist in the target
--    and were not written by this batch.
-- ---------------------------------------------------------------------------
SELECT 'PRE-CHECK conflicts (must be empty)' AS check_name, trace_id, count() AS rows
FROM default.hmp_agentobs_observations_all
WHERE trace_id IN
(
    SELECT trace_id FROM default.hmp_agentobs_debug_migration_batch
    WHERE batch_id = 'tracing-refactor-20260910'
)
GROUP BY trace_id;

-- ---------------------------------------------------------------------------
-- 5. Trace summaries
--
--    The legacy snapshot never set is_app_root, so the product root is derived
--    as the single span with an empty parent. Without this the new trace table
--    would stay empty for all historical data.
-- ---------------------------------------------------------------------------
INSERT INTO default.hmp_agentobs_traces
(
    schema_version, root_span_id, service_name, trace_id, trace_name,
    start_time, end_time, duration_ms, user_id, user_hash, session_id,
    conversation_id, request_id, tags, environment, version, status_code,
    status_message, level, trace_input, trace_output, metadata,
    extra_attributes, ingestion_version
)
-- NOTE: the aggregation runs in an inner query and the projection happens outside it.
-- ClickHouse substitutes aliases textually, so writing "min(start_time) AS start_time"
-- and then referencing start_time again in duration_ms would expand to min(min(start_time))
-- and fail with ILLEGAL_AGGREGATION. Keep the two layers separate.
SELECT
    'legacy-langfuse-migration-v1' AS schema_version,
    CAST(root_span_id AS FixedString(16)) AS root_span_id,
    coalesce(nullIf(service_name, ''), 'legacy-langfuse') AS service_name,
    CAST(trace_id AS FixedString(32)) AS trace_id,
    trace_name,
    min_start_time AS start_time,
    max_end_time AS end_time,
    toFloat64(greatest(toInt64(0), dateDiff('millisecond', min_start_time, max_end_time))) AS duration_ms,
    user_id,
    '' AS user_hash,
    session_id,
    '' AS conversation_id,
    '' AS request_id,
    tags,
    environment,
    version,
    status_code,
    status_message,
    trace_level AS level,
    trace_input,
    trace_output,
    metadata,
    '{}' AS extra_attributes,
    ingestion_version
FROM
(
    SELECT
        argMinIf(span_id, start_time, parent_span_id = '') AS root_span_id,
        argMinIf(service_name, start_time, parent_span_id = '') AS service_name,
        trace_id,
        coalesce(
            nullIf(argMaxIf(trace_name, event_ts, trace_name != ''), ''),
            argMinIf(name, start_time, parent_span_id = ''),
            argMin(name, start_time)) AS trace_name,
        -- Deliberately NOT aliased as start_time / end_time: ClickHouse substitutes
        -- aliases textually, and the aggregate expressions below read the raw
        -- start_time / end_time columns. Same-named aliases would rewrite those
        -- reads into nested aggregates (ILLEGAL_AGGREGATION) or cyclic aliases.
        min(start_time) AS min_start_time,
        coalesce(max(end_time), min(start_time)) AS max_end_time,
        argMax(user_id, event_ts) AS user_id,
        argMax(session_id, event_ts) AS session_id,
        argMax(tags, event_ts) AS tags,
        argMax(environment, event_ts) AS environment,
        argMax(version, event_ts) AS version,
        -- status_code reads the raw level column, so the derived level below must not
        -- be aliased as "level" - doing so would rewrite this countIf into a nested
        -- aggregate. Map trace_level -> level in the outer SELECT instead.
        toInt8(if(countIf(level = 'ERROR') > 0, 2, 1)) AS status_code,
        argMaxIf(status_message, event_ts, status_message != '') AS status_message,
        if(countIf(level = 'ERROR') > 0, 'ERROR', 'DEFAULT') AS trace_level,
        coalesce(nullIf(argMaxIf(input, event_ts, is_app_root), ''), argMinIf(input, start_time, parent_span_id = '')) AS trace_input,
        coalesce(nullIf(argMaxIf(output, event_ts, is_app_root), ''), argMinIf(output, start_time, parent_span_id = '')) AS trace_output,
        toJSONString(mapFromArrays(
            argMinIf(metadata_names, start_time, parent_span_id = ''),
            argMinIf(metadata_values, start_time, parent_span_id = ''))) AS metadata,
        toUInt64(toUnixTimestamp64Milli(max(event_ts))) AS ingestion_version
    FROM default.events_core FINAL
    WHERE is_deleted = 0
      AND trace_id IN
      (
          SELECT trace_id FROM default.hmp_agentobs_debug_migration_batch
          WHERE batch_id = 'tracing-refactor-20260910'
      )
    GROUP BY trace_id
);

-- ---------------------------------------------------------------------------
-- 6. Observations
-- ---------------------------------------------------------------------------
INSERT INTO default.hmp_agentobs_observations
(
    schema_version, is_product_root, usage_present, tool_definitions,
    scope_name, scope_version, resource_schema_url, scope_schema_url,
    trace_state, trace_flags, dropped_attributes_count, dropped_events_count,
    dropped_links_count, error_type, service_name, trace_id, span_id,
    parent_span_id, span_kind, type, name, operation, start_time, end_time,
    duration_ms, level, status_code, status_message, session_id, user_id,
    user_hash, conversation_id, request_id, environment, version,
    request_model, model, provider, model_parameters,
    usage_input_tokens, usage_output_tokens, usage_total_tokens,
    usage_cache_read_tokens, usage_cache_write_tokens, usage_reasoning_tokens,
    usage_audio_output_tokens, cost, finish_reasons, response_id,
    time_to_first_chunk_ms, tool_name, tool_call_id, tool_type,
    agent_name, agent_id, agent_version, prompt_name, prompt_version,
    system_instructions, input, output, metadata, resource_attributes,
    scope_attributes, extra_attributes, ingestion_version
)
SELECT
    'legacy-langfuse-migration-v1' AS schema_version,
    (is_app_root OR parent_span_id = '') AS is_product_root,
    arrayFilter(k -> mapContains(usage_details, k), ['input', 'output', 'total']) AS usage_present,
    toJSONString(tool_definitions) AS tool_definitions,
    scope_name,
    scope_version,
    '' AS resource_schema_url,
    '' AS scope_schema_url,
    '' AS trace_state,
    toUInt32(0) AS trace_flags,
    toUInt32(0) AS dropped_attributes_count,
    toUInt32(0) AS dropped_events_count,
    toUInt32(0) AS dropped_links_count,
    if(level = 'ERROR', status_message, '') AS error_type,
    coalesce(nullIf(service_name, ''), 'legacy-langfuse') AS service_name,
    CAST(trace_id AS FixedString(32)) AS trace_id,
    CAST(span_id AS FixedString(16)) AS span_id,
    parent_span_id,
    'INTERNAL' AS span_kind,
    if(type IN ('SPAN','GENERATION','EVENT','AGENT','TOOL','CHAIN','RETRIEVER','EVALUATOR','EMBEDDING','GUARDRAIL'), type, 'SPAN') AS type,
    name,
    name AS operation,
    start_time,
    -- Renamed on purpose: an alias literally named end_time would be substituted into
    -- the duration_ms expression below and produce a cyclic alias.
    coalesce(end_time, start_time) AS resolved_end_time,
    toFloat64(greatest(toInt64(0), dateDiff('millisecond', start_time, resolved_end_time))) AS duration_ms,
    if(level IN ('DEBUG','DEFAULT','WARNING','ERROR'), level, 'DEFAULT') AS level,
    toInt8(if(level = 'ERROR', 2, 1)) AS status_code,
    status_message,
    session_id,
    user_id,
    '' AS user_hash,
    '' AS conversation_id,
    '' AS request_id,
    environment,
    version,
    provided_model_name AS request_model,
    provided_model_name AS model,
    '' AS provider,
    model_parameters,
    toUInt64(usage_details['input']) AS usage_input_tokens,
    toUInt64(usage_details['output']) AS usage_output_tokens,
    toUInt64(if(mapContains(usage_details, 'total'), usage_details['total'], usage_details['input'] + usage_details['output'])) AS usage_total_tokens,
    toUInt64(0) AS usage_cache_read_tokens,
    toUInt64(0) AS usage_cache_write_tokens,
    toUInt64(0) AS usage_reasoning_tokens,
    toUInt64(0) AS usage_audio_output_tokens,
    if(
        mapContains(cost_details, 'total'),
        CAST(cost_details['total'] AS Nullable(Float64)),
        if(calculated_total_cost > 0, CAST(calculated_total_cost AS Nullable(Float64)), CAST(NULL AS Nullable(Float64)))) AS cost,
    CAST([] AS Array(String)) AS finish_reasons,
    '' AS response_id,
    if(completion_start_time IS NULL, CAST(NULL AS Nullable(Float64)),
       CAST(dateDiff('millisecond', start_time, completion_start_time) AS Nullable(Float64))) AS time_to_first_chunk_ms,
    '' AS tool_name,
    '' AS tool_call_id,
    '' AS tool_type,
    '' AS agent_name,
    '' AS agent_id,
    '' AS agent_version,
    prompt_name,
    if(prompt_version IS NULL, '', toString(prompt_version)) AS prompt_version,
    '' AS system_instructions,
    input,
    output,
    toJSONString(mapFromArrays(metadata_names, metadata_values)) AS metadata,
    '{}' AS resource_attributes,
    '{}' AS scope_attributes,
    '{}' AS extra_attributes,
    toUInt64(toUnixTimestamp64Milli(event_ts)) AS ingestion_version
FROM default.events_full FINAL
WHERE is_deleted = 0
  AND length(trace_id) = 32
  AND length(span_id) = 16
  AND trace_id IN
  (
      SELECT trace_id FROM default.hmp_agentobs_debug_migration_batch
      WHERE batch_id = 'tracing-refactor-20260910'
  );

-- ---------------------------------------------------------------------------
-- 7. Acceptance checks - all four must pass
-- ---------------------------------------------------------------------------

-- 7.1 observation counts per trace must match the source
SELECT
    b.trace_id                    AS trace_id,
    old_rows                      AS old_rows,
    ifNull(new_rows, 0)           AS new_rows,
    if(old_rows = ifNull(new_rows, 0), 'OK', 'MISMATCH') AS verdict
FROM
(
    SELECT trace_id, count() AS old_rows
    FROM default.events_full FINAL
    WHERE is_deleted = 0
      AND trace_id IN (SELECT trace_id FROM default.hmp_agentobs_debug_migration_batch WHERE batch_id = 'tracing-refactor-20260910')
    GROUP BY trace_id
) AS b
LEFT JOIN
(
    SELECT toString(trace_id) AS trace_id, count() AS new_rows
    FROM default.hmp_agentobs_observations FINAL
    WHERE trace_id IN (SELECT trace_id FROM default.hmp_agentobs_debug_migration_batch WHERE batch_id = 'tracing-refactor-20260910')
    GROUP BY trace_id
) AS n USING trace_id
ORDER BY b.trace_id;

-- 7.2 exactly one trace summary per trace
SELECT toString(trace_id) AS trace_id, count() AS summaries,
       if(count() = 1, 'OK', 'MISMATCH') AS verdict
FROM default.hmp_agentobs_traces FINAL
WHERE trace_id IN (SELECT trace_id FROM default.hmp_agentobs_debug_migration_batch WHERE batch_id = 'tracing-refactor-20260910')
GROUP BY trace_id;

-- 7.3 no dangling parent (root excluded) - must be empty
-- Note the alias-before-FINAL order: ClickHouse rejects "table FINAL AS alias".
SELECT toString(o.trace_id) AS trace_id, toString(o.span_id) AS span_id, o.parent_span_id AS parent
FROM default.hmp_agentobs_observations AS o FINAL
LEFT JOIN default.hmp_agentobs_observations AS p FINAL
  ON o.trace_id = p.trace_id AND o.parent_span_id = toString(p.span_id)
WHERE o.parent_span_id != ''
  AND p.span_id = CAST('', 'FixedString(16)')
  AND o.trace_id IN (SELECT trace_id FROM default.hmp_agentobs_debug_migration_batch WHERE batch_id = 'tracing-refactor-20260910');

-- 7.4 the locator must have been populated by the MV, per service
SELECT toString(trace_id) AS trace_id, service_name, minMerge(min_start_state) AS min_start, maxMerge(max_end_state) AS max_end
FROM default.hmp_agentobs_trace_locator_all
WHERE trace_id IN (SELECT trace_id FROM default.hmp_agentobs_debug_migration_batch WHERE batch_id = 'tracing-refactor-20260910')
GROUP BY trace_id, service_name
ORDER BY trace_id;

-- 7.5 type / model coverage of the migrated sample
SELECT toString(trace_id) AS trace_id, type, count() AS n
FROM default.hmp_agentobs_observations FINAL
WHERE trace_id IN (SELECT trace_id FROM default.hmp_agentobs_debug_migration_batch WHERE batch_id = 'tracing-refactor-20260910')
GROUP BY trace_id, type ORDER BY trace_id, type;

-- ---------------------------------------------------------------------------
-- 8. LOCAL ROLLBACK - only if section 4 confirmed this batch owns the ids
-- ---------------------------------------------------------------------------
-- ALTER TABLE default.hmp_agentobs_observations DELETE WHERE toString(trace_id) IN
-- ('a874a391240d48d9cfcfe66ea874b9cb','c4b4ad2262f4a4893f26d33da248e905','ba8298c87681f90ab4ae2f580068e3bb');
--
-- ALTER TABLE default.hmp_agentobs_traces DELETE WHERE toString(trace_id) IN
-- ('a874a391240d48d9cfcfe66ea874b9cb','c4b4ad2262f4a4893f26d33da248e905','ba8298c87681f90ab4ae2f580068e3bb');
--
-- ALTER TABLE default.hmp_agentobs_debug_migration_batch
-- DELETE WHERE batch_id = 'tracing-refactor-20260910';
--
-- Wait for system.mutations.is_done = 1 before re-inserting.
-- The locator is a derived index: rebuild it from observations after a rollback.
