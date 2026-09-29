package com.icbc.aiops.langfuse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dsf.cocoa.router")
public class DsfCocoaRouterProperties {
    private String addr;

    public String getAddr() { return addr; }
    public void setAddr(String addr) { this.addr = addr; }
}
