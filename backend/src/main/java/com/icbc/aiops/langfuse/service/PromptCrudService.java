package com.icbc.aiops.langfuse.service;

import com.icbc.aiops.langfuse.api.PageResponse;
import com.icbc.aiops.langfuse.api.PromptCreateRequest;
import com.icbc.aiops.langfuse.domain.PromptVersion;
import java.util.List;

public interface PromptCrudService {
    PageResponse<PromptVersion> findPrompts(String projectId, String search, int page, int size);
    PromptVersion getPromptVersion(String projectId, String id);
    List<PromptVersion> findVersions(String projectId, String name);
    PromptVersion createVersion(String projectId, PromptCreateRequest request, String createdBy);
    PromptVersion setLabels(String projectId, String id, List<String> labels);
    List<PromptVersion> updateTags(String projectId, String id, List<String> tags);
    void deleteVersion(String projectId, String id);
    void deletePrompt(String projectId, String name);
}
