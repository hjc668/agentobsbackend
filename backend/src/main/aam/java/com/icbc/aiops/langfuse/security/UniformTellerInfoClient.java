package com.icbc.aiops.langfuse.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.aam.util.EncryptUtils;
import com.icbc.aiops.langfuse.config.AamIntegrationProperties;
import com.icbc.aiops.langfuse.config.DsfCocoaRouterProperties;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/** UniformTeller/2.0/qryTellerInfo 加密客户端，实现方式与参考工程保持一致。 */
public class UniformTellerInfoClient implements AamUserInfoService {
    private static final Logger log = LoggerFactory.getLogger(UniformTellerInfoClient.class);
    private static final String QUERY_PATH =
            "/json/com.icbc.aam.service.UniformTeller/2.0/qryTellerInfo";

    private final AamIntegrationProperties aam;
    private final DsfCocoaRouterProperties cocoa;
    private final ObjectMapper objectMapper;

    public UniformTellerInfoClient(AamIntegrationProperties aam,
            DsfCocoaRouterProperties cocoa, ObjectMapper objectMapper) {
        this.aam = aam;
        this.cocoa = cocoa;
        this.objectMapper = objectMapper;
    }

    @Override
    public AamUserInfo query(String normalizedUserNo) {
        if (!StringUtils.hasText(normalizedUserNo)) {
            throw new AamAuthenticationException("AAM user identity is missing");
        }
        try {
            return execute(normalizedUserNo.trim());
        } catch (AamAuthenticationException rejected) {
            throw rejected;
        } catch (Exception failure) {
            // 禁止记录响应体、加密报文、密钥、服务地址或用户数据。
            log.error("AAM UniformTeller user lookup failed: {}", failure.getClass().getSimpleName());
            throw new AamAuthenticationException("AAM user information service failed");
        }
    }

    private AamUserInfo execute(String userNo) throws Exception {
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("serviceName", aam.getService().getSystem().getLabel());
        params.put("clientId", aam.getService().getWeb().getNum());
        params.put("digestFlag", "1");
        params.put("serviceIp", "");
        params.put("QUERYINFO", userNo);
        params.put("QUERYFLAG", "1");
        params.put("IGNORESTATUS", "1");

        List<String> encrypted = EncryptUtils.genEncryptParamJson(
                params, aam.getService().getPub().getKey());
        if (encrypted == null || encrypted.size() < 2
                || !StringUtils.hasText(encrypted.get(0)) || !StringUtils.hasText(encrypted.get(1))) {
            throw new AamAuthenticationException("AAM user information request encryption failed");
        }

        String router = cocoa.getAddr().endsWith("/")
                ? cocoa.getAddr().substring(0, cocoa.getAddr().length() - 1) : cocoa.getAddr();
        HttpURLConnection connection = (HttpURLConnection) new URL(router + QUERY_PATH).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-Request-App", "F-HMP");

            byte[] body = ("[" + encrypted.get(1) + "]").getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            OutputStream output = connection.getOutputStream();
            try { output.write(body); } finally { output.close(); }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new AamAuthenticationException("AAM user information service rejected request");
            }
            JsonNode response = objectMapper.readTree(read(connection.getInputStream()));
            JsonNode appStat = response == null ? null : response.get("appStat");
            if (!"0".equals(text(appStat, "return_code"))) {
                throw new AamAuthenticationException("AAM user information query was rejected");
            }
            String encryptedData = text(response, "data");
            if (!StringUtils.hasText(encryptedData)) {
                throw new AamAuthenticationException("AAM user information response is empty");
            }

            JsonNode user = objectMapper.readTree(EncryptUtils.decrypt(encryptedData, encrypted.get(0)));
            String returnedUserNo = text(user, "TELLERNO");
            if (StringUtils.hasText(returnedUserNo)
                    && !userNo.equals(AamUserIdNormalizer.normalize(returnedUserNo))) {
                throw new AamAuthenticationException("AAM user information identity mismatch");
            }
            String displayName = text(user, "TELLERNAME");
            if (!StringUtils.hasText(displayName)) {
                throw new AamAuthenticationException("AAM user information is incomplete");
            }
            return new AamUserInfo(userNo, displayName, text(user, "NOTESID"),
                    text(user, "BRANCHID"), text(user, "BRANCHNAME"));
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream input) throws java.io.IOException {
        InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        try {
            StringBuilder value = new StringBuilder();
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) >= 0) value.append(buffer, 0, count);
            return value.toString();
        } finally {
            reader.close();
        }
    }

    private static String text(JsonNode parent, String field) {
        if (parent == null) return null;
        JsonNode value = parent.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
