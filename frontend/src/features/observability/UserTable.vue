<template>
  <div class="user-table-wrap">
    <EmptyState
      v-if="!items.length"
      :title="c.noUsers"
      :description="c.noUsersHint"
      icon-class="el-icon-user"
    />
    <div v-else class="table-wrap">
      <el-table :data="items" @row-click="row => $emit('open', row.id)" style="width: 100%" size="mini">
        <el-table-column prop="id" :label="c.userId" min-width="150">
          <template slot-scope="{ row }"
            ><strong class="mono-link">{{ row.id }}</strong></template
          >
        </el-table-column>
        <el-table-column :label="c.environment" min-width="110">
          <template slot-scope="{ row }">
            <el-tag v-if="row.environment" size="mini">{{ row.environment }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column :label="c.firstEvent" min-width="130">
          <template slot-scope="{ row }">{{ formatDate(row.firstEvent) }}</template>
        </el-table-column>
        <el-table-column :label="c.lastEvent" min-width="130">
          <template slot-scope="{ row }">{{ formatDate(row.lastEvent) }}</template>
        </el-table-column>
        <el-table-column :label="c.totalEvents" width="100">
          <template slot-scope="{ row }">{{ (row.observationCount + row.traceCount).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column :label="c.totalTraces" width="100">
          <template slot-scope="{ row }">{{ row.traceCount.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column :label="c.totalTokens" width="100">
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
  computed: {
    c() {
      return this.$locale === "zh"
        ? {
            noUsers: "未找到用户",
            noUsersHint: "在链路中设置用户 ID 后，此视图将显示数据。",
            userId: "用户 ID",
            environment: "环境",
            firstEvent: "首次事件",
            lastEvent: "最近事件",
            totalEvents: "事件总数",
            totalTraces: "链路总数",
            totalTokens: "Token 总数"
          }
        : {
            noUsers: "No users found",
            noUsersHint: "Set a user ID when tracing to populate this view.",
            userId: "User ID",
            environment: "Environment",
            firstEvent: "First Event",
            lastEvent: "Last Event",
            totalEvents: "Total Events",
            totalTraces: "Total Traces",
            totalTokens: "Total Tokens"
          };
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
