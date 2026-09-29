package com.icbc.aiops.langfuse.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotBlank;

/**
 * AAM 回调请求体：统一认证服务生成的签名票据对。
 *
 * <p>该类型有意<strong>只声明</strong>这两个字段。{@code aamId}、{@code role}、
 * {@code admin}、{@code department}、{@code userName} 等字段完全不参与绑定，
 * 浏览器无法通过这些字段影响身份或权限；权威身份从已验证凭据中读取。
 *
 * <p>{@code SSIAuth}/{@code SSISign} 是标准字段名；同时接受小写别名，
 * 以兼容参考前端发送的两种写法。
 */
public class AamSsoLoginRequest {

    @JsonProperty("SSIAuth")
    @JsonAlias("ssiAuth")
    @NotBlank
    private String ssiAuth;

    @JsonProperty("SSISign")
    @JsonAlias("ssiSign")
    @NotBlank
    private String ssiSign;

    public String getSsiAuth() { return ssiAuth; }
    public void setSsiAuth(String ssiAuth) { this.ssiAuth = ssiAuth; }
    public String getSsiSign() { return ssiSign; }
    public void setSsiSign(String ssiSign) { this.ssiSign = ssiSign; }
}
