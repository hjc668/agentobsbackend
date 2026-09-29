# SDK → Collector/Sinker → ClickHouse 字段映射核对单

状态：**设计讨论与待验收清单，不代表本仓库已经实现采集转换**。最后整理：2026-09-22。接手前先读 [`../PROJECT_PROGRESS.md`](../PROJECT_PROGRESS.md)，以实际 SDK、Collector/Sinker 代码及目标库 DDL 为准。工作区同级 `langfuse/outputs/` 中的 69 字段 Excel 是最小查询需求，不是 SDK 覆盖测试报告。

| 编号 | 需要核对的口径 | 当前建议或已知约束 | 完成判据 |
| --- | --- | --- | --- |
| Q1 TTFT | SDK `gen_ai.response.time_to_first_chunk` 为秒；CK `time_to_first_chunk_ms` 为毫秒 | 建议在 OTLP→CK 转换层乘 1000，写入 `Nullable(Float64)`，保留小数；不要为迎合表名改 SDK 的标准属性单位 | 找到转换实现并以非整数秒值做端到端断言；前端显示与 CK 值一致 |
| Q2 Trace input/output | Trace 摘要是否从根 observation 派生 | 当前表设计将 trace 定义为**显式业务根 Span 结束时的快照**，不是任意子 Span 聚合；需核对 SDK 的根标记及内容属性 | 根与子 Span 内容不同的样例中，trace 摘要取值符合约定 |
| Q3 environment/version | Resource、`langfuse.environment`、`langfuse.release` 的来源及优先级 | 不应把 `langfuse.release` 自动当作表的 `version`；优先级尚需平台与 SDK 确认 | 有冲突属性样例，明确各列的来源和覆盖顺序 |
| Q4 metadata | 带前缀的 `langfuse.*.metadata.*` 属性是否聚合 JSON | 必须先明确 trace/observation 各自的前缀、类型与脱敏规则；本仓库不能证明网关已聚合 | 多键、嵌套值和敏感值样例写入后格式稳定 |
| Q5 trace_name | 未显式上报时的回退顺序 | 建议以显式 trace 名优先；根 Span 名等回退规则待确认，不应从任意子 Span 名取值 | 显式名、缺失名和多子 Span 三种样例可重复验证 |
| Q6 status_code | 数值与语义映射 | CK 列为 `Int8`；调试迁移脚本写入 1/2，不能由此推断生产只支持 SUCCESS/ERROR 两态；需确认 UNSET/OK/ERROR 全量映射及运行中状态 | SDK、转换层、CK、API 对相同样例给出一致状态 |
| Q7 tags | 来源、去重与基数上限 | 根 Trace 与子 observation 的 tags 不应无条件混合；上限及截断行为待平台确认 | 负载较大和重复 tag 的样例可验证取值、顺序和上限 |
| Q8 Score | 12 个 Score 域字段由谁写入 | 当前架构将 Score 作为独立 REST 业务操作，**不应默认从 OTLP Span 派生**；写入者及留存策略需另定 | 写入、查询及权限路径明确，且不会误计为 observation |

相关表定义：[`../backend/sql/clickhouse/agentobs-clickhouse-schema-final.sql`](../backend/sql/clickhouse/agentobs-clickhouse-schema-final.sql)；架构说明：[`../backend/sql/clickhouse/schema3-design.md`](../backend/sql/clickhouse/schema3-design.md)。本地调试迁移脚本只用于构造样例，不能替代生产 Collector/Sinker 映射验收。
