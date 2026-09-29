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
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("mybatis")
@MapperScan(basePackages = "com.icbc.aiops.langfuse.mapper",
        sqlSessionTemplateRef = "clickhouseSqlSessionTemplate")
public class ClickHouseMyBatisConfig {

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource.clickhouse")
    DataSourceProperties clickhouseDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource.clickhouse.hikari")
    HikariDataSource clickhouseDataSource(
            @Qualifier("clickhouseDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    SqlSessionFactory clickhouseSqlSessionFactory(
            @Qualifier("clickhouseDataSource") DataSource dataSource) throws Exception {
        return MyBatisFactorySupport.create(dataSource, 30);
    }

    @Bean
    @Primary
    SqlSessionTemplate clickhouseSqlSessionTemplate(
            @Qualifier("clickhouseSqlSessionFactory") SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }
}
