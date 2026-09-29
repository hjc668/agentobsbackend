<template>
  <div>
    <header class="page-header">
      <div>
        <h1>{{ c.title }}</h1>
        <p>{{ c.subtitle }}</p>
      </div>
      <div class="page-actions">
        <el-button size="small" :disabled="!items.length" @click="exportCsv"> {{ c.export }}</el-button>
      </div>
    </header>

    <section class="data-panel">
      <div class="data-toolbar">
        <div class="filter-controls">
          <el-select v-model="environment" size="small" :placeholder="c.environment" @change="onFilterChange">
            <el-option :label="c.all" value="" />
            <el-option label="default" value="default" />
            <el-option label="production" value="production" />
            <el-option label="staging" value="staging" />
          </el-select>
        </div>
        <el-input
          v-model="search"
          :placeholder="c.searchUser"
          prefix-icon="el-icon-search"
          size="small"
          class="search-input"
          @input="onSearch"
        />
        <el-button size="small" icon="el-icon-more" :aria-label="c.tableOptions" />
      </div>

      <ErrorBanner :message="error" />

      <LoadingState v-if="loading" :message="c.loading" />
      <UserTable v-else :items="items" @open="openUser" />

      <el-pagination
        v-if="pageData"
        class="pagination"
        layout="total, prev, pager, next"
        :total="pageData.total"
        :page-size="pageData.size || 50"
        :current-page="pageData.page + 1"
        @current-change="p => goPage(p - 1)"
      />
    </section>

    <el-drawer
      v-if="selectedUser"
      :visible.sync="drawerVisible"
      :before-close="closeDrawer"
      size="670px"
      :show-close="false"
      custom-class="user-drawer"
    >
      <div class="drawer-content">
        <header>
          <div>
            <div class="drawer-label">{{ c.userLabel }}</div>
            <h2>{{ selectedUser.summary.id }}</h2>
            <code>{{ selectedUser.summary.environment || c.allEnvironments }}</code>
          </div>
          <el-button size="mini" circle @click="closeDrawer">×</el-button>
        </header>
        <div class="drawer-metrics">
          <div>
            <span>{{ c.traces }}</span>
            <strong>{{ selectedUser.summary.traceCount }}</strong>
          </div>
          <div>
            <span>{{ c.observations }}</span>
            <strong>{{ selectedUser.summary.observationCount }}</strong>
          </div>
          <div>
            <span>{{ c.tokens }}</span>
            <strong>{{ selectedUser.summary.totalTokens.toLocaleString() }}</strong>
          </div>
        </div>
        <section class="drawer-section">
          <h3>{{ c.sessions }} ({{ selectedUser.sessions.length }})</h3>
          <el-timeline v-if="selectedUser.sessions.length">
            <el-timeline-item v-for="(session, index) in selectedUser.sessions" :key="session.id">
              <div class="timeline-entry">
                <strong>{{ session.id }}</strong>
                <small
                  >{{ formatDate(session.createdAt) }} · {{ session.traceCount }} {{ c.tracesLower }}</small
                >
              </div>
            </el-timeline-item>
          </el-timeline>
          <p v-else class="empty-inline">{{ c.noSessions }}</p>
        </section>
        <section class="drawer-section">
          <h3>{{ c.traces }} ({{ selectedUser.traces.length }})</h3>
          <el-timeline>
            <el-timeline-item v-for="(trace, index) in selectedUser.traces" :key="trace.id">
              <div class="timeline-entry">
                <strong>{{ trace.name }}</strong>
                <small>{{ formatDate(trace.timestamp) }} · {{ trace.observationCount }} {{ c.observationsLower }}</small>
              </div>
              <el-tag size="mini" :type="statusTagType(trace.status)">{{ trace.status }}</el-tag>
            </el-timeline-item>
          </el-timeline>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { mapState } from "vuex";
import { observabilityApi } from "../../api/client";
import { formatDate, formatDuration, downloadCsv } from "../../utils/format";
import NavIcon from "../../components/NavIcon.vue";
import ErrorBanner from "../../components/ErrorBanner.vue";
import LoadingState from "../../components/LoadingState.vue";
import UserTable from "./UserTable.vue";
import remoteData from "../../mixins/remoteData";

export default {
  name: "UsersPage",
  components: { NavIcon, ErrorBanner, LoadingState, UserTable },
  mixins: [remoteData],
  data() {
    return {
      search: "",
      environment: "",
      currentPage: 0,
      pageData: null,
      items: [],
      selectedUser: null,
      drawerVisible: false
    };
  },
  computed: {
    ...mapState(["refreshKey"]),
    error() {
      return this.remoteError;
    },
    loading() {
      return this.remoteLoading;
    },
    c() {
      return this.$locale === "zh"
        ? {
            title: "用户",
            subtitle: "通过链路中附加的用户 ID 分析使用情况。",
            export: " 导出本页 CSV",
            environment: "环境",
            all: "全部",
            searchUser: "搜索用户 ID",
            tableOptions: "表格选项",
            loading: "正在加载数据…",
            userLabel: "用户",
            allEnvironments: "所有环境",
            traces: "链路",
            observations: "观测",
            tokens: "Token 数",
            sessions: "会话",
            tracesLower: "条链路",
            observationsLower: "条观测",
            noSessions: "没有与此用户关联的会话。"
          }
        : {
            title: "Users",
            subtitle: "Analyze usage by the user IDs attached to your traces.",
            export: " Export page CSV",
            environment: "Environment",
            all: "All",
            searchUser: "Search user ID",
            tableOptions: "Table options",
            loading: "Loading data…",
            userLabel: "USER",
            allEnvironments: "all environments",
            traces: "Traces",
            observations: "Observations",
            tokens: "Tokens",
            sessions: "Sessions",
            tracesLower: "traces",
            observationsLower: "observations",
            noSessions: "No sessions are associated with this user."
          };
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
  methods: {
    formatDate,
    formatDuration,
    statusTagType(status) {
      const map = {
        ERROR: "danger",
        SUCCESS: "success",
        WARNING: "warning",
        RUNNING: "warning",
        DEFAULT: "info",
        DEBUG: "info"
      };
      return map[status ? status.toUpperCase() : ""] || "";
    },
    async loadData() {
      await this.loadRemote(async signal => {
        const result = await observabilityApi.getUsers(this.search, this.environment, this.currentPage, signal);
        this.pageData = result;
        this.items = result.items || [];
      });
    },
    onSearch() {
      this.currentPage = 0;
      this.loadData();
    },
    onFilterChange() {
      this.currentPage = 0;
      this.loadData();
    },
    goPage(page) {
      this.currentPage = page;
      this.loadData();
    },
    async openUser(id) {
      try {
        this.selectedUser = await observabilityApi.getUser(id);
        this.drawerVisible = true;
      } catch (e) {
        console.error("Failed to load user:", e);
      }
    },
    closeDrawer() {
      this.drawerVisible = false;
      this.selectedUser = null;
    },
    exportCsv() {
      const rows = this.items.map(item => ({
        userId: item.id,
        environment: item.environment,
        firstEvent: item.firstEvent,
        lastEvent: item.lastEvent,
        traces: item.traceCount,
        observations: item.observationCount,
        tokens: item.totalTokens
      }));
      downloadCsv(rows, "langfuse-users.csv");
    }
  }
};
</script>

<style lang="scss" scoped>
.drawer-content {
  padding: 20px;
}
.search-input {
  width: min(260px, 35vw);
}
.pagination {
  padding: 7px 10px;
}
</style>
