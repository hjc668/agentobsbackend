package com.icbc.aiops.langfuse.api;

import com.icbc.aiops.langfuse.domain.PromptVersion;
import com.icbc.aiops.langfuse.service.PromptCrudService;
import com.icbc.aiops.langfuse.config.WorkspaceProperties;
import com.icbc.aiops.langfuse.security.CurrentAamUser;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Validated
@RestController
@RequestMapping({"/api/v1/workspace/prompts", "/api/v1/projects/{ignoredProjectId}/prompts"})
public class PromptController {

    private final PromptCrudService service;
    private final WorkspaceProperties workspace;

    public PromptController(PromptCrudService service, WorkspaceProperties workspace) {
        this.service = service; this.workspace = workspace;
    }

    @GetMapping
    public PageResponse<PromptVersion> findPrompts(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return service.findPrompts(workspace.getProjectId(), search, page, size);
    }

    @GetMapping("/{id}")
    public PromptVersion getPrompt(@PathVariable String id) {
        return service.getPromptVersion(workspace.getProjectId(), id);
    }

    @GetMapping("/by-name/{name}/versions")
    public List<PromptVersion> findVersions(@PathVariable String name) {
        return service.findVersions(workspace.getProjectId(), name);
    }

    @PostMapping
    public ResponseEntity<PromptVersion> createVersion(
            @Valid @RequestBody PromptCreateRequest request) {
        PromptVersion created = service.createVersion(workspace.getProjectId(), request, CurrentAamUser.aamId());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/v1/workspace/prompts/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{id}/labels")
    public PromptVersion setLabels(
            @PathVariable String id,
            @Valid @RequestBody PromptLabelsRequest request) {
        return service.setLabels(workspace.getProjectId(), id, request.labels());
    }

    @PutMapping("/{id}/tags")
    public List<PromptVersion> updateTags(
            @PathVariable String id,
            @Valid @RequestBody PromptTagsRequest request) {
        return service.updateTags(workspace.getProjectId(), id, request.tags());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVersion(@PathVariable String id) {
        service.deleteVersion(workspace.getProjectId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(params = "name")
    public ResponseEntity<Void> deletePrompt(
            @RequestParam String name) {
        service.deletePrompt(workspace.getProjectId(), name);
        return ResponseEntity.noContent().build();
    }
}
