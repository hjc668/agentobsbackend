package com.icbc.aiops.langfuse.api;

/**
 * 行内前端调用 {@code /aam/login/auth} 时约定的响应包装结构。
 *
 * <p>成功时 {@code code} 为 {@code "0"}。认证失败使用统一的 HTTP 401
 * {@link ApiError} 结构，不得伪装为成功的 HTTP 200。
 */
public class AamCommonResponse<T> {

    public static final String SUCCESS_CODE = "0";

    private final String code;
    private final String msg;
    private final T result;

    private AamCommonResponse(String code, String msg, T result) {
        this.code = code;
        this.msg = msg;
        this.result = result;
    }

    public static <T> AamCommonResponse<T> success(String msg, T result) {
        return new AamCommonResponse<T>(SUCCESS_CODE, msg, result);
    }

    public String getCode() { return code; }
    public String getMsg() { return msg; }
    public T getResult() { return result; }
}
