-- Langfuse Web transactional schema for PolarDB-X (MySQL 8 compatible dialect).
-- Observability events/traces remain in ClickHouse and are intentionally absent here.
-- Run with an account that can create objects in the selected database.
--
-- Every table carries the `langfuse_` prefix because this database is shared with other
-- projects; the prefixed names make the owner of each table obvious. The prefix is a hard
-- contract: the MyBatis mappers in com.icbc.aiops.langfuse.postgres.mapper must be changed
-- in the same commit as this file. For a database created before the prefix existed, run
-- polardbx-rename-tables.sql instead of re-running this script.

CREATE DATABASE IF NOT EXISTS langfuse_web
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE langfuse_web;

CREATE TABLE IF NOT EXISTS langfuse_projects (
  id VARCHAR(191) NOT NULL,
  name VARCHAR(255) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_projects_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS langfuse_users (
  id VARCHAR(191) NOT NULL,
  aam_user_no VARCHAR(191) NULL,
  display_name VARCHAR(255) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_users_aam_user_no (aam_user_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- VIEW/ADMIN assignment for AAM users. This is the source of truth for authorization:
-- AAM only authenticates identity, and a role sent by the browser is always ignored.
-- Only 'VIEW' and 'ADMIN' are accepted; any other value is treated as VIEW (fail closed
-- to least privilege) and logged. Absent row => VIEW.
CREATE TABLE IF NOT EXISTS langfuse_user_roles (
  aam_user_no VARCHAR(191) NOT NULL,
  role_code VARCHAR(32) NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (aam_user_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS langfuse_comments (
  id VARCHAR(191) NOT NULL,
  project_id VARCHAR(191) NOT NULL,
  object_type VARCHAR(32) NOT NULL,
  object_id VARCHAR(191) NOT NULL,
  content LONGTEXT NOT NULL,
  author_user_id VARCHAR(191) NULL,
  data_field VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_comments_project_object (project_id, object_type, object_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS langfuse_dashboards (
  id VARCHAR(191) NOT NULL,
  project_id VARCHAR(191) NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT NULL,
  definition JSON NOT NULL,
  filters JSON NOT NULL,
  created_by VARCHAR(191) NULL,
  updated_by VARCHAR(191) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_dashboards_project_updated (project_id, updated_at),
  KEY idx_dashboards_project_name (project_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS langfuse_dashboard_widgets (
  id VARCHAR(191) NOT NULL,
  project_id VARCHAR(191) NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT NULL,
  view VARCHAR(32) NOT NULL,
  dimensions JSON NOT NULL,
  metrics JSON NOT NULL,
  filters JSON NOT NULL,
  chart_type VARCHAR(64) NOT NULL,
  chart_config JSON NOT NULL,
  min_version INT NOT NULL DEFAULT 1,
  created_by VARCHAR(191) NULL,
  updated_by VARCHAR(191) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_widgets_project_updated (project_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Prompt Management is currently outside the UI migration scope. These tables
-- are retained because the compatibility API is still present in the backend.
CREATE TABLE IF NOT EXISTS langfuse_prompts (
  id VARCHAR(191) NOT NULL,
  project_id VARCHAR(191) NOT NULL,
  created_by VARCHAR(191) NULL,
  prompt JSON NOT NULL,
  name VARCHAR(255) NOT NULL,
  version INT NOT NULL,
  type VARCHAR(32) NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  config JSON NOT NULL,
  tags JSON NOT NULL,
  labels JSON NOT NULL,
  commit_message TEXT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (project_id, name, version),
  KEY idx_prompts_id (project_id, id),
  KEY idx_prompts_project_updated (project_id, updated_at),
  KEY idx_prompts_project_name (project_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS langfuse_prompt_locks (
  project_id VARCHAR(191) NOT NULL,
  prompt_name VARCHAR(255) NOT NULL,
  PRIMARY KEY (project_id, prompt_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS langfuse_prompt_protected_labels (
  project_id VARCHAR(191) NOT NULL,
  label VARCHAR(191) NOT NULL,
  PRIMARY KEY (project_id, label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS langfuse_prompt_dependencies (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id VARCHAR(191) NOT NULL,
  parent_name VARCHAR(255) NOT NULL,
  child_name VARCHAR(255) NOT NULL,
  child_version INT NULL,
  child_label VARCHAR(191) NULL,
  PRIMARY KEY (id),
  KEY idx_prompt_dependencies_child (project_id, child_name, child_version),
  KEY idx_prompt_dependencies_label (project_id, child_name, child_label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Required initial workspace. Keep this id equal to APP_WORKSPACE_PROJECT_ID.
INSERT IGNORE INTO langfuse_projects (id, name) VALUES ('aiops-default', 'AIOps');

-- Optional service actor used by dashboard audit columns.
INSERT IGNORE INTO langfuse_users (id, aam_user_no, display_name)
VALUES ('migration-service', 'migration-service', 'Migration Service');
