package com.icbc.aiops.langfuse.api;

import static org.hamcrest.Matchers.hasSize;
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
class DashboardControllerTest {

    private static final String CONTEXT = "/icbc/hmp/agentobs";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void useAamAdminPrincipal() {
        AamUserPrincipal user = new AamUserPrincipal("test-admin", "Test Admin", "test-dept-id", "test", AamRole.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    void listsProjectAndLangfuseDashboards() throws Exception {
        mockMvc.perform(get("/api/v1/projects/demo-project/dashboards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void createsUpdatesClonesAndDeletesDashboard() throws Exception {
        String response = mockMvc.perform(post(CONTEXT + "/api/v1/projects/demo-project/dashboards")
                        .contextPath(CONTEXT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Migration dashboard\",\"description\":\"Java CRUD\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                        "http://localhost" + CONTEXT + "/api/v1/workspace/dashboards/")))
                .andExpect(jsonPath("$.owner").value("PROJECT"))
                .andExpect(jsonPath("$.definition.widgets", hasSize(0)))
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(response, "$.id");
        mockMvc.perform(put("/api/v1/projects/demo-project/dashboards/{id}/metadata", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated dashboard\",\"description\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated dashboard"));

        mockMvc.perform(put("/api/v1/projects/demo-project/dashboards/{id}/definition", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"definition\":{\"widgets\":[{\"id\":\"widget-1\"}]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definition.widgets", hasSize(1)));

        mockMvc.perform(post("/api/v1/projects/demo-project/dashboards/{id}/clone", id))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Updated dashboard (Clone)"));

        mockMvc.perform(delete("/api/v1/projects/demo-project/dashboards/{id}", id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/projects/demo-project/dashboards/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void protectsLangfuseOwnedDashboardFromMutation() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/demo-project/dashboards/langfuse-home"))
                .andExpect(status().isNotFound());
    }
}
