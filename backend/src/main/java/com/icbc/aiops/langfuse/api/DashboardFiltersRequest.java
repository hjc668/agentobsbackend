package com.icbc.aiops.langfuse.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@lombok.Value
public class DashboardFiltersRequest {
    @NotNull List<Map<String, Object>> filters;

    @JsonCreator
    public DashboardFiltersRequest(@JsonProperty("filters") List<Map<String, Object>> filters) {
        this.filters = filters;
    }

    public List<Map<String, Object>> filters() { return filters; }

}
