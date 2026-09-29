package com.icbc.aiops.langfuse.postgres.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 从内部授权表读取 VIEW/ADMIN 角色配置。
 *
 * <p>表名使用 {@code langfuse_} 前缀，必须与 {@code backend/sql/polardbx-schema.sql} 保持一致。
 */
@Mapper
public interface UserRoleMapper {

    /** @return 已配置的角色编码；用户没有对应记录时返回 {@code null}。 */
    @Select("SELECT role_code FROM langfuse_user_roles WHERE aam_user_no = #{aamUserNo}")
    String selectRoleCode(@Param("aamUserNo") String aamUserNo);
}
