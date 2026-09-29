package com.icbc.aiops.langfuse.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;

class MyBatisFactorySupportTest {

    @Test
    void createsFactoryWithSharedMappingAndTimeoutSettings() throws Exception {
        DataSource dataSource = new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                throw new SQLException("connection is not required while building the factory");
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return getConnection();
            }
        };

        SqlSessionFactory factory = MyBatisFactorySupport.create(dataSource, 17);
        Configuration configuration = factory.getConfiguration();

        assertTrue(configuration.isMapUnderscoreToCamelCase());
        assertTrue(configuration.isArgNameBasedConstructorAutoMapping());
        assertEquals(Integer.valueOf(200), configuration.getDefaultFetchSize());
        assertEquals(Integer.valueOf(17), configuration.getDefaultStatementTimeout());
        assertSame(dataSource, configuration.getEnvironment().getDataSource());
    }
}
