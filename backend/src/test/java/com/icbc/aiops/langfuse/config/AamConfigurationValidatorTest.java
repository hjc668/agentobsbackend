package com.icbc.aiops.langfuse.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * An {@code aam} deployment must not start with incomplete credentials: otherwise the first
 * sign-in attempt would be the first sign that something is wrong.
 */
class AamConfigurationValidatorTest {

    @Test
    void failsStartupAndListsEveryMissingKey() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("aam.ssic.server.ip", "aam.example.internal");
        // Everything else left unset.

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> validator(environment).postProcessBeanFactory(null));

        String message = failure.getMessage();
        assertTrue(message.contains("aam.ssic.server.publickey"), message);
        assertTrue(message.contains("aam.ssic.client.site_url"), message);
        assertTrue(message.contains("aam.ssic.client.pri_key_passwd"), message);
        assertTrue(message.contains("aam.service.pub.key"), message);
        assertTrue(message.contains("aam.service.system.label"), message);
        assertTrue(message.contains("aam.service.web.num"), message);
        assertTrue(message.contains("dsf.cocoa.router.addr"), message);
        // The key that IS set must not be reported as missing.
        assertFalse(message.contains("aam.ssic.server.ip,"));
        // And the failure must explain why refusing to start is the right call.
        assertTrue(message.contains("AAM mode requires property"), message);
        // The failure must point at the file to edit, not at a removed env-var mechanism.
        assertTrue(message.contains("application.yml"), message);
        assertFalse(message.contains("AAM_*"), message);
    }

    @Test
    void treatsBlankValuesAsMissing() {
        MockEnvironment environment = completeEnvironment();
        environment.setProperty("aam.ssic.client.pri_key_passwd", "   ");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> validator(environment).postProcessBeanFactory(null));
        assertTrue(failure.getMessage().contains("aam.ssic.client.pri_key_passwd"));
    }

    @Test
    void neverLeaksThePasswordIntoTheFailureMessage() {
        MockEnvironment environment = completeEnvironment();
        environment.setProperty("aam.service.pub.key", "");

        String message = assertThrows(IllegalStateException.class,
                () -> validator(environment).postProcessBeanFactory(null)).getMessage();

        // Only key names appear; the secret value from the complete configuration must not.
        assertFalse(message.contains("super-secret-password"),
                "the validator must name missing keys, never echo values");
    }

    @Test
    void acceptsACompleteConfiguration() {
        assertDoesNotThrow(() -> validator(completeEnvironment()).postProcessBeanFactory(null));
    }

    /** Mirrors how Spring wires the post-processor: environment first, then the check. */
    private static AamConfigurationValidator validator(org.springframework.core.env.Environment environment) {
        AamConfigurationValidator validator = new AamConfigurationValidator();
        validator.setEnvironment(environment);
        return validator;
    }

    private static MockEnvironment completeEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        Map<String, String> values = new HashMap<String, String>();
        values.put("aam.enableSSIC", "true");
        values.put("aam.enableSpecialUrl", "false");
        values.put("aam.ssic.server.ip", "aam.example.internal");
        values.put("aam.ssic.server.version", "SM2");
        values.put("aam.ssic.server.publickey", "public-key-material");
        values.put("aam.ssic.client.site_url", "https://example.internal/aamlogin");
        values.put("aam.ssic.client.key_name", "langfuse-web");
        values.put("aam.ssic.client.pri_key_passwd", "super-secret-password");
        values.put("aam.ssic.return_url_key", "return-key");
        values.put("aam.service.pub.key", "partner-public-key");
        values.put("aam.service.system.label", "agentobs");
        values.put("aam.service.web.num", "001642");
        values.put("dsf.cocoa.router.addr", "http://cocoa.internal/icbc/cocoa");
        values.forEach(environment::setProperty);
        return environment;
    }
}
