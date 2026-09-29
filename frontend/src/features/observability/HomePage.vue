<template>
  <div>
    <header class="page-header">
      <div>
        <h1>{{ $t("home.title") }}</h1>
        <p>{{ $t("home.subtitle") }}</p>
      </div>
      <div class="page-actions">
        <el-button size="small"> <span class="calendar-dot" /> {{ $t("home.last7days") }} </el-button>
      </div>
    </header>

    <ErrorBanner :message="error" />

    <section class="metrics-grid">
      <el-card v-for="metric in metrics" :key="metric.label" class="metric-card" :body-style="{ padding: '13px 15px' }">
        <div>
          <span>{{ metric.label }}</span>
          <el-button type="text" circle size="mini" :aria-label="`${metric.label} info`">ⓘ</el-button>
        </div>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.hint }}</small>
      </el-card>
    </section>

    <section class="dashboard-grid">
      <el-card class="dashboard-card chart-card" :body-style="{ padding: '14px' }">
        <header>
          <div>
            <h2>{{ $t("home.traces") }}</h2>
            <p>{{ $t("home.traceVolume") }}</p>
          </div>
          <span class="metric-change">{{ trendTotal }} {{ $t("home.total") }}</span>
        </header>
        <div ref="trendChart" style="width: 100%; height: 180px"></div>
        <div class="trend-labels">
          <span v-for="point in trendData" :key="point.timestamp">
            {{ formatTrendLabel(point.timestamp) }}
          </span>
        </div>
      </el-card>

      <el-card class="dashboard-card quick-links" :body-style="{ padding: '14px' }">
        <header>
          <div>
            <h2>{{ $t("home.getStarted") }}</h2>
            <p>{{ $t("home.getStartedHint") }}</p>
          </div>
        </header>
        <el-button type="text" class="quick-link-item" @click="navigate('/traces')">
          <div class="quick-link-text">
            <NavIcon name="trace" />
            <strong>{{ $t("home.viewTraces") }}</strong>
            <small>{{ $t("home.viewTracesHint") }}</small>
            <b>›</b>
          </div>
        </el-button>
        <el-button type="text" class="quick-link-item" @click="navigate('/sessions')">
          <div class="quick-link-text">
            <NavIcon name="clock" />
            <strong>{{ $t("home.viewSessions") }}</strong>
            <small>{{ $t("home.viewSessionsHint") }}</small>
            <b>›</b>
          </div>
        </el-button>
        <el-button type="text" class="quick-link-item" @click="navigate('/users')">
          <div class="quick-link-text">
            <NavIcon name="users" />
            <strong>{{ $t("home.viewUsers") }}</strong>
            <small>{{ $t("home.viewUsersHint") }}</small>
            <b>›</b>
          </div>
        </el-button>
      </el-card>
    </section>
  </div>
</template>

<script>
import { mapState } from "vuex";
import * as echarts from "echarts";
import { observabilityApi } from "../../api/client";
import { compactNumber } from "../../utils/format";
import NavIcon from "../../components/NavIcon.vue";
import ErrorBanner from "../../components/ErrorBanner.vue";
import remoteData from "../../mixins/remoteData";

const EMPTY_SUMMARY = {
  traceCount: 0,
  observationCount: 0,
  errorCount: 0,
  totalTokens: 0,
  averageLatencyMs: 0
};

export default {
  name: "HomePage",
  components: { NavIcon, ErrorBanner },
  mixins: [remoteData],
  data() {
    return {
      summaryData: EMPTY_SUMMARY,
      trendData: [],
      chart: null
    };
  },
  computed: {
    ...mapState(["refreshKey", "dateRange"]),
    metrics() {
      const data = this.summaryData;
      return [
        { label: this.$t("home.metricTraces"), value: compactNumber(data.traceCount), hint: this.$t("home.metricTracesHint") },
        { label: this.$t("home.metricObservations"), value: compactNumber(data.observationCount), hint: this.$t("home.metricObservationsHint") },
        { label: this.$t("home.metricTokens"), value: compactNumber(data.totalTokens), hint: this.$t("home.metricTokensHint") }
      ];
    },
    trendTotal() {
      return this.trendData.reduce((sum, p) => sum + p.traceCount, 0).toLocaleString();
    },
    error() {
      return this.remoteError;
    }
  },
  watch: {
    refreshKey() {
      this.loadData();
    }
  },
  mounted() {
    this.loadData();
  },
  beforeDestroy() {
    if (this.chart) {
      this.chart.dispose();
    }
  },
  methods: {
    async loadData() {
      await this.loadRemote(async signal => {
        const [summary, trend] = await Promise.all([
          observabilityApi.getSummary(signal),
          observabilityApi.getMetricTimeSeries(signal)
        ]);
        this.summaryData = summary || EMPTY_SUMMARY;
        this.trendData = trend || [];
        this.$nextTick(() => this.renderChart());
      });
    },
    renderChart() {
      if (!this.$refs.trendChart || !this.trendData.length) return;

      if (!this.chart) {
        this.chart = echarts.init(this.$refs.trendChart);
      }

      const option = {
        tooltip: {
          trigger: "axis",
          formatter: params => {
            const p = params[0];
            return `${p.name}<br/>${this.$t("home.tooltipTraces")} ${p.value.toLocaleString()}`;
          }
        },
        grid: {
          left: 10,
          right: 10,
          top: 10,
          bottom: 10
        },
        xAxis: {
          type: "category",
          data: this.trendData.map(p => p.timestamp),
          show: false
        },
        yAxis: {
          type: "value",
          show: false
        },
        series: [
          {
            data: this.trendData.map(p => p.traceCount),
            type: "line",
            smooth: true,
            showSymbol: false,
            lineStyle: {
              color: "#6374ee",
              width: 2
            },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: "rgba(107, 124, 255, 0.24)" },
                { offset: 1, color: "rgba(107, 124, 255, 0)" }
              ])
            }
          }
        ]
      };

      this.chart.setOption(option);
    },
    formatTrendLabel(timestamp) {
      const locale = this.$locale === "zh" ? "zh-CN" : "en";
      return new Date(timestamp).toLocaleDateString(locale, { month: "short", day: "numeric" });
    },
    navigate(path) {
      this.$router.push(path);
    }
  }
};
</script>
<style lang="scss" scoped>
.quick-link-item {
  .quick-link-text {
    display: flex;
    width: 100%;
    height: 20px;
    align-items: center;
  }
}
::v-deep .el-button+.el-button {
  margin-left: 0;
}
</style>
