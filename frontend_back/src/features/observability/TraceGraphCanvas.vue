<template>
  <div class="trace-graph-canvas">
    <div class="trace-graph-controls">
      <span>{{ graph.nodes.length - 2 }} unique steps</span>
      <el-button title="缩小" size="mini" circle aria-label="Zoom out" @click="zoom = Math.max(0.55, zoom - 0.15)">−</el-button>
      <el-button title="还原" size="mini" circle aria-label="Fit graph" @click="zoom = 1; panX = 0; panY = 0">⊞</el-button>
      <el-button title="放大" size="mini" circle aria-label="Zoom in" @click="zoom = Math.min(1.6, zoom + 0.15)">+</el-button>
    </div>
    <div
      class="trace-graph-viewport"
      :class="{ dragging }"
      @mousedown="onDragStart"
    >
      <div
        class="trace-graph-stage"
        :style="{ width: width + 'px', height: height + 'px', transform: 'translate(' + panX + 'px,' + panY + 'px) scale(' + zoom + ')' }"
      >
        <svg class="trace-graph-edges" :width="width" :height="height" aria-hidden="true">
          <defs>
            <marker
              id="trace-arrow"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerWidth="6"
              markerHeight="6"
              orient="auto-start-reverse"
            >
              <path d="M 0 0 L 10 5 L 0 10 z" />
            </marker>
          </defs>
          <path v-for="edge in graph.edges" :key="edge.id" :d="edgePath(edge)" marker-end="url(#trace-arrow)" />
        </svg>
        <button
          v-for="node in graph.nodes"
          :key="node.id"
          :class="[
            'trace-graph-node',
            node.type.toLowerCase(),
            { selected: node.observationIds.includes(selectedId), system: isSystem(node.id) }
          ]"
          :style="{ left: node.x + 'px', top: node.y + 'px', width: NODE_WIDTH + 'px', height: NODE_HEIGHT + 'px' }"
          :disabled="isSystem(node.id)"
          :title="node.label"
          @click="selectNode(node)"
        >
          <span>{{ node.label }}</span
          ><small>{{
            isSystem(node.id)
              ? ""
              : node.type + (node.observationIds.length > 1 ? " · " + node.observationIds.length + " calls" : "")
          }}</small>
        </button>
      </div>
    </div>
  </div>
</template>

<script>
const NODE_WIDTH = 164;
const NODE_HEIGHT = 52;
const COLUMN_GAP = 72;
const ROW_GAP = 28;
const START_ID = "__langfuse_start__";
const END_ID = "__langfuse_end__";

function observationDepth(item, byId) {
  let depth = 0,
    parent = item.parentObservationId;
  const seen = new Set();
  while (parent && !seen.has(parent) && depth < 32) {
    seen.add(parent);
    depth++;
    const match = byId.get(parent);
    parent = match ? match.parentObservationId : null;
  }
  return depth;
}

function layoutNodes(nodes) {
  const byDepth = new Map();
  nodes.forEach(node => {
    const list = byDepth.get(node.depth) || [];
    list.push(node);
    byDepth.set(node.depth, list);
  });
  const maxRows = Math.max(1, ...Array.from(byDepth.values(), items => items.length));
  const totalHeight = Math.max(300, maxRows * (NODE_HEIGHT + ROW_GAP) + 64);
  return nodes.map(node => {
    const siblings = byDepth.get(node.depth) || [];
    const index = siblings.findIndex(item => item.id === node.id);
    const groupHeight = siblings.length * NODE_HEIGHT + Math.max(0, siblings.length - 1) * ROW_GAP;
    return {
      ...node,
      x: 34 + node.depth * (NODE_WIDTH + COLUMN_GAP),
      y: Math.max(28, (totalHeight - groupHeight) / 2 + index * (NODE_HEIGHT + ROW_GAP))
    };
  });
}

