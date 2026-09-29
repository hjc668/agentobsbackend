<template>
  <div>
    <header class="page-header">
      <div>
        <h1>Prompts</h1>
        <p>Create, version, label, and deploy prompts from one place.</p>
      </div>
      <div class="page-actions">
        <template v-if="canManage">
          <el-button size="small">Import</el-button>
          <el-button type="primary" size="small" @click="openNew">＋ New prompt</el-button>
        </template>
      </div>
    </header>

    <section class="data-panel">
      <div class="data-toolbar">
        <div class="filter-controls">
          <el-button size="small">All types</el-button>
          <el-button size="small">All labels</el-button>
        </div>
        <el-input
          v-model="search"
          placeholder="Search prompts"
          prefix-icon="el-icon-search"
          size="small"
          class="search-input"
          @input="onSearch"
        />
        <el-button size="small" icon="el-icon-more" />
      </div>

      <ErrorBanner v-if="error" :message="error" />

      <LoadingState v-if="loading" message="Loading prompts…" />
      <EmptyState
        v-else-if="!items.length"
        title="No prompts found"
        description="Create a prompt to start managing versions and deployment labels."
        icon-class="el-icon-document"
      />
      <div v-else class="table-wrap">
        <el-table :data="items" style="width: 100%" size="mini">
          <el-table-column label="Name" min-width="200">
            <template slot-scope="{ row }">
              <div class="prompt-name">
                <NavIcon name="file" :size="16" />
                <span>
                  <strong>{{ row.name }}</strong>
                  <small>{{ row.commitMessage || "No commit message" }}</small>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Type" width="100">
            <template slot-scope="{ row }"
              ><el-tag size="mini">{{ row.type }}</el-tag></template
            >
          </el-table-column>
          <el-table-column label="Latest version" width="120">
            <template slot-scope="{ row }">v{{ row.version }}</template>
          </el-table-column>
          <el-table-column label="Labels" min-width="180">
            <template slot-scope="{ row }">
              <div class="chip-list">
                <el-tag
                  v-for="label in row.labels"
                  :key="label"
                  size="mini"
                  :type="label === 'production' ? 'success' : ''"
                  >{{ label }}</el-tag
                >
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Tags" min-width="150">
            <template slot-scope="{ row }">
              <div class="chip-list">
                <el-tag v-for="tag in row.tags" :key="tag" size="mini" type="info">{{ tag }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Updated" width="140">
            <template slot-scope="{ row }">{{ formatDate(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column v-if="canManage" label="Actions" width="300">
            <template slot-scope="{ row }">
              <div class="row-actions">
                <el-button size="mini" @click="openEdit(row)">New version</el-button>
                <el-button size="mini" @click="editLabels(row)">Labels</el-button>
                <el-button size="mini" @click="editTags(row)">Tags</el-button>
                <el-button size="mini" type="danger" @click="remove(row)">Delete</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <el-dialog
      v-if="canManage && editing"
      :visible.sync="dialogVisible"
      :title="editing === 'new' ? 'Create prompt' : 'Create new version'"
      width="720px"
      :before-close="closeDialog"
      custom-class="prompt-modal"
    >
      <el-form
        ref="promptForm"
        :model="formData"
        :rules="formRules"
        label-position="top"
        @submit.native.prevent="submitForm"
      >
        <el-form-item label="Name" prop="name" class="full-width">
          <el-input v-model="formData.name" :disabled="editing !== 'new'" placeholder="folder/prompt-name" />
        </el-form-item>
        <el-form-item label="Type" prop="type">
          <el-select v-model="formData.type" :disabled="editing !== 'new'">
            <el-option value="text" label="Text" />
            <el-option value="chat" label="Chat" />
          </el-select>
        </el-form-item>
        <el-form-item label="Commit message">
          <el-input v-model="formData.commitMessage" placeholder="What changed?" />
        </el-form-item>
        <el-form-item
          :label="formData.type === 'chat' ? 'Prompt (JSON messages)' : 'Prompt'"
          prop="content"
          class="full-width"
        >
          <el-input v-model="formData.content" type="textarea" :rows="10" placeholder="Enter the prompt template…" />
        </el-form-item>
        <el-form-item label="Labels">
          <el-input v-model="formData.labels" placeholder="production, staging" />
        </el-form-item>
        <el-form-item label="Tags">
          <el-input v-model="formData.tags" placeholder="support, rag" />
        </el-form-item>
        <el-form-item label="Config (JSON)" class="full-width">
          <el-input v-model="formData.config" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="closeDialog">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">
          {{ saving ? "Saving…" : editing === "new" ? "Create prompt" : "Create version" }}
        </el-button>
      </span>
    </el-dialog>

    <el-dialog v-if="labelDialog.visible" :visible.sync="labelDialog.visible" title="Edit Labels" width="400px">
      <el-input v-model="labelDialog.value" placeholder="Comma-separated labels" />
      <span slot="footer">
        <el-button @click="labelDialog.visible = false">Cancel</el-button>
        <el-button type="primary" @click="saveLabels">Save</el-button>
      </span>
    </el-dialog>

    <el-dialog v-if="tagDialog.visible" :visible.sync="tagDialog.visible" title="Edit Tags" width="400px">
      <el-input v-model="tagDialog.value" placeholder="Comma-separated tags" />
      <span slot="footer">
        <el-button @click="tagDialog.visible = false">Cancel</el-button>
        <el-button type="primary" @click="saveTags">Save</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { mapGetters, mapState } from "vuex";
import { observabilityApi } from "../../api/client";
import { formatDate } from "../../utils/format";
import NavIcon from "../../components/NavIcon.vue";
import ErrorBanner from "../../components/ErrorBanner.vue";
import LoadingState from "../../components/LoadingState.vue";
import EmptyState from "../../components/EmptyState.vue";
import remoteData from "../../mixins/remoteData";

function splitValues(value) {
  return value
    .split(",")
    .map(item => item.trim())
    .filter(Boolean);
}

function promptText(prompt) {
  return typeof prompt === "string" ? prompt : JSON.stringify(prompt, null, 2);
}

export default {
  name: "PromptsPage",
  components: { NavIcon, ErrorBanner, LoadingState, EmptyState },
  mixins: [remoteData],
  data() {
    return {
      search: "",
      revision: 0,
      items: [],
      editing: null,
      dialogVisible: false,
      saving: false,
      formData: {
        name: "",
        type: "text",
        content: "",
        labels: "",
        tags: "",
        commitMessage: "",
        config: "{}"
      },
      formRules: {
        name: [{ required: true, message: "Name is required", trigger: "blur" }],
        content: [{ required: true, message: "Prompt content is required", trigger: "blur" }]
      },
      labelDialog: {
        visible: false,
        value: "",
        prompt: null
      },
      tagDialog: {
        visible: false,
        value: "",
        prompt: null
      }
    };
  },
  computed: {
    ...mapState(["refreshKey"]),
    ...mapGetters(["isAdmin"]),
    canManage() {
      return this.isAdmin;
    },
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
    },
    revision() {
      this.loadData();
    }
  },
  mounted() {
    this.loadData();
  },
  methods: {
    formatDate,
    async loadData() {
      await this.loadRemote(async signal => {
        const result = await observabilityApi.getPrompts(this.search, 0, signal);
        this.items = result.items || [];
      });
    },
    onSearch() {
      this.loadData();
    },
    openNew() {
      this.editing = "new";
      this.formData = {
        name: "",
        type: "text",
        content: "",
        labels: "",
        tags: "",
        commitMessage: "",
        config: "{}"
      };
      this.dialogVisible = true;
    },
    openEdit(prompt) {
      this.editing = prompt;
      this.formData = {
        name: prompt.name,
        type: prompt.type,
        content: promptText(prompt.prompt),
        labels: prompt.labels.filter(l => l !== "latest").join(", "),
        tags: prompt.tags.join(", "),
        commitMessage: "",
        config: JSON.stringify(prompt.config, null, 2)
      };
      this.dialogVisible = true;
    },
    closeDialog() {
      this.dialogVisible = false;
      this.editing = null;
    },
    async submitForm() {
      this.$refs.promptForm.validate(async valid => {
        if (!valid) return;

        this.saving = true;
        try {
          const parsedPrompt =
            this.formData.type === "chat" ? JSON.parse(this.formData.content) : this.formData.content;
          const parsedConfig = JSON.parse(this.formData.config);

          await observabilityApi.createPrompt({
            name: this.formData.name,
            type: this.formData.type,
            prompt: parsedPrompt,
            config: parsedConfig,
            labels: splitValues(this.formData.labels),
            tags: splitValues(this.formData.tags),
            commitMessage: this.formData.commitMessage
          });

          this.revision++;
          this.closeDialog();
        } catch (e) {
          this.$message.error(e.message);
        } finally {
          this.saving = false;
        }
      });
    },
    editLabels(prompt) {
      this.labelDialog.prompt = prompt;
      this.labelDialog.value = prompt.labels.join(", ");
      this.labelDialog.visible = true;
    },
    async saveLabels() {
      try {
        await observabilityApi.setPromptLabels(this.labelDialog.prompt.id, splitValues(this.labelDialog.value));
        this.labelDialog.visible = false;
        this.revision++;
      } catch (e) {
        this.$message.error(e.message);
      }
    },
    editTags(prompt) {
      this.tagDialog.prompt = prompt;
      this.tagDialog.value = prompt.tags.join(", ");
      this.tagDialog.visible = true;
    },
    async saveTags() {
      try {
        await observabilityApi.updatePromptTags(this.tagDialog.prompt.id, splitValues(this.tagDialog.value));
        this.tagDialog.visible = false;
        this.revision++;
      } catch (e) {
        this.$message.error(e.message);
      }
    },
    async remove(prompt) {
      try {
        await this.$confirm(`Delete all versions of ${prompt.name}?`, "Confirm", {
          confirmButtonText: "Delete",
          cancelButtonText: "Cancel",
          type: "warning"
        });
        await observabilityApi.deletePrompt(prompt.name);
        this.revision++;
      } catch (e) {
        if (e !== "cancel" && e && e.toString() !== "cancel") {
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
.search-input {
  width: min(260px, 35vw);
}
</style>
