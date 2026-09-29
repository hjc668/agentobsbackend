package com.icbc.aiops.langfuse.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.aiops.langfuse.api.PageResponse;
import com.icbc.aiops.langfuse.api.PromptCreateRequest;
import com.icbc.aiops.langfuse.domain.PromptVersion;
import com.icbc.aiops.langfuse.postgres.mapper.PromptMapper;
import com.icbc.aiops.langfuse.postgres.mapper.PromptRows.PromptRow;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mybatis")
public class PostgresPromptCrudService implements PromptCrudService {

    private static final String LATEST_LABEL = "latest";
    private final PromptMapper mapper;
    private final ObjectMapper objectMapper;

    public PostgresPromptCrudService(PromptMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResponse<PromptVersion> findPrompts(String projectId, String search, int page, int size) {
        long offset = (long) page * size;
        List<PromptVersion> prompts = mapper.selectLatestPrompts(projectId, normalized(search), size, offset)
                .stream().map(this::toPrompt).collect(java.util.stream.Collectors.toList());
        return PageResponse.of(prompts, page, size, mapper.countPromptNames(projectId, normalized(search)));
    }

    @Override
    public PromptVersion getPromptVersion(String projectId, String id) {
        return toPrompt(required(mapper.selectById(projectId, id), "Prompt version not found: " + id));
    }

    @Override
    public List<PromptVersion> findVersions(String projectId, String name) {
        return mapper.selectVersionsByName(projectId, name).stream().map(this::toPrompt).collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional(transactionManager = "polarDbxTransactionManager")
    public PromptVersion createVersion(String projectId, PromptCreateRequest request, String createdBy) {
        String name = request.name().trim();
        String type = request.type() == null ? "text" : request.type();
        mapper.ensurePromptLock(projectId, name);
        mapper.lockPromptName(projectId, name);
        PromptRow previous = mapper.selectLatestByName(projectId, name);
        if (previous != null && !previous.type().equals(type)) {
            throw new ConflictException("Previous versions use a different prompt type.");
        }

        List<String> requestedLabels = distinct(request.labels());
        List<String> finalLabels = distinctWith(requestedLabels, LATEST_LABEL);
        assertLabelsAreMutable(projectId, finalLabels);
        List<String> tags = request.tags() == null && previous != null
                ? parseList(previous.tagsJson()) : distinct(request.tags());
        String id = UUID.randomUUID().toString();
        int version = mapper.selectNextVersion(projectId, name);

        finalLabels.forEach(label -> mapper.removeLabelFromOtherVersions(projectId, name, id, label));
        if (previous != null) {
            mapper.updateTagsForName(projectId, name, json(tags));
        }
        mapper.insert(id, projectId, normalizedActor(createdBy), json(request.prompt()), name, version, type,
                json(request.config() == null ? com.icbc.aiops.langfuse.util.Java8Collections.mapOf() : request.config()), json(tags), json(finalLabels),
                normalized(request.commitMessage()));
        return getPromptVersion(projectId, id);
    }

    @Override
    @Transactional(transactionManager = "polarDbxTransactionManager")
    public PromptVersion setLabels(String projectId, String id, List<String> labels) {
        PromptRow prompt = required(mapper.selectById(projectId, id), "Prompt version not found: " + id);
        List<String> oldLabels = parseList(prompt.labelsJson());
        List<String> newLabels = distinct(labels);
        Set<String> changed = new LinkedHashSet<>(oldLabels);
        newLabels.forEach(label -> {
            if (!changed.add(label)) changed.remove(label);
        });
        assertLabelsAreMutable(projectId, new ArrayList<>(changed));

        List<String> removed = oldLabels.stream().filter(label -> !newLabels.contains(label)).collect(java.util.stream.Collectors.toList());
        if (!removed.isEmpty() && mapper.countLabelDependents(projectId, prompt.name(), json(removed)) > 0) {
            throw new ConflictException("Other prompts depend on a label you are trying to remove.");
        }
        newLabels.forEach(label -> mapper.removeLabelFromOtherVersions(projectId, prompt.name(), id, label));
        mapper.updateLabels(projectId, id, json(newLabels));
        return getPromptVersion(projectId, id);
    }

    @Override
    @Transactional(transactionManager = "polarDbxTransactionManager")
    public List<PromptVersion> updateTags(String projectId, String id, List<String> tags) {
        PromptRow prompt = required(mapper.selectById(projectId, id), "Prompt version not found: " + id);
        mapper.updateTagsForName(projectId, prompt.name(), json(distinct(tags)));
        return findVersions(projectId, prompt.name());
    }

    @Override
    @Transactional(transactionManager = "polarDbxTransactionManager")
    public void deleteVersion(String projectId, String id) {
        PromptRow prompt = required(mapper.selectById(projectId, id), "Prompt version not found: " + id);
        List<String> labels = parseList(prompt.labelsJson());
        assertLabelsAreMutable(projectId, labels);
        if (mapper.countDependentsForVersion(projectId, prompt.name(), prompt.version(), json(labels)) > 0) {
            throw new ConflictException("Other prompts depend on the prompt version you are trying to delete.");
        }
        mapper.deleteVersion(projectId, id);
        if (labels.contains(LATEST_LABEL)) {
            PromptRow nextLatest = mapper.selectLatestByName(projectId, prompt.name());
            if (nextLatest != null) mapper.addLabel(projectId, nextLatest.id(), LATEST_LABEL);
        }
    }

    @Override
    @Transactional(transactionManager = "polarDbxTransactionManager")
    public void deletePrompt(String projectId, String name) {
        List<PromptRow> versions = mapper.selectVersionsByName(projectId, name);
        if (versions.isEmpty()) throw new ResourceNotFoundException("Prompt not found: " + name);
        List<String> labels = versions.stream().flatMap(row -> parseList(row.labelsJson()).stream()).distinct().collect(java.util.stream.Collectors.toList());
        assertLabelsAreMutable(projectId, labels);
        if (mapper.countDependentsForName(projectId, name) > 0) {
            throw new ConflictException("Other prompts depend on the prompt you are trying to delete.");
        }
        mapper.deletePrompt(projectId, name);
    }

    private void assertLabelsAreMutable(String projectId, List<String> labels) {
        if (!labels.isEmpty() && mapper.countProtectedLabels(projectId, json(labels)) > 0) {
            throw new ForbiddenOperationException("A protected prompt label requires project administrator access.");
        }
    }

    private PromptVersion toPrompt(PromptRow row) {
        return new PromptVersion(row.id(), row.projectId(), row.name(), row.version(), row.type(),
                parseObject(row.promptJson()), parseMap(row.configJson()), parseList(row.labelsJson()),
                parseList(row.tagsJson()), row.commitMessage(), row.createdBy(), toInstant(row.createdAt()),
                toInstant(row.updatedAt()));
    }

    private PromptRow required(PromptRow row, String message) {
        if (row == null) throw new ResourceNotFoundException(message);
        return row;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Value is not valid JSON", exception);
        }
    }

    private Object parseObject(String value) {
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception exception) {
            return value;
        }
    }

    private Map<String, Object> parseMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (Exception exception) {
            return com.icbc.aiops.langfuse.util.Java8Collections.mapOf();
        }
    }

    private List<String> parseList(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            return com.icbc.aiops.langfuse.util.Java8Collections.listOf();
        }
    }

    private static List<String> distinct(List<String> values) {
        if (values == null) return com.icbc.aiops.langfuse.util.Java8Collections.listOf();
        return values.stream().filter(value -> value != null && !value.trim().isEmpty())
                .map(String::trim).distinct().collect(java.util.stream.Collectors.toList());
    }

    private static List<String> distinctWith(List<String> values, String extra) {
        LinkedHashSet<String> result = new LinkedHashSet<>(values);
        result.add(extra);
        return com.icbc.aiops.langfuse.util.Java8Collections.listCopyOf(result);
    }

    private static String normalized(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String normalizedActor(String value) {
        return value == null || value.trim().isEmpty() ? "migration-service" : value.trim();
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
