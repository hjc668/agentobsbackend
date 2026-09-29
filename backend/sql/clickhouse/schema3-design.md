# Schema 3.0：采集、路由、存储和查询

本版数据库 `default`，本地表 `hmp_agentobs_<实体>`，Distributed 表 `hmp_agentobs_<实体>_all`。DDL 面向新表，不是旧 `_local/_dist` 表的原地迁移。默认保留 7 天；投产前按容量调整所有表 TTL 和 Kafka retention。

| 采集信息 | SDK 上送 | Collector 路由 / Kafka topic | 本地表（查询集群时加 `_all`） | 查询方式 |
|---|---|---|---|---|
| 显式业务入口名称、用户、会话、输入输出、状态、起止时间 | OTLP Span，`agentobs.trace.root=true` | TRACES / `HMP_AGENTOBS_TRACES` | `hmp_agentobs_traces` | 列表只读索引/摘要列；大文本按 ID 点查 |
| agent、LLM generation、tool、retrieval、普通子步骤；模型、token、TTFC | 所有 OTLP Spans，包含业务根 | OBSERVATIONS / `HMP_AGENTOBS_OBSERVATIONS` | `hmp_agentobs_observations` | trace locator 定位服务/时间后按 trace 查完整 waterfall；generation 直接统计 token |
| Span Event、exception | Span.events | `HMP_AGENTOBS_SPAN_EVENTS` | `hmp_agentobs_span_events` | trace/span 详情按需查 |
| Span Link、异步/批处理因果关系 | Span.links | `HMP_AGENTOBS_SPAN_LINKS` | `hmp_agentobs_span_links` | 按 span 或 linked_trace_id 查 |
| 请求计数、CPU、延迟 Histogram、vLLM 指标 | 原生 OTLP Metrics，或 Prometheus receiver | `HMP_AGENTOBS_METRICS` | `hmp_agentobs_metric_points` | 按 service、metric_name、series_id、时间聚合 |
| 应用日志、独立业务事件 | 原生 OTLP Logs | `HMP_AGENTOBS_LOGS` | `hmp_agentobs_logs` | 时间检索；trace/span 关联 |
| 人工评价、模型评分、纠正 | 独立 REST `/api/v1/scores` | query 服务同步写 CH | `hmp_agentobs_scores` | trace/observation/session 维度独立查询 |
| 缺失必要身份/时间等无效记录 | 上述 OTLP | `HMP_AGENTOBS_REJECTED` | 无自动入库任务 | 监控、人工排查；目前是诊断记录，**不是含原文的可重放 DLQ** |

早期逐字段审计目录没有随当前工程交付，原链接已移除。当前工程可直接核对本目录的 DDL 和项目根目录 `PROJECT_PROGRESS.md`；工作区另有 `tracing_clickhouse_minimum_fields.xlsx` 用于查询字段需求，但它不是 SDK/Collector 已完成映射的证据。本页描述设计契约，不代表采集转换代码已在本工程验收。

## SDK 与 Collector 的职责

SDK 负责真实时间、父子上下文、业务根、状态、语义属性和源端内容治理；不拼 Kafka JSON、不知道 ClickHouse 表。Collector 接受三类 OTLP 信号，展开 Span / Event / Link，生成业务根摘要并路由。一个业务根同时产生一条 trace 和一条 observation，这是不同查询用途的有意冗余。

业务 trace 是**结束后的业务根 Span 快照**，不是所有子 Span 的最终聚合。整条 trace 只能有一个显式产品根；即使外面已有 HTTP Span，也要显式标记业务入口。跨服务保留原 `service.name`，不拿根服务覆盖 Resource。`trace_locator` 从 observations 派生服务集合与时间范围，REST 按 `trace_id` 返回 Agent、Gateway、vLLM 的全部 Span，再由应用层按 `parent_span_id` 组装。

成本未知为 NULL；usage_present 区分字段缺失和已知零。`finish_reasons` 是数组；input/output 是 JSON 字符串，默认不采内容。Scope、schema URL、trace flags、dropped counts 与资源扩展字段保留。Metric 的 Int64 与 Float64 分列，保留 temporality、bucket、exemplar、quantile 和原始 point；不能直接 SUM cumulative 点值，也不能平均各实例 p99。对累计计数先按同一 series 处理重置和差分，再跨 series 汇总。Summary quantile 不可任意跨实例合并。

## 查询成本与重复数据

常用列表、Span 详情、模型 token 统计不需要 JOIN。Trace 详情分开读取 trace、observations、events、links、scores，由应用按 ID 组装。跨维度“trace 数＋所有 generation token”仍可能需要有界聚合后 JOIN，不能宣称全系统零 JOIN。

列表与聚合查询必须带 service 和时间；Trace 详情是例外，先由 locator 得到真实服务集合与精确时间窗。REST 默认列表窗口为最近 24 小时，结果上限 32 MiB、HTTP 30 秒、CH 25 秒。列表使用 `(start_time, trace_id/span_id)` 游标，旧时间字符串游标需重新开始翻页。trace 的模型/agent 过滤子查询也限定相同窗口：这是“窗口内 Span 匹配”，不等同于跨窗口整条 trace 匹配。

Kafka 重试和 sinker 重放允许重复。不可变结束 Span 使用结束时间作为版本；Metric/Log 用源时间与稳定内容/ID 生成键。ReplacingMergeTree 后台合并不提供即时唯一性；需要精确结果时，在**有界筛选**下使用 FINAL。禁止将原始重放流直接接 additive SUM 物化视图，否则合并前重复会永久放大计数。首期保留明细并有界去重查询；大规模仪表盘应增加按事件 ID 去重的聚合服务或定时窗口重算，再写分钟汇总表。本包不宣称达到 20 万条/秒。

日志请上送稳定 `agentobs.event.id`；缺省内容 hash 会合并完全相同的合法日志。结束 Span 的属性不得在重放时改写。Score REST 当前无跨请求幂等性保证，超时重试需业务侧核对。

## 可靠性边界

Collector 每类信号使用持久化 exporter queue、Kafka all-ISR ACK、无限失败重试；部署必须持久化 `/var/lib/otelcol`。未在 receiver 后使用内存 batch processor，避免其提前返回成功而尚未持久化。队列满/磁盘不可用会向发送端返回失败；SDK 自身内存批队列满或进程崩溃仍可能丢数据，需监控并设置合理 flush。

修正版 sinker 遇到不可恢复写入、解析错误、队列饱和或位点缺口时停止并保留未提交位点；supervisor 重启后重放。永久坏消息会造成持续阻塞，必须定位 topic/partition/offset 后修复；禁止直接跳位点掩盖问题。Distributed 写入强制 `insert_distributed_sync=1`。这仍是至少一次方案，不是跨 topic 事务或端到端 exactly-once。

## 标准和产品参考

- [OTel Metrics 数据模型](https://opentelemetry.io/docs/specs/otel/metrics/data-model/)：信号类型、temporality、Histogram、Exemplar。
- [Langfuse 数据模型](https://langfuse.com/docs/observability/data-model)：trace/observation/score/session 是产品模型，借鉴其详情与摘要分工，不能把 observation 当作 OTLP Metric。
- [OTel GenAI 语义约定](https://opentelemetry.io/docs/specs/semconv/gen-ai/)：以当前字段契约锁定映射，标准升级需版本化评审。
