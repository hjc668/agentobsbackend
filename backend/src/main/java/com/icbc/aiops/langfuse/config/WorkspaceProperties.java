package com.icbc.aiops.langfuse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** PolarDB-X 内部工作空间范围，不作为 HTTP 租户边界。 */
@ConfigurationProperties(prefix = "app.workspace")
public class WorkspaceProperties {
    private String projectId = "demo-project";
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
}
