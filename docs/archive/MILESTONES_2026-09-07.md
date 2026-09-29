# Langfuse Web Java 迁移 · 里程碑清单

最后更新：2026-09-07
数据来源：`PROJECT_PROGRESS.md`、`MIGRATION_MATRIX.md`、`design-qa.md`

图例：`[x]` 已完成　`[ ]` 未完成　`[-]` 延期/不在本轮范围

---

## 阶段一 · 架构与基础

- [x] 技术选型确定（Spring Boot 2.7.18 / Java 8 / MyBatis 2.3.2 全注解）
- [x] Java 8 字节码兼容验证（major version 52）及 36 项后端回归测试
- [x] 前端独立工程落地（React 19 + TypeScript + Vite）
- [x] 前端与后端解耦，可分别独立启动
- [x] ClickHouse 数据源接入（可观测事件）
- [x] ClickHouse JDBC 固定为 Java 8 兼容的 0.4.6
- [x] 正式 8080 后端以 JDK 8 / Spring Boot 2.7.18 / ClickHouse JDBC 0.4.6 重启并通过真实 Tracing 查询验收
- [x] PolarDB-X 数据源接入（事务型 CRUD，connector 2.2.12）
- [x] 双数据源 + 事务管理配置完成
- [x] 复用原 Langfuse Redis / Worker / MinIO 链路
- [x] 原 Web 端（:3000）保留用于 1:1 对比

## 阶段二 · 只读链路打通

- [x] Tracing 列表与详情
- [x] Observation 列表与详情
- [x] Sessions 列表与详情
- [x] Users 列表与详情
- [x] 趋势图、分页、排序、列显隐
- [x] 详情面板（预览 / Scores / Comments / Log View）
- [x] 真实数据一致性比对通过（597 条唯一 Trace）

## 阶段三 · Tracing 核心能力（本轮重点）

- [x] 真实调用图谱（有向父子拓扑）
- [x] 图谱聚合 / 展开双视角
- [x] 图谱缩放、适配画布、节点联动
- [x] 复杂筛选：文本 / 数值 / 时间 / 标签
- [x] 复杂筛选：Metadata 键 / Score / 空值判断
- [x] 筛选参数绑定与字段白名单（防注入）
- [x] 快速筛选预设对齐原版（Quality / Slow / Cost）
- [x] 筛选标签内联呈现，支持单删与清空
- [x] 筛选状态与 URL、趋势图、列表同步
- [x] AAM 会话访问控制（全库 Observability 只读）
- [x] 参数与校验错误统一 400
- [x] 连接池与线程参数设限
- [x] 并发安全验证（64 并发构造测试）

## 阶段四 · 质量门禁

- [x] 后端测试全部通过（37 个）
- [x] 前端生产构建通过（1687 个模块）
- [x] 真实链路性能基线采集（热态 p95 0.705 秒）
- [x] 权限与异常请求探测通过

## 阶段五 · 周边模块

- [x] Dashboard 增删改查与克隆
- [x] Dashboard Widget 增删改查与克隆
- [x] 内置官方仪表盘只读约束
- [x] Widget 指标查询执行（白名单指标）
- [x] Sessions / Users 当前页导出
- [x] Prompt 版本管理保留可用（已冻结）

## 阶段六 · 待补齐（Tracing 未完项）

- [ ] 删除语义对齐原版
- [ ] 批量删除
- [ ] 全量筛选感知导出
- [ ] Comment 可写操作
- [ ] Score 可写操作
- [x] Mock AAM + VIEW/ADMIN 授权
- [ ] ICBC 网关签名 / 远程 AAM 验票实现接入（当前 fail-closed）
- [ ] 逐屏视觉回归比对
- [x] 高频分页查询优化（移除全表 FINAL、详情按需加载大字段）
- [ ] 生产 latest-state 物化视图（支持多版本/删除语义）
- [ ] 目标容量压测达标

## 阶段七 · 后续规划

- [ ] Tracing 全量 1:1 验收
- [ ] 仪表盘布局编排与高级筛选
- [ ] Sessions 模块补齐
- [ ] Users 模块补齐
- [ ] Alerts 告警模块
- [ ] Playground
- [ ] 项目与组织设置
- [ ] 集成、媒体与异步导出
- [ ] 摄取与公共 API 兼容
- [-] Prompt Management（产品决策延期）
- [-] Evaluation 与 Scores 配置（延期）
- [-] Datasets / Experiments / 人工标注（延期）

---

## 关键风险

| 风险 | 说明 | 应对 |
| --- | --- | --- |
| 查询性能 | 高频分页已移除全表 `FINAL`；未来多版本/删除数据不能继续依赖原始行计数 | 上线前建立 latest-state 物化视图并按目标容量压测 |
| 容量未验证 | 本机单节点 ClickHouse，不能代表生产容量 | 上线前按目标数据量重新压测 |
| AAM 集成 | 网关签名和远程 AAM 验票尚未接入 | 非 Mock 模式 fail-closed；按 ICBC 协议实现 verifier 后启用 |
| 版本依赖 | Langfuse 内部库表非稳定公开 API | 原版本升级时重跑 Mapper 集成测试 |
# Milestone: AAM session authentication and global ClickHouse queries

- Spring Security supplied by Spring Boot 2.7.18; Java 8 compatible.
- VIEW may use read endpoints; ADMIN (server configured) may mutate workspace CRUD endpoints.
- Legacy `/api/v1/projects/{id}` controller URLs are compatibility aliases only; the path id and browser-supplied headers do not affect query scope.
