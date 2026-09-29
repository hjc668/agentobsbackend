package com.icbc.aiops.langfuse.postgres.mapper;

import java.time.LocalDateTime;

public final class CommentRows {

    private CommentRows() {
    }

    @lombok.Value
    public static class CommentRow {
        String id;
        String objectType;
        String objectId;
        String content;
        String authorUserId;
        String dataField;
        LocalDateTime createdAt;
        LocalDateTime updatedAt;

        public String id() { return id; }
        public String objectType() { return objectType; }
        public String objectId() { return objectId; }
        public String content() { return content; }
        public String authorUserId() { return authorUserId; }
        public String dataField() { return dataField; }
        public LocalDateTime createdAt() { return createdAt; }
        public LocalDateTime updatedAt() { return updatedAt; }

    }
}