function buildAggregatedGraph(observations) {
  const sorted = [...observations].filter(Boolean).sort((a, b) => a.startTime.localeCompare(b.startTime) || a.id.localeCompare(b.id));
  const byId = new Map(sorted.map(item => [item.id, item]));
  const groups = new Map();
  sorted.forEach(item => {
    const list = groups.get(item.name) || [];
    list.push(item);
    groups.set(item.name, list);
  });
  const groupedNodes = Array.from(groups.entries()).map(([name, items]) => ({
    id: "group:" + name,
    label: name,
    type: items[0].type,
    observationIds: items.map(item => item.id),
    depth: Math.min(...items.map(item => observationDepth(item, byId))) + 1
  }));
  const maxDepth = Math.max(1, ...groupedNodes.map(node => node.depth));
  const nodes = layoutNodes([
    { id: START_ID, label: "Start", type: "SYSTEM_START", observationIds: [], depth: 0 },
    ...groupedNodes,
    { id: END_ID, label: "End", type: "SYSTEM_END", observationIds: [], depth: maxDepth + 1 }
  ]);
  const edgesById = new Map();
  sorted.forEach(item => {
    const parent = item.parentObservationId ? byId.get(item.parentObservationId) : null;
    const source = parent ? "group:" + parent.name : START_ID;
    const target = "group:" + item.name;
    const id = source + "-" + target;
    edgesById.set(id, { id, source, target });
  });
  const sourceGroups = new Set(Array.from(edgesById.values(), edge => edge.source));
  groupedNodes
    .filter(node => !sourceGroups.has(node.id))
    .forEach(node => {
      const id = node.id + "-" + END_ID;
      edgesById.set(id, { id, source: node.id, target: END_ID });
    });
  return { nodes, edges: Array.from(edgesById.values()) };
}

export default {
  props: {
    observations: { type: Array, required: true },
    selectedId: { type: String, default: "" }
  },
  data: () => ({ zoom: 1, panX: 0, panY: 0, dragging: false, dragStartX: 0, dragStartY: 0, panStartX: 0, panStartY: 0, NODE_WIDTH, NODE_HEIGHT }),
  computed: {
    graph() {
      return buildAggregatedGraph(this.observations);
    },
    nodeById() {
      return new Map(this.graph.nodes.map(node => [node.id, node]));
    },
    width() {
      return Math.max(620, ...this.graph.nodes.map(node => node.x + NODE_WIDTH + 34));
    },
    height() {
      return Math.max(300, ...this.graph.nodes.map(node => node.y + NODE_HEIGHT + 34));
    }
  },
  mounted() {
    this._onDragMove = this.onDragMove.bind(this);
    this._onDragEnd = this.onDragEnd.bind(this);
    document.addEventListener("mousemove", this._onDragMove);
    document.addEventListener("mouseup", this._onDragEnd);
  },
  beforeDestroy() {
    document.removeEventListener("mousemove", this._onDragMove);
    document.removeEventListener("mouseup", this._onDragEnd);
  },
  methods: {
    onDragStart(e) {
      if (e.target.closest(".trace-graph-node") || e.target.closest(".el-button")) return;
      this.dragging = true;
      this.dragStartX = e.clientX;
      this.dragStartY = e.clientY;
      this.panStartX = this.panX;
      this.panStartY = this.panY;
      e.preventDefault();
    },
    onDragMove(e) {
      if (!this.dragging) return;
      this.panX = this.panStartX + (e.clientX - this.dragStartX);
      this.panY = this.panStartY + (e.clientY - this.dragStartY);
    },
    onDragEnd() {
      this.dragging = false;
    },
    isSystem(id) {
      return id === START_ID || id === END_ID;
    },
    selectNode(node) {
      if (!node.observationIds.length) return;
      const current = node.observationIds.indexOf(this.selectedId);
      this.$emit("select", node.observationIds[(current + 1) % node.observationIds.length]);
    },
    edgePath(edge) {
      const source = this.nodeById.get(edge.source);
      const target = this.nodeById.get(edge.target);
      if (!source || !target) return "";
      if (source.id === target.id) {
        const x = source.x + NODE_WIDTH - 18,
          y = source.y;
        return (
          "M " +
          x +
          " " +
          (y + 5) +
          " C " +
          (x + 54) +
          " " +
          (y - 42) +
          ", " +
          (x + 54) +
          " " +
          (y + NODE_HEIGHT + 42) +
          ", " +
          x +
          " " +
          (y + NODE_HEIGHT - 5)
        );
      }
      const startX = source.x + NODE_WIDTH,
        startY = source.y + NODE_HEIGHT / 2;
      const endX = target.x,
        endY = target.y + NODE_HEIGHT / 2;
      const bend = Math.max(38, (endX - startX) / 2);
      return (
        "M " +
        startX +
        " " +
        startY +
        " C " +
        (startX + bend) +
        " " +
        startY +
        ", " +
        (endX - bend) +
        " " +
        endY +
        ", " +
        endX +
        " " +
        endY
      );
    }
  }
};
</script>
