<template>
  <div>
    <div v-if="authLoading" class="auth-loading">
      <i class="el-icon-loading" /> {{ $locale === "zh" ? "正在验证登录状态…" : "Checking your session…" }}
    </div>
    <LoginPage v-else-if="!user" :expired="authExpired" :on-login="handleLogin" />
    <el-container v-else class="app-shell" :class="{ 'menu-open': mobileMenu }">
      <el-aside width="184px" class="sidebar-aside">
        <div class="brand-row">
          <img class="brand-logo" src="/wordart-white.svg" alt="Langfuse" />
          <el-button type="text" size="mini" class="version">v4.15.0 <span>OSS</span> ↑</el-button>
        </div>
        <div class="sidebar-divider" />
        <el-button type="text" class="command-button">
          <i class="el-icon-search" />
          <span>{{ $t("app.search") }}</span>
          <kbd>⌘ K</kbd>
        </el-button>
        <el-menu :default-active="activeMenu" class="sidebar-nav" router>
          <el-menu-item-group v-for="group in navGroups" :key="group.key">
            <template v-if="group.label" slot="title">
              <span class="nav-group-label">{{ group.label }}</span>
            </template>
            <el-menu-item v-for="item in group.items" :key="item.id" :index="item.path">
              <NavIcon :name="item.icon" :size="16" />
              <span>{{ $t(`nav.${item.label}`) }}</span>
            </el-menu-item>
          </el-menu-item-group>
        </el-menu>
        <div class="sidebar-spacer" />
        <el-menu :default-active="activeSecondaryMenu" class="secondary-nav" router>
          <el-menu-item :index="settingsItem.path">
            <NavIcon name="settings" :size="16" />
            <span>{{ $t("nav.settings") }}</span>
          </el-menu-item>
        </el-menu>
        <div class="account-wrap">
          <el-dropdown trigger="click" @command="handleAccountCommand" class="account-dropdown">
            <div class="account-row">
              <div class="avatar">{{ initials }}</div>
              <div>
                <strong>{{ user.displayName }}</strong>
                <small>{{ user.aamId }} · {{ user.departmentName }}</small>
              </div>
              <i class="el-icon-arrow-down" />
            </div>
            <el-dropdown-menu slot="dropdown">
              <span class="account-menu-title">{{ $t("app.language") }}</span>
              <el-dropdown-item command="lang-zh" :class="{ active: $locale === 'zh' }">
                <span>中文</span><i v-if="$locale === 'zh'" class="el-icon-check" />
              </el-dropdown-item>
              <el-dropdown-item command="lang-en" :class="{ active: $locale === 'en' }">
                <span>English</span><i v-if="$locale === 'en'" class="el-icon-check" />
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <span><i class="el-icon-switch-button" /> {{ $t("app.logout") }}</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
        </div>
      </el-aside>
      <div v-if="mobileMenu" class="mobile-menu-shade" @click="mobileMenu = false" />
      <div class="workspace">
        <el-header class="topbar" height="43px">
          <el-button
            class="sidebar-toggle"
            icon="el-icon-s-unfold"
            aria-label="Toggle sidebar"
            @click="mobileMenu = !mobileMenu"
          />
          <img class="mobile-brand" src="/wordart-black.svg" alt="Langfuse" />
          <el-button class="mobile-avatar" @click="mobileAccountMenu = !mobileAccountMenu">{{ initials }}</el-button>
          <div class="project-breadcrumb">
            <el-button type="text" class="project-name">main org <i class="el-icon-arrow-down" /></el-button>
            <span class="topbar-separator">／</span>
            <el-button type="text" class="project-name">test <i class="el-icon-arrow-down" /></el-button>
          </div>
          <div class="topbar-status">
            <el-select v-model="dateRange" size="small" class="date-range-select" @change="rangeChanged">
              <el-option value="24h" label="Past 24 hours" />
              <el-option value="7d" label="Past 7 days" />
              <el-option value="30d" label="Past 30 days" />
              <el-option value="90d" label="Past 90 days" />
            </el-select>
            <el-button class="refresh-button" icon="el-icon-refresh" aria-label="Refresh" @click="handleRefresh" />
            <el-button class="refresh-button">⌘ &nbsp; Off</el-button>
          </div>
        </el-header>
        <div v-if="mobileAccountMenu" id="mobile-account-menu" class="mobile-account-menu" role="menu">
          <span class="account-menu-title">{{ user.displayName }} · {{ user.aamId }}</span>
          <el-button type="text" :class="{ active: $locale === 'zh' }" @click="changeLanguage('zh')">
            <span>中文</span><i v-if="$locale === 'zh'" class="el-icon-check" />
          </el-button>
          <el-button type="text" :class="{ active: $locale === 'en' }" @click="changeLanguage('en')">
            <span>English</span><i v-if="$locale === 'en'" class="el-icon-check" />
          </el-button>
          <el-button type="text" class="logout-action" @click="handleLogout">
            <span><i class="el-icon-switch-button" /> {{ $t("app.logout") }}</span>
          </el-button>
        </div>
        <el-main class="main-content">
          <router-view :refresh-key="refreshKey" :date-range="dateRange" />
        </el-main>
      </div>
    </el-container>
  </div>
