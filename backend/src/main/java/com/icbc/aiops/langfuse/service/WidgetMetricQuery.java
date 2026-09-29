package com.icbc.aiops.langfuse.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@lombok.Value
public class WidgetMetricQuery {
    String projectId;
    String view;
    String dimension;
    String measure;
    String aggregation;
    boolean timeSeries;
    Instant fromTimestamp;
    Instant toTimestamp;
    List<WidgetMetricFilter> filters;

    public WidgetMetricQuery(String projectId, String view, String dimension, String measure,
            String aggregation, boolean timeSeries, Instant fromTimestamp, Instant toTimestamp) {
        this(projectId, view, dimension, measure, aggregation, timeSeries, fromTimestamp, toTimestamp,
                Collections.emptyList());
    }

    public WidgetMetricQuery(String projectId, String view, String dimension, String measure,
            String aggregation, boolean timeSeries, Instant fromTimestamp, Instant toTimestamp,
            List<WidgetMetricFilter> filters) {
        this.projectId = projectId;
        this.view = view;
        this.dimension = dimension;
        this.measure = measure;
        this.aggregation = aggregation;
        this.timeSeries = timeSeries;
        this.fromTimestamp = fromTimestamp;
        this.toTimestamp = toTimestamp;
        this.filters = filters;
    }

    public String projectId() { return projectId; }
    public String view() { return view; }
    public String dimension() { return dimension; }
    public String measure() { return measure; }
    public String aggregation() { return aggregation; }
    public boolean timeSeries() { return timeSeries; }
    public Instant fromTimestamp() { return fromTimestamp; }
    public Instant toTimestamp() { return toTimestamp; }
    public List<WidgetMetricFilter> filters() { return filters; }

}
