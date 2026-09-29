<template>
  <div>
    <header class="page-header">
      <div>
        <h1>Sessions</h1>
        <p>Sessions group traces that belong to the same workflow or conversation.</p>
      </div>
      <div class="page-actions">
        <el-button size="small" :disabled="!items.length" @click="exportCsv"> Export page CSV </el-button>
      </div>
    </header>

    <section class="data-panel">
      <div class="data-toolbar">
        <div class="filter-controls"></div>
        <el-input
          v-model="search"
          placeholder="Search sessions"
          prefix-icon="el-icon-search"
          size="small"
          class="search-input"
          @input="onSearch"
        />
        <el-button size="small" icon="el-icon-more" aria-label="Table options" />
      </div>

      <ErrorBanner :message="error" />

      <LoadingState v-if="loading" message="Loading data…" />
      <SessionTable v-else :items="items" @open="openSession" />

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
      v-if="selectedSession"
      :visible.sync="drawerVisible"
      :before-close="closeDrawer"
      size="670px"
      :show-close="false"
      custom-class="session-drawer"
    >
      <div class="drawer-content">
        <header>
          <div>
            <div class="drawer-label">SESSION</div>
            <h2>{{ selectedSession.summary.id }}</h2>
            <code>{{ selectedSession.summary.userId || "anonymous" }}</code>
          </div>
          <el-button size="mini" circle @click="closeDrawer">×</el-button>
        </header>
        <div class="drawer-metrics">
          <div>
            <span>Traces</span>
            <strong>{{ selectedSession.summary.traceCount }}</strong>
          </div>
          <div>
            <span>Observations</span>
            <strong>{{ selectedSession.summary.observationCount }}</strong>
          </div>
          <div>
            <span>Duration</span>
            <strong>{{ formatDuration(selectedSession.summary.durationMs) }}</strong>
          </div>
        </div>
        <section class="drawer-section">
          <h3>Trace sequence</h3>
          <el-timeline>
            <el-timeline-item v-for="(trace, index) in selectedSession.traces" :key="trace.id">
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
import remoteData from "../../mixins/remoteData";

export default {
  name: "SessionsPage",
  components: { NavIcon, ErrorBanner, LoadingState },
  mixins: [remoteData],
  data() {
    return {
      search: "",
      currentPage: 0,
      pageData: null,
      items: [],
      selectedSession: null,
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
        const result = await observabilityApi.getSessions(this.search, this.currentPage, signal);
        this.pageData = result;
        this.items = result.items || [];
      });
    },
    onSearch() {
      this.currentPage = 0;
      this.loadData();
    },
    goPage(page) {
      this.currentPage = page;
      this.loadData();
    },
    async openSession(id) {
      try {
        this.selectedSession = await observabilityApi.getSession(id);
        this.drawerVisible = true;
      } catch (e) {
        console.error("Failed to load session:", e);
      }
    },
    closeDrawer() {
      this.drawerVisible = false;
      this.selectedSession = null;
    },
    exportCsv() {
      const rows = this.items.map(item => ({
        sessionId: item.id,
        createdAt: item.createdAt,
        userId: item.userId,
        traces: item.traceCount,
        observations: item.observationCount,
        durationMs: item.durationMs,
        tokens: item.totalTokens
      }));
      downloadCsv(rows, "langfuse-sessions.csv");
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
