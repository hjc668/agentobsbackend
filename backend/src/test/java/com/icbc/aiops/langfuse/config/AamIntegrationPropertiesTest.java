package com.icbc.aiops.langfuse.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AamIntegrationPropertiesTest {

    @Test
    void bindsServiceSettingsAsStringsWithoutDroppingLeadingZeroes() {
        Map<String, Object> values = new HashMap<String, Object>();
        values.put("aam.service.pub.key", "partner-key");
        values.put("aam.service.system.label", "agentobs");
        values.put("aam.service.web.num", "001642");

        AamIntegrationProperties properties = new Binder(
                new MapConfigurationPropertySource(values))
                .bind("aam", Bindable.of(AamIntegrationProperties.class)).get();

        assertEquals("partner-key", properties.getService().getPub().getKey());
        assertEquals("agentobs", properties.getService().getSystem().getLabel());
        assertEquals("001642", properties.getService().getWeb().getNum());
    }
}
