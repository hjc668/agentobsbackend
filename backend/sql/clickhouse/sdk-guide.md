# SDK 0.3.0 接入说明

SDK 只上送 OTLP，topic 和表路由由 Collector 管理。Python wheel、Java 普通 jar 与含依赖 jar 随离线包提供。Python 3.11、Java 8 字节码是本次验证目标；其他 Python 版本应在目标解释器解析 OTel 兼容依赖。Python wheel 不包含依赖；联网构建机用与目标一致的 Python/OS 下载 wheelhouse，再离线安装。

```bash
python -m pip download --dest wheelhouse ./agent_obs-0.3.0-py3-none-any.whl
# 目标机器
python -m pip install --no-index --find-links wheelhouse ./agent_obs-0.3.0-py3-none-any.whl
```

```bash
export OBS_BACKEND=agentobs
export OBS_SERVICE_NAME=my-agent
export OBS_OTLP_ENDPOINT=http://COLLECTOR:4318/v1/traces
export OBS_SCORE_ENDPOINT=http://QUERY:8080
export OBS_SAMPLE_RATE=1
export AGENTOBS_CONTENT_POLICY=NO_CONTENT
```

```python
from agent_obs import init, trace, generation_context, force_flush, shutdown
init()

@trace(name="answer")  # 一个业务入口；嵌套步骤用 agent/span/tool/generation
async def answer():
    with generation_context(name="chat", model="my-model", provider="internal",
                            usage={"input": 11, "output": 7}):
        return "answer"

# 进程优雅退出前调用；业务代码还需执行上面的异步函数
force_flush(10000)
shutdown()
```

生产采集不得用示例中的固定 usage，必须读取模型真实返回；未返回的 token/成本不要补零伪装已知。Python 装饰器/上下文管理器会记录异常；正常结束保留 OTel UNSET，不把原 ERROR 重写为 OK。内容默认 NO_CONTENT；开启 FULL_CONTENT 前配置 mask_hook、限制长度。结构化截断保证 JSON 可解析，长度预算主要针对内容文本，JSON 外壳和截断标记存在额外开销。

Java 将 `agent-obs-java-0.3.0-all.jar` 放入 classpath，或通过 Maven 安装普通 jar 并提供依赖。API 见随包源码和原 SDK README。设置相同 OBS_* 环境变量，调用 AgentObs.init；try-with-resources 只能保证 close/end，业务异常需要显式 recordException/ERROR。独立 SDK 使用自己的 provider 和 W3C propagators；与 javaagent 集成时由应用明确统一 provider/context，不重复初始化全局 SDK。

## 原生指标采集

本项目 agent_obs 封装主要负责 Traces；增加 Collector Metrics pipeline **不会自动让 SDK 产生指标**。在应用中配置标准 MeterProvider 和 OTLP MetricExporter：

```python
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.exporter.otlp.proto.http.metric_exporter import OTLPMetricExporter

reader = PeriodicExportingMetricReader(OTLPMetricExporter(
    endpoint="http://COLLECTOR:4318/v1/metrics"), export_interval_millis=10000)
provider = MeterProvider(resource=Resource.create({"service.name": "my-agent"}),
                         metric_readers=[reader])
meter = provider.get_meter("my-agent.metrics", "1.0")
requests = meter.create_counter("agent.requests", unit="{request}")
latency = meter.create_histogram("agent.request.duration", unit="s")
requests.add(1, {"operation": "answer"})
latency.record(0.25, {"operation": "answer"})
provider.force_flush()
provider.shutdown()
```

不要把 trace_id、prompt、用户原文加入 Metric labels，防止高基数；trace 关联使用 exemplar。vLLM Prometheus 抓取示例见 collector 部署文档。Logs 使用标准 LoggerProvider/OTLP LogExporter，端点 `/v1/logs`，应用在日志发生时携带当前 trace/span ID 与稳定 `agentobs.event.id`；本 SDK 未自动接管 Python logging 或 Java 日志框架。

## 升级注意

- 业务根显式标记后，即使父级 HTTP Span 存在也有业务 trace 摘要；同一 trace 内不可重复使用多个业务入口装饰器。
- score endpoint 与 OTLP endpoint 分离；HTTP 失败不再当成功。score 不是 OTLP Span，也不写 METRICS。
- 先部署新表、sinker、Collector，再滚动升级 SDK。SDK 接口仍是 OTLP；不要同时让同一 SDK 直连旧 ingest 和 Collector，否则会产生双写。
- 历史 `HMP_AGENTOBS_METRICS` 的 Span 消息必须隔离。切换前归档/迁移旧 topic，或先用 `_V3` topic 配置过渡，确认旧消息不会进入新指标表后再使用正式名称。
