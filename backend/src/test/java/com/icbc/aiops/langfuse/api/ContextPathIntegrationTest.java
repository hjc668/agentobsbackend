package com.icbc.aiops.langfuse.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class ContextPathIntegrationTest {

    private static final String CONTEXT = "/icbc/hmp/agentobs";

    @Autowired private MockMvc mockMvc;
    @Autowired private ServerProperties serverProperties;

    @Test
    void publicEndpointsAndMockLoginWorkUnderTheContextPath() throws Exception {
        mockMvc.perform(get(CONTEXT + "/actuator/health").contextPath(CONTEXT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get(CONTEXT + "/api/v1/auth/config").contextPath(CONTEXT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("mock"))
                .andExpect(jsonPath("$.loginUrl").value(CONTEXT + "/api/v1/auth/login"))
                .andExpect(jsonPath("$.logoutUrl").doesNotExist());

        MockHttpSession session = new MockHttpSession();
        MvcResult csrf = mockMvc.perform(get(CONTEXT + "/api/v1/auth/csrf")
                        .contextPath(CONTEXT).session(session))
                .andExpect(status().isOk()).andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        assertEquals(CONTEXT, csrfCookie.getPath());
        assertEquals(CONTEXT, serverProperties.getServlet().getSession().getCookie().getPath());

        MvcResult login = mockMvc.perform(post(CONTEXT + "/api/v1/auth/login")
                        .contextPath(CONTEXT).session(session).cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aamId\":\"38971135\",\"ticket\":\"mock-ticket\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession authenticated = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get(CONTEXT + "/api/v1/observability/traces")
                        .contextPath(CONTEXT).session(authenticated))
                .andExpect(status().isOk());

        MvcResult logout = mockMvc.perform(post(CONTEXT + "/api/v1/auth/logout")
                        .contextPath(CONTEXT).session(authenticated).cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isNoContent()).andReturn();
        assertExpiredAtContext(logout, "XSRF-TOKEN");
        assertExpiredAtContext(logout, "JSESSIONID");
    }

    @Test
    void plainTextProbeAnswersAnonymouslyUnderTheContextPath() throws Exception {
        // 这里的响应体是字面量、刻意不引用 HealthController.PROBE_RESPONSE：
        // 该字符串是与 F5 / SLB 配置的契约，改动必须让测试失败以强制确认两边同步。
        //
        // 另：只断言「不创建 Session」而不断言 XSRF-TOKEN —— 负载均衡按秒级轮询，
        // 若每次轮询都建 Session 会造成会话无限增长。当前 CsrfFilter 会给所有公开端点
        // 下发 XSRF-TOKEN（/actuator/health 同样如此），属既有平台行为且不建 Session。
        mockMvc.perform(get(CONTEXT + "/healthz").contextPath(CONTEXT))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("@the@health@is@good@"))
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void plainTextProbeIgnoresTheAcceptHeader() throws Exception {
        // 负载均衡发送的 Accept 不可控（F5 默认 */*，也有配置发 application/json）。
        // 声明 produces 的端点遇到不匹配的 Accept 会返回 406，探针会被误判为故障，
        // 因此这里显式锁定行为：无论 Accept 是什么都返回 200 + text/plain + 契约响应体。
        mockMvc.perform(get(CONTEXT + "/healthz").contextPath(CONTEXT)
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().string("@the@health@is@good@"));
    }

    @Test
    void securityStillRejectsAnonymousBusinessRequestsUnderTheContextPath() throws Exception {
        mockMvc.perform(get(CONTEXT + "/api/v1/observability/traces").contextPath(CONTEXT))
                .andExpect(status().isUnauthorized());
    }

    private static void assertExpiredAtContext(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertNotNull(cookie, name + " deletion cookie");
        assertEquals(0, cookie.getMaxAge());
        assertEquals(CONTEXT, cookie.getPath());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.getSecure());
    }
}
