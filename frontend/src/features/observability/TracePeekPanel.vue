<template>
  <div class="trace-peek-backdrop">
    <aside class="trace-peek" role="dialog" aria-modal="true">
      <div class="peek-drag-handle" />
      <header class="peek-header">
        <div>
          <span class="trace-type-icon">{{ selectedObservation ? typeIcon(selectedObservation.type) : "↔" }}</span
          ><strong
            >{{ selectedObservation ? selectedObservation.name : detail.trace.name }}:
            <span>{{ detail.trace.id }}</span></strong
          >
        </div>
        <div class="peek-header-actions">
          <el-button size="mini" :disabled="selectedIndex <= 0" @click="navigateObservation(-1)">↑</el-button
          ><el-button
            size="mini"
            :disabled="selectedIndex >= detail.observations.length - 1"
            @click="navigateObservation(1)"
            >↓</el-button
          ><el-button size="mini" circle aria-label="Close" @click="$emit('close')">×</el-button>
        </div>
      </header>
      <nav class="peek-mobile-navigation">
        <el-radio-group :value="detailMode" size="mini" @input="$emit('update:detailMode', $event)"
          ><el-radio-button label="tree" title="Tree"><i class="el-icon-s-operation" /></el-radio-button
          ><el-radio-button label="timeline" title="Timeline"><i class="el-icon-time" /></el-radio-button
          ><el-radio-button v-if="detail && detail.observations.length > 1" label="graph" title="Graph"
            ><i class="el-icon-share" /></el-radio-button
          ><el-radio-button label="data" title="Data"><i class="el-icon-document" /></el-radio-button></el-radio-group
        >
      </nav>
      <div :class="['peek-body', 'mobile-' + detailMode, { 'inspector-is-collapsed': inspectorCollapsed }]">
        <section class="peek-tree">
            <div class="peek-tree-toolbar">
              <el-radio-group :value="desktopNavMode" size="mini" @input="$emit('update:desktopNavMode', $event)"
                ><el-radio-button label="tree" title="Tree"><i class="el-icon-s-operation" /></el-radio-button
                ><el-radio-button label="timeline" title="Timeline"><i class="el-icon-time" /></el-radio-button
                ><el-radio-button v-if="detail && detail.observations.length > 1" label="graph" title="Graph"
                  ><i class="el-icon-share" /></el-radio-button>
              </el-radio-group>
              <el-input
                v-if="desktopNavMode === 'tree'"
                :value="treeSearch"
                size="mini"
                placeholder="Search"
                class="peek-tree-search"
                @input="$emit('update:treeSearch', $event)"
              />
            </div>
            <TraceGraphCanvas
              v-if="desktopNavMode === 'graph'"
              :observations="detail.observations"
              :selected-id="selectedId"
              @select="id => $emit('select', id)"
            />
            <div v-else-if="desktopNavMode === 'timeline'" class="trace-timeline">
              <header><strong>Name</strong><span>Duration</span></header>
              <button
                v-for="item in detail.observations"
                :key="item.id"
                :class="{ active: selectedId === item.id }"
                @click="$emit('select', item.id)"
              >
                <span
                  >{{ typeIcon(item.type) }} <b>{{ item.name }}</b></span
                ><i><em :style="timelineStyle(item)" /></i><small>{{ formatDuration(item.latencyMs) }}</small>
              </button>
            </div>
            <el-tree
              v-else
              ref="tree"
              :data="treeData"
              :props="{ children: 'children', label: 'label' }"
              node-key="id"
              :expand-on-click-node="false"
              default-expand-all
              :filter-node-method="filterNode"
              class="peek-el-tree"
            >
              <template slot-scope="{ node, data }">
                <span class="peek-tree-node" @click="$emit('select', data.item.id)">
                  <span :class="'type-dot ' + data.item.type.toLowerCase()" />
                  <span class="peek-tree-node-info">
                    <strong>{{ data.item.name }}</strong>
                    <small>{{ data.item.type }}{{ data.item.model ? " · " + data.item.model : "" }}</small>
                  </span>
                  <b class="peek-tree-node-duration">{{ formatDuration(data.item.latencyMs) }}</b>
                </span>
              </template>
            </el-tree>
            <div class="peek-graph-label">{{ detail.observations.length }} observations</div>
        </section>
        <section class="peek-mobile-pane peek-mobile-tree">
          <el-tree
            ref="mobileTree"
            :data="treeData"
            :props="{ children: 'children', label: 'label' }"
            node-key="id"
            :expand-on-click-node="false"
            default-expand-all
            :filter-node-method="filterNode"
            class="peek-el-tree"
          >
            <template slot-scope="{ node, data }">
              <span class="peek-tree-node" @click="$emit('select', data.item.id)">
                <span :class="'type-dot ' + data.item.type.toLowerCase()" />
                <span class="peek-tree-node-info">
                  <strong>{{ data.item.name }}</strong>
                  <small>{{ data.item.type }}</small>
                </span>
                <b class="peek-tree-node-duration">{{ formatDuration(data.item.latencyMs) }}</b>
              </span>
            </template>
          </el-tree>
        </section>
        <section class="peek-mobile-pane peek-mobile-timeline">
          <div class="trace-timeline">
            <button v-for="item in detail.observations" :key="item.id" @click="$emit('select', item.id)">
              <span
                >{{ typeIcon(item.type) }} <b>{{ item.name }}</b></span
              ><i><em :style="timelineStyle(item)" /></i><small>{{ formatDuration(item.latencyMs) }}</small>
            </button>
          </div>
        </section>
        <section class="peek-mobile-pane peek-mobile-graph">
          <TraceGraphCanvas
            :observations="detail.observations"
            :selected-id="selectedId"
            @select="id => $emit('select', id)"
          />
        </section>
        <div v-if="selectedObservation" class="peek-inspector-wrap">
          <button :class="['peek-collapse-btn', 'peek-collapse-btn--inspector', { 'is-collapsed': inspectorCollapsed }]" :title="inspectorCollapsed ? 'Expand inspector' : 'Collapse inspector'" @click="inspectorCollapsed = !inspectorCollapsed">
            <i :class="inspectorCollapsed ? 'el-icon-arrow-left' : 'el-icon-arrow-right'" />
          </button>
          <section v-show="!inspectorCollapsed" class="peek-inspector">
          <header>
            <div class="peek-detail-heading">
              <span class="trace-type-icon">{{ typeIcon(selectedObservation.type) }}</span>
              <h2>{{ selectedObservation.name }}</h2>
            </div>
            <div class="peek-chips">
              <span>Latency: {{ formatDuration(selectedObservation.latencyMs) }}</span
              ><span>Env: {{ detail.trace.environment || "default" }}</span
              ><span v-if="detail.trace.version">Version: {{ detail.trace.version }}</span
              ><span v-if="selectedObservation.model">Model: {{ selectedObservation.model }}</span
              ><span
                >Tokens:
                {{ (selectedObservation.inputTokens + selectedObservation.outputTokens).toLocaleString() }}</span
              >
            </div>
          </header>
          <div class="peek-tabs">
            <el-tabs :value="detailTab" @tab-click="onTabClick" class="peek-detail-tabs">
              <el-tab-pane label="Preview" name="preview" />
              <el-tab-pane label="Log View" name="log" />
            </el-tabs>
            <span />
            <div class="peek-format-switch">
              <el-radio-group :value="detailFormat" size="mini" @input="$emit('update:detailFormat', $event)"
                ><el-radio-button label="formatted">Formatted</el-radio-button
                ><el-radio-button label="json">JSON</el-radio-button></el-radio-group
              >
            </div>
          </div>
          <div class="peek-scroll">
            <template v-if="detailTab === 'preview'"
              ><div v-if="detailFormat === 'json'" class="json-tree-panel">
                <JsonTree :value="selectedObservation" />
              </div>
              <template v-else
                ><section class="peek-value input">
                  <header><strong>Input</strong></header>
                  <pre class="json-block">{{ jsonValue(selectedObservation.input) }}</pre>
                </section>
                <section class="peek-value output">
                  <header><strong>Output</strong></header>
                  <pre class="json-block">{{ jsonValue(selectedObservation.output) }}</pre>
                </section>
                <section class="peek-value metadata-section">
                  <header><strong>Metadata</strong></header>
                  <el-table v-if="metadataRows.length" :data="metadataRows" size="mini" class="metadata-table"
                    ><el-table-column label="Path" prop="0"
                      ><template slot-scope="{ row }">{{ row[0] }}</template></el-table-column
                    ><el-table-column label="Value"
                      ><template slot-scope="{ row }">{{ inlineJson(row[1]) }}</template></el-table-column
                    ></el-table
                  >
                  <div v-else class="peek-empty-value">No metadata</div>
                </section></template
              ></template
            >
            <div v-else class="observation-log-view">
              <div class="log-view-toolbar">
                <label v-if="detailFormat === 'formatted'" class="log-view-search"
                  ><i class="el-icon-search" /><el-input
                    :value="logSearch"
                    size="mini"
                    placeholder="Search observations..."
                    @input="$emit('update:logSearch', $event)" /></label
                ><span v-else /><el-button size="mini" @click="toggleAllLogs">{{
                  allLogsExpanded ? "Collapse all" : "Expand all"
                }}</el-button
                ><el-button size="mini" @click="copyLogJson">Copy</el-button>
              </div>
              <div v-if="detailFormat === 'json'" class="json-tree-panel"><JsonTree :value="logJson" /></div>
              <div v-else class="log-view-table">
                <div class="log-view-row log-view-table-header">
                  <span>Observation</span><span>Depth</span><span>Start</span><span>Duration</span>
                </div>
                <div
                  v-for="item in logObservations"
                  :key="item.id"
                  :class="['log-view-item', { expanded: expandedIds.includes(item.id) }]"
                >
                  <button class="log-view-row" @click="$emit('toggle-log', item.id)">
                    <span class="log-view-observation"
                      ><i class="log-view-chevron">›</i><i class="log-view-type-badge">{{ typeIcon(item.type) }}</i
                      ><strong>{{ logName(item) }}</strong></span
                    ><span>L{{ observationDepth(item) }}</span
                    ><span>{{ relativeTime(item) }}</span
                    ><span>{{ formatDuration(item.latencyMs) }}</span>
                  </button>
                  <div v-if="expandedIds.includes(item.id)" class="log-view-expanded">
                    <JsonTree :value="{ input: item.input, output: item.output, metadata: item.metadata }" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
        </div>
      </div>
    </aside>
  </div>
