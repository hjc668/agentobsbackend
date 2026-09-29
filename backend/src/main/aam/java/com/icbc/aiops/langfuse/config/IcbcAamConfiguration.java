package com.icbc.aiops.langfuse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.aiops.langfuse.security.AamUserInfoService;
import com.icbc.aiops.langfuse.security.AamTicketAuthenticator;
import com.icbc.aiops.langfuse.security.HermesAamTicketAuthenticator;
import com.icbc.aiops.langfuse.security.UniformTellerInfoClient;
import com.icbc.hermes.aam.AamConfig;
import com.icbc.hermes.aam.EnableAam;
import com.icbc.hermes.aam.wrapper.AamWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 加载 Hermes AAM 组件并装配票据认证器。
 *
 * <p>仅在 {@code aam} Maven profile 下编译。{@code @EnableAam} 会引入
 * {@link AamConfig}、SSIC {@code ServerSideAuthenticator}，以及用于发起统一认证跳转的
 * Portal 入口 {@code /api/aam/login}。
 *
 * <p>仅在 {@code app.auth.mode=aam} 时启用。Hermes 密钥配置缺失时，Spring Context
 * 会启动失败；这是有意设计的行为。系统不会回退到本地 mock 登录，因为静默降级会带来安全风险。
 */
@Configuration(proxyBeanMethods = false)
@EnableAam
@EnableConfigurationProperties({AamIntegrationProperties.class, DsfCocoaRouterProperties.class})
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "aam")
public class IcbcAamConfiguration {

    /**
     * SSIC 验签算法，必须与 {@code application.yml} 中的
     * {@code aam.ssic.server.version} 保持一致。
     */
    @Bean
    @ConditionalOnMissingBean(AamWrapper.class)
    public AamWrapper aamWrapper(AamIntegrationProperties properties) {
        return new AamWrapper(properties.getSsic().getServer().getVersion());
    }

    @Bean
    public AamUserInfoService aamUserInfoService(AamIntegrationProperties aam,
            DsfCocoaRouterProperties cocoa, ObjectMapper objectMapper) {
        return new UniformTellerInfoClient(aam, cocoa, objectMapper);
    }

    @Bean
    public AamTicketAuthenticator aamTicketAuthenticator(AamWrapper aamWrapper, AamConfig aamConfig,
            AamUserInfoService userInfoService) {
        return new HermesAamTicketAuthenticator(aamWrapper, aamConfig, userInfoService);
    }
}
