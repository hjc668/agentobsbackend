package com.icbc.aiops.langfuse.mapper;

import com.icbc.aiops.langfuse.mapper.WidgetMetricRows.WidgetMetricRow;
import com.icbc.aiops.langfuse.service.WidgetMetricQuery;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

@Mapper
public interface WidgetMetricMapper {
    @SelectProvider(type = WidgetMetricSqlProvider.class, method = "metric")
    List<WidgetMetricRow> selectMetric(@Param("query") WidgetMetricQuery query);
}
