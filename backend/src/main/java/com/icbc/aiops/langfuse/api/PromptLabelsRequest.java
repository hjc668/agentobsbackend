package com.icbc.aiops.langfuse.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotNull;
import java.util.List;

@lombok.Value
public class PromptLabelsRequest {
    @NotNull List<@NotNull String> labels;

    @JsonCreator
    public PromptLabelsRequest(@JsonProperty("labels") List<String> labels) {
        this.labels = labels;
    }

    public List<@NotNull String> labels() { return labels; }

}
