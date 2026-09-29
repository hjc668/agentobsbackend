package com.icbc.aiops.langfuse.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.icbc.aiops.langfuse.postgres.mapper.CommentMapper;
import com.icbc.aiops.langfuse.postgres.mapper.DashboardMapper;
import com.icbc.aiops.langfuse.postgres.mapper.DashboardWidgetMapper;
import com.icbc.aiops.langfuse.postgres.mapper.PromptMapper;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class PolarDbxDialectContractTest {

    @Test
    void transactionalMappersDoNotContainPostgresOnlySyntax() {
        String sql = Arrays.asList(CommentMapper.class, DashboardMapper.class,
                        DashboardWidgetMapper.class, PromptMapper.class).stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(PolarDbxDialectContractTest::sql)
                .reduce("", (left, right) -> left + "\n" + right);

        Arrays.asList("::", "jsonb", "ILIKE", "DISTINCT ON", "pg_advisory",
                "jsonb_array", "unnest(", "array_append", "ANY(labels)")
                .forEach(token -> assertFalse(sql.contains(token), token));
        assertTrue(sql.contains("FOR UPDATE"));
        assertTrue(sql.contains("CAST(#{definitionJson} AS JSON)"));
    }

    @Test
    void updateStatementsDoNotContainSubqueriesUnsupportedByPolarDbx() {
        String sql = Arrays.asList(DashboardMapper.class, DashboardWidgetMapper.class).stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(Update.class))
                .map(PolarDbxDialectContractTest::sql)
                .reduce("", (left, right) -> left + "\n" + right);

        assertFalse(sql.toUpperCase().contains("SELECT"));
        assertTrue(sql.contains("updated_by = #{updatedBy}"));
    }

    @Test
    void mybatisProfileUsesTheVerifiedLocalPolarDbxConfiguration() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/application-mybatis.yml");
        assertNotNull(stream);
        byte[] bytes = new byte[stream.available()];
        int length = stream.read(bytes);
        String yaml = new String(bytes, 0, length, StandardCharsets.UTF_8);
        assertTrue(yaml.contains("com.alibaba.polardbx.core.jdbc.Driver"));
        assertFalse(yaml.contains("org.postgresql.Driver"));
        // The local 5.4.x playground uses the ordinary JDBC implementation embedded in the
        // PolarDB-X connector. No standalone com.mysql driver is required.
        assertTrue(yaml.contains("jdbc:mysql://127.0.0.1:8527/langfuse_web"));
        assertFalse(yaml.contains("jdbc:polardbx://"));
        assertTrue(yaml.contains("pool-name: HikariPool(Mysql)"));
    }

    @Test
    void java8RuntimeCanLoadDatabaseAndLoggingClasses() throws Exception {
        assertNotNull(Class.forName("com.alibaba.polardbx.Driver"));
        assertNotNull(Class.forName("com.alibaba.polardbx.core.jdbc.Driver"));
        assertNotNull(Class.forName("ch.qos.logback.core.joran.spi.JoranException"));
    }

    /**
     * The intranet Maven repository carries no com.mysql groupId. PolarDB-X Connector embeds the
     * whole Connector/J implementation under com.alibaba.polardbx.core.cj.*, and a jdbc:polardbx://
     * connection reaches the socket layer with no com.mysql class on the classpath, so a standalone
     * MySQL driver must not be reintroduced.
     */
    @Test
    void noStandaloneMysqlConnectorOnClasspath() {
        assertFalse(isOnClasspath("com.mysql.cj.jdbc.Driver"),
                "com.mysql:mysql-connector-j must not be a dependency: the groupId is unavailable "
                        + "on the intranet and is redundant with the PolarDB-X Connector");
    }

    private static boolean isOnClasspath(String className) {
        try {
            Class.forName(className, false, PolarDbxDialectContractTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    private static String sql(Method method) {
        if (method.isAnnotationPresent(Select.class)) return String.join("\n", method.getAnnotation(Select.class).value());
        if (method.isAnnotationPresent(Insert.class)) return String.join("\n", method.getAnnotation(Insert.class).value());
        if (method.isAnnotationPresent(Update.class)) return String.join("\n", method.getAnnotation(Update.class).value());
        if (method.isAnnotationPresent(Delete.class)) return String.join("\n", method.getAnnotation(Delete.class).value());
        return "";
    }
}
