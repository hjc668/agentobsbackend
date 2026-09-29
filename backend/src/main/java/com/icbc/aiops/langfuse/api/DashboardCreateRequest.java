package com.icbc.aiops.langfuse.api;

import javax.validation.constraints.NotBlank;

@lombok.Value
public class DashboardCreateRequest {
    @NotBlank String name;
    String description;

    public String name() { return name; }
    public String description() { return description; }

}
