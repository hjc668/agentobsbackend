package com.icbc.aiops.langfuse.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 当 {@code app.auth.mode=aam} 但 AAM 配置不完整时阻止应用启动。
 *
 * <p>如果没有这层校验，应用会在后续阶段因 Hermes 内部异常（或看似无关的 Bean 缺失）而失败，
 * 运维人员难以快速定位。此处提前失败可以明确暴露部署配置错误，更重要的是确保
 * {@code aam} 模式绝不会在认证凭据不可用时静默运行。
 *
 * <p>这里实现为 {@link BeanFactoryPostProcessor} 而非普通初始化 Bean，从而在
 * <em>任何 Bean 实例化之前</em>执行。该顺序很重要，否则 Hermes 适配器自身的 Bean
 * 会先失败，并产生更难理解的错误信息。
 *
 * <p>仅在 {@code aam} 模式下生效，因此 mock/本地启动无需提供这些配置。
 * 此处有意直接读取 {@link Environment}，而不是将 {@code aam.*} 绑定到 Bean，
 * 避免干扰 Hermes 自身的配置绑定方式。
 *
 * <p><strong>绝不</strong>记录配置值，只记录缺失的配置键名称。
 */
@Component
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "aam")
public class AamConfigurationValidator implements BeanFactoryPostProcessor, EnvironmentAware {

    /** SSIC 验签和 UniformTeller 查询所需的完整配置集合。 */
    static final List<String> REQUIRED_AAM_PROPERTIES = Arrays.asList(
            "aam.enableSSIC",
            "aam.enableSpecialUrl",
            "aam.ssic.server.ip",
            "aam.ssic.server.version",
            "aam.ssic.server.publickey",
            "aam.ssic.client.site_url",
            "aam.ssic.client.key_name",
            "aam.ssic.client.pri_key_passwd",
            "aam.ssic.return_url_key",
            "aam.service.pub.key",
            "aam.service.system.label",
            "aam.service.web.num",
            "dsf.cocoa.router.addr");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        List<String> missing = new ArrayList<String>();
        for (String key : REQUIRED_AAM_PROPERTIES) {
            if (!StringUtils.hasText(environment.getProperty(key))) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("AAM mode requires property: "
                    + String.join(", ", missing)
                    + ". Fill in application.yml; values are intentionally not logged.");
        }
    }
}
