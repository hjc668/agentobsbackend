package com.icbc.aiops.langfuse.api;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

@lombok.Value
public class DashboardWidgetRequest {
    @NotBlank @Size(max = 200) String name;
    @Size(max = 2000) String description;
    @NotBlank String view;
    @NotNull List<Map<String, Object>> dimensions;
    @NotNull @Size(min = 1) List<Map<String, Object>> metrics;
    @NotNull List<Map<String, Object>> filters;
    @NotBlank String chartType;
    @NotNull Map<String, Object> chartConfig;

    public String name() { return name; }
    public String description() { return description; }
    public String view() { return view; }
    public List<Map<String, Object>> dimensions() { return dimensions; }
    public List<Map<String, Object>> metrics() { return metrics; }
    public List<Map<String, Object>> filters() { return filters; }
    public String chartType() { return chartType; }
    public Map<String, Object> chartConfig() { return chartConfig; }

}
