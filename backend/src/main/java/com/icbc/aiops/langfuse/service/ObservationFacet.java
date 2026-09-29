package com.icbc.aiops.langfuse.service;

public enum ObservationFacet {
    ENVIRONMENT,
    /** AgentObs schema 中的第一标识维度，旧模型中不存在。 */
    SERVICE_NAME,
    TYPE,
    ROOT,
    LEVEL,
    NAME,
    TRACE_NAME,
    MODEL,
    PROMPT_NAME,
    USER_ID,
    SESSION_ID,
    TAG
}