</template>

<script>
import { mapState, mapGetters, mapActions } from "vuex";
import NavIcon from "./components/NavIcon.vue";
import LoginPage from "./features/auth/LoginPage.vue";

const projectId = "cmt2am51r0006pa07ghcbt7vi";
const root = `/project/${projectId}`;

const navigation = [
  { id: "home", label: "home", path: `${root}/home`, icon: "home" },
  { id: "dashboards", label: "dashboards", path: `${root}/dashboards`, icon: "dashboard" },
  { id: "tracing", label: "tracing", path: `${root}/traces`, icon: "trace", group: "observability" },
  { id: "sessions", label: "sessions", path: `${root}/sessions`, icon: "clock", group: "observability" },
  { id: "users", label: "users", path: `${root}/users`, icon: "users", group: "observability" },
  { id: "alerts", label: "alerts", path: `${root}/alerts`, icon: "bell", group: "observability" },
  { id: "prompts", label: "prompts", path: `${root}/prompts`, icon: "file", group: "prompt" },
  { id: "playground", label: "playground", path: `${root}/playground`, icon: "terminal", group: "prompt" },
  { id: "scores", label: "scores", path: `${root}/scores`, icon: "score", group: "evaluation" },
  { id: "evaluators", label: "evaluators", path: `${root}/evals`, icon: "bulb", group: "evaluation" },
  {
    id: "annotation-queues",
    label: "annotation",
    path: `${root}/annotation-queues`,
    icon: "clipboard",
    group: "evaluation"
  },
  { id: "datasets", label: "datasets", path: `${root}/datasets`, icon: "database", group: "evaluation" },
  { id: "experiments", label: "experiments", path: `${root}/experiments`, icon: "beaker", group: "evaluation" }
];

const settingsItem = { id: "settings", label: "settings", path: `${root}/settings` };

export default {
  name: "AppShell",
  components: {
    NavIcon,
    LoginPage
  },
  data() {
    return {
      navigation,
      settingsItem,
      dateRange: "30d",
      mobileMenu: false,
      mobileAccountMenu: false
    };
  },
  computed: {
    ...mapState(["user", "authLoading", "authReady", "authExpired", "refreshKey"]),
    ...mapGetters(["initials", "isAdmin"]),
    activeMenu() {
      const path = this.$route.path;
      const match = navigation.find(item => {
        if (item.id === "home") return path === `${root}/home` || path === `${root}/home/`;
        return path.startsWith(item.path);
      });
      return match ? match.path : "";
    },
    activeSecondaryMenu() {
      const path = this.$route.path;
      return path.startsWith(settingsItem.path) ? settingsItem.path : "";
    },
    navGroups() {
      const groups = [];
      let currentKey = null;
      let currentItems = [];
      navigation.forEach(item => {
        const key = item.group || "__ungrouped__";
        if (key !== currentKey) {
          if (currentItems.length) {
            groups.push({
              key: currentKey,
              label: currentKey !== "__ungrouped__" ? this.$t(`groups.${currentKey}`) : "",
              items: currentItems
            });
          }
          currentKey = key;
          currentItems = [];
        }
        currentItems.push(item);
      });
      if (currentItems.length) {
        groups.push({
          key: currentKey,
          label: currentKey !== "__ungrouped__" ? this.$t(`groups.${currentKey}`) : "",
          items: currentItems
        });
      }
      return groups;
    }
  },
  created() {
    this.$store.dispatch("checkAuth");
  },
  methods: {
    ...mapActions(["refresh", "changeDateRange", "changeLanguage"]),
    handleAccountCommand(command) {
      if (command === "lang-zh") this.changeLanguage("zh");
      else if (command === "lang-en") this.changeLanguage("en");
      else if (command === "logout") this.handleLogout();
    },
    handleLogin(aamId, ticket) {
      return this.$store.dispatch("login", { aamId, ticket }).then(() => {
        this.$router.push(`${root}/home`);
      });
    },
    handleLogout() {
      this.$store.dispatch("logout").then(() => {
        this.mobileMenu = false;
        this.mobileAccountMenu = false;
      });
    },
    handleRefresh() {
      this.refresh();
    },
    rangeChanged() {
      this.changeDateRange(this.dateRange);
    }
  }
};
</script>

<style lang="scss" scoped>
.auth-loading {
  min-height: 100vh;
  display: grid;
  place-content: center;
  gap: 8px;
  color: #777b85;
  font-size: 12px;
}

.app-shell {
  min-height: 100vh;
}

.sidebar-aside {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e4e5e9;
  background: #fbfbfc;
  overflow: hidden;
}

.brand-row {
  min-height: 43px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  gap: 8px;
}

