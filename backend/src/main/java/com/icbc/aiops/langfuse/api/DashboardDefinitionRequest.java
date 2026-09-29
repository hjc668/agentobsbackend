package com.icbc.aiops.langfuse.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotNull;
import java.util.Map;

@lombok.Value
public class DashboardDefinitionRequest {
    @NotNull Map<String, Object> definition;

    @JsonCreator
    public DashboardDefinitionRequest(@JsonProperty("definition") Map<String, Object> definition) {
        this.definition = definition;
    }

    public Map<String, Object> definition() { return definition; }

}
