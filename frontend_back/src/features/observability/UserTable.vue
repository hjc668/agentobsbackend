<template>
  <div class="user-table-wrap">
    <EmptyState
      v-if="!items.length"
      title="No users found"
      description="Set a user ID when tracing to populate this view."
      icon-class="el-icon-user"
    />
    <div v-else class="table-wrap">
      <el-table :data="items" @row-click="row => $emit('open', row.id)" style="width: 100%" size="mini">
        <el-table-column prop="id" label="User ID" min-width="150">
          <template slot-scope="{ row }"
            ><strong class="mono-link">{{ row.id }}</strong></template
          >
        </el-table-column>
        <el-table-column label="Environment" min-width="110">
          <template slot-scope="{ row }">
            <el-tag v-if="row.environment" size="mini">{{ row.environment }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="First Event" min-width="130">
          <template slot-scope="{ row }">{{ formatDate(row.firstEvent) }}</template>
        </el-table-column>
        <el-table-column label="Last Event" min-width="130">
          <template slot-scope="{ row }">{{ formatDate(row.lastEvent) }}</template>
        </el-table-column>
        <el-table-column label="Total Events" width="100">
          <template slot-scope="{ row }">{{ (row.observationCount + row.traceCount).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="Total Traces" width="100">
          <template slot-scope="{ row }">{{ row.traceCount.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="Total Tokens" width="100">
          <template slot-scope="{ row }">{{ row.totalTokens.toLocaleString() }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script>
import { formatDate } from "../../utils/format";
import EmptyState from "../../components/EmptyState.vue";

export default {
  name: "UserTable",
  components: { EmptyState },
  props: {
    items: {
      type: Array,
      default: () => []
    }
  },
  methods: {
    formatDate
  }
};
</script>

<style lang="scss" scoped>
.el-table {
  font-size: 10px;
}
</style>
