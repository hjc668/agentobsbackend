import Vue from "vue";
import ElementUI from "element-ui";
import "element-ui/lib/theme-chalk/index.css";
import "./styles/global.scss";
import "./i18n";
import router from "./router";
import store from "./store";
import App from "./App.vue";

Vue.config.productionTip = false;
Vue.use(ElementUI);

// Global event listeners for auth events
window.addEventListener("aam:unauthenticated", () => {
  store.dispatch("onUnauthenticated");
});

new Vue({
  router,
  store,
  render: h => h(App)
}).$mount("#root");