.brand-logo {
  width: 94px;
  height: auto;
  display: block;
}

.sidebar-divider {
  height: 1px;
  background: #e4e5e9;
}

.command-button {
  &.el-button {
    width: calc(100% - 12px);
    margin: 7px 6px 4px;
    padding: 0 8px;
    height: 39px;
    display: flex;
    align-items: center;
    gap: 9px;
    border: 0;
    border-radius: 5px;
    background: transparent;
    text-align: left;
    font-size: 13px;
    color: #30333b;
    &:hover {
      background: #f0f1f4;
    }
  }
}

.version {
  &.el-button--text {
    margin-left: auto;
    border: 0;
    background: transparent;
    padding: 4px 1px;
    color: #4f535d;
    font-size: 10px;
    white-space: nowrap;
  }
}

.sidebar-spacer {
  flex: 1;
  min-height: 16px;
}

.sidebar-nav {
  border-right: none !important;
  background: transparent !important;
  padding: 0 6px;
}

.secondary-nav {
  border-right: none !important;
  border-top: 1px solid #e2e3e7;
  background: transparent !important;
  padding: 8px 6px 0;
}

::v-deep .sidebar-nav .el-menu-item-group__title {
  height: 25px;
  display: flex;
  align-items: center;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 500;
  color: #7f848e;
}

::v-deep .sidebar-nav .el-menu-item,
::v-deep .secondary-nav .el-menu-item {
  height: 33px;
  line-height: 33px;
  font-size: 13px;
  padding: 0 8px !important;
  margin: 0 0 1px;
  border-radius: 5px;
  color: #30333b;
  display: flex;
  align-items: center;
  gap: 9px;

  &:hover {
    background: #f0f1f4;
  }

  &.is-active {
    background: #e8f2fa;
    font-weight: 600;
    color: #11131a;
  }

  .el-icon,
  i {
    font-size: 16px;
    margin-right: 0;
    width: 16px;
  }
}

.account-wrap {
  position: relative;
  padding: 0 6px 6px;
}

.account-row {
  width: 100%;
  min-height: 48px;
  margin-top: 7px;
  padding: 6px 8px;
  display: grid;
  grid-template-columns: 29px minmax(0, 1fr) auto;
  align-items: center;
  gap: 7px;
  border: 1px solid transparent;
  border-top-color: #e4e5e9;
  border-radius: 5px;
  background: transparent;
  color: #30333b;
  text-align: left;
  cursor: pointer;

  &:hover {
    border-color: #dfe1e6;
    background: #f0f1f4;
  }
}

.avatar {
  width: 29px;
  height: 29px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: #eff0f4;
  color: #555a66;
  font-size: 10px;
  font-weight: 700;
}

.account-dropdown {
  width: 100%;
}

.mobile-menu-shade {
  position: fixed;
  inset: 0;
  z-index: 15;
  background: rgba(0, 0, 0, 0.3);
}

.workspace {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.topbar {
  &.el-header {
    position: sticky;
    top: 0;
    z-index: 5;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 0 24px;
    border-bottom: 1px solid #e4e5e9;
    background: rgba(255, 255, 255, 0.97);
    backdrop-filter: blur(10px);
    color: #2f343d;
    font-size: 13px;
    height: 43px !important;
  }
}

.sidebar-toggle {
  &.el-button {
    display: grid;
    place-items: center;
    width: 28px;
    height: 28px;
    padding: 0;
    border: 0;
    background: transparent;
    border-radius: 5px;
    &:hover {
      background: #f1f2f4;
    }
  }
}

.project-breadcrumb {
  display: flex;
  align-items: center;
  gap: 12px;
}

.topbar-separator {
  color: #b7bac1;
}

.project-name {
  &.el-button--text {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 0;
    color: #3b3f48;
    font-size: 13px;
  }
}

.topbar-status {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 7px;
  color: #333842;
  font-size: 11px;
}

.date-range-select {
  width: 160px;
}

.refresh-button {
  &.el-button {
    height: 34px;
    padding: 0 11px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 5px;
    border: 1px solid #d9dce2;
    border-radius: 6px;
    background: #fff;
    color: #343943;
    font-size: 14px;
  }
}

.main-content {
  &.el-main {
    min-width: 0;
    padding: 14px 32px 40px;
  }
}

.mobile-brand,
.mobile-avatar {
  display: none;
}

.mobile-account-menu {
  display: none;
  z-index: 30;
  gap: 2px;
  padding: 6px;
  border: 1px solid #dcdfe4;
  border-radius: 7px;
  background: #fff;
  color: #30333b;
  box-shadow: 0 12px 35px rgba(16, 24, 40, 0.16);
  font-size: 11px;
  font-weight: 450;
}

.account-menu-title {
  display: block;
  padding: 6px 8px 5px;
  color: #777d88;
  font-size: 9px;
  font-weight: 600;
  line-height: 1.2;
}
</style>
