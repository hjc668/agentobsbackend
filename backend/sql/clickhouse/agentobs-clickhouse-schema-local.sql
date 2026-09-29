-- AgentObs ClickHouse LOCAL single-node schema.
--
-- DERIVED from agentobs-clickhouse-schema-final.sql. Do not edit by hand without
-- re-deriving: the final DDL is the authority for columns, partitioning, ordering,
-- indexes and TTL. This file only changes what cannot run on a single node.
--
-- Transform rules applied to the final DDL (nothing else is modified):
--   1. removed every "ON CLUSTER {cluster}";
--   2. ReplicatedReplacingMergeTree(path, '{replica}', ingestion_version)
--        -> ReplacingMergeTree(ingestion_version);
--   3. ReplicatedAggregatingMergeTree(path, '{replica}')
--        -> AggregatingMergeTree();
--   4. the 8 Distributed "_all" tables -> local views selecting from the local table,
--      so the backend keeps querying the same "_all" names on one node.
--   5. each ReplacingMergeTree-backed "_all" view selects with FINAL.
--      This one is NOT cosmetic: ClickHouse silently ignores "FROM view FINAL" for a
--      plain view, so a view defined without FINAL would return pre-merge duplicates and
--      the node would disagree with the cluster, where "_all" is a Distributed table
--      that does honour FINAL. Verified with a duplicated span: base table FINAL = 21,
--      "FINAL" through a plain view = 22. The locator view is left alone because its
--      readers use minMerge/maxMerge on an AggregatingMergeTree.
--
--      Because these are "CREATE VIEW IF NOT EXISTS", re-running THIS FILE against an
--      environment that already has the old view definitions will NOT upgrade them.
--      Run upgrade-views-add-final.sql on such an environment instead; it replaces the
--      definitions with CREATE OR REPLACE VIEW and is safe to re-run.
--   6. DETAIL TTL IS 30 DAYS HERE, NOT 7. This is the one intentional divergence from
--      the final DDL, requested for local development so the 30d range the UI offers can
--      actually return complete data. The final (cluster) DDL still says 7 days and that
--      remains the open product decision - do not treat this file as the contract for it.
--
-- Columns, PARTITION BY, ORDER BY / PRIMARY KEY and indexes are identical to the final
-- DDL; TTL is the deliberate exception described in rule 6.

-- Derived from: AgentObs ClickHouse final cluster schema (target ClickHouse 21.8.14.5).
-- Database: default. Single node: one shard, no replicas, no Keeper.
--
-- Local deployment contract inherited from the final schema:
--   1. Producers write to the local tables; the *_all views exist so the backend can
--      always query the same "_all" names it uses in a cluster deployment.
--   2. service_name remains the first identity/query dimension in fact tables.
--   3. All records belonging to one Trace are co-located by trace_id; span_* and the
--      locator key on trace_id as well.
--   4. Metrics are not Trace records and keep their series identity ordering.
--   5. Detail fact tables and the trace locator retain 30 days of data locally
--      (see transform rule 6; the final cluster DDL still specifies 7).

CREATE TABLE IF NOT EXISTS default.hmp_agentobs_traces (
    schema_version String,
    root_span_id FixedString(16),

    service_name      LowCardinality(String),
    trace_id          FixedString(32),
    trace_name        String,
    start_time        DateTime64(6, 'UTC'),
    end_time          DateTime64(6, 'UTC'),
    duration_ms       Float64,
    user_id           String,
    user_hash         String,
    session_id        String,
    conversation_id   String,
    request_id        String,
    tags              Array(String),
    environment       LowCardinality(String),
    version           String,
    status_code       Int8,
    status_message    String,
    level             LowCardinality(String),
    trace_input       String CODEC(ZSTD(3)),
    trace_output      String CODEC(ZSTD(3)),
    metadata          String CODEC(ZSTD(3)),
    extra_attributes  String CODEC(ZSTD(3)),
    ingestion_version UInt64,
    INDEX idx_trace_id trace_id TYPE bloom_filter(0.001) GRANULARITY 4,
    INDEX idx_session_id session_id TYPE bloom_filter(0.01) GRANULARITY 4
) ENGINE = ReplacingMergeTree(ingestion_version)
PARTITION BY toDate(start_time)
PRIMARY KEY (service_name, toDate(start_time))
ORDER BY (service_name, toDate(start_time), start_time, trace_id)
TTL toDate(start_time) + INTERVAL 30 DAY
SETTINGS index_granularity = 8192, ttl_only_drop_parts = 1;

