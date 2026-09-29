package com.icbc.aiops.langfuse.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotNull;
import java.util.List;

@lombok.Value
public class PromptTagsRequest {
    @NotNull List<@NotNull String> tags;

    @JsonCreator
    public PromptTagsRequest(@JsonProperty("tags") List<String> tags) {
        this.tags = tags;
    }

    public List<@NotNull String> tags() { return tags; }

}