</template>

<script>
import { formatDuration } from "../../utils";
import JsonTree from "../../components/JsonTree.vue";
import TraceGraphCanvas from "./TraceGraphCanvas.vue";

export default {
  name: "TracePeekPanel",
  components: { JsonTree, TraceGraphCanvas },
  props: {
    detail: { type: Object, required: true },
    selectedId: { type: String, default: "" },
    detailMode: { type: String, default: "data" },
    desktopNavMode: { type: String, default: "tree" },
    treeSearch: { type: String, default: "" },
    detailTab: { type: String, default: "preview" },
    detailFormat: { type: String, default: "formatted" },
    logSearch: { type: String, default: "" },
    expandedIds: { type: Array, default: () => [] }
  },
  data() {
    return {
      inspectorCollapsed: false
    };
  },
  computed: {
    selectedIndex() {
      return Math.max(0, this.detail.observations.findIndex(item => item.id === this.selectedId));
    },
    selectedObservation() {
      return this.detail.observations[this.selectedIndex] || null;
    },
    treeData() {
      const query = this.treeSearch.toLowerCase();
      const filtered = query
        ? this.detail.observations.filter(item =>
            (item.name + " " + item.type + " " + (item.model || "")).toLowerCase().includes(query)
          )
        : this.detail.observations;
      const childMap = {};
      filtered.forEach(item => {
        const pid = item.parentObservationId;
        if (pid) {
          if (!childMap[pid]) childMap[pid] = [];
          childMap[pid].push(item);
        }
      });
      const build = item => ({
        id: item.id,
        label: item.name,
        item,
        children: (childMap[item.id] || []).map(build)
      });
      return filtered.filter(item => !item.parentObservationId || !filtered.find(o => o.id === item.parentObservationId)).map(build);
    },
    metadataRows() {
      return this.selectedObservation ? this.flattenMetadata(this.selectedObservation.metadata) : [];
    },
    logObservations() {
      const query = this.logSearch.toLowerCase();
      return this.detail.observations
        .filter(Boolean)
        .slice()
        .sort((a, b) => new Date(a.startTime) - new Date(b.startTime))
        .filter(item => (item.name + " " + item.type + " " + item.id).toLowerCase().includes(query));
    },
    allLogsExpanded() {
      return this.logObservations.length > 0 && this.logObservations.every(item => this.expandedIds.includes(item.id));
    },
    logJson() {
      return this.detail.observations.filter(Boolean).map(item => ({
        id: item.id,
        type: item.type,
        name: this.logName(item),
        startTime: item.startTime,
        endTime: item.endTime,
        depth: this.observationDepth(item),
        input: item.input,
        output: item.output,
        metadata: item.metadata
      }));
    }
  },
  watch: {
    treeSearch(value) {
      this.$nextTick(() => {
        if (this.$refs.tree) this.$refs.tree.filter(value);
        if (this.$refs.mobileTree) this.$refs.mobileTree.filter(value);
      });
    }
  },
  methods: {
    formatDuration,
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
    filterNode(value, data) {
      if (!value) return true;
      const q = value.toLowerCase();
      return (data.item.name + " " + data.item.type + " " + (data.item.model || "")).toLowerCase().includes(q);
    },
    observationDepth(item) {
      let depth = 0,
        parent = item.parentObservationId;
      const seen = [];
      while (parent && depth < 8 && !seen.includes(parent)) {
        seen.push(parent);
        depth++;
        const match = this.detail.observations.find(candidate => candidate.id === parent);
        parent = match ? match.parentObservationId : null;
      }
      return depth;
    },
    timelineStyle(item) {
      const obs = this.detail.observations.filter(Boolean);
      const starts = obs.map(value => new Date(value.startTime).getTime()),
        start = Math.min(...starts),
        end = Math.max(
          ...obs.map(value => new Date(value.endTime || value.startTime).getTime()),
          start + 1
        ),
        span = Math.max(1, end - start),
        itemStart = new Date(item.startTime).getTime(),
        itemEnd = new Date(item.endTime || item.startTime).getTime();
      return {
        left: Math.max(0, ((itemStart - start) / span) * 100) + "%",
        width: Math.max(1.5, ((itemEnd - itemStart) / span) * 100) + "%"
      };
    },
    formatTimestamp(value) {
      const date = new Date(value),
        pad = n => String(n).padStart(2, "0");
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
    formatTimestampMs(value) {
      const date = new Date(value);
      return this.formatTimestamp(value) + "." + String(date.getMilliseconds()).padStart(3, "0");
    },
    jsonValue(value) {
      return value === undefined
        ? "undefined"
        : value === null
          ? "null"
          : typeof value === "string"
            ? value
            : JSON.stringify(value, null, 2);
    },
    inlineJson(value) {
      return value === undefined
        ? "undefined"
        : value === null
          ? "null"
          : typeof value === "string"
            ? value
            : JSON.stringify(value);
    },
    flattenMetadata(value, prefix = "") {
      if (!value || typeof value !== "object" || Array.isArray(value)) return prefix ? [[prefix, value]] : [];
      return Object.entries(value).flatMap(([key, item]) =>
        item && typeof item === "object" && !Array.isArray(item)
          ? this.flattenMetadata(item, prefix ? prefix + "." + key : key)
          : [[prefix ? prefix + "." + key : key, item]]
      );
    },
    logName(item) {
      return (item.name || item.type) + " (" + item.id.slice(0, 8) + ")";
    },
    relativeTime(item) {
      const obs = this.detail.observations.filter(Boolean);
      if (!obs.length) return "0:00";
      const start = Math.min(...obs.map(value => new Date(value.startTime).getTime())),
        milliseconds = Math.max(0, new Date(item.startTime).getTime() - start),
        seconds = Math.floor(milliseconds / 1000);
      return Math.floor(seconds / 60) + ":" + String(seconds % 60).padStart(2, "0");
    },
    onTabClick(tab) {
      this.$emit("update:detailTab", tab.name);
    },
    toggleAllLogs() {
      const ids = this.allLogsExpanded ? [] : this.logObservations.map(item => item.id);
      this.$emit("update:expandedIds", ids);
    },
    async copyLogJson() {
      const text = JSON.stringify(this.logJson, null, 2);
      try {
        if (navigator.clipboard && window.isSecureContext) {
          await navigator.clipboard.writeText(text);
        } else {
          const ta = document.createElement("textarea");
          ta.value = text;
          ta.style.position = "fixed";
          ta.style.left = "-9999px";
          ta.style.top = "-9999px";
          document.body.appendChild(ta);
          ta.focus();
          ta.select();
          document.execCommand("copy");
          document.body.removeChild(ta);
        }
        this.$message({ message: "Copied", type: "success", duration: 1500 });
      } catch (e) {
        this.$message({ message: "Copy failed", type: "error", duration: 1500 });
      }
    },
    navigateObservation(offset) {
      const index = Math.min(this.detail.observations.length - 1, Math.max(0, this.selectedIndex + offset));
      this.$emit("select", this.detail.observations[index].id);
    }
  }
};
</script>
