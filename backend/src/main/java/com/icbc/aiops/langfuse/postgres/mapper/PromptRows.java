package com.icbc.aiops.langfuse.postgres.mapper;

import java.time.LocalDateTime;

public final class PromptRows {
    private PromptRows() {
    }

    @lombok.Value
    public static class PromptRow {
        String id;
        String projectId;
        String name;
        int version;
        String type;
        String promptJson;
        String configJson;
        String labelsJson;
        String tagsJson;
        String commitMessage;
        String createdBy;
        LocalDateTime createdAt;
        LocalDateTime updatedAt;

        public String id() { return id; }
        public String projectId() { return projectId; }
        public String name() { return name; }
        public int version() { return version; }
        public String type() { return type; }
        public String promptJson() { return promptJson; }
        public String configJson() { return configJson; }
        public String labelsJson() { return labelsJson; }
        public String tagsJson() { return tagsJson; }
        public String commitMessage() { return commitMessage; }
        public String createdBy() { return createdBy; }
        public LocalDateTime createdAt() { return createdAt; }
        public LocalDateTime updatedAt() { return updatedAt; }

    }
}
