<template>
  <div>
    <header class="page-header">
      <div>
        <h1>Dashboards</h1>
        <p>Create custom views for observability metrics and quality signals.</p>
      </div>
      <div class="page-actions">
        <el-button v-if="canManage" type="primary" size="small" @click="openNewDashboard">＋ New dashboard</el-button>
      </div>
    </header>

    <ErrorBanner v-if="dashboardError" :message="dashboardError" />

    <LoadingState v-if="dashboardsLoading" message="Loading dashboards…" />
    <EmptyState
      v-else-if="!dashboards.length"
      title="No dashboards yet"
      description="Create a dashboard to arrange project metrics."
      icon-class="el-icon-menu"
    />
    <section v-else class="dashboard-list-grid">
      <el-card
        v-for="dashboard in dashboards"
        :key="dashboard.id"
        class="dashboard-list-card"
        :body-style="{ padding: '16px' }"
      >
        <header>
          <div class="dashboard-card-icon">
            <NavIcon name="dashboard" :size="18" />
          </div>
          <el-tag size="mini" :type="dashboard.owner === 'PROJECT' ? '' : 'info'">
            {{ dashboard.owner === "PROJECT" ? "Project" : "Langfuse" }}
          </el-tag>
        </header>
        <h2>{{ dashboard.name }}</h2>
        <p>{{ dashboard.description || "No description" }}</p>
        <div class="dashboard-card-meta">
          <span>{{ getWidgetCount(dashboard) }} widgets</span>
          <span>Updated {{ formatDate(dashboard.updatedAt) }}</span>
        </div>
        <footer>
          <el-button
            v-if="canManage && dashboard.owner === 'PROJECT'"
            size="small"
            @click="openEditDashboard(dashboard)"
            >Edit</el-button
          >
          <el-button v-if="canManage" size="small" @click="cloneDashboard(dashboard)">Clone</el-button>
          <el-button
            v-if="canManage && dashboard.owner === 'PROJECT'"
            type="danger"
            size="small"
            @click="removeDashboard(dashboard)"
            >Delete</el-button
          >
        </footer>
      </el-card>
    </section>

    <el-dialog
      v-if="canManage && dashboardDialog.visible"
      :visible.sync="dashboardDialog.visible"
      :title="dashboardDialog.editing ? 'Edit dashboard' : 'Create dashboard'"
      width="560px"
      custom-class="dashboard-modal"
    >
      <el-form ref="dashboardForm" :model="dashboardDialog.form" label-position="top">
        <el-form-item label="Name" prop="name" class="full-width" required>
          <el-input v-model="dashboardDialog.form.name" placeholder="Dashboard name" />
        </el-form-item>
        <el-form-item label="Description" class="full-width">
          <el-input
            v-model="dashboardDialog.form.description"
            type="textarea"
            :rows="5"
            placeholder="What does this dashboard monitor?"
          />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="dashboardDialog.visible = false">Cancel</el-button>
        <el-button type="primary" :loading="dashboardDialog.saving" @click="submitDashboard">
          {{ dashboardDialog.saving ? "Saving…" : dashboardDialog.editing ? "Save changes" : "Create dashboard" }}
        </el-button>
      </span>
    </el-dialog>

    <section class="widget-library">
      <header class="page-header">
        <div>
          <h1>Widgets</h1>
          <p>Reusable observability metric definitions.</p>
        </div>
        <div class="page-actions">
          <el-button v-if="canManage" type="primary" size="small" @click="openNewWidget">＋ New widget</el-button>
        </div>
      </header>

      <ErrorBanner v-if="widgetError" :message="widgetError" />

      <LoadingState v-if="widgetsLoading" message="Loading widgets…" />
      <EmptyState
        v-else-if="!widgets.length"
        title="No project widgets"
        description="Create a trace or observation metric widget."
      />
      <section v-else class="dashboard-list-grid">
        <el-card
          v-for="widget in widgets"
          :key="widget.id"
          class="dashboard-list-card"
          :body-style="{ padding: '16px' }"
        >
          <header>
            <div class="dashboard-card-icon">
              <NavIcon name="dashboard" :size="18" />
            </div>
            <el-tag size="mini" :type="isWidgetMutable(widget) ? '' : 'info'">
              {{ widget.view }}
            </el-tag>
          </header>
          <h2>{{ widget.name }}</h2>
          <p>{{ widget.description || "No description" }}</p>
          <div class="dashboard-card-meta">
            <span>{{ widget.chartType.replace(/_/g, " ") }}</span>
            <span>{{
              isWidgetSupported(widget) ? (widget.metrics[0] || {}).measure || "metric" : "Evaluation deferred"
            }}</span>
          </div>
          <WidgetPreview v-if="isWidgetSupported(widget)" :widget="widget" />
          <footer>
            <el-button v-if="canManage && isWidgetMutable(widget)" size="small" @click="openEditWidget(widget)"
              >Edit</el-button
            >
            <el-button v-if="canManage && isWidgetSupported(widget)" size="small" @click="cloneWidget(widget)"
              >Clone</el-button
            >
            <el-button
              v-if="canManage && isWidgetMutable(widget)"
              type="danger"
              size="small"
              @click="removeWidget(widget)"
              >Delete</el-button
            >
          </footer>
        </el-card>
      </section>

      <el-dialog
        v-if="canManage && widgetDialog.visible"
        :visible.sync="widgetDialog.visible"
        :title="widgetDialog.editing ? 'Edit widget' : 'Create widget'"
        width="560px"
        custom-class="dashboard-modal"
      >
        <el-form ref="widgetForm" :model="widgetDialog.form" label-position="top">
          <el-form-item label="Name" class="full-width" required>
            <el-input v-model="widgetDialog.form.name" />
          </el-form-item>
          <el-form-item label="Description" class="full-width">
            <el-input v-model="widgetDialog.form.description" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="View">
            <el-select v-model="widgetDialog.form.view">
              <el-option value="OBSERVATIONS" label="Observations" />
              <el-option value="TRACES" label="Traces" />
            </el-select>
          </el-form-item>
          <el-form-item label="Measure">
            <el-select v-model="widgetDialog.form.measure">
              <el-option value="count" label="Count" />
              <el-option value="totalTokens" label="Total tokens" />
              <el-option value="latency" label="Latency" />
            </el-select>
          </el-form-item>
          <el-form-item label="Chart" class="full-width">
            <el-select v-model="widgetDialog.form.chartType">
              <el-option value="LINE_TIME_SERIES" label="Line time series" />
              <el-option value="BAR_TIME_SERIES" label="Bar time series" />
              <el-option value="NUMBER" label="Number" />
              <el-option value="VERTICAL_BAR" label="Vertical bar" />
            </el-select>
          </el-form-item>
        </el-form>
        <span slot="footer">
          <el-button @click="widgetDialog.visible = false">Cancel</el-button>
          <el-button type="primary" :loading="widgetDialog.saving" @click="submitWidget">
            {{ widgetDialog.saving ? "Saving…" : "Save widget" }}
          </el-button>
        </span>
      </el-dialog>
    </section>
  </div>
