package com.icbc.aiops.langfuse.postgres.mapper;

import com.icbc.aiops.langfuse.postgres.mapper.CommentRows.CommentRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommentMapper {

    @Select("<script>\nSELECT id,\n       object_type AS objectType,\n       object_id AS objectId,\n       content,\n       author_user_id AS authorUserId,\n       data_field AS dataField,\n       created_at AS createdAt,\n       updated_at AS updatedAt\nFROM langfuse_comments\nWHERE project_id = #{projectId}\n  AND (\n    (object_type = 'TRACE' AND object_id = #{traceId})\n    <if test=\"observationIds != null and !observationIds.isEmpty()\">\n      OR (object_type = 'OBSERVATION' AND object_id IN\n        <foreach collection=\"observationIds\" item=\"id\" open=\"(\" separator=\",\" close=\")\">\n          #{id}\n        </foreach>\n      )\n    </if>\n  )\nORDER BY created_at ASC, id ASC\n</script>\n")
    List<CommentRow> selectForTrace(
            @Param("projectId") String projectId,
            @Param("traceId") String traceId,
            @Param("observationIds") List<String> observationIds);
}
