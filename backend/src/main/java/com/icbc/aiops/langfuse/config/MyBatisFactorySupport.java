package com.icbc.aiops.langfuse.config;

import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.boot.autoconfigure.SpringBootVFS;

final class MyBatisFactorySupport {

    private MyBatisFactorySupport() {
    }

    static SqlSessionFactory create(DataSource dataSource, int timeoutSeconds) throws Exception {
        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setArgNameBasedConstructorAutoMapping(true);
        configuration.setDefaultFetchSize(200);
        configuration.setDefaultStatementTimeout(timeoutSeconds);

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setVfs(SpringBootVFS.class);
        return factory.getObject();
    }
}
