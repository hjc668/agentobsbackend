<template>
  <div class="tracing-page">
    <div class="tracing-title-row">
      <h1>{{ c.title }} <span>ⓘ</span></h1>
    </div>
    <div class="tracing-commandbar">
      <div class="tracing-search-wrap">
        <div class="tracing-search">
          <i class="el-icon-search" />
          <div class="tracing-search-composer">
            <span v-for="chip in filterChips" :key="chip.id" class="inline-filter-chip" :title="chip.expression"
              ><span v-if="chip.chipKey" class="chip-key">{{ chip.chipKey }}</span
              ><span class="chip-value">{{ chip.chipValue }}</span
              ><button
                type="button"
                :aria-label="'Remove ' + chip.expression"
                @mousedown.prevent
                @click="removeFilterChip(chip)"
              >
                ×
              </button></span
            >
            <input
              ref="searchInput"
              v-model="searchDraft"
              :placeholder="filterChips.length ? 'Add filter…' : c.searchHint"
              aria-label="Add a filter"
              @focus="searchFocused = true"
              @blur="commitOnBlur"
              @keyup.enter.prevent="commitSearchDraft"
              @keydown.delete="removeLastChip"
            />
          </div>
          <el-button
            v-if="filterChips.length"
            type="text"
            size="mini"
            class="clear-filter-chips"
            @mousedown.prevent
            @click="clearFilters"
            >{{ c.clearBtn }}</el-button
          ><kbd>⌘ K</kbd>
        </div>
        <div v-if="searchFocused && searchSuggestions.length" class="search-suggestions" @mousedown.prevent>
          <header>
            <span>{{ c.filterDimension }}</span
            ><el-button type="text" size="mini" @click="searchFocused = false">×</el-button>
          </header>
          <button
            v-for="field in searchSuggestions"
            :key="field.key"
            type="button"
            class="search-suggestion-item"
            @click="selectSearchField(field.key)"
          >
            <code>{{ field.key }}</code>
            <span>{{ field.label }}</span>
          </button>
        </div>
        <div v-if="searchIssue" class="search-validation" role="status">{{ searchIssue }}</div>
      </div>
      <div class="tracing-actions">
        <el-button size="small" class="mobile-filter-toggle" @click="filtersVisible = !filtersVisible"
          ><i class="el-icon-s-operation" /> Filters</el-button
        >
        <el-button :class="{ active: quick === 'quality' }" size="small" @click.stop="toggleQuick('quality')"
          ><i class="el-icon-thumb" /> {{ c.quality }}</el-button
        >
        <el-button :class="{ active: quick === 'slow' }" size="small" @click.stop="toggleQuick('slow')"
          ><i class="el-icon-timer" /> {{ c.slow }}</el-button
        >
        <el-button size="small" @click.stop="savedOpen = !savedOpen"
          >{{ c.views }} <b>{{ savedViews.length }}</b></el-button
        >
        <div v-if="savedOpen" class="tracing-popover saved-views-popover" @click.stop>
          <header>
            <strong>{{ c.views }}</strong
            ><el-button type="text" size="mini" @click="saveCurrentView">Save current</el-button>
          </header>
          <div v-if="savedViews.length">
            <div v-for="saved in savedViews" :key="saved.id" class="saved-view-row">
              <el-button type="text" @click="applySavedView(saved)"
                ><strong>{{ saved.name }}</strong
                ><small>{{ saved.search || "All observations" }}</small></el-button
              ><el-button type="text" icon="el-icon-close" @click="deleteSavedView(saved.id)" />
            </div>
          </div>
          <p v-else>No saved views yet.</p>
        </div>
        <el-radio-group v-model="view" size="small">
          <el-radio-button label="table"><i class="el-icon-s-grid" /> {{ c.table }}</el-radio-button>
          <el-radio-button label="chart"><i class="el-icon-s-data" /> {{ c.chart }}</el-radio-button>
        </el-radio-group>
        <span class="command-spacer" />
        <el-button size="small" class="columns-button" @click.stop="columnsOpen = !columnsOpen"
          >{{ c.columns }} <b>16/32</b></el-button
        >
        <el-button size="small" :class="['square-action', { active: dense }]" icon="el-icon-document" :title="dense ? c.normalTitle : c.denseTitle" @click="dense = !dense" />
        <el-button
          size="small"
          class="square-action"
          icon="el-icon-download"
          :title="c.exportTitle"
          :disabled="!items.length"
          @click="exportPage"
        />
      </div>
      <div v-if="quick" class="quick-popover" @click.stop>
        <strong>{{ quickTitle }}</strong
        ><el-button
          v-for="preset in quickPresets"
          :key="preset.label"
          type="text"
          class="quick-preset-item"
          @click="usePreset(preset)"
          ><i :class="preset.icon" /><span
            ><b>{{ preset.label }}</b
            ><small>{{ preset.hint }}</small></span
          ></el-button
        >
      </div>
      <div v-if="columnsOpen" class="columns-popover" @click.stop>
        <div class="columns-popover-header">
          <strong>{{ c.columns }}</strong>
          <el-button type="text" size="mini" icon="el-icon-close" @click="columnsOpen = false" />
        </div>
        <el-checkbox v-for="column in allColumns" :key="column.key" v-model="visibleColumns" :label="column.key">{{
          column.label
        }}</el-checkbox>
      </div>
    </div>

    <div :class="['tracing-workbench', { 'filters-hidden': !filtersVisible }]">
      <aside class="tracing-filters">
        <header>
          <strong><i class="el-icon-s-operation" /> {{ c.filters }}</strong>
          <div>
            <el-button type="text" :title="c.clearAll" icon="el-icon-magic-stick" @click="clearFilters" /><el-button
              type="text"
              class="mobile-filter-close"
              aria-label="Close filters"
              @click="filtersVisible = false"
              >×</el-button
            >
          </div>
        </header>
        <div class="filter-search">
          <i class="el-icon-search" /><el-input v-model="filterSearch" :placeholder="c.searchFilters" size="mini" />
        </div>
        <section v-if="matches('environment')" class="filter-group">
          <h3 @click="open.environment = !open.environment">
            <i :class="open.environment ? 'el-icon-arrow-down' : 'el-icon-arrow-right'" /> {{ c.environment }}
          </h3>
          <div v-if="open.environment">
            <el-radio-group v-model="filterModes.environment" size="mini" class="segmented"
              ><el-radio-button label="select">{{ c.select }}</el-radio-button
              ><el-radio-button label="text">{{ c.text }}</el-radio-button></el-radio-group
            >
            <div v-if="filterModes.environment === 'select'">
              <el-checkbox
                v-for="option in environmentOptions"
                :key="option.value"
                v-model="environment"
                :label="option.value"
                class="check-row"
                ><span>{{ option.value }}</span
                ><b>{{ option.count }}</b></el-checkbox
              >
            </div>
            <div v-else class="filter-text-mode">
              <el-input
                v-model="filterDrafts.environment"
                size="mini"
                :placeholder="c.enterValue"
                @keyup.enter.native="applyFilterDraft('environment')"
              /><el-button size="mini" @click="applyFilterDraft('environment')">{{ c.apply }}</el-button>
            </div>
          </div>
        </section>
        <section v-if="matches('type')" class="filter-group">
          <h3 @click="open.type = !open.type">
            <i :class="open.type ? 'el-icon-arrow-down' : 'el-icon-arrow-right'" /> {{ c.type }} <span>ⓘ</span>
          </h3>
          <div v-if="open.type">
            <el-radio-group v-model="filterModes.type" size="mini" class="segmented"
              ><el-radio-button label="select">{{ c.select }}</el-radio-button
              ><el-radio-button label="text">{{ c.text }}</el-radio-button></el-radio-group
            >
            <div v-if="filterModes.type === 'select'">
              <el-checkbox
                v-for="option in typeOptions"
                :key="option.value"
                v-model="types"
                :label="option.value"
                class="check-row"
                ><span :class="['filter-type', option.value.toLowerCase()]"
                  >{{ typeIcon(option.value) }} &nbsp;{{ option.value }}</span
                ><b>{{ option.count }}</b></el-checkbox
              >
            </div>
            <div v-else class="filter-text-mode">
              <el-input
                v-model="filterDrafts.type"
                size="mini"
                placeholder="SPAN, GENERATION…"
                @keyup.enter.native="applyFilterDraft('type')"
              /><el-button size="mini" @click="applyFilterDraft('type')">{{ c.apply }}</el-button>
            </div>
          </div>
        </section>
        <section v-if="matches('root')" class="filter-group">
          <h3 @click="open.root = !open.root">
            <i :class="open.root ? 'el-icon-arrow-down' : 'el-icon-arrow-right'" /> {{ c.root }} <span>ⓘ</span>
          </h3>
          <div v-if="open.root">
            <el-radio-group v-model="filterModes.root" size="mini" class="segmented"
              ><el-radio-button label="select">{{ c.select }}</el-radio-button
              ><el-radio-button label="text">{{ c.text }}</el-radio-button></el-radio-group
            >
            <div v-if="filterModes.root === 'select'">
              <el-checkbox
                v-for="option in rootOptions"
                :key="option.value"
                v-model="rootValues"
                :label="option.value"
                class="check-row"
                ><span>{{ option.value === "true" ? "True" : "False" }}</span
                ><b>{{ option.count }}</b></el-checkbox
              >
            </div>
            <div v-else class="filter-text-mode">
              <el-input
                v-model="filterDrafts.root"
                size="mini"
                placeholder="true / false"
                @keyup.enter.native="applyFilterDraft('root')"
              /><el-button size="mini" @click="applyFilterDraft('root')">{{ c.apply }}</el-button>
            </div>
          </div>
        </section>
        <section v-for="name in collapsedFilterNames" :key="name" v-show="matches(name)" class="collapsed-filter">
          <h3 @click="toggleCollapsed(name)">
            <i :class="collapsedFilters[name] ? 'el-icon-arrow-down' : 'el-icon-arrow-right'" />
            {{ labelFor(name) }}
          </h3>
          <div v-if="collapsedFilters[name]" class="filter-text-mode">
            <el-input
              v-model="filterDrafts[name]"
              size="mini"
              :placeholder="c.enterValue"
              @keyup.enter.native="applyFilterDraft(name)"
            /><el-button size="mini" @click="applyFilterDraft(name)">{{ c.apply }}</el-button>
          </div>
        </section>
      </aside>

      <section class="tracing-results">
        <div class="results-header">
          <el-button size="small" class="mobile-filter-toggle" @click="filtersVisible = !filtersVisible"
            ><i class="el-icon-s-operation" /> {{ c.filters }}</el-button
          >
        </div>
        <div v-if="selectedIds.length" class="selection-bar">
          <strong>{{ selectedIds.length }} selected</strong
          ><el-button size="mini" @click="exportSelected">Export selected</el-button
          ><el-button size="mini" @click="selectedIds = []">Clear</el-button>
        </div>
        <el-alert v-if="error" :title="c.backendError + ': ' + error" type="error" :closable="false" show-icon />
        <div v-if="loading" class="loading-state"><i class="el-icon-loading" /> {{ c.loading }}</div>
        <div v-else-if="view === 'table'" :class="['event-table-wrap', { dense }]">
          <el-table
            :data="items"
            size="mini"
            :class="['event-table', { dense }]"
            @row-click="openDetail"
            style="width: 100%"
          >
            <el-table-column width="40" class-name="checkbox-col">
              <template slot="header"
                ><div class="checkbox-col-cell"><input type="checkbox" @change="toggleAll" /></div
              ></template>
              <template slot-scope="{ row }"
                ><div @click.stop class="checkbox-col-cell"><el-checkbox v-model="selectedIds" :label="row.id" /></div
              ></template>
            </el-table-column>
            <el-table-column v-for="column in activeColumns" :key="column.key" :label="column.label" min-width="120">
              <template slot-scope="{ row }"
                ><template v-if="row"
                  ><span v-if="column.key === 'startTime'">{{ formatTimestamp(row.startTime) }}</span
                  ><span v-else-if="column.key === 'type'" :class="['event-type-icon', (row.type || '').toLowerCase()]">{{
                    typeIcon(row.type)
                  }}</span
                  ><strong v-else-if="column.key === 'name'" class="event-name">{{ row.name }}</strong
                  ><span v-else-if="column.key === 'traceName'" class="event-name">{{ traceName(row) }}</span
                  ><span v-else-if="column.key === 'input'" class="event-preview">{{ preview(row.input) }}</span
                  ><span v-else-if="column.key === 'output'" class="event-preview">{{ preview(row.output) }}</span
                  ><span v-else-if="column.key === 'metadata'" class="event-preview">{{ preview(row.metadata) }}</span
                  ><span v-else-if="column.key === 'level'">{{ row.level }}</span
                  ><span v-else-if="column.key === 'latency'">{{ formatDuration(row.latencyMs) }}</span
                  ><span v-else-if="column.key === 'model'">{{ row.model || "—" }}</span
                  ><span v-else>—</span
                ></template>
                <span v-else>—</span></template
              >
            </el-table-column>
          </el-table>
        </div>
        <div v-else class="tracing-chart-view">
          <div class="big-chart">
            <button v-for="(height, index) in bars" :key="index" :style="{ height: `${height}%` }" />
          </div>
          <strong>{{ c.chartTitle }}</strong>
          <p>{{ c.chartHint }}</p>
        </div>
        <footer v-if="view === 'table'" class="langfuse-pagination">
          <span>{{ c.total }} ≈ {{ number(pageData ? pageData.total : 0) }}</span>
          <div>
            <b class="rows-desktop">{{ c.rows }}</b
            ><b class="rows-mobile">{{ c.rowsShort }}</b
            ><el-select v-model.number="pageSize" size="mini" style="width: 70px"
              ><el-option :value="25" :label="'25'" /><el-option :value="50" :label="'50'" /><el-option
                :value="100"
                :label="'100'" /></el-select
            ><b>{{ c.page }} {{ page + 1 }}</b
            ><el-button size="mini" :disabled="page === 0" @click="page--">‹</el-button
            ><el-button size="mini" :disabled="!pageData || !pageData.hasNext" @click="page++">›</el-button>
          </div>
        </footer>
      </section>
    </div>

    <TracePeekPanel
      v-if="detail"
      :detail="detail"
      :selected-id="selectedId"
      :detail-mode.sync="detailMode"
      :desktop-nav-mode.sync="desktopNavMode"
      :tree-search.sync="treeSearch"
      :detail-tab.sync="detailTab"
      :detail-format.sync="detailFormat"
      :log-search.sync="logSearch"
      :expanded-ids.sync="expandedIds"
      @close="closeDetail"
      @select="selectObservation"
      @toggle-log="toggleLog"
    />
  </div>
