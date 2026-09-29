package com.icbc.aiops.langfuse.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.icbc.aiops.langfuse.service.ObservationQuery;
import com.icbc.aiops.langfuse.service.TraceQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Concurrent construction of the Tracing predicate: the DSL parser binds every value into
 * the caller's own parameter map, so 64 simultaneous requests must never see each other's
 * bindings. This is the regression guard for a shared-state bug that would silently mix
 * filters between users.
 */
class TracingQueryReliabilityTest {

    @Test
    void buildsComplexQueriesConcurrentlyWithoutSharedState() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int index = 0; index < 64; index++) {
                final String marker = "eu" + index;
                final String neighbourMarker = "eu" + ((index + 1) % 64);
                tasks.add(() -> {
                    Map<String, Object> parameters = new HashMap<>();
                    parameters.put("query", new ObservationQuery(
                            "metadata.region:=" + marker + " tags:(billing AND urgent) -has:endTime",
                            "", "", null, null, "", "", "", null, null,
                            ObservationQuery.SortBy.TIMESTAMP, TraceQuery.SortDirection.DESC, 0, 50));
                    String sql = TracingSqlProvider.observationCount(parameters);
                    // Own marker bound, no other request's marker leaked into this statement.
                    return !sql.contains("project_id")
                            && sql.contains("JSONExtractString(metadata, ")
                            && sql.contains(" AND has(tags,")
                            && sql.contains("default.hmp_agentobs_observations_all")
                            && parameters.values().contains(marker)
                            && !sql.contains(neighbourMarker);
                });
            }
            List<Future<Boolean>> results = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
            for (Future<Boolean> result : results) {
                assertFalse(result.isCancelled());
                assertTrue(result.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
