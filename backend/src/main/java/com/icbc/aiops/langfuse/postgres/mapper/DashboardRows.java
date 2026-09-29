package com.icbc.aiops.langfuse.postgres.mapper;

import java.time.LocalDateTime;

public final class DashboardRows {

    private DashboardRows() {
    }

    @lombok.Value
    public static class DashboardRow {
        String id;
        String projectId;
        String name;
        String description;
        String definitionJson;
        String filtersJson;
        String createdBy;
        String updatedBy;
        LocalDateTime createdAt;
        LocalDateTime updatedAt;

        public String id() { return id; }
        public String projectId() { return projectId; }
        public String name() { return name; }
        public String description() { return description; }
        public String definitionJson() { return definitionJson; }
        public String filtersJson() { return filtersJson; }
        public String createdBy() { return createdBy; }
        public String updatedBy() { return updatedBy; }
        public LocalDateTime createdAt() { return createdAt; }
        public LocalDateTime updatedAt() { return updatedAt; }

    }
}
