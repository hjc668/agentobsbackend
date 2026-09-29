package com.icbc.aiops.langfuse.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.aiops.langfuse.config.WorkspaceProperties;
import com.icbc.aiops.langfuse.mapper.ObservabilityMapper;
import com.icbc.aiops.langfuse.mapper.TracingMapper;
import com.icbc.aiops.langfuse.mapper.TracingRows.TraceDetailRow;
import com.icbc.aiops.langfuse.postgres.mapper.CommentMapper;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Trace lookups now go through {@link TracingMapper}; comments stay in the PolarDB-X
 * workspace. The point of this test is that the comment query uses the configured
 * workspace id rather than any browser-supplied project.
 */
class WorkspaceCommentScopeTest {
    @Test
    void commentsUseConfiguredWorkspaceRatherThanHardCodedProject() {
        AtomicReference<String> commentProject = new AtomicReference<String>();
        ObservabilityMapper observability = (ObservabilityMapper) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { ObservabilityMapper.class }, (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
        TracingMapper tracing = (TracingMapper) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { TracingMapper.class }, (proxy, method, args) -> {
                    if ("selectTraceObservations".equals(method.getName())) return Collections.emptyList();
                    if ("selectTraceMetrics".equals(method.getName())) return Collections.emptyList();
                    // Empty locator: the service must then fall back to a plain trace_id read.
                    if ("selectTraceLocator".equals(method.getName())) return Collections.emptyList();
                    if ("selectTrace".equals(method.getName())) return new TraceDetailRow("trace-1", "trace", LocalDateTime.now(),
                            "", "", "default", "SUCCESS", 0L, 0L, BigDecimal.ZERO, 0, "[]", "{}", "{}", "{}", "", "");
                    throw new UnsupportedOperationException(method.getName());
                });
        CommentMapper comments = (CommentMapper) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { CommentMapper.class }, (proxy, method, args) -> {
                    commentProject.set((String) args[0]); return Collections.emptyList();
                });
        WorkspaceProperties workspace = new WorkspaceProperties();
        workspace.setProjectId("configured-workspace");
        MybatisObservabilityQueryService service = new MybatisObservabilityQueryService(
                observability, tracing, comments, new ObjectMapper(), workspace);

        service.findTraceComments("trace-1");

        assertEquals("configured-workspace", commentProject.get());
    }
}
