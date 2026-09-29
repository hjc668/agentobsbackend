package com.icbc.aiops.langfuse.config;

import com.icbc.aiops.langfuse.postgres.mapper.UserRoleMapper;
import com.icbc.aiops.langfuse.security.AamRoleResolver;
import com.icbc.aiops.langfuse.security.AamSessionService;
import com.icbc.aiops.langfuse.security.ConfiguredAamRoleResolver;
import com.icbc.aiops.langfuse.security.TableAamRoleResolver;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * 装配不依赖行内 Hermes 制品的认证组件。
 *
 * <p>{@code app.auth.mode} 是唯一的运行时开关，并且有意不使用 Spring profile：mock
 * 校验器配置了 {@code matchIfMissing}，如果再由 profile 控制，忘记启用 profile 的 AAM
 * 部署可能静默回退到 mock 登录。采用当前方式后，无法识别的模式不会创建
 * {@link AamRoleResolver}，应用会直接启动失败。
 */
@Configuration(proxyBeanMethods = false)
public class AamAuthConfiguration {

    /** 本地/开发环境的角色来自配置白名单。 */
    @Bean
    @ConditionalOnProperty(name = "app.auth.mode", havingValue = "mock", matchIfMissing = true)
    public AamRoleResolver configuredAamRoleResolver(
            @Value("${app.auth.admin-users:}") String adminUsers) {
        return new ConfiguredAamRoleResolver(adminUsers);
    }

    /**
     * 生产环境角色来自 {@code langfuse_user_roles}。该实现依赖 PolarDB-X Mapper，
     * 因此缺少数据库层的 {@code aam} 部署会启动失败，而不会把所有用户静默认证为 VIEW。
     */
    @Bean
    @ConditionalOnProperty(name = "app.auth.mode", havingValue = "aam")
    public AamRoleResolver tableAamRoleResolver(UserRoleMapper userRoleMapper) {
        return new TableAamRoleResolver(userRoleMapper);
    }

    /**
     * 两种模式共用该实现，使 Session/CSRF 交接逻辑只有一套需要测试。
     * {@code app.auth.session-timeout} 使用 Spring 的简化时间格式。
     */
    @Bean
    public AamSessionService aamSessionService(AamRoleResolver aamRoleResolver,
            CookieCsrfTokenRepository csrfTokenRepository,
            @Value("${app.auth.session-timeout:30m}") Duration sessionTimeout) {
        return new AamSessionService(aamRoleResolver, csrfTokenRepository,
                (int) sessionTimeout.getSeconds());
    }
}
