package com.icbc.aiops.langfuse.service;

import com.icbc.aiops.langfuse.api.PageResponse;
import com.icbc.aiops.langfuse.api.PromptCreateRequest;
import com.icbc.aiops.langfuse.domain.PromptVersion;
import com.icbc.aiops.langfuse.util.ImmutableJsonSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("mock")
public class MockPromptCrudService implements PromptCrudService {

    private final CopyOnWriteArrayList<PromptVersion> prompts = new CopyOnWriteArrayList<>(com.icbc.aiops.langfuse.util.Java8Collections.listOf(
            new PromptVersion("prompt-1", "demo-project", "support/answer", 1, "text",
                    "Answer the customer question: {{question}}", com.icbc.aiops.langfuse.util.Java8Collections.mapOf("temperature", 0.2),
                    com.icbc.aiops.langfuse.util.Java8Collections.listOf("production", "latest"), com.icbc.aiops.langfuse.util.Java8Collections.listOf("support"), "Initial version",
                    "demo-user", Instant.parse("2026-08-28T08:00:00Z"), Instant.parse("2026-08-28T08:00:00Z"))));

    @Override
    public synchronized PageResponse<PromptVersion> findPrompts(
            String projectId, String search, int page, int size) {
        List<PromptVersion> latest = prompts.stream()
                .filter(prompt -> prompt.projectId().equals(projectId))
                .filter(prompt -> search == null || search.trim().isEmpty()
                        || prompt.name().toLowerCase().contains(search.toLowerCase()))
                .collect(java.util.stream.Collectors.groupingBy(PromptVersion::name))
                .values().stream()
                .map(versions -> versions.stream().max(Comparator.comparingInt(PromptVersion::version))
                        .orElseThrow(() -> new IllegalStateException("Prompt family has no versions")))
                .sorted(Comparator.comparing(PromptVersion::updatedAt).reversed())
                .collect(java.util.stream.Collectors.toList());
        int from = Math.min(page * size, latest.size());
        int to = Math.min(from + size, latest.size());
        return PageResponse.of(latest.subList(from, to), page, size, latest.size());
    }

    @Override
    public synchronized PromptVersion getPromptVersion(String projectId, String id) {
        return prompts.stream().filter(prompt -> prompt.projectId().equals(projectId) && prompt.id().equals(id))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Prompt version not found: " + id));
    }

    @Override
    public synchronized List<PromptVersion> findVersions(String projectId, String name) {
        return prompts.stream().filter(prompt -> prompt.projectId().equals(projectId) && prompt.name().equals(name))
                .sorted(Comparator.comparingInt(PromptVersion::version).reversed()).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public synchronized PromptVersion createVersion(
            String projectId, PromptCreateRequest request, String createdBy) {
        List<PromptVersion> versions = findVersions(projectId, request.name());
        String type = request.type() == null ? "text" : request.type();
        if (!versions.isEmpty() && !versions.get(0).type().equals(type)) {
            throw new ConflictException("Previous versions use a different prompt type.");
        }
        List<String> labels = new ArrayList<>(distinct(request.labels()));
        if (!labels.contains("latest")) labels.add("latest");
        removeLabels(projectId, request.name(), labels, null);
        List<String> tags = request.tags() == null && !versions.isEmpty() ? versions.get(0).tags() : distinct(request.tags());
        if (!versions.isEmpty()) replaceTags(projectId, request.name(), tags);
        Instant now = Instant.now();
        PromptVersion created = new PromptVersion(UUID.randomUUID().toString(), projectId, request.name(),
                versions.isEmpty() ? 1 : versions.get(0).version() + 1,
                type, ImmutableJsonSnapshot.value(request.prompt()),
                request.config() == null ? com.icbc.aiops.langfuse.util.Java8Collections.mapOf()
                        : ImmutableJsonSnapshot.map(request.config()),
                com.icbc.aiops.langfuse.util.Java8Collections.listCopyOf(labels), tags, request.commitMessage(),
                createdBy, now, now);
        prompts.add(created);
        return created;
    }

    @Override
    public synchronized PromptVersion setLabels(String projectId, String id, List<String> labels) {
        PromptVersion current = getPromptVersion(projectId, id);
        List<String> normalized = distinct(labels);
        removeLabels(projectId, current.name(), normalized, id);
        PromptVersion updated = copy(current, normalized, current.tags());
        replace(current, updated);
        return updated;
    }

    @Override
    public synchronized List<PromptVersion> updateTags(String projectId, String id, List<String> tags) {
        PromptVersion current = getPromptVersion(projectId, id);
        replaceTags(projectId, current.name(), distinct(tags));
        return findVersions(projectId, current.name());
    }

    @Override
    public synchronized void deleteVersion(String projectId, String id) {
        PromptVersion current = getPromptVersion(projectId, id);
        prompts.removeIf(prompt -> prompt.id().equals(current.id()));
        if (current.labels().contains("latest")) {
            List<PromptVersion> remaining = findVersions(projectId, current.name());
            if (!remaining.isEmpty()) {
                PromptVersion latest = remaining.get(0);
                List<String> labels = new ArrayList<>(latest.labels());
                if (!labels.contains("latest")) labels.add("latest");
                replace(latest, copy(latest, labels, latest.tags()));
            }
        }
    }

    @Override
    public synchronized void deletePrompt(String projectId, String name) {
        if (findVersions(projectId, name).isEmpty()) throw new ResourceNotFoundException("Prompt not found: " + name);
        prompts.removeIf(prompt -> prompt.projectId().equals(projectId) && prompt.name().equals(name));
    }

    private void removeLabels(String projectId, String name, List<String> labels, String exceptId) {
        List<PromptVersion> affected = prompts.stream().filter(prompt -> prompt.projectId().equals(projectId)
                && prompt.name().equals(name) && !prompt.id().equals(exceptId)).collect(java.util.stream.Collectors.toList());
        affected.forEach(prompt -> replace(prompt, copy(prompt,
                prompt.labels().stream().filter(label -> !labels.contains(label)).collect(java.util.stream.Collectors.toList()), prompt.tags())));
    }

    private void replaceTags(String projectId, String name, List<String> tags) {
        findVersions(projectId, name).forEach(prompt -> replace(prompt, copy(prompt, prompt.labels(), tags)));
    }

    private void replace(PromptVersion oldValue, PromptVersion newValue) {
        prompts.replaceAll(prompt -> prompt.id().equals(oldValue.id()) ? newValue : prompt);
    }

    private PromptVersion copy(PromptVersion source, List<String> labels, List<String> tags) {
        return new PromptVersion(source.id(), source.projectId(), source.name(), source.version(), source.type(),
                source.prompt(), source.config(), com.icbc.aiops.langfuse.util.Java8Collections.listCopyOf(labels), com.icbc.aiops.langfuse.util.Java8Collections.listCopyOf(tags), source.commitMessage(),
                source.createdBy(), source.createdAt(), Instant.now());
    }

    private static List<String> distinct(List<String> values) {
        if (values == null) return com.icbc.aiops.langfuse.util.Java8Collections.listOf();
        return com.icbc.aiops.langfuse.util.Java8Collections.listCopyOf(new LinkedHashSet<>(values.stream().filter(value -> value != null && !value.trim().isEmpty())
                .map(String::trim).collect(java.util.stream.Collectors.toList())));
    }
}
