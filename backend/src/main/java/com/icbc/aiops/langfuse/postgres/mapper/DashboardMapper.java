package com.icbc.aiops.langfuse.postgres.mapper;

import com.icbc.aiops.langfuse.postgres.mapper.DashboardRows.DashboardRow;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface DashboardMapper {

    String COLUMNS = "id,\nproject_id AS projectId,\nname,\ndescription,\nCAST(definition AS CHAR) AS definitionJson,\nCAST(filters AS CHAR) AS filtersJson,\ncreated_by AS createdBy,\nupdated_by AS updatedBy,\ncreated_at AS createdAt,\nupdated_at AS updatedAt\n";

    @Select("SELECT " + COLUMNS + " FROM langfuse_dashboards "
            + "WHERE project_id = #{projectId} OR project_id IS NULL "
            + "ORDER BY updated_at DESC LIMIT #{size} OFFSET #{offset}")
    List<DashboardRow> selectDashboards(
            @Param("projectId") String projectId,
            @Param("size") int size,
            @Param("offset") long offset);

    @Select("SELECT count(*) FROM langfuse_dashboards WHERE project_id = #{projectId} OR project_id IS NULL")
    long countDashboards(@Param("projectId") String projectId);

    @Select("SELECT " + COLUMNS + " FROM langfuse_dashboards WHERE id = #{dashboardId} "
            + "AND (project_id = #{projectId} OR project_id IS NULL)")
    DashboardRow selectById(
            @Param("projectId") String projectId,
            @Param("dashboardId") String dashboardId);

    @Select("SELECT name FROM langfuse_dashboards WHERE project_id = #{projectId} AND name LIKE CONCAT(#{prefix}, '%')")
    List<String> selectNamesStartingWith(
            @Param("projectId") String projectId,
            @Param("prefix") String prefix);

    @Select("SELECT id FROM langfuse_users WHERE id = #{actor} LIMIT 1")
    String selectActorId(@Param("actor") String actor);

    @Insert("INSERT INTO langfuse_dashboards (\n  id, project_id, name, description, definition, filters,\n  created_by, updated_by, created_at, updated_at\n) VALUES (\n  #{id}, #{projectId}, #{name}, #{description},\n  CAST(#{definitionJson} AS JSON), CAST(#{filtersJson} AS JSON),\n  (SELECT id FROM langfuse_users WHERE id = #{actor} LIMIT 1),\n  (SELECT id FROM langfuse_users WHERE id = #{actor} LIMIT 1), now(3), now(3)\n)\n")
    int insert(
            @Param("id") String id,
            @Param("projectId") String projectId,
            @Param("name") String name,
            @Param("description") String description,
            @Param("definitionJson") String definitionJson,
            @Param("filtersJson") String filtersJson,
            @Param("actor") String actor);

    @Update("UPDATE langfuse_dashboards SET name = #{name}, description = #{description},\n  updated_by = #{updatedBy}, updated_at = now()\nWHERE id = #{dashboardId} AND project_id = #{projectId}\n")
    int updateMetadata(
            @Param("projectId") String projectId,
            @Param("dashboardId") String dashboardId,
            @Param("name") String name,
            @Param("description") String description,
            @Param("updatedBy") String updatedBy);

    @Update("UPDATE langfuse_dashboards SET definition = CAST(#{definitionJson} AS JSON),\n  updated_by = #{updatedBy}, updated_at = now(3)\nWHERE id = #{dashboardId} AND project_id = #{projectId}\n")
    int updateDefinition(
            @Param("projectId") String projectId,
            @Param("dashboardId") String dashboardId,
            @Param("definitionJson") String definitionJson,
            @Param("updatedBy") String updatedBy);

    @Update("UPDATE langfuse_dashboards SET filters = CAST(#{filtersJson} AS JSON),\n  updated_by = #{updatedBy}, updated_at = now(3)\nWHERE id = #{dashboardId} AND project_id = #{projectId}\n")
    int updateFilters(
            @Param("projectId") String projectId,
            @Param("dashboardId") String dashboardId,
            @Param("filtersJson") String filtersJson,
            @Param("updatedBy") String updatedBy);

    @Delete("DELETE FROM langfuse_dashboards WHERE id = #{dashboardId} AND project_id = #{projectId}")
    int deleteDashboard(
            @Param("projectId") String projectId,
            @Param("dashboardId") String dashboardId);
}
