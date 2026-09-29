<template>
  <div>
    <header class="page-header">
      <div>
        <h1>Users</h1>
        <p>Analyze usage by the user IDs attached to your traces.</p>
      </div>
      <div class="page-actions">
        <el-button size="small" :disabled="!items.length" @click="exportCsv"> Export page CSV </el-button>
      </div>
    </header>

    <section class="data-panel">
      <div class="data-toolbar">
        <div class="filter-controls">
          <el-select v-model="environment" size="small" placeholder="Environment" @change="onFilterChange">
            <el-option label="All" value="" />
            <el-option label="default" value="default" />
            <el-option label="production" value="production" />
            <el-option label="staging" value="staging" />
          </el-select>
        </div>
        <el-input
          v-model="search"
          placeholder="Search user ID"
          prefix-icon="el-icon-search"
          size="small"
          class="search-input"
          @input="onSearch"
        />
        <el-button size="small" icon="el-icon-more" aria-label="Table options" />
      </div>

      <ErrorBanner :message="error" />

      <LoadingState v-if="loading" message="Loading data…" />
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
            <div class="drawer-label">USER</div>
            <h2>{{ selectedUser.summary.id }}</h2>
            <code>{{ selectedUser.summary.environment || "all environments" }}</code>
          </div>
          <el-button size="mini" circle @click="closeDrawer">×</el-button>
        </header>
        <div class="drawer-metrics">
          <div>
            <span>Traces</span>
            <strong>{{ selectedUser.summary.traceCount }}</strong>
          </div>
          <div>
            <span>Observations</span>
            <strong>{{ selectedUser.summary.observationCount }}</strong>
          </div>
          <div>
            <span>Tokens</span>
            <strong>{{ selectedUser.summary.totalTokens.toLocaleString() }}</strong>
          </div>
        </div>
        <section class="drawer-section">
          <h3>Sessions ({{ selectedUser.sessions.length }})</h3>
          <el-timeline v-if="selectedUser.sessions.length">
            <el-timeline-item v-for="(session, index) in selectedUser.sessions" :key="session.id">
              <div class="timeline-entry">
                <strong>{{ session.id }}</strong>
                <small
                  >{{ formatDate(session.createdAt) }} · {{ session.traceCount }} traces</small
                >
              </div>
            </el-timeline-item>
          </el-timeline>
          <p v-else class="empty-inline">No sessions are associated with this user.</p>
        </section>
        <section class="drawer-section">
          <h3>Traces ({{ selectedUser.traces.length }})</h3>
          <el-timeline>
            <el-timeline-item v-for="(trace, index) in selectedUser.traces" :key="trace.id">
              <div class="timeline-entry">
                <strong>{{ trace.name }}</strong>
                <small>{{ formatDate(trace.timestamp) }} · {{ trace.observationCount }} observations</small>
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
      return map[status?.toUpperCase()] || "";
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