CREATE TABLE IF NOT EXISTS default.hmp_agentobs_observations (
    schema_version String,
    is_product_root Bool,
    usage_present Array(String),
    tool_definitions String CODEC(ZSTD(3)),
    scope_name String,
    scope_version String,
    resource_schema_url String,
    scope_schema_url String,
    trace_state String,
    trace_flags UInt32,
    dropped_attributes_count UInt32,
    dropped_events_count UInt32,
    dropped_links_count UInt32,
    error_type LowCardinality(String),

    service_name        LowCardinality(String),
    trace_id            FixedString(32),
    span_id             FixedString(16),
    parent_span_id      String,
    span_kind           LowCardinality(String),
    type                LowCardinality(String),
    name                String,
    operation           LowCardinality(String),
    start_time          DateTime64(6, 'UTC'),
    end_time            DateTime64(6, 'UTC'),
    duration_ms         Float64,
    level               LowCardinality(String),
    status_code         Int8,
    status_message      String,
    session_id          String,
    user_id             String,
    user_hash           String,
    conversation_id     String,
    request_id          String,
    environment         LowCardinality(String),
    version             String,
    request_model       LowCardinality(String),
    model               LowCardinality(String),
    provider            LowCardinality(String),
    model_parameters    String CODEC(ZSTD(3)),
    usage_input_tokens          UInt64,
    usage_output_tokens         UInt64,
    usage_total_tokens          UInt64,
    usage_cache_read_tokens     UInt64,
    usage_cache_write_tokens    UInt64,
    usage_reasoning_tokens      UInt64,
    usage_audio_output_tokens   UInt64,
    cost                Nullable(Float64),
    finish_reasons      Array(String),
    response_id         String,
    time_to_first_chunk_ms Nullable(Float64),
    tool_name           LowCardinality(String),
    tool_call_id        String,
    tool_type           LowCardinality(String),
    agent_name          LowCardinality(String),
    agent_id            String,
    agent_version       String,
    prompt_name         String,
    prompt_version      String,
    system_instructions String CODEC(ZSTD(3)),
    input               String CODEC(ZSTD(3)),
    output              String CODEC(ZSTD(3)),
    metadata            String CODEC(ZSTD(3)),
    resource_attributes String CODEC(ZSTD(3)),
    scope_attributes    String CODEC(ZSTD(3)),
    extra_attributes    String CODEC(ZSTD(3)),
    ingestion_version   UInt64,
    INDEX idx_trace_id trace_id TYPE bloom_filter(0.001) GRANULARITY 4,
    INDEX idx_span_id span_id TYPE bloom_filter(0.001) GRANULARITY 4,
    INDEX idx_session_id session_id TYPE bloom_filter(0.01) GRANULARITY 4
) ENGINE = ReplacingMergeTree(ingestion_version)
PARTITION BY toDate(start_time)
PRIMARY KEY (service_name, toDate(start_time))
ORDER BY (service_name, toDate(start_time), start_time, trace_id, span_id)
TTL toDate(start_time) + INTERVAL 30 DAY
SETTINGS index_granularity = 8192, ttl_only_drop_parts = 1;

-- Scores are submitted through the query REST API, not the telemetry Kafka topics.
CREATE TABLE IF NOT EXISTS default.hmp_agentobs_scores (
    session_id String,
    config_id String,

    service_name      LowCardinality(String),
    score_id          String,
    trace_id          String,
    observation_id    String,
    name              LowCardinality(String),
    value             Float64,
    value_string      String,
    data_type         LowCardinality(String),
    comment           String,
    source            LowCardinality(String),
    created_at        DateTime64(3, 'UTC'),
    ingestion_version UInt64,
    INDEX idx_score_trace_id trace_id TYPE bloom_filter(0.001) GRANULARITY 4,
    INDEX idx_score_observation_id observation_id TYPE bloom_filter(0.001) GRANULARITY 4
) ENGINE = ReplacingMergeTree(ingestion_version)
PARTITION BY toDate(created_at)
PRIMARY KEY (service_name, toDate(created_at))
ORDER BY (service_name, toDate(created_at), trace_id, observation_id, name, score_id)
TTL toDate(created_at) + INTERVAL 30 DAY
SETTINGS index_granularity = 8192, ttl_only_drop_parts = 1;