</template>

<script>
import { observabilityApi } from "../../api/client";
import { downloadCsv, formatDuration, json } from "../../utils";
import JsonTree from "../../components/JsonTree.vue";
import TraceGraphCanvas from "./TraceGraphCanvas.vue";
import TracePeekPanel from "./TracePeekPanel.vue";
import { SEARCH_FIELDS, scanTokens, removeField, fieldValues, setDslField } from "./tracingDsl";
export default {
  components: { JsonTree, TraceGraphCanvas, TracePeekPanel },
  props: { refreshKey: { type: Number, default: 0 }, dateRange: { type: String, default: "30d" } },
  data: () => {
    const params = new URLSearchParams(window.location.search),
      initial = params.get("filter") || "";
    return {
      search: initial,
      searchDraft: "",
      searchFocused: false,
      filterSearch: "",
      environment: fieldValues(initial, "environment"),
      types: fieldValues(initial, "type"),
      rootValues: fieldValues(initial, "root"),
      filterModes: { environment: "select", type: "select", root: "select" },
      filterDrafts: {
        environment: "",
        type: "",
        root: "",
        traceName: "",
        name: "",
        tags: "",
        status: "",
        model: "",
        modelId: "",
        prompt: "",
        metadata: ""
      },
      open: { environment: true, type: true, root: true },
      filtersVisible: window.innerWidth > 760,
      view: "table",
      detailMode: "data",
      navigationMode: "tree",
      detailTab: "preview",
      detailFormat: "formatted",
      selectedId: "",
      treeSearch: "",
      logSearch: "",
      expandedIds: [],
      facetOptions: { environment: [], type: [], root: [] },
      pulse: [],
      dense: true,
      page: 0,
      pageSize: 50,
      pageData: null,
      loading: false,
      error: null,
      controller: null,
      quick: null,
      columnsOpen: false,
      savedOpen: false,
      selectedIds: [],
      detail: null,
      visibleColumns: ["startTime", "type", "name", "traceName", "input", "output"],
      collapsedFilters: {
        traceName: false,
        name: false,
        tags: false,
        status: false,
        model: false,
        modelId: false,
        prompt: false,
        metadata: false
      },
      desktopNavMode: "tree",
      savedViews: JSON.parse(localStorage.getItem("langfuse-tracing-saved-views") || "[]"),
      sortBy: "TIMESTAMP"
    };
  },
  computed: {
    items() {
      return (this.pageData?.items || []).filter(Boolean);
    },
    collapsedFilterNames() {
      return Object.keys(this.collapsedFilters);
    },
    filterChips() {
      return scanTokens(this.search).map((token, index) => {
        const match = token.raw.match(/^(-?[^:]+:)(.*)$/);
        return {
          id: "filter-" + index,
          expression: token.raw,
          chipKey: match ? match[1] : "",
          chipValue: match ? match[2] : token.raw,
          start: token.start,
          end: token.end
        };
      });
    },
    searchSuggestions() {
      const query = (this.searchDraft.split(/\s/).pop() || "").replace(/:.*/, "").toLowerCase();
      if (this.searchDraft.includes(":")) return [];
      return SEARCH_FIELDS.filter(
        field => !query || field[0].toLowerCase().includes(query) || field[1].toLowerCase().includes(query)
      )
        .slice(0, 20)
        .map(field => ({ key: field[0], label: field[1] }));
    },
    searchIssue() {
      const combined = (this.search + " " + this.searchDraft).trim();
      if (!combined) return null;
      const quotes = (combined.match(/"/g) || []).length;
      if (quotes % 2 !== 0) return "Unclosed quote";
      let depth = 0;
      for (const ch of combined) {
        if (ch === "(") depth++;
        if (ch === ")") depth--;
        if (depth < 0) return "Unexpected closing parenthesis";
      }
      if (depth !== 0) return "Unclosed parenthesis";
      return null;
    },
    c() {
      return this.$locale === "zh"
        ? {
            title: "链路追踪",
            searchHint: "搜索 — 例如 level:ERROR、-env:dev、latency:>2、scores.accuracy:>0.8",
            quality: "质量",
            slow: "慢请求",
            views: "我的视图",
            table: "表格",
            chart: "图表",
            columns: "列",
            filters: "筛选",
            clearAll: "清除全部筛选",
            searchFilters: "搜索筛选项",
            environment: "环境",
            select: "选择",
            text: "文本",
            apply: "应用",
            type: "类型",
            root: "是否根观测",
            enterValue: "输入值…",
            backendError: "后端错误",
            loading: "正在加载数据…",
            chartTitle: "观测趋势",
            chartHint: "所选时间范围内的观测数据",
            total: "总计",
            rows: "每页行数",
            rowsShort: "行数",
            page: "页码",
            input: "输入",
            output: "输出",
            metadata: "元数据",
            denseTitle: "紧凑行高",
            normalTitle: "普通行高",
            exportTitle: "导出本页 CSV",
            filterDimension: "选择筛选维度",
            clearBtn: "清除全部"
          }
        : {
            title: "Tracing",
            searchHint: "Search — e.g. level:ERROR, -env:dev, latency:>2, scores.accuracy:>0.8",
            quality: "Quality",
            slow: "Slow",
            views: "My Views",
            table: "Table",
            chart: "Chart",
            columns: "Columns",
            filters: "Filters",
            clearAll: "Clear all filters",
            searchFilters: "Search filters",
            environment: "Environment",
            select: "Select",
            text: "Text",
            apply: "Apply",
            type: "Type",
            root: "Is Root Observation",
            enterValue: "Enter value…",
            backendError: "Backend error",
            loading: "Loading data…",
            chartTitle: "Observation trend",
            chartHint: "Observations in the selected time range",
            total: "Total",
            rows: "Rows per page",
            rowsShort: "Rows",
            page: "Page",
            input: "Input",
            output: "Output",
            metadata: "Metadata",
            denseTitle: "Compact row height",
            normalTitle: "Normal row height",
            exportTitle: "Export page CSV",
            filterDimension: "Select filter dimension",
            clearBtn: "Clear all"
          };
    },
    allColumns() {
      return [
        { key: "startTime", label: "Start Time" },
        { key: "type", label: "Type" },
        { key: "name", label: "Name" },
        { key: "traceName", label: "Trace Name" },
        { key: "input", label: "Input" },
        { key: "output", label: "Output" },
        { key: "metadata", label: "Metadata" },
        { key: "level", label: "Level" },
        { key: "latency", label: "Latency" },
        { key: "model", label: "Model" }
      ];
    },
    activeColumns() {
      return this.allColumns.filter(x => this.visibleColumns.includes(x.key));
    },
    environmentOptions() {
      return this.facetOptions.environment.length ? this.facetOptions.environment : this.countOptions("environment");
    },
    typeOptions() {
      return this.facetOptions.type.length ? this.facetOptions.type : this.countOptions("type");
    },
    rootOptions() {
      return this.facetOptions.root.length
        ? this.facetOptions.root
        : [
            { value: "true", count: this.items.filter(item => !item.parentObservationId).length },
            { value: "false", count: this.items.filter(item => item.parentObservationId).length }
          ];
    },
    rangeQuery() {
      const durations = { "24h": 86400000, "7d": 604800000, "30d": 2592000000, "90d": 7776000000 };
      const duration = durations[this.dateRange] || durations["30d"];
      return { fromTimestamp: new Date(Date.now() - duration).toISOString(), toTimestamp: new Date().toISOString() };
    },
    pulseBucket() {
      return this.dateRange === "24h" ? "HOUR" : this.dateRange === "90d" ? "WEEK" : "DAY";
    },
    pulseValues() {
      return this.pulse.map(point => Number(point.count || 0));
    },
    maxPulseValue() {
      return Math.max(0, ...this.pulseValues);
    },
    bars() {
      const max = this.maxPulseValue || 1;
      return this.pulseValues.map(value => (value ? Math.max(3, (value / max) * 100) : 0));
    },
    quickTitle() {
      return this.quick === "quality" ? this.c.quality : this.c.slow;
    },
    quickPresets() {
      const e = this.$locale === "zh";
      return this.quick === "quality"
        ? [
            {
              label: e ? "仅错误" : "Errors Only",
              hint: e ? "仅查看失败的观测" : "Focus on failed observations",
              icon: "el-icon-warning-outline",
              search: "level:ERROR"
            },
            {
              label: e ? "检查生成输出" : "Review generations",
              hint: e ? "查看模型生成质量" : "Review LLM outputs",
              icon: "el-icon-view",
              type: "GENERATION"
            }
          ]
        : this.quick === "slow"
          ? [
              {
                label: e ? "延迟超过 10 秒" : "Latency over 10s",
                hint: e ? "筛选慢请求" : "Filter slow observations",
                icon: "el-icon-timer",
                search: "latency:>10",
                sort: "LATENCY"
              }
            ]
          : [];
    }
  },
  watch: {
    refreshKey() {
      this.load();
    },
    dateRange() {
      this.page = 0;
      this.load();
    },
    page() {
      this.load();
    },
    pageSize() {
      this.page = 0;
      this.load();
    },
    search() {
      this.environment = fieldValues(this.search, "environment");
      this.types = fieldValues(this.search, "type");
      this.rootValues = fieldValues(this.search, "root");
      this.page = 0;
      this.syncFilterUrl();
      this.load();
    },
    savedViews: {
      deep: true,
      handler(value) {
        localStorage.setItem("langfuse-tracing-saved-views", JSON.stringify(value));
      }
    },
    environment() {
      const next = this.withFacet("environment", this.environment);
      if (next !== this.search) this.search = next;
    },
    types() {
      const next = this.withFacet("type", this.types);
      if (next !== this.search) this.search = next;
    },
    rootValues() {
      const next = this.withFacet("root", this.rootValues);
      if (next !== this.search) this.search = next;
    }
  },
  mounted() {
    this.filtersVisible = !window.matchMedia("(max-width: 760px)").matches;
    this.load();
    window.addEventListener("keydown", this.onKeydown);
    document.addEventListener("click", this.onDocClick);
  },
  beforeDestroy() {
    this.controller?.abort();
    window.removeEventListener("keydown", this.onKeydown);
    document.removeEventListener("click", this.onDocClick);
  },
  methods: {
    formatDuration,
    json,
    number(value) {
      return Number(value || 0).toLocaleString();
    },
    matches(name) {
      return !this.filterSearch || this.labelFor(name).toLowerCase().includes(this.filterSearch.toLowerCase());
    },
    labelFor(name) {
      const labels = {
        traceName: "Trace Name",
        name: "Name",
        tags: "Trace Tags",
        status: "Status",
        model: "Provided Model Name",
        modelId: "Model ID",
        prompt: "Prompt Name",
        metadata: "Metadata"
      };
      return labels[name] || name;
    },
    countOptions(field) {
      const values = {};
      this.items.forEach(item => {
        const value = item[field] || "default";
        values[value] = (values[value] || 0) + 1;
      });
      return Object.entries(values).map(([value, count]) => ({ value, count }));
    },
    async load() {
      this.controller?.abort();
      this.controller = new AbortController();
      const signal = this.controller.signal;
      this.loading = true;
      this.error = null;
      try {
        const traceId = fieldValues(this.search, "traceId")[0] || fieldValues(this.search, "trace")[0] || "",
          query = {
            ...this.rangeQuery,
            search: this.search,
            traceId,
            sortBy: this.sortBy || "TIMESTAMP",
            direction: "DESC",
            page: this.page,
            size: this.pageSize
          };
        const [pageData, pulse] = await Promise.all([
          observabilityApi.getObservations(query, signal),
          observabilityApi.getObservationPulse(query, this.pulseBucket, signal)
        ]);
        this.pageData = pageData;
        this.pulse = pulse;
        this.loadFacets(query, signal);
      } catch (error) {
        if (error.name !== "AbortError") this.error = error.message;
      } finally {
        if (!signal.aborted) this.loading = false;
      }
    },
    async loadFacets(query, signal) {
      try {
        const [environment, type, root] = await Promise.all([
          observabilityApi.getObservationFacets("ENVIRONMENT", query, signal),
          observabilityApi.getObservationFacets("TYPE", query, signal),
          observabilityApi.getObservationFacets("ROOT", query, signal)
        ]);
        this.facetOptions = { environment, type, root };
      } catch (error) {
        if (error.name !== "AbortError") this.facetOptions = { environment: [], type: [], root: [] };
      }
    },
    withFacet(field, list) {
      if (!list.length) return removeField(this.search, field);
      return setDslField(this.search, field, list.length === 1 ? list[0] : "(" + list.join(" OR ") + ")");
    },
    toggleCollapsed(name) {
      this.$set(this.collapsedFilters, name, !this.collapsedFilters[name]);
    },
    setFilterMode(field, mode) {
      this.$set(this.filterModes, field, mode);
    },
    applyFilterDraft(field) {
      const queryField = { status: "level", prompt: "prompt" }[field] || field,
        value = String(this.filterDrafts[field] || "").trim();
      this.search = setDslField(this.search, queryField, value);
      this.$set(this.filterDrafts, field, "");
    },
    applySearch() {
      this.commitSearchDraft();
    },
    commitOnBlur() {
      setTimeout(() => {
        if (this.searchFocused) this.commitSearchDraft();
        this.searchFocused = false;
      }, 100);
    },
    commitSearchDraft() {
      const draft = this.searchDraft.replace(/\b(traceId|trace):\s+/gi, "$1:").trim();
      if (!draft) return;
      let next = this.search;
      scanTokens(draft).forEach(token => {
        const match = token.raw.match(/^-?([^:]+):/);
        if (match) next = removeField(next, match[1]);
      });
      this.search = (next + " " + draft).trim();
      this.searchDraft = "";
      this.searchFocused = false;
    },
    selectSearchField(field) {
      this.searchDraft = field + ":";
      this.$nextTick(() => this.$refs.searchInput.focus());
    },
    removeFilterChip(chip) {
      let start = chip.start,
        end = chip.end;
      while (end < this.search.length && /\s/.test(this.search[end])) end++;
      if (end === chip.end) while (start > 0 && /\s/.test(this.search[start - 1])) start--;
      this.search = (this.search.slice(0, start) + this.search.slice(end)).replace(/\s+/g, " ").trim();
    },
    removeLastChip(event) {
      if (!this.searchDraft && this.filterChips.length) {
        event.preventDefault();
        this.removeFilterChip(this.filterChips[this.filterChips.length - 1]);
      }
    },
    syncFilterUrl() {
      const params = new URLSearchParams(window.location.search);
      this.search ? params.set("filter", this.search) : params.delete("filter");
      window.history.replaceState(null, "", window.location.pathname + "?" + params.toString());
    },
    clearFilters() {
      this.search = "";
      this.searchDraft = "";
      this.environment = [];
      this.types = [];
      this.rootValues = [];
      Object.keys(this.filterDrafts).forEach(key => this.$set(this.filterDrafts, key, ""));
      this.sortBy = "TIMESTAMP";
      this.quick = null;
    },
    toggleQuick(id) {
      this.quick = this.quick === id ? null : id;
      this.columnsOpen = false;
    },
    usePreset(preset) {
      if (preset.search) this.search = preset.search;
      else if (preset.type) this.search = setDslField(this.search, "type", preset.type);
      this.sortBy = preset.sort || "TIMESTAMP";
      this.quick = null;
    },
    toggleAll(event) {
      this.selectedIds = event.target.checked ? this.items.map(item => item.id) : [];
    },
    traceName(item) {
      return item.traceName || item.name || item.traceId;
    },
    preview(value) {
      if (value == null) return "";
      const text = typeof value === "string" ? value : JSON.stringify(value);
      return text.length > 80 ? text.slice(0, 77) + "…" : text;
    },
    formatTimestamp(value) {
      const date = new Date(value),
        pad = number => String(number).padStart(2, "0");
      return (
        date.getFullYear() +
        "-" +
        pad(date.getMonth() + 1) +
        "-" +
        pad(date.getDate()) +
        " " +
        pad(date.getHours()) +
        ":" +
        pad(date.getMinutes()) +
        ":" +
        pad(date.getSeconds())
      );
    },
    typeIcon(type) {
      const icons = {
        SPAN: "↔",
        GENERATION: "♧",
        EVENT: "◇",
        AGENT: "◎",
        TOOL: "⌘",
        CHAIN: "⛓",
        RETRIEVER: "⌕",
        EVALUATOR: "✓",
        EMBEDDING: "◫",
        GUARDRAIL: "◇"
      };
      return icons[type] || "◇";
    },
    async openDetail(item) {
      try {
        this.detail = await observabilityApi.getTraceView(item.traceId);
        this.selectedId = item.id;
        this.detailMode = "data";
        this.detailTab = "preview";
        this.detailFormat = "formatted";
        const params = new URLSearchParams(window.location.search);
        params.set("peek", item.id);
        params.set("observation", item.id);
        params.set("traceId", item.traceId);
        window.history.replaceState(null, "", window.location.pathname + "?" + params.toString());
      } catch (error) {
        this.error = error.message;
      }
    },
    closeDetail() {
      this.detail = null;
      const params = new URLSearchParams(window.location.search);
      ["peek", "observation", "traceId", "timestamp"].forEach(key => params.delete(key));
      window.history.replaceState(null, "", window.location.pathname + "?" + params.toString());
    },
    onKeydown(event) {
      if (event.key === "Escape" && this.detail) this.closeDetail();
    },
    onDocClick() {
      this.columnsOpen = false;
      this.quick = null;
      this.savedOpen = false;
    },
    selectObservation(id) {
      this.selectedId = id;
      const traceId = this.detail ? this.detail.trace.id : "";
      const params = new URLSearchParams(window.location.search);
      params.set("peek", id);
      params.set("observation", id);
      if (traceId) params.set("traceId", traceId);
      window.history.replaceState(null, "", window.location.pathname + "?" + params.toString());
    },
    toggleLog(id) {
      this.expandedIds = this.expandedIds.includes(id)
        ? this.expandedIds.filter(value => value !== id)
        : this.expandedIds.concat(id);
    },
    saveCurrentView() {
      const name = window.prompt("Name this view");
      if (!name || !name.trim()) return;
      this.savedViews.push({
        id: Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
        name: name.trim(),
        search: this.search,
        sortBy: this.sortBy,
        view: this.view,
        columns: this.visibleColumns.slice()
      });
      this.savedOpen = false;
    },
    applySavedView(saved) {
      this.search = saved.search || "";
      this.searchDraft = "";
      this.sortBy = saved.sortBy || "TIMESTAMP";
      this.view = saved.view || "table";
      if (saved.columns) this.visibleColumns = saved.columns.slice();
      this.page = 0;
      this.savedOpen = false;
    },
    deleteSavedView(id) {
      this.savedViews = this.savedViews.filter(v => v.id !== id);
    },
    exportSelected() {
      const rows = this.items.filter(item => this.selectedIds.includes(item.id));
      downloadCsv(
        "langfuse-observations-selected.csv",
        ["observationId", "traceId", "name", "startTime", "type", "status", "model", "latencyMs"],
        rows.map(item => [
          item.id,
          item.traceId,
          item.name,
          item.startTime,
          item.type,
          item.level,
          item.model,
          item.latencyMs
        ])
      );
    },
    exportPage() {
      downloadCsv(
        "langfuse-observations-page.csv",
        ["observationId", "traceId", "name", "startTime", "type", "status", "model", "latencyMs"],
        this.items.map(item => [
          item.id,
          item.traceId,
          item.name,
          item.startTime,
          item.type,
          item.level,
          item.model,
          item.latencyMs
        ])
      );
    }
  }
};
</script>
