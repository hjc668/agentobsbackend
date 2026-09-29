<template>
  <div>
    <div v-if="authLoading" class="auth-loading">
      <i class="el-icon-loading" /> {{ $locale === "zh" ? "正在验证登录状态…" : "Checking your session…" }}
    </div>
    <LoginPage v-else-if="!user" :expired="authExpired" :on-login="handleLogin" />
    <el-container v-else class="app-shell" :class="{ 'menu-open': mobileMenu, 'sidebar-collapsed': sidebarCollapsed }">
      <el-aside :width="sidebarCollapsed ? '48px' : '184px'" class="sidebar-aside">
        <div class="brand-row">
          <img class="brand-logo" src="/wordart-white.svg" alt="Langfuse" />
          <el-button v-if="!sidebarCollapsed" type="text" size="mini" class="version">智能体可观测</el-button>
        </div>
        <el-menu :default-active="activeMenu" class="sidebar-nav" :collapse="sidebarCollapsed" router>
          <el-menu-item-group v-for="group in navGroups" :key="group.key">
            <template v-if="group.label && !sidebarCollapsed" slot="title">
              <span class="nav-group-label">{{ group.label }}</span>
            </template>
            <el-menu-item v-for="item in group.items" :key="item.id" :index="item.path">
              <NavIcon :name="item.icon" :size="16" />
              <span v-if="!sidebarCollapsed">{{ $t(`nav.${item.label}`) }}</span>
            </el-menu-item>
          </el-menu-item-group>
        </el-menu>
        <div class="sidebar-spacer" />
        <el-menu :default-active="activeSecondaryMenu" class="secondary-nav" :collapse="sidebarCollapsed" router>
          <el-menu-item :index="settingsItem.path">
            <NavIcon name="settings" :size="16" />
            <span v-if="!sidebarCollapsed">{{ $t("nav.settings") }}</span>
          </el-menu-item>
        </el-menu>
        <div v-if="!sidebarCollapsed" class="account-wrap">
          <el-dropdown trigger="click" @command="handleAccountCommand" class="account-dropdown">
            <div class="account-row">
              <div class="avatar">{{ initials }}</div>
              <div>
                <strong>{{ user.displayName }}</strong>
                <small>{{ user.aamId }} · {{ user.department }}</small>
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
        <div v-else class="account-wrap-collapsed">
          <el-dropdown trigger="click" @command="handleAccountCommand">
            <div class="avatar avatar-sm">{{ initials }}</div>
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
            :icon="sidebarCollapsed ? 'el-icon-s-unfold' : 'el-icon-s-fold'"
            aria-label="Toggle sidebar"
            @click="toggleSidebar"
          />
          <img class="mobile-brand" src="/wordart-black.svg" alt="Langfuse" />
          <el-button class="mobile-avatar" @click="mobileAccountMenu = !mobileAccountMenu">{{ initials }}</el-button>
          <div class="project-breadcrumb">
            <span class="breadcrumb-current">{{ currentPageLabel }}</span>
          </div>
          <div class="topbar-status">
            <el-select v-model="dateRange" size="small" class="date-range-select" @change="rangeChanged">
              <el-option value="24h" :label="$t('app.past24h')" />
              <el-option value="7d" :label="$t('app.past7d')" />
              <el-option value="30d" :label="$t('app.past30d')" />
              <el-option value="90d" :label="$t('app.past90d')" />
            </el-select>
            <el-button class="refresh-button" icon="el-icon-refresh" aria-label="Refresh" @click="handleRefresh" />
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

const navigation = [
  { id: "home", label: "home", path: "/home", icon: "home" },
  { id: "dashboards", label: "dashboards", path: "/dashboards", icon: "dashboard" },
  { id: "tracing", label: "tracing", path: "/traces", icon: "trace", group: "observability" },
  { id: "sessions", label: "sessions", path: "/sessions", icon: "clock", group: "observability" },
  { id: "users", label: "users", path: "/users", icon: "users", group: "observability" },
  { id: "alerts", label: "alerts", path: "/alerts", icon: "bell", group: "observability" },
  { id: "prompts", label: "prompts", path: "/prompts", icon: "file", group: "prompt" },
  { id: "playground", label: "playground", path: "/playground", icon: "terminal", group: "prompt" },
  { id: "scores", label: "scores", path: "/scores", icon: "score", group: "evaluation" },
  { id: "evaluators", label: "evaluators", path: "/evals", icon: "bulb", group: "evaluation" },
  {
    id: "annotation-queues",
    label: "annotation",
    path: "/annotation-queues",
    icon: "clipboard",
    group: "evaluation"
  },
  { id: "datasets", label: "datasets", path: "/datasets", icon: "database", group: "evaluation" },
  { id: "experiments", label: "experiments", path: "/experiments", icon: "beaker", group: "evaluation" }
];

const settingsItem = { id: "settings", label: "settings", path: "/settings" };

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
      mobileAccountMenu: false,
      sidebarCollapsed: localStorage.getItem("sidebar-collapsed") === "1"
    };
  },
  computed: {
    ...mapState(["user", "authLoading", "authReady", "authExpired", "refreshKey"]),
    ...mapGetters(["initials", "isAdmin"]),
    activeMenu() {
      const path = this.$route.path;
      const match = navigation.find(item => {
        if (item.id === "home") return path === "/home" || path === "/home/";
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
    },
    currentPageLabel() {
      const path = this.$route.path;
      if (path.startsWith(settingsItem.path)) {
        return this.$t("nav.settings");
      }
      const active = navigation.find(item => {
        if (item.id === "home") return path === "/home" || path === "/home/";
        return path.startsWith(item.path);
      });
      return active ? this.$t(`nav.${active.label}`) : "";
    }
  },
  created() {
    this.$store.dispatch("checkAuth");
  },
  methods: {
    ...mapActions(["refresh", "changeDateRange", "changeLanguage"]),
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed;
      localStorage.setItem("sidebar-collapsed", this.sidebarCollapsed ? "1" : "0");
    },
    handleAccountCommand(command) {
      if (command === "lang-zh") this.changeLanguage("zh");
      else if (command === "lang-en") this.changeLanguage("en");
      else if (command === "logout") this.handleLogout();
    },
    handleLogin(aamId, ticket) {
      return this.$store.dispatch("login", { aamId, ticket }).then(() => {
        this.$router.push("/home");
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
  transition: width 0.2s ease;
}

.sidebar-collapsed .sidebar-aside {
  align-items: center;
}

.sidebar-collapsed .brand-row {
  justify-content: center;
  padding: 0;
}

.sidebar-collapsed .brand-logo {
  width: 24px;
}

.sidebar-collapsed .sidebar-nav,
.sidebar-collapsed .secondary-nav {
  padding: 0 6px;
  width: 100%;
}

.sidebar-collapsed ::v-deep .sidebar-nav .el-menu-item,
.sidebar-collapsed ::v-deep .secondary-nav .el-menu-item {
  justify-content: center;
  padding: 0 !important;
}

.account-wrap-collapsed {
  padding: 0 6px 6px;
  display: flex;
  justify-content: center;
}

.avatar-sm {
  width: 29px;
  height: 29px;
  cursor: pointer;
  border-radius: 6px;
  background: #eff0f4;
  color: #555a66;
  font-size: 10px;
  font-weight: 700;
  display: grid;
  place-items: center;
}

.brand-row {
  min-height: 43px;
  display: flex;
  align-items: center;
  padding: 0 10px;
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
    cursor: pointer;
    &:hover {
      color: #6374ee;
    }
  }
}

.breadcrumb-current {
  font-size: 13px;
  color: #11131a;
  font-weight: 500;
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
