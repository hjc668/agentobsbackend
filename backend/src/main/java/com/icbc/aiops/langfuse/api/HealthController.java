package com.icbc.aiops.langfuse.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 纯文本存活探针，供 F5 / SLB 轮询。
 *
 * <p>本接口只反映<b>进程自身是否存活</b>，不访问任何数据源，因此数据库故障时
 * 不会导致负载均衡摘除节点。ClickHouse 与 PolarDB-X 为全部实例共享，
 * 把共享依赖放进负载均衡检查会让一次抖动同时摘除所有节点，
 * 把局部故障放大成全站不可用（见 PROJECT_PROGRESS.md §40）。
 *
 * <p>数据源健康请使用 {@code /actuator/health/readiness}，由监控系统采集。
 *
 * <p>文根为 {@code /icbc/hmp/agentobs}，完整地址为
 * {@code /icbc/hmp/agentobs/healthz}。
 *
 * <p>有意不写日志：负载均衡按秒级轮询，记录访问日志只会产生噪声。
 */
@RestController
public class HealthController {

    /**
     * 探针响应体，**与 F5 / SLB 健康检查配置的匹配串必须完全一致**。
     *
     * <p>修改此值等同于修改负载均衡配置：两边不同步会让负载均衡把正常节点判为故障。
     * {@code ContextPathIntegrationTest} 以字面量锁定该值，改动会先让测试失败。
     */
    public static final String PROBE_RESPONSE = "@the@health@is@good@";

    /**
     * 这里<b>不能</b>写成 {@code @GetMapping(produces = TEXT_PLAIN_VALUE)} 返回 {@code String}：
     * 声明 {@code produces} 后，只要负载均衡发送的 {@code Accept} 不匹配（例如
     * {@code application/json}），Spring 就会返回 <b>406</b>，探针会把正常节点判为故障。
     * 改为在响应上显式设置 Content-Type，Spring 会跳过 Accept 协商，
     * 无论 {@code Accept} 是什么都稳定返回 200 + {@code text/plain} + {@link #PROBE_RESPONSE}。
     */
    @GetMapping("/healthz")
    public ResponseEntity<String> healthz() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(PROBE_RESPONSE);
    }
}
