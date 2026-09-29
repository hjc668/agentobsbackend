package com.icbc.aiops.langfuse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Hermes 验签与 UniformTeller 服务适配器共用的 AAM 配置。 */
@ConfigurationProperties(prefix = "aam")
public class AamIntegrationProperties {

    private String enableSSIC;
    private String enableSpecialUrl;
    private final Ssic ssic = new Ssic();
    private final Service service = new Service();

    public String getEnableSSIC() { return enableSSIC; }
    public void setEnableSSIC(String enableSSIC) { this.enableSSIC = enableSSIC; }
    public String getEnableSpecialUrl() { return enableSpecialUrl; }
    public void setEnableSpecialUrl(String enableSpecialUrl) { this.enableSpecialUrl = enableSpecialUrl; }
    public Ssic getSsic() { return ssic; }
    public Service getService() { return service; }

    public static class Ssic {
        private final Server server = new Server();
        private final Client client = new Client();
        private String returnUrlKey;

        public Server getServer() { return server; }
        public Client getClient() { return client; }
        public String getReturnUrlKey() { return returnUrlKey; }
        public void setReturnUrlKey(String returnUrlKey) { this.returnUrlKey = returnUrlKey; }
    }

    public static class Server {
        private String ip;
        private String version;
        private String publickey;

        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getPublickey() { return publickey; }
        public void setPublickey(String publickey) { this.publickey = publickey; }
    }

    public static class Client {
        private String keyName;
        private String siteUrl;
        private String priKeyPasswd;

        public String getKeyName() { return keyName; }
        public void setKeyName(String keyName) { this.keyName = keyName; }
        public String getSiteUrl() { return siteUrl; }
        public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl; }
        public String getPriKeyPasswd() { return priKeyPasswd; }
        public void setPriKeyPasswd(String priKeyPasswd) { this.priKeyPasswd = priKeyPasswd; }
    }

    public static class Service {
        private final Pub pub = new Pub();
        private final SystemConfig system = new SystemConfig();
        private final Web web = new Web();

        public Pub getPub() { return pub; }
        public SystemConfig getSystem() { return system; }
        public Web getWeb() { return web; }
    }

    public static class Pub {
        private String key;
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
    }

    public static class SystemConfig {
        private String label;
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }

    public static class Web {
        private String num;
        public String getNum() { return num; }
        public void setNum(String num) { this.num = num; }
    }
}