</template>

<script>
import { mapGetters } from "vuex";
import { observabilityApi } from "../../api/client";
import { formatDate } from "../../utils/format";
import NavIcon from "../../components/NavIcon.vue";
import ErrorBanner from "../../components/ErrorBanner.vue";
import LoadingState from "../../components/LoadingState.vue";
import EmptyState from "../../components/EmptyState.vue";
import WidgetPreview from "./WidgetPreview.vue";

export default {
  name: "DashboardsPage",
  components: { NavIcon, ErrorBanner, LoadingState, EmptyState, WidgetPreview },
  data() {
    return {
      dashboards: [],
      dashboardsLoading: true,
      dashboardError: null,
      widgets: [],
      widgetsLoading: true,
      widgetError: null,
      dashboardDialog: {
        visible: false,
        editing: null,
        saving: false,
        form: {
          name: "",
          description: ""
        }
      },
      widgetDialog: {
        visible: false,
        editing: null,
        saving: false,
        form: {
          name: "",
          description: "",
          view: "OBSERVATIONS",
          measure: "count",
          chartType: "LINE_TIME_SERIES"
        }
      }
    };
  },
  computed: {
    ...mapGetters(["isAdmin"]),
    canManage() {
      return this.isAdmin;
    }
  },
  mounted() {
    this.loadDashboards();
    this.loadWidgets();
  },
  methods: {
    formatDate,
    getWidgetCount(dashboard) {
      return Array.isArray(dashboard.definition?.widgets) ? dashboard.definition.widgets.length : 0;
    },
    isWidgetSupported(widget) {
      return widget.view === "TRACES" || widget.view === "OBSERVATIONS";
    },
    isWidgetMutable(widget) {
      return widget.owner === "PROJECT" && this.isWidgetSupported(widget);
    },
    async loadDashboards() {
      this.dashboardsLoading = true;
      this.dashboardError = null;
      try {
        const result = await observabilityApi.getDashboards(0);
        this.dashboards = result.items || [];
      } catch (e) {
        this.dashboardError = e.message;
      } finally {
        this.dashboardsLoading = false;
      }
    },
    async loadWidgets() {
      this.widgetsLoading = true;
      this.widgetError = null;
      try {
        const result = await observabilityApi.getDashboardWidgets(0);
        this.widgets = result.items || [];
      } catch (e) {
        this.widgetError = e.message;
      } finally {
        this.widgetsLoading = false;
      }
    },
    openNewDashboard() {
      this.dashboardDialog.editing = null;
      this.dashboardDialog.form = { name: "", description: "" };
      this.dashboardDialog.visible = true;
    },
    openEditDashboard(dashboard) {
      this.dashboardDialog.editing = dashboard;
      this.dashboardDialog.form = {
        name: dashboard.name,
        description: dashboard.description
      };
      this.dashboardDialog.visible = true;
    },
    async submitDashboard() {
      this.dashboardDialog.saving = true;
      try {
        if (this.dashboardDialog.editing) {
          await observabilityApi.updateDashboardMetadata(this.dashboardDialog.editing.id, this.dashboardDialog.form);
        } else {
          await observabilityApi.createDashboard(this.dashboardDialog.form);
        }
        this.dashboardDialog.visible = false;
        this.loadDashboards();
      } catch (e) {
        this.$message.error(e.message);
      } finally {
        this.dashboardDialog.saving = false;
      }
    },
    async cloneDashboard(dashboard) {
      try {
        await observabilityApi.cloneDashboard(dashboard.id);
        this.loadDashboards();
      } catch (e) {
        this.$message.error(e.message);
      }
    },
    async removeDashboard(dashboard) {
      try {
        await this.$confirm(`Delete dashboard "${dashboard.name}"?`, "Confirm", {
          confirmButtonText: "Delete",
          cancelButtonText: "Cancel",
          type: "warning"
        });
        await observabilityApi.deleteDashboard(dashboard.id);
        this.loadDashboards();
      } catch (e) {
        if (e !== "cancel" && e?.toString() !== "cancel") {
          this.$message.error(e.message);
        }
      }
    },
    openNewWidget() {
      this.widgetDialog.editing = null;
      this.widgetDialog.form = {
        name: "",
        description: "",
        view: "OBSERVATIONS",
        measure: "count",
        chartType: "LINE_TIME_SERIES"
      };
      this.widgetDialog.visible = true;
    },
    openEditWidget(widget) {
      this.widgetDialog.editing = widget;
      this.widgetDialog.form = {
        name: widget.name,
        description: widget.description,
        view: widget.view,
        measure: widget.metrics[0]?.measure || "count",
        chartType: widget.chartType
      };
      this.widgetDialog.visible = true;
    },
    async submitWidget() {
      this.widgetDialog.saving = true;
      try {
        const aggregation = this.widgetDialog.form.measure === "count" ? "count" : "sum";
        const dimensions =
          this.widgetDialog.form.chartType === "NUMBER" ? [] : [{ field: "timestamp", granularity: "day" }];

        const input = {
          name: this.widgetDialog.form.name.trim(),
          description: this.widgetDialog.form.description.trim(),
          view: this.widgetDialog.form.view,
          dimensions,
          metrics: [{ measure: this.widgetDialog.form.measure, aggregation }],
          filters: this.widgetDialog.editing?.filters || [],
          chartType: this.widgetDialog.form.chartType,
          chartConfig: this.widgetDialog.editing?.chartConfig || {}
        };

        if (this.widgetDialog.editing) {
          await observabilityApi.updateDashboardWidget(this.widgetDialog.editing.id, input);
        } else {
          await observabilityApi.createDashboardWidget(input);
        }
        this.widgetDialog.visible = false;
        this.loadWidgets();
      } catch (e) {
        this.$message.error(e.message);
      } finally {
        this.widgetDialog.saving = false;
      }
    },
    async cloneWidget(widget) {
      try {
        await observabilityApi.cloneDashboardWidget(widget.id);
        this.loadWidgets();
      } catch (e) {
        this.$message.error(e.message);
      }
    },
    async removeWidget(widget) {
      try {
        await this.$confirm(`Delete widget "${widget.name}"?`, "Confirm", {
          confirmButtonText: "Delete",
          cancelButtonText: "Cancel",
          type: "warning"
        });
        await observabilityApi.deleteDashboardWidget(widget.id);
        this.loadWidgets();
      } catch (e) {
        if (e !== "cancel" && e?.toString() !== "cancel") {
          this.$message.error(e.message);
        }
      }
    }
  }
};
</script>

<style lang="scss" scoped>
.full-width {
  width: 100%;
}
</style>
