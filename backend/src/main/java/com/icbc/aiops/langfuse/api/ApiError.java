package com.icbc.aiops.langfuse.api;

import java.time.Instant;

@lombok.Value
public class ApiError {
    String code;
    String message;
    Instant timestamp;

    public String code() { return code; }
    public String message() { return message; }
    public Instant timestamp() { return timestamp; }

}