CREATE TABLE IF NOT EXISTS default.hmp_agentobs_span_events (
    service_name LowCardinality(String),
    trace_id FixedString(32),
    span_id FixedString(16),
    event_uid FixedString(32),
    name LowCardinality(String),
    event_time DateTime64(6, 'UTC'),
    exception_type String,
    exception_message String CODEC(ZSTD(3)),
    exception_stacktrace String CODEC(ZSTD(3)),
    attributes String CODEC(ZSTD(3)),
    ingestion_version UInt64,
    INDEX idx_trace_id trace_id TYPE bloom_filter(0.001) GRANULARITY 4,
    INDEX idx_span_id span_id TYPE bloom_filter(0.001) GRANULARITY 4
) ENGINE = ReplacingMergeTree(ingestion_version)
PARTITION BY toDate(event_time)
ORDER BY (service_name, toDate(event_time), trace_id, span_id, event_time, event_uid)
TTL toDate(event_time) + INTERVAL 30 DAY
SETTINGS index_granularity = 8192, ttl_only_drop_parts = 1;

-- A Link row is co-located with its source trace_id. linked_trace_id may belong
-- to another shard and is resolved through the linked-trace index/query path.
CREATE TABLE IF NOT EXISTS default.hmp_agentobs_span_links (
    service_name LowCardinality(String),
    trace_id FixedString(32),
    span_id FixedString(16),
    linked_trace_id FixedString(32),
    linked_span_id FixedString(16),
    link_uid FixedString(32),
    start_time DateTime64(6, 'UTC'),
    attributes String CODEC(ZSTD(3)),
    ingestion_version UInt64,
    INDEX idx_trace_id trace_id TYPE bloom_filter(0.001) GRANULARITY 4,
    INDEX idx_linked_trace_id linked_trace_id TYPE bloom_filter(0.001) GRANULARITY 4
) ENGINE = ReplacingMergeTree(ingestion_version)
PARTITION BY toDate(start_time)
ORDER BY (service_name, toDate(start_time), trace_id, span_id, linked_trace_id, linked_span_id, link_uid)
TTL toDate(start_time) + INTERVAL 30 DAY
SETTINGS index_granularity = 8192, ttl_only_drop_parts = 1;

CREATE TABLE IF NOT EXISTS default.hmp_agentobs_logs (
    schema_version String,
    resource_schema_url String,
    scope_schema_url String,
    trace_flags UInt32,
    dropped_attributes_count UInt32,
    raw_log String CODEC(ZSTD(3)),

    service_name LowCardinality(String),
    event_uid FixedString(32),
    timestamp DateTime64(6, 'UTC'),
    observed_timestamp DateTime64(6, 'UTC'),
    trace_id String,
    span_id String,
    severity_number Int16,
    severity_text LowCardinality(String),
    body String CODEC(ZSTD(3)),
    environment LowCardinality(String),
    version String,
    scope_name LowCardinality(String),
    scope_version String,
    resource_attributes String CODEC(ZSTD(3)),
    scope_attributes String CODEC(ZSTD(3)),
    attributes String CODEC(ZSTD(3)),
    ingestion_version UInt64,
    INDEX idx_trace_id trace_id TYPE bloom_filter(0.001) GRANULARITY 4
) ENGINE = ReplacingMergeTree(ingestion_version)
PARTITION BY toDate(timestamp)
ORDER BY (service_name, toDate(timestamp), timestamp, trace_id, span_id, event_uid)
TTL toDate(timestamp) + INTERVAL 30 DAY
SETTINGS index_granularity = 8192, ttl_only_drop_parts = 1;

