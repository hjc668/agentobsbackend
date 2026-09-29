package com.icbc.aiops.langfuse.postgres.mapper;

import com.icbc.aiops.langfuse.postgres.mapper.PromptRows.PromptRow;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface PromptMapper {

    String COLUMNS = "id,\nproject_id AS projectId,\nname,\nversion,\ntype,\nCAST(prompt AS CHAR) AS promptJson,\nCAST(config AS CHAR) AS configJson,\nCAST(labels AS CHAR) AS labelsJson,\nCAST(tags AS CHAR) AS tagsJson,\ncommit_message AS commitMessage,\ncreated_by AS createdBy,\ncreated_at AS createdAt,\nupdated_at AS updatedAt\n";

    @Select("<script>\nSELECT " + COLUMNS + " FROM langfuse_prompts p\nWHERE p.project_id = #{projectId}\n  AND p.version = (SELECT MAX(p2.version) FROM langfuse_prompts p2 WHERE p2.project_id = p.project_id AND p2.name = p.name)\n<if test=\"search != null and search != ''\">\n  AND LOWER(p.name) LIKE CONCAT('%', LOWER(#{search}), '%')\n</if>\nORDER BY p.updated_at DESC\nLIMIT #{size} OFFSET #{offset}\n</script>")
    List<PromptRow> selectLatestPrompts(
            @Param("projectId") String projectId,
            @Param("search") String search,
            @Param("size") int size,
            @Param("offset") long offset);

    @Select("<script>\nSELECT count(DISTINCT name) FROM langfuse_prompts\nWHERE project_id = #{projectId}\n<if test=\"search != null and search != ''\">\n  AND LOWER(name) LIKE CONCAT('%', LOWER(#{search}), '%')\n</if>\n</script>")
    long countPromptNames(@Param("projectId") String projectId, @Param("search") String search);

    @Select("SELECT " + COLUMNS + " FROM langfuse_prompts WHERE project_id = #{projectId} AND id = #{id}")
    PromptRow selectById(@Param("projectId") String projectId, @Param("id") String id);

    @Select("SELECT " + COLUMNS + " FROM langfuse_prompts WHERE project_id = #{projectId} AND name = #{name} ORDER BY version DESC LIMIT 1")
    PromptRow selectLatestByName(@Param("projectId") String projectId, @Param("name") String name);

    @Select("SELECT " + COLUMNS + " FROM langfuse_prompts WHERE project_id = #{projectId} AND name = #{name} ORDER BY version DESC")
    List<PromptRow> selectVersionsByName(@Param("projectId") String projectId, @Param("name") String name);

    @Insert("INSERT IGNORE INTO langfuse_prompt_locks (project_id, prompt_name) VALUES (#{projectId}, #{name})")
    int ensurePromptLock(@Param("projectId") String projectId, @Param("name") String name);

    @Select("SELECT prompt_name FROM langfuse_prompt_locks WHERE project_id = #{projectId} AND prompt_name = #{name} FOR UPDATE")
    String lockPromptName(@Param("projectId") String projectId, @Param("name") String name);

    @Select("SELECT coalesce(max(version), 0) + 1 FROM langfuse_prompts WHERE project_id = #{projectId} AND name = #{name}")
    int selectNextVersion(@Param("projectId") String projectId, @Param("name") String name);

    @Select("SELECT count(*) FROM langfuse_prompt_protected_labels\nWHERE project_id = #{projectId}\n  AND JSON_CONTAINS(CAST(#{labelsJson} AS JSON), JSON_QUOTE(label))\n")
    int countProtectedLabels(@Param("projectId") String projectId, @Param("labelsJson") String labelsJson);

    @Update("UPDATE langfuse_prompts SET labels = JSON_REMOVE(labels, JSON_UNQUOTE(JSON_SEARCH(labels, 'one', #{label}))), updated_at = now(3)\n"
            + "WHERE project_id = #{projectId} AND name = #{name} AND id != #{exceptId}\n"
            + "  AND JSON_SEARCH(labels, 'one', #{label}) IS NOT NULL")
    int removeLabelFromOtherVersions(
            @Param("projectId") String projectId,
            @Param("name") String name,
            @Param("exceptId") String exceptId,
            @Param("label") String label);

    @Update("UPDATE langfuse_prompts SET labels = CAST(#{labelsJson} AS JSON), updated_at = now(3)\nWHERE project_id = #{projectId} AND id = #{id}\n")
    int updateLabels(@Param("projectId") String projectId, @Param("id") String id,
            @Param("labelsJson") String labelsJson);

    @Update("UPDATE langfuse_prompts SET tags = CAST(#{tagsJson} AS JSON), updated_at = now(3)\nWHERE project_id = #{projectId} AND name = #{name}\n")
    int updateTagsForName(@Param("projectId") String projectId, @Param("name") String name,
            @Param("tagsJson") String tagsJson);

    @Insert("INSERT INTO langfuse_prompts (\n  id, project_id, created_by, prompt, name, version, type, is_active,\n  config, tags, labels, commit_message, created_at, updated_at\n) VALUES (\n  #{id}, #{projectId}, #{createdBy}, CAST(#{promptJson} AS JSON), #{name}, #{version}, #{type}, 1,\n  CAST(#{configJson} AS JSON), CAST(#{tagsJson} AS JSON), CAST(#{labelsJson} AS JSON),\n  #{commitMessage}, now(3), now(3)\n)\n")
    int insert(
            @Param("id") String id,
            @Param("projectId") String projectId,
            @Param("createdBy") String createdBy,
            @Param("promptJson") String promptJson,
            @Param("name") String name,
            @Param("version") int version,
            @Param("type") String type,
            @Param("configJson") String configJson,
            @Param("tagsJson") String tagsJson,
            @Param("labelsJson") String labelsJson,
            @Param("commitMessage") String commitMessage);

    @Select("SELECT count(*) FROM langfuse_prompt_dependencies\nWHERE project_id = #{projectId} AND child_name = #{name}\n")
    int countDependentsForName(@Param("projectId") String projectId, @Param("name") String name);

    @Select("SELECT count(*) FROM langfuse_prompt_dependencies\nWHERE project_id = #{projectId} AND child_name = #{name}\n  AND (child_version = #{version}\n    OR JSON_CONTAINS(CAST(#{labelsJson} AS JSON), JSON_QUOTE(child_label)))\n")
    int countDependentsForVersion(
            @Param("projectId") String projectId,
            @Param("name") String name,
            @Param("version") int version,
            @Param("labelsJson") String labelsJson);

    @Select("SELECT count(*) FROM langfuse_prompt_dependencies\nWHERE project_id = #{projectId} AND child_name = #{name}\n  AND JSON_CONTAINS(CAST(#{labelsJson} AS JSON), JSON_QUOTE(child_label))\n")
    int countLabelDependents(
            @Param("projectId") String projectId,
            @Param("name") String name,
            @Param("labelsJson") String labelsJson);

    @Delete("DELETE FROM langfuse_prompts WHERE project_id = #{projectId} AND id = #{id}")
    int deleteVersion(@Param("projectId") String projectId, @Param("id") String id);

    @Update("UPDATE langfuse_prompts SET labels = JSON_ARRAY_APPEND(labels, '$', #{label}), updated_at = now(3) "
            + "WHERE project_id = #{projectId} AND id = #{id} "
            + "AND NOT JSON_CONTAINS(labels, JSON_QUOTE(#{label}))")
    int addLabel(@Param("projectId") String projectId, @Param("id") String id, @Param("label") String label);

    @Delete("DELETE FROM langfuse_prompts WHERE project_id = #{projectId} AND name = #{name}")
    int deletePrompt(@Param("projectId") String projectId, @Param("name") String name);
}
