package com.icbc.aiops.langfuse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.icbc.aiops.langfuse.config.WorkspaceProperties;

@SpringBootApplication
@EnableConfigurationProperties(WorkspaceProperties.class)
public class LangfuseQueryApplication {

    public static void main(String[] args) {
        SpringApplication.run(LangfuseQueryApplication.class, args);
    }
}
