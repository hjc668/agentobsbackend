package com.icbc.aiops.langfuse.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@Profile("mybatis")
@MapperScan(basePackages = "com.icbc.aiops.langfuse.postgres.mapper",
        sqlSessionTemplateRef = "polarDbxSqlSessionTemplate")
public class PostgresMyBatisConfig {

    @Bean
    @ConfigurationProperties("app.datasource.polardbx")
    DataSourceProperties polarDbxDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("app.datasource.polardbx.hikari")
    HikariDataSource polarDbxDataSource(
            @Qualifier("polarDbxDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    SqlSessionFactory polarDbxSqlSessionFactory(
            @Qualifier("polarDbxDataSource") DataSource dataSource) throws Exception {
        return MyBatisFactorySupport.create(dataSource, 30);
    }

    @Bean
    SqlSessionTemplate polarDbxSqlSessionTemplate(
            @Qualifier("polarDbxSqlSessionFactory") SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }

    @Bean
    PlatformTransactionManager polarDbxTransactionManager(
            @Qualifier("polarDbxDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
