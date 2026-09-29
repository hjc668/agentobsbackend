package com.icbc.aiops.langfuse.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.icbc.aiops.langfuse.security.AamRole;
import com.icbc.aiops.langfuse.security.AamUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("mock")
class DashboardWidgetControllerTest {
    private static final String CONTEXT = "/icbc/hmp/agentobs";
    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void useAamAdminPrincipal() {
        AamUserPrincipal user = new AamUserPrincipal("test-admin", "Test Admin", "test-dept-id", "test", AamRole.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
    private static final String BODY = "{\"name\":\"Trace volume\",\"description\":\"Daily trace count\",\"view\":\"TRACES\",\n \"dimensions\":[{\"field\":\"timestamp\",\"granularity\":\"day\"}],\n \"metrics\":[{\"measure\":\"count\",\"aggregation\":\"count\"}],\"filters\":[],\n \"chartType\":\"LINE_TIME_SERIES\",\"chartConfig\":{}}\n";

    @Test
    void createsUpdatesClonesAndDeletesWidget() throws Exception {
        String response = mockMvc.perform(post(CONTEXT + "/api/v1/projects/demo-project/dashboard-widgets")
                        .contextPath(CONTEXT)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                        "http://localhost" + CONTEXT + "/api/v1/workspace/dashboard-widgets/")))
                .andExpect(jsonPath("$.view").value("TRACES"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.id");
        mockMvc.perform(get("/api/v1/projects/demo-project/dashboard-widgets/{id}/metrics", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[6].value").value(70));
        mockMvc.perform(put("/api/v1/projects/demo-project/dashboard-widgets/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("Trace volume", "Updated volume")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated volume"));
        mockMvc.perform(post("/api/v1/projects/demo-project/dashboard-widgets/{id}/clone", id))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Updated volume (Clone)"));
        mockMvc.perform(delete("/api/v1/projects/demo-project/dashboard-widgets/{id}", id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/projects/demo-project/dashboard-widgets/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDeferredScoreViews() throws Exception {
        mockMvc.perform(post("/api/v1/projects/demo-project/dashboard-widgets")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("TRACES", "SCORES_NUMERIC")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
