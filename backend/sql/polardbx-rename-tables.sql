-- One-shot migration: add the `langfuse_` prefix to an EXISTING langfuse_web database.
--
-- Why: the database is shared with other projects, so every Langfuse table now carries a
-- `langfuse_` prefix (see polardbx-schema.sql). A fresh database does NOT need this script --
-- just run polardbx-schema.sql. This script is only for databases created before the rename,
-- so that existing rows (the `aiops-default` workspace, `migration-service` actor and any
-- Dashboard/Comment/Prompt data) survive the rename instead of being lost by a DROP + recreate.
--
-- This script is NOT idempotent: RENAME TABLE fails if the source table is already gone.
-- Run it exactly once per database, against the schema that still uses the old names.
--
-- Index names are intentionally left alone. InnoDB index names are scoped to their table, so
-- `idx_projects_updated_at` on `langfuse_projects` is correct and purely cosmetic.

USE langfuse_web;

-- Pre-flight: this must list the 9 old names. If it returns anything prefixed with `langfuse_`,
-- the database was already renamed (or created from the new schema) -- stop and do not run the
-- RENAME statements below.
SELECT table_name AS old_name, table_rows AS approx_rows
FROM information_schema.tables
WHERE table_schema = 'langfuse_web'
  AND table_name IN ('projects', 'users', 'comments', 'dashboards', 'dashboard_widgets',
                     'prompts', 'prompt_locks', 'prompt_protected_labels', 'prompt_dependencies')
ORDER BY table_name;

-- The rename. No foreign keys exist in this schema, so RENAME TABLE is safe and atomic
-- for the whole statement; on failure nothing is renamed.
RENAME TABLE projects               TO langfuse_projects,
             users                  TO langfuse_users,
             comments               TO langfuse_comments,
             dashboards             TO langfuse_dashboards,
             dashboard_widgets      TO langfuse_dashboard_widgets,
             prompts                TO langfuse_prompts,
             prompt_locks           TO langfuse_prompt_locks,
             prompt_protected_labels TO langfuse_prompt_protected_labels,
             prompt_dependencies    TO langfuse_prompt_dependencies;

-- Post-flight: must list exactly the 9 prefixed names and 0 old names.
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'langfuse_web'
  AND (table_name LIKE 'langfuse\_%' OR table_name IN
       ('projects', 'users', 'comments', 'dashboards', 'dashboard_widgets',
        'prompts', 'prompt_locks', 'prompt_protected_labels', 'prompt_dependencies'))
ORDER BY table_name;

-- Post-flight: the seeded rows must still be here (the rename preserves RENAME-untouched data).
SELECT COUNT(*) AS projects_kept FROM langfuse_projects WHERE id = 'aiops-default';
SELECT COUNT(*) AS users_kept FROM langfuse_users WHERE id = 'migration-service';

-- If the instance rejects RENAME TABLE, fall back to recreate + copy, one table at a time:
--   CREATE TABLE langfuse_dashboards LIKE dashboards;
--   INSERT INTO langfuse_dashboards SELECT * FROM dashboards;
--   -- verify row counts match, then:
--   DROP TABLE dashboards;

-- Afterwards, add the tables introduced after this rename:
--   langfuse_user_roles  (VIEW/ADMIN assignment for AAM sign-in, added 2026-09-14)
-- Running polardbx-schema.sql again is safe for these: every statement is IF NOT EXISTS
-- and it will not touch the rows renamed above.
--
-- Note: PolarDB-X rejects an unconditional `DELETE FROM t` with PXC-4620 as a safety
-- guard. Always give a WHERE clause, or add /*TDDL:FORBID_EXECUTE_DML_ALL=FALSE*/.
