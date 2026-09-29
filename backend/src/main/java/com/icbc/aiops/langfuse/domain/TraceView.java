package com.icbc.aiops.langfuse.domain;

import java.util.List;

/** Trace/Observation 详情抽屉使用的读取模型。 */
@lombok.Value
public class TraceView {
    TraceDetail trace;
    List<Observation> observations;

    public TraceDetail trace() { return trace; }
    public List<Observation> observations() { return observations; }

}
