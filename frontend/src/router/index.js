import Vue from "vue";
import Router from "vue-router";

Vue.use(Router);

const router = new Router({
  mode: "history",
  base: "/icbc/hmp/agentobs/",
  routes: [
    { path: "/", redirect: "/home" },
    {
      path: "/home",
      name: "home",
      component: () => import(/* webpackChunkName: "home" */ "../features/observability/HomePage.vue")
    },
    {
      path: "/dashboards",
      name: "dashboards",
      component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
    },
    {
      path: "/traces",
      name: "tracing",
      component: () => import(/* webpackChunkName: "tracing" */ "../features/observability/TracingPage.vue")
    },
    {
      path: "/sessions",
      name: "sessions",
      component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
    },
    {
      path: "/users",
      name: "users",
      component: () => import(/* webpackChunkName: "users" */ "../features/observability/UsersPage.vue")
    },
    {
      path: "/prompts",
      name: "prompts",
      component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
    },
    {
      path: "/alerts",
      name: "alerts",
      component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
    },
    {
      path: "/playground",
      name: "playground",
      component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
    },
    {
      path: "/scores",
      name: "scores",
      component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
    },
    {
      path: "/evals",
      name: "evaluators",
      component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
    },
    {
      path: "/annotation-queues",
      name: "annotation",
      component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
    },
    {
      path: "/datasets",
      name: "datasets",
      component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
    },
    {
      path: "/experiments",
      name: "experiments",
      component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
    },
    {
      path: "/settings",
      name: "settings",
      component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
    },
    { path: "*", redirect: "/home" }
  ]
});

export default router;
