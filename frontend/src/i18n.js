import Vue from "vue";

const messages = {
  zh: {
    app: { search: "前往...", localUser: "本地用户", api: "Java 接口", language: "语言", logout: "退出登录", past24h: "过去 24 小时", past7d: "过去 7 天", past30d: "过去 30 天", past90d: "过去 90 天", off: "关闭" },
    breadcrumb: { org: "主组织" },
    groups: { observability: "可观测性", prompt: "提示词管理", evaluation: "评估" },
    nav: {
      home: "首页",
      dashboards: "仪表盘",
      tracing: "链路追踪",
      sessions: "会话",
      users: "用户",
      alerts: "告警",
      prompts: "提示词",
      playground: "调试台",
      scores: "评分",
      evaluators: "评估器",
      annotation: "人工标注",
      datasets: "数据集",
      experiments: "实验",
      settings: "设置",
      call: "预约沟通",
      support: "支持"
    },
    tracing: {
      title: "链路追踪",
      traces: "链路",
      observations: "观测",
      last7: "最近 7 天",
      export: "导出本页 CSV",
      addFilter: "添加筛选",
      searchTraces: "搜索链路",
      searchObservations: "搜索观测",
      status: "状态",
      type: "类型",
      environment: "环境",
      sort: "排序",
      direction: "方向",
      all: "全部",
      userId: "用户 ID",
      sessionId: "会话 ID",
      model: "模型",
      traceId: "链路 ID",
      tag: "标签",
      from: "开始时间",
      to: "结束时间",
      clear: "清空",
      name: "名称",
      timestamp: "时间",
      startTime: "开始时间",
      latency: "耗时",
      tokens: "Token 数",
      input: "输入",
      output: "输出",
      page: "第 {page} 页，共 {total} 页",
      results: "{count} 条结果",
      noTraces: "未找到链路",
      noTracesHint: "请调整筛选条件，或接入一条新链路。",
      noObservations: "未找到观测",
      noObservationsHint: "请调整筛选条件，或接入新的观测数据。",
      loading: "正在加载数据…",
      backendError: "后端错误",
      untitled: "未命名链路",
      observationsTitle: "观测",
      scores: "评分（{count}）",
      comments: "评论（{count}）",
      metadata: "元数据",
      modelParameters: "模型参数",
      usageDetails: "用量明细",
      ttft: "首 Token 时间",
      prompt: "提示词",
      noScores: "此链路暂无评分。",
      noComments: "此链路及其观测暂无评论。",
      unknownAuthor: "未知作者",
      trace: "链路",
      observation: "观测",
      statusMessage: "状态信息",
      loadDetailError: "无法加载链路详情"
    },
    placeholder: {
      badge: "待建设",
      title: "{name}待建设",
      description: "该模块正在建设中，当前版本仅开放链路追踪功能。"
    },
    home: {
      title: "首页",
      subtitle: "监控并理解你的 LLM 应用。",
      last7days: "最近 7 天",
      traces: "链路",
      traceVolume: "链路量趋势",
      total: "总计",
      getStarted: "快速开始",
      getStartedHint: "探索你的可观测性数据",
      viewTraces: "查看链路",
      viewTracesHint: "查看单条 LLM 请求",
      viewSessions: "查看会话",
      viewSessionsHint: "分析多轮对话交互",
      viewUsers: "查看用户",
      viewUsersHint: "了解用户级别的使用情况",
      metricTraces: "链路",
      metricTracesHint: "捕获的总请求数",
      metricObservations: "观测",
      metricObservationsHint: "Span 和 Generation",
      metricTokens: "总 Token 数",
      metricTokensHint: "模型用量",
      tooltipTraces: "链路："
    }
  },
  en: {
    app: { search: "Go to...", localUser: "Local user", api: "Java API", language: "Language", logout: "Sign out", past24h: "Past 24 hours", past7d: "Past 7 days", past30d: "Past 30 days", past90d: "Past 90 days", off: "Off" },
    breadcrumb: { org: "Main Org" },
    groups: { observability: "Observability", prompt: "Prompt Management", evaluation: "Evaluation" },
    nav: {
      home: "Home",
      dashboards: "Dashboards",
      tracing: "Tracing",
      sessions: "Sessions",
      users: "Users",
      alerts: "Alerts",
      prompts: "Prompts",
      playground: "Playground",
      scores: "Scores",
      evaluators: "Evaluators",
      annotation: "Human Annotation",
      datasets: "Datasets",
      experiments: "Experiments",
      settings: "Settings",
      call: "Book a call",
      support: "Support"
    },
    tracing: {
      title: "Tracing",
      traces: "Traces",
      observations: "Observations",
      last7: "Last 7 days",
      export: "Export page CSV",
      addFilter: "Add filter",
      searchTraces: "Search traces",
      searchObservations: "Search observations",
      status: "Status",
      type: "Type",
      environment: "Environment",
      sort: "Sort",
      direction: "Direction",
      all: "All",
      userId: "User ID",
      sessionId: "Session ID",
      model: "Model",
      traceId: "Trace ID",
      tag: "Tag",
      from: "From",
      to: "To",
      clear: "Clear",
      name: "Name",
      timestamp: "Timestamp",
      startTime: "Start time",
      latency: "Latency",
      tokens: "Tokens",
      input: "Input",
      output: "Output",
      page: "Page {page} of {total}",
      results: "{count} results",
      noTraces: "No traces found",
      noTracesHint: "Adjust your filters or ingest a new trace.",
      noObservations: "No observations found",
      noObservationsHint: "Adjust your filters or ingest new observations.",
      loading: "Loading data…",
      backendError: "Backend error",
      untitled: "Untitled trace",
      observationsTitle: "Observations",
      scores: "Scores ({count})",
      comments: "Comments ({count})",
      metadata: "Metadata",
      modelParameters: "Model parameters",
      usageDetails: "Usage details",
      ttft: "TTFT",
      prompt: "Prompt",
      noScores: "No scores are attached to this trace.",
      noComments: "No comments are attached to this trace or its observations.",
      unknownAuthor: "Unknown author",
      trace: "Trace",
      observation: "Observation",
      statusMessage: "Status message",
      loadDetailError: "Unable to load trace details"
    },
    placeholder: {
      badge: "Under construction",
      title: "{name} is under construction",
      description: "This module is under construction. Tracing is the only feature available in this release."
    },
    home: {
      title: "Home",
      subtitle: "Monitor and understand your LLM application.",
      last7days: "Last 7 days",
      traces: "Traces",
      traceVolume: "Trace volume over time",
      total: "total",
      getStarted: "Get started",
      getStartedHint: "Explore your observability data",
      viewTraces: "View traces",
      viewTracesHint: "Inspect individual LLM requests",
      viewSessions: "View sessions",
      viewSessionsHint: "Analyze multi-turn interactions",
      viewUsers: "View users",
      viewUsersHint: "Understand user-level usage",
      metricTraces: "Traces",
      metricTracesHint: "Total requests captured",
      metricObservations: "Observations",
      metricObservationsHint: "Spans and generations",
      metricTokens: "Total tokens",
      metricTokensHint: "Model usage",
      tooltipTraces: "Traces:"
    }
  }
};

const state = Vue.observable({ locale: localStorage.getItem("langfuse-locale") || "en" });

function lookup(locale, key) {
  return key.split(".").reduce((value, part) => value && value[part], messages[locale]);
}

export const i18n = {
  state,
  setLocale(locale) {
    state.locale = locale;
    localStorage.setItem("langfuse-locale", locale);
    document.documentElement.lang = locale === "zh" ? "zh-CN" : "en";
  },
  t(key, params = {}) {
    let value = lookup(state.locale, key) || lookup("en", key) || key;
    Object.entries(params).forEach(([name, replacement]) => {
      value = value.replace(`{${name}}`, String(replacement));
    });
    return value;
  }
};

Vue.mixin({
  computed: {
    $locale() {
      if (this.$store && this.$store.state.locale) return this.$store.state.locale;
      return state.locale;
    }
  },
  methods: {
    $t(key, params) {
      return i18n.t(key, params);
    }
  }
});
