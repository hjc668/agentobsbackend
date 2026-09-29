import Vue from "vue";
import Router from "vue-router";

Vue.use(Router);

const projectMatch = window.location.pathname.match(/^\/project\/([^/]+)/);
const defaultProjectId = projectMatch ? projectMatch[1] : "cmt2am51r0006pa07ghcbt7vi";
const projectPath = `/project/${defaultProjectId}`;

const homePath = `${projectPath}/home`;

const routes = [
  { path: "/", redirect: homePath },
  { path: `/project/${defaultProjectId}`, redirect: homePath },
  {
    path: `/project/${defaultProjectId}/home`,
    name: "home",
    component: () => import(/* webpackChunkName: "home" */ "../features/observability/HomePage.vue")
  },
  {
    path: `/project/${defaultProjectId}/dashboards`,
    name: "dashboards",
    component: () => import(/* webpackChunkName: "dashboards" */ "../features/dashboards/DashboardsPage.vue")
  },
  {
    path: `/project/${defaultProjectId}/traces`,
    name: "tracing",
    component: () => import(/* webpackChunkName: "tracing" */ "../features/observability/TracingPage.vue")
  },
  {
    path: `/project/${defaultProjectId}/sessions`,
    name: "sessions",
    component: () => import(/* webpackChunkName: "sessions" */ "../features/observability/SessionsPage.vue")
  },
  {
    path: `/project/${defaultProjectId}/users`,
    name: "users",
    component: () => import(/* webpackChunkName: "users" */ "../features/observability/UsersPage.vue")
  },
  {
    path: `/project/${defaultProjectId}/prompts`,
    name: "prompts",
    component: () => import(/* webpackChunkName: "prompts" */ "../features/prompts/PromptsPage.vue")
  },
  {
    path: `/project/${defaultProjectId}/alerts`,
    name: "alerts",
    component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
  },
  {
    path: `/project/${defaultProjectId}/playground`,
    name: "playground",
    component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
  },
  {
    path: `/project/${defaultProjectId}/scores`,
    name: "scores",
    component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
  },
  {
    path: `/project/${defaultProjectId}/evals`,
    name: "evaluators",
    component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
  },
  {
    path: `/project/${defaultProjectId}/annotation-queues`,
    name: "annotation",
    component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
  },
  {
    path: `/project/${defaultProjectId}/datasets`,
    name: "datasets",
    component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
  },
  {
    path: `/project/${defaultProjectId}/experiments`,
    name: "experiments",
    component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
  },
  {
    path: `/project/${defaultProjectId}/settings`,
    name: "settings",
    component: () => import(/* webpackChunkName: "placeholder" */ "../features/placeholders/UnderConstruction.vue")
  },
  { path: "*", redirect: homePath }
];

const router = new Router({
  mode: "history",
  routes
});

export default router;
