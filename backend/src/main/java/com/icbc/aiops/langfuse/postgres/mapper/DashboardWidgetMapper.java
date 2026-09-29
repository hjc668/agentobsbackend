package com.icbc.aiops.langfuse.postgres.mapper;

import com.icbc.aiops.langfuse.postgres.mapper.DashboardWidgetRows.DashboardWidgetRow;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface DashboardWidgetMapper {

    String COLUMNS = "id, project_id AS projectId, name, description, view,\nCAST(dimensions AS CHAR) AS dimensionsJson, CAST(metrics AS CHAR) AS metricsJson,\nCAST(filters AS CHAR) AS filtersJson, chart_type AS chartType,\nCAST(chart_config AS CHAR) AS chartConfigJson, min_version AS minVersion,\ncreated_by AS createdBy, updated_by AS updatedBy,\ncreated_at AS createdAt, updated_at AS updatedAt\n";

    @Select("SELECT " + COLUMNS + " FROM langfuse_dashboard_widgets "
            + "WHERE project_id = #{projectId} OR project_id IS NULL "
            + "ORDER BY updated_at DESC LIMIT #{size} OFFSET #{offset}")
    List<DashboardWidgetRow> selectWidgets(
            @Param("projectId") String projectId, @Param("size") int size, @Param("offset") long offset);

    @Select("SELECT count(*) FROM langfuse_dashboard_widgets WHERE project_id = #{projectId} OR project_id IS NULL")
    long countWidgets(@Param("projectId") String projectId);

    @Select("SELECT " + COLUMNS + " FROM langfuse_dashboard_widgets WHERE id = #{widgetId} "
            + "AND (project_id = #{projectId} OR project_id IS NULL)")
    DashboardWidgetRow selectById(@Param("projectId") String projectId, @Param("widgetId") String widgetId);

    @Select("SELECT id FROM langfuse_users WHERE id = #{actor} LIMIT 1")
    String selectActorId(@Param("actor") String actor);

    @Insert("INSERT INTO langfuse_dashboard_widgets (\n  id, project_id, name, description, view, dimensions, metrics, filters,\n  chart_type, chart_config, min_version, created_by, updated_by, created_at, updated_at\n) VALUES (\n  #{id}, #{projectId}, #{name}, #{description}, #{view},\n  CAST(#{dimensionsJson} AS JSON), CAST(#{metricsJson} AS JSON), CAST(#{filtersJson} AS JSON),\n  #{chartType}, CAST(#{chartConfigJson} AS JSON), 1,\n  (SELECT id FROM langfuse_users WHERE id = #{actor} LIMIT 1),\n  (SELECT id FROM langfuse_users WHERE id = #{actor} LIMIT 1), now(3), now(3)\n)\n")
    int insert(
            @Param("id") String id, @Param("projectId") String projectId,
            @Param("name") String name, @Param("description") String description,
            @Param("view") String view, @Param("dimensionsJson") String dimensionsJson,
            @Param("metricsJson") String metricsJson, @Param("filtersJson") String filtersJson,
            @Param("chartType") String chartType, @Param("chartConfigJson") String chartConfigJson,
            @Param("actor") String actor);

    @Update("UPDATE langfuse_dashboard_widgets SET name = #{name}, description = #{description},\n  view = #{view}, dimensions = CAST(#{dimensionsJson} AS JSON),\n  metrics = CAST(#{metricsJson} AS JSON), filters = CAST(#{filtersJson} AS JSON),\n  chart_type = #{chartType},\n  chart_config = CAST(#{chartConfigJson} AS JSON),\n  updated_by = #{updatedBy}, updated_at = now(3)\nWHERE id = #{widgetId} AND project_id = #{projectId}\n")
    int update(
            @Param("projectId") String projectId, @Param("widgetId") String widgetId,
            @Param("name") String name, @Param("description") String description,
            @Param("view") String view, @Param("dimensionsJson") String dimensionsJson,
            @Param("metricsJson") String metricsJson, @Param("filtersJson") String filtersJson,
            @Param("chartType") String chartType, @Param("chartConfigJson") String chartConfigJson,
            @Param("updatedBy") String updatedBy);

    @Delete("DELETE FROM langfuse_dashboard_widgets WHERE id = #{widgetId} AND project_id = #{projectId}")
    int delete(@Param("projectId") String projectId, @Param("widgetId") String widgetId);
}
