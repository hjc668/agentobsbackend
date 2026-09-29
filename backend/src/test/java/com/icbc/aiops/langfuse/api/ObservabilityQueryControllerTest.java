package com.icbc.aiops.langfuse.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("mock")
class ObservabilityQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsTracePageAndSupportsSearch() throws Exception {
        mockMvc.perform(get("/api/v1/projects/demo-project/traces")
                        .param("search", "risk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value("trace-agent-002"));
    }

    @Test
    void filtersAndSortsTraces() throws Exception {
        mockMvc.perform(get("/api/v1/projects/demo-project/traces")
                        .param("status", "SUCCESS")
                        .param("environment", "production")
                        .param("sortBy", "COST")
                        .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value("trace-rag-001"));
    }

    @Test
    void filtersTracesByIdentityTagAndTimeRange() throws Exception {
        mockMvc.perform(get("/api/v1/projects/demo-project/traces")
                        .param("userId", "user-1842")
                        .param("sessionId", "session-alpha")
                        .param("tag", "rag")
                        .param("fromTimestamp", "2026-08-28T08:40:00Z")
                        .param("toTimestamp", "2026-08-28T08:42:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value("trace-rag-001"));
    }

    @Test
    void returnsTraceDetailAndNestedObservations() throws Exception {
        mockMvc.perform(get("/api/v1/projects/demo-project/traces/trace-rag-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("customer-support-rag"))
                .andExpect(jsonPath("$.totalTokens").value(1684));

        mockMvc.perform(get("/api/v1/projects/demo-project/traces/trace-rag-001/observations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[2].type").value("GENERATION"));

        mockMvc.perform(get("/api/v1/projects/demo-project/traces/trace-rag-001/view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace.name").value("customer-support-rag"))
                .andExpect(jsonPath("$.observations", hasSize(4)))
                .andExpect(jsonPath("$.observations[2].type").value("GENERATION"));

        mockMvc.perform(get("/api/v1/projects/demo-project/traces/trace-rag-001/scores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].observationId").value("obs-rag-gen"))
                .andExpect(jsonPath("$[1].dataType").value("BOOLEAN"));

        mockMvc.perform(get("/api/v1/projects/demo-project/traces/trace-rag-001/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].objectType").value("OBSERVATION"));
    }

    @Test
    void returnsNotFoundContract() throws Exception {
        mockMvc.perform(get("/api/v1/projects/demo-project/traces/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void returnsDashboardTrendAndSessionReplay() throws Exception {
        mockMvc.perform(get("/api/v1/projects/demo-project/summary/timeseries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[6].traceCount").value(77));

        mockMvc.perform(get("/api/v1/projects/demo-project/sessions/session-alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.traceCount").value(2))
                .andExpect(jsonPath("$.traces", hasSize(2)))
                .andExpect(jsonPath("$.observations", hasSize(7)));
    }

    @Test
    void returnsUsersWithAggregatedMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/projects/demo-project/users")
                        .param("search", "1842"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value("user-1842"))
                .andExpect(jsonPath("$.items[0].traceCount").value(2))
                .andExpect(jsonPath("$.items[0].observationCount").value(7));

        mockMvc.perform(get("/api/v1/projects/demo-project/users")
                        .param("search", "1842")
                        .param("environment", "staging"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].traceCount").value(1));

        mockMvc.perform(get("/api/v1/projects/demo-project/users/user-1842"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.traceCount").value(2))
                .andExpect(jsonPath("$.traces", hasSize(2)))
                .andExpect(jsonPath("$.sessions", hasSize(1)));
    }

    @Test
    void returnsObservationFacetsAndPulse() throws Exception {
        mockMvc.perform(get("/api/v1/projects/demo-project/observations/facets")
                        .param("field", "TYPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].value").exists())
                .andExpect(jsonPath("$[0].count").isNumber());

        mockMvc.perform(get("/api/v1/projects/demo-project/observations/pulse")
                        .param("bucket", "DAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timestamp").exists())
                .andExpect(jsonPath("$[0].count").isNumber());
    }

    @Test
    void mockObservationsSupportTracingFilterDsl() throws Exception {
        mockMvc.perform(get("/api/v1/projects/demo-project/observations")
                        .param("search", "type:GENERATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items", hasSize(3)));

        mockMvc.perform(get("/api/v1/projects/demo-project/observations")
                        .param("search", "environment:development"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5));

        mockMvc.perform(get("/api/v1/projects/demo-project/observations")
                        .param("search", "root:true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4));

        mockMvc.perform(get("/api/v1/projects/demo-project/observations/pulse")
                        .param("bucket", "DAY")
                        .param("search", "type:AGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].count").value(1));

        mockMvc.perform(get("/api/v1/projects/demo-project/observations/facets")
                        .param("field", "TYPE")
                        .param("search", "type:AGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    void rejectsInvalidPaginationWithStableErrorContract() throws Exception {
        mockMvc.perform(get("/api/v1/projects/demo-project/observations")
                        .param("size", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
    }
}
