package com.icbc.aiops.langfuse.api;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

@lombok.Value
public class PromptCreateRequest {
    @NotBlank String name;
    @Pattern(regexp = "text|chat") String type;
    @NotNull Object prompt;
    Map<String, Object> config;
    List<String> labels;
    List<String> tags;
    String commitMessage;

    public String name() { return name; }
    public String type() { return type; }
    public Object prompt() { return prompt; }
    public Map<String, Object> config() { return config; }
    public List<String> labels() { return labels; }
    public List<String> tags() { return tags; }
    public String commitMessage() { return commitMessage; }

}
