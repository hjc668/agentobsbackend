package com.icbc.aiops.langfuse.api;

import static org.hamcrest.Matchers.hasItems;
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
class PromptControllerTest {

    private static final String CONTEXT = "/icbc/hmp/agentobs";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void useAamAdminPrincipal() {
        AamUserPrincipal user = new AamUserPrincipal("test-admin", "Test Admin", "test-dept-id", "test", AamRole.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    void listsPromptFamilies() throws Exception {
        mockMvc.perform(get("/api/v1/projects/demo-project/prompts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value("support/answer"))
                .andExpect(jsonPath("$.items[0].labels", hasItems("production", "latest")));
    }

    @Test
    void createsUpdatesAndDeletesPromptVersion() throws Exception {
        String response = mockMvc.perform(post(CONTEXT + "/api/v1/projects/demo-project/prompts")
                        .contextPath(CONTEXT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\n  \"name\": \"test/crud-prompt\",\n  \"type\": \"text\",\n  \"prompt\": \"Hello {{name}}\",\n  \"labels\": [\"staging\"],\n  \"tags\": [\"test\"]\n}\n"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                        "http://localhost" + CONTEXT + "/api/v1/workspace/prompts/")))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.labels", hasItems("staging", "latest")))
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(response, "$.id");
        mockMvc.perform(put("/api/v1/projects/demo-project/prompts/{id}/labels", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labels\":[\"production\",\"latest\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels", hasItems("production", "latest")));

        mockMvc.perform(put("/api/v1/projects/demo-project/prompts/{id}/tags", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":[\"reviewed\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tags[0]").value("reviewed"));

        mockMvc.perform(delete("/api/v1/projects/demo-project/prompts/{id}", id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/projects/demo-project/prompts/{id}", id))
                .andExpect(status().isNotFound());
    }
}
