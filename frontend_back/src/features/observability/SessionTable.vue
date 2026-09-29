<template>
  <div class="session-table-wrap">
    <EmptyState
      v-if="!items.length"
      title="No sessions found"
      description="Add a session ID to traces to group related interactions."
      icon-class="el-icon-time"
    />
    <div v-else class="table-wrap">
      <el-table :data="items" @row-click="row => $emit('open', row.id)" style="width: 100%" size="mini">
        <el-table-column prop="id" label="Session ID" min-width="180">
          <template slot-scope="{ row }"
            ><strong class="mono-link">{{ row.id }}</strong></template
          >
        </el-table-column>
        <el-table-column label="Created at" min-width="140">
          <template slot-scope="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="User ID" min-width="120">
          <template slot-scope="{ row }">{{ row.userId || "—" }}</template>
        </el-table-column>
        <el-table-column label="Traces" width="80">
          <template slot-scope="{ row }">{{ row.traceCount.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="Observations" width="110">
          <template slot-scope="{ row }">{{ row.observationCount.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="Duration" width="100">
          <template slot-scope="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="Tokens" width="80">
          <template slot-scope="{ row }">{{ row.totalTokens.toLocaleString() }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script>
import { formatDate, formatDuration } from "../../utils/format";
import EmptyState from "../../components/EmptyState.vue";

export default {
  name: "SessionTable",
  components: { EmptyState },
  props: {
    items: {
      type: Array,
      default: () => []
    }
  },
  methods: {
    formatDate,
    formatDuration
  }
};
</script>

<style lang="scss" scoped>
.el-table {
  font-size: 10px;
}
</style>
