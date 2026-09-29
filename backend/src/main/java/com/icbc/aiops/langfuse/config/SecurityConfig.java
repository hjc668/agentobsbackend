package com.icbc.aiops.langfuse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.aiops.langfuse.api.ApiError;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 共享该 Repository，使 {@link com.icbc.aiops.langfuse.security.AamSessionService}
     * 能通过过滤器链所使用的同一个 Repository 重新签发 token。
    */
    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${server.servlet.context-path:/}") String contextPath) {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath(cookiePath(contextPath));
        return csrf;
    }

    private static String cookiePath(String contextPath) {
        if (contextPath == null || contextPath.trim().isEmpty() || "/".equals(contextPath.trim())) {
            return "/";
        }
        String normalized = contextPath.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, ObjectMapper objectMapper,
            CookieCsrfTokenRepository csrf) throws Exception {
        http.cors().and()
                .csrf()
                    .csrfTokenRepository(csrf)
                    // AAM 回调来自统一认证服务，无法获知登录前签发的业务 CSRF token。
                    // 仅豁免此入口，其他所有写操作仍要求携带 token。
                    .ignoringAntMatchers("/aam/login/auth")
                    .and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).and()
                .authorizeRequests()
                    // /actuator/health/** 单独列出：antMatchers 只做精确匹配，
                    // 不含子路径，否则 /actuator/health/liveness 会落到认证后面返回 401，
                    // 负载均衡探针无法使用。
                    //
                    // /healthz 是给 F5 / SLB 用的纯文本探针。虽然末尾的 anyRequest().permitAll()
                    // 当前也会放行它，但这里显式声明，避免将来收紧兜底规则时探针被静默挡住。
                    .antMatchers("/healthz", "/actuator/health", "/actuator/health/**", "/actuator/info", "/api/v1/auth/csrf",
                            "/api/v1/auth/login", "/api/v1/auth/config",
                            "/api/aam/login", "/aam/login/auth", "/aamlogout").permitAll()
                    .antMatchers("/api/v1/auth/logout").hasRole("VIEW")
                    .antMatchers(HttpMethod.GET, "/api/**").hasRole("VIEW")
                    .antMatchers("/api/**").hasRole("ADMIN")
                    .anyRequest().permitAll()
                .and()
                .exceptionHandling()
                    .authenticationEntryPoint((request, response, error) -> write(response, objectMapper,
                            HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please sign in through AAM."))
                    .accessDeniedHandler((request, response, error) -> write(response, objectMapper,
                            HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission for this operation."));
        return http.build();
    }

    private static void write(javax.servlet.http.HttpServletResponse response, ObjectMapper mapper,
            HttpStatus status, String code, String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ApiError(code, message, Instant.now()));
    }
}
