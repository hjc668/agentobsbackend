import Vue from "vue";
import Vuex from "vuex";
import { authApi } from "../api/client";
import { i18n } from "../i18n";

Vue.use(Vuex);

export default new Vuex.Store({
  state: {
    user: null,
    authLoading: true,
    authReady: false,
    authExpired: false,
    dateRange: new URLSearchParams(window.location.search).get("dateRange") || "30d",
    refreshKey: 0,
    mobileMenu: false,
    accountMenu: false,
    locale: localStorage.getItem("langfuse-locale") || "en"
  },

  getters: {
    isAuthenticated: state => !!state.user,
    isAdmin: state => state.user && state.user.role === "ADMIN",
    initials: state => {
      const source = (state.user && (state.user.displayName || state.user.aamId)) || "AAM";
      return source
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map(x => x[0])
        .join("")
        .toUpperCase();
    }
  },

  mutations: {
    SET_USER(state, user) {
      state.user = user;
    },
    SET_AUTH_LOADING(state, val) {
      state.authLoading = val;
    },
    SET_AUTH_READY(state, val) {
      state.authReady = val;
    },
    SET_AUTH_EXPIRED(state, val) {
      state.authExpired = val;
    },
    SET_DATE_RANGE(state, range) {
      state.dateRange = range;
    },
    INCREMENT_REFRESH(state) {
      state.refreshKey++;
    },
    SET_MOBILE_MENU(state, val) {
      state.mobileMenu = val;
    },
    SET_ACCOUNT_MENU(state, val) {
      state.accountMenu = val;
    },
    SET_LOCALE(state, locale) {
      state.locale = locale;
    }
  },

  actions: {
    async checkAuth({ commit }) {
      commit("SET_AUTH_LOADING", true);
      try {
        const user = await authApi.me();
        commit("SET_USER", user);
      } catch (e) {
        commit("SET_USER", null);
      } finally {
        commit("SET_AUTH_LOADING", false);
        commit("SET_AUTH_READY", true);
      }
    },

    async login({ commit }, { aamId, ticket }) {
      const user = await authApi.login(aamId, ticket);
      commit("SET_USER", user);
      commit("SET_AUTH_EXPIRED", false);
      commit("SET_AUTH_READY", true);
    },

    async logout({ commit }) {
      // Read the portal sign-out URL before the local session goes away. In AAM mode the
      // local logout alone is not enough: the unified-authentication portal keeps its own
      // SSO cookie, and without a full page navigation to this URL the next page load
      // signs the user straight back in.
      let portalLogoutUrl = null;
      try {
        portalLogoutUrl = (await authApi.config()).logoutUrl || null;
      } catch (error) {
        // Discovery is best effort: a failed lookup must not block signing out locally.
      }

      try {
        await authApi.logout();
      } finally {
        commit("SET_USER", null);
        commit("SET_AUTH_EXPIRED", false);
        commit("SET_ACCOUNT_MENU", false);
        commit("SET_MOBILE_MENU", false);
      }

      if (portalLogoutUrl) {
        // Full navigation, not an XHR: only the browser can present the portal's cookie,
        // and only a top-level request lets the portal clear its session and redirect back.
        window.location.href = portalLogoutUrl;
      }
    },

    changeDateRange({ commit }, range) {
      commit("SET_DATE_RANGE", range);
      commit("INCREMENT_REFRESH");
    },

    refresh({ commit }) {
      commit("INCREMENT_REFRESH");
    },

    onUnauthenticated({ commit, state }) {
      if (state.authReady) {
        commit("SET_AUTH_EXPIRED", true);
      }
      commit("SET_USER", null);
      commit("SET_ACCOUNT_MENU", false);
      commit("SET_MOBILE_MENU", false);
    },

    changeLanguage({ commit }, locale) {
      i18n.setLocale(locale);
      commit("SET_LOCALE", locale);
      commit("SET_ACCOUNT_MENU", false);
    }
  }
});