CREATE TABLE IF NOT EXISTS default.hmp_agentobs_metric_points (
    series_id FixedString(32),
    description String,
    special_value String,
    exponential_zero_threshold Nullable(Float64),
    scope_name String,
    scope_version String,
    resource_schema_url String,
    scope_schema_url String,
    exemplars String CODEC(ZSTD(3)),
    quantile_values String CODEC(ZSTD(3)),
    raw_point String CODEC(ZSTD(3)),
    schema_version String,

    service_name LowCardinality(String),
    point_uid FixedString(32),
    metric_name LowCardinality(String),
    metric_type LowCardinality(String),
    unit LowCardinality(String),
    timestamp DateTime64(6, 'UTC'),
    start_timestamp Nullable(DateTime64(6, 'UTC')),
    value_float Nullable(Float64),
    value_int Nullable(Int64),
    flags UInt32,
    count UInt64,
    sum Nullable(Float64),
    min Nullable(Float64),
    max Nullable(Float64),
    bucket_counts Array(UInt64),
    explicit_bounds Array(Float64),
    exponential_scale Nullable(Int32),
    exponential_zero_count UInt64,
    exponential_positive_offset Nullable(Int32),
    exponential_positive_bucket_counts Array(UInt64),
    exponential_negative_offset Nullable(Int32),
    exponential_negative_bucket_counts Array(UInt64),
    is_monotonic UInt8,
    aggregation_temporality LowCardinality(String),
    attributes String CODEC(ZSTD(3)),
    resource_attributes String CODEC(ZSTD(3)),
    scope_attributes String CODEC(ZSTD(3)),
    ingestion_version UInt64
) ENGINE = ReplacingMergeTree(ingestion_version)
PARTITION BY toDate(timestamp)
ORDER BY (service_name, metric_name, toDate(timestamp), timestamp, point_uid)
TTL toDate(timestamp) + INTERVAL 30 DAY
SETTINGS index_granularity = 8192, ttl_only_drop_parts = 1;

-- Rebuildable read index for trace_id -> participating services and time range.
-- It is derived locally from observations and is not a new source-of-truth signal.
CREATE TABLE IF NOT EXISTS default.hmp_agentobs_trace_locator (
    trace_id FixedString(32),
    service_name LowCardinality(String),
    event_date Date,
    min_start_state AggregateFunction(min, DateTime64(6, 'UTC')),
    max_end_state AggregateFunction(max, DateTime64(6, 'UTC'))
) ENGINE = AggregatingMergeTree()
PARTITION BY event_date
ORDER BY (trace_id, service_name, event_date)
TTL event_date + INTERVAL 30 DAY
SETTINGS index_granularity = 8192, ttl_only_drop_parts = 1;

CREATE MATERIALIZED VIEW IF NOT EXISTS default.hmp_agentobs_trace_locator_mv
TO default.hmp_agentobs_trace_locator
AS
SELECT
    trace_id,
    service_name,
    toDate(start_time) AS event_date,
    minState(start_time) AS min_start_state,
    maxState(end_time) AS max_end_state
FROM default.hmp_agentobs_observations
GROUP BY trace_id, service_name, event_date;

-- Distributed tables. Trace-bearing tables deliberately use the exact same
-- sharding expression. Do not prepend service_name to these expressions.
CREATE VIEW IF NOT EXISTS default.hmp_agentobs_traces_all AS
SELECT * FROM default.hmp_agentobs_traces FINAL;

CREATE VIEW IF NOT EXISTS default.hmp_agentobs_observations_all AS
SELECT * FROM default.hmp_agentobs_observations FINAL;

CREATE VIEW IF NOT EXISTS default.hmp_agentobs_scores_all AS
SELECT * FROM default.hmp_agentobs_scores FINAL;

CREATE VIEW IF NOT EXISTS default.hmp_agentobs_span_events_all AS
SELECT * FROM default.hmp_agentobs_span_events FINAL;

CREATE VIEW IF NOT EXISTS default.hmp_agentobs_span_links_all AS
SELECT * FROM default.hmp_agentobs_span_links FINAL;

CREATE VIEW IF NOT EXISTS default.hmp_agentobs_logs_all AS
SELECT * FROM default.hmp_agentobs_logs FINAL;

CREATE VIEW IF NOT EXISTS default.hmp_agentobs_metric_points_all AS
SELECT * FROM default.hmp_agentobs_metric_points FINAL;

CREATE VIEW IF NOT EXISTS default.hmp_agentobs_trace_locator_all AS
SELECT * FROM default.hmp_agentobs_trace_locator;

-- Trace detail lookup first resolves the participating services and exact time
-- range from the locator, then queries observations with service/time predicates.
-- Example:
--
-- SELECT
--     service_name,
--     minMerge(min_start_state) AS min_start_time,
--     maxMerge(max_end_state) AS max_end_time
-- FROM default.hmp_agentobs_trace_locator_all
-- WHERE trace_id = '00000000000000000000000000000000'
-- GROUP BY service_name;
