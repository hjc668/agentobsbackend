package com.icbc.aiops.langfuse.service;

/** 经服务层校验的 Widget 筛选条件；SQL Provider 只接收该封闭结构中的值。 */
@lombok.Value
public class WidgetMetricFilter {
    String column;
    String type;
    String operator;
    Object value;

    public String column() { return column; }
    public String type() { return type; }
    public String operator() { return operator; }
    public Object value() { return value; }
}
