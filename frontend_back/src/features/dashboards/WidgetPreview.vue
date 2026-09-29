<template>
  <div class="widget-preview">
    <el-button class="preview-trigger" size="mini" :disabled="loading" @click="togglePreview">
      {{ loading ? "Loading metric…" : points ? "Hide preview" : "Preview data" }}
    </el-button>
    <span v-if="error" class="widget-preview-error">Metric unavailable</span>
    <div v-if="points" class="widget-mini-chart">
      <span v-if="!points.length">No data in the last 30 days</span>
      <div
        v-for="(point, index) in displayPoints"
        :key="`${point.bucket}-${point.dimension}-${index}`"
        class="widget-mini-row"
        :title="`${point.dimension || point.bucket || 'Total'}: ${point.value}`"
      >
        <span>{{ point.dimension || (point.bucket ? formatBucketDate(point.bucket) : "Total") }}</span>
        <i :style="{ width: `${getBarWidth(point.value)}%` }" />
        <b>{{ formatValue(point.value) }}</b>
      </div>
    </div>
  </div>
</template>

<script>
import { observabilityApi } from "../../api/client";

export default {
  name: "WidgetPreview",
  props: {
    widget: {
      type: Object,
      required: true
    }
  },
  data() {
    return {
      points: null,
      loading: false,
      error: null
    };
  },
  computed: {
    displayPoints() {
      return this.points ? this.points.slice(-14) : [];
    },
    maxValue() {
      if (!this.points || !this.points.length) return 1;
      return Math.max(1, ...this.points.map(p => Number(p.value)));
    }
  },
  methods: {
    async togglePreview() {
      if (this.points) {
        this.points = null;
        return;
      }
      this.loading = true;
      this.error = null;
      try {
        this.points = await observabilityApi.getDashboardWidgetMetrics(this.widget.id);
      } catch (e) {
        this.error = e.message;
      } finally {
        this.loading = false;
      }
    },
    getBarWidth(value) {
      return Math.max(2, (Number(value) / this.maxValue) * 100);
    },
    formatValue(value) {
      return Number(value).toLocaleString(undefined, { maximumFractionDigits: 4 });
    },
    formatBucketDate(bucket) {
      return new Date(bucket).toLocaleDateString();
    }
  }
};
</script>
