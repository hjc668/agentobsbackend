`frontend0914` 当前仍是手工输入 AAM 号和 ticket 的 Mock 登录，需要改为“自动跳转 AAM → `/aamlogin` 回调 → 提交 `SSIAuth/SSISign` → 恢复 Session”的模式。

# frontend0914 行内 AAM 接入改造方案

## 1. 当前问题

当前实现：

- [LoginPage.vue](/Users/mac/Desktop/icbc/Aiops/langfuse-web/frontend0914/src/features/auth/LoginPage.vue:18) 手工输入统一认证号和票据。
- [client.js](/Users/mac/Desktop/icbc/Aiops/langfuse-web/frontend0914/src/api/client.js:220) 调用 `/api/v1/auth/login`，提交 `aamId/ticket`。
- [App.vue](/Users/mac/Desktop/icbc/Aiops/langfuse-web/frontend0914/src/App.vue:200) 启动后只检查 `/api/v1/auth/me`。
- [router/index.js](/Users/mac/Desktop/icbc/Aiops/langfuse-web/frontend0914/src/router/index.js:12) 没有 `/aamlogin` 回调路由。
- [webpack.config.js](/Users/mac/Desktop/icbc/Aiops/langfuse-web/frontend0914/webpack.config.js:106) 只代理 `/api`，没有代理 `/aam/login/auth` 和 `/aamlogout`。

这些行为与行内 AAM 标准流程不一致。

## 2. 目标流程

```text
用户访问 Tracing 页面
        ↓
GET /api/v1/auth/me
        ↓
返回 401
        ↓
保存当前页面地址
        ↓
跳转后端 /api/aam/login
        ↓
AAM 统一认证
        ↓
回调前端 /aamlogin?SSIAuth=...&SSISign=...
        ↓
前端 POST /aam/login/auth
        ↓
后端验票并创建 Session
        ↓
GET /api/v1/auth/csrf
        ↓
GET /api/v1/auth/me
        ↓
恢复原页面
```

## 3. 文件改造清单

新增：

```text
src/features/auth/AamCallbackPage.vue
src/auth/aam.js
```

修改：

```text
src/router/index.js
src/api/client.js
src/store/index.js
src/App.vue
src/main.js
webpack.config.js
```

处理现有文件：

```text
src/features/auth/LoginPage.vue
```

该文件仅保留给本地 Mock 模式，AAM 模式不再展示账号和 ticket 输入框。

## 4. 增加 AAM 配置接口

前端启动时调用：

```text
GET /api/v1/auth/config
```

建议响应：

```json
{
  "mode": "aam",
  "ssicEnabled": true,
  "loginUrl": "/api/aam/login",
  "logoutUrl": "/aamlogout"
}
```

本地模式：

```json
{
  "mode": "mock",
  "ssicEnabled": false,
  "loginUrl": "",
  "logoutUrl": "/api/v1/auth/logout"
}
```

不要在前端代码中写死行内 AAM 域名。登录地址由后端返回，避免不同环境重新打包。

## 5. 新增 AAM 工具模块

新增 `src/auth/aam.js`，负责：

- 保存登录前页面。
- 跳转 AAM。
- 读取回调参数。
- 清理敏感 URL。
- 防止重复跳转。
- 恢复登录前页面。

建议接口：

```javascript
const RETURN_URL_KEY = "aam.returnUrl";
let redirecting = false;

export function saveReturnUrl(route) {
  const value = route.fullPath;

  if (
    value &&
    value.startsWith("/") &&
    !value.startsWith("//") &&
    !value.startsWith("/aamlogin")
  ) {
    sessionStorage.setItem(RETURN_URL_KEY, value);
  }
}

export function consumeReturnUrl(defaultPath) {
  const value = sessionStorage.getItem(RETURN_URL_KEY);
  sessionStorage.removeItem(RETURN_URL_KEY);

  if (!value || !value.startsWith("/") || value.startsWith("//")) {
    return defaultPath;
  }

  return value;
}

export function redirectToAam(loginUrl, route) {
  if (redirecting) return;

  redirecting = true;
  saveReturnUrl(route);
  window.location.assign(loginUrl);
}
```

登录前页面使用 `sessionStorage`，不要使用长期保存的 `localStorage`，避免用户下次登录跳到过期页面。

## 6. 新增 `/aamlogin` 路由

修改 `src/router/index.js`：

```javascript
{
  path: "/aamlogin",
  name: "aam-callback",
  component: () =>
    import(
      /* webpackChunkName: "aam-callback" */
      "../features/auth/AamCallbackPage.vue"
    ),
  meta: {
    public: true,
    aamCallback: true
  }
}
```

该路由必须位于通配符路由之前。

如果最终部署带应用上下文，需要同时设置 Router base：

```javascript
new Router({
  mode: "history",
  base: process.env.APP_PUBLIC_PATH || "/",
  routes
});
```

Web 服务器必须把 `/aamlogin` 回退到 `index.html`。

## 7. AamCallbackPage.vue

页面只显示：

```text
统一认证登录中……
```

核心行为：

1. 从 URL 读取原始 `SSIAuth` 和 `SSISign`。
2. 保存到组件内存。
3. 立即清理地址栏中的敏感参数。
4. POST `/aam/login/auth`。
5. 登录成功后重新获取 CSRF Token。
6. 调用 `/api/v1/auth/me` 获取最终用户。
7. 恢复登录前页面。

参考结构：

```javascript
export default {
  name: "AamCallbackPage",

  data() {
    return {
      loading: true,
      error: ""
    };
  },

  async mounted() {
    const credentials = readRawAamCredentials();

    window.history.replaceState(
      {},
      document.title,
      `${window.location.pathname}`
    );

    if (!credentials.SSIAuth || !credentials.SSISign) {
      this.error = "统一认证回调参数不完整";
      this.loading = false;
      return;
    }

    try {
      await this.$store.dispatch("completeAamCallback", credentials);

      const defaultPath =
        `/project/cmt2am51r0006pa07ghcbt7vi/traces`;

      const target = consumeReturnUrl(defaultPath);
      await this.$router.replace(target);
    } catch (error) {
      this.error = "统一认证失败，请重新登录";
      this.loading = false;
    }
  }
};
```

错误页面提供“重新登录”按钮，但绝不能显示：

- `SSIAuth`
- `SSISign`
- 后端原始异常堆栈

## 8. 回调参数读取

为了和参考后端“URLDecoder 解码一次”的行为一致，前端应把 URL 中的原始编码值传给后端，不要提前执行 `decodeURIComponent`。

```javascript
function rawQueryParameter(name) {
  const query = window.location.search.replace(/^\?/, "");
  const parts = query.split("&");

  for (const part of parts) {
    const separator = part.indexOf("=");
    if (separator < 0) continue;

    const key = part.substring(0, separator);
    if (key === name) {
      return part.substring(separator + 1);
    }
  }

  return "";
}

export function readRawAamCredentials() {
  return {
    SSIAuth: rawQueryParameter("SSIAuth"),
    SSISign: rawQueryParameter("SSISign")
  };
}
```

`page` 等普通导航参数可以使用 `URLSearchParams`，但 AAM 凭证保持原始编码。

## 9. 修改 API Client

删除 AAM 模式的：

```javascript
login(aamId, ticket)
```

新增：

```javascript
aamConfig: () =>
  request("/api/v1/auth/config", undefined, {
    suppressAuthRedirect: true
  }),

aamCallback: credentials =>
  rawMutate("/aam/login/auth", "POST", credentials, {
    csrf: false,
    suppressAuthRedirect: true
  }),

me: () =>
  request("/api/v1/auth/me", undefined, {
    suppressAuthRedirect: true
  }),

logout: logoutUrl =>
  mutate(logoutUrl, "POST")
```

AAM 回调请求：

```javascript
fetch("/aam/login/auth", {
  method: "POST",
  headers: {
    Accept: "application/json",
    "Content-Type": "application/json"
  },
  credentials: "include",
  body: JSON.stringify({
    SSIAuth,
    SSISign
  })
});
```

该请求不携带 CSRF Token。后端只豁免 `/aam/login/auth`。

需要解析行内响应包：

```javascript
const json = await response.json();

if (json.code !== "0") {
  throw new Error(json.msg || "AAM login failed");
}

return json.result;
```

## 10. Vuex 改造

当前 [store/index.js](/Users/mac/Desktop/icbc/Aiops/langfuse-web/frontend0914/src/store/index.js:80) 的 `login({aamId,ticket})` 应拆除或仅保留本地模式。

新增状态：

```javascript
authConfig: null,
authRedirecting: false
```

新增 Action：

```javascript
async bootstrapAuth({ commit, dispatch }) {
  commit("SET_AUTH_LOADING", true);

  const config = await authApi.config();
  commit("SET_AUTH_CONFIG", config);

  try {
    const user = await authApi.me();
    commit("SET_USER", user);
    commit("SET_AUTH_READY", true);
  } catch (error) {
    commit("SET_USER", null);

    if (
      config.mode === "aam" &&
      this._vm.$route.name !== "aam-callback"
    ) {
      redirectToAam(config.loginUrl, this._vm.$route);
      return;
    }

    commit("SET_AUTH_READY", true);
  } finally {
    commit("SET_AUTH_LOADING", false);
  }
}
```

回调 Action：

```javascript
async completeAamCallback({ commit }, credentials) {
  const result = await authApi.aamCallback(credentials);

  await authApi.csrf();

  const user = await authApi.me();

  commit("SET_USER", user);
  commit("SET_AUTH_EXPIRED", false);
  commit("SET_AUTH_READY", true);

  localStorage.setItem("loginType", "aam");

  return result;
}
```

最终用户信息以 `/api/v1/auth/me` 为准，不直接信任回调响应中的角色。

## 11. App.vue 改造

当前 App 在未登录时直接展示 LoginPage：

```vue
<LoginPage v-else-if="!user" />
```

应改成：

```vue
<router-view
  v-if="$route.meta && $route.meta.public"
/>

<div v-else-if="authLoading" class="auth-loading">
  正在验证登录状态……
</div>

<LoginPage
  v-else-if="!user && authConfig.mode === 'mock'"
  :expired="authExpired"
/>

<div v-else-if="!user" class="auth-loading">
  正在跳转统一认证……
</div>

<el-container v-else>
  <!-- 原应用布局 -->
</el-container>
```

启动时将：

```javascript
this.$store.dispatch("checkAuth");
```

替换为：

```javascript
if (!this.$route.meta.aamCallback) {
  this.$store.dispatch("bootstrapAuth");
}
```

AAM 回调页由自身完成认证，不能在回调参数处理前被 `checkAuth` 再次跳转。

## 12. 401 处理

当前 [main.js](/Users/mac/Desktop/icbc/Aiops/langfuse-web/frontend0914/src/main.js:13) 收到 401 后只清空用户，会重新显示手工登录页。

AAM 模式应改为：

```javascript
window.addEventListener("aam:unauthenticated", () => {
  store.dispatch("handleUnauthenticated", router.currentRoute);
});
```

处理步骤：

1. 清空用户状态。
2. 当前不在 `/aamlogin` 时保存完整路径。
3. 确保同一时间只发生一次重定向。
4. 跳转后端 `loginUrl`。

API Client 对以下请求不要再次触发全局重定向：

- `/api/v1/auth/config`
- `/api/v1/auth/me`
- `/aam/login/auth`
- `/aamlogout`

否则会形成登录循环。

## 13. 退出登录

修改 Vuex logout：

```javascript
async logout({ state, commit }) {
  try {
    await authApi.logout(state.authConfig.logoutUrl);
  } finally {
    commit("SET_USER", null);

    sessionStorage.removeItem("aam.returnUrl");
    localStorage.removeItem("loginType");
    localStorage.removeItem("userId");
    localStorage.removeItem("role");

    document.cookie =
      "LOGIN=; Max-Age=0; path=/";

    window.location.assign(
      state.authConfig.loginUrl
    );
  }
}
```

认证依据必须始终是后端 HttpOnly Session，不能把：

```text
LOGIN=1
loginType=aam
localStorage.user
```

作为真实登录凭据。

这些值最多用于界面兼容。

## 14. Webpack 本地代理

当前只代理 `/api`，需要增加：

```javascript
proxy: {
  "/api": {
    target: process.env.API_PROXY_TARGET || "http://localhost:8080",
    changeOrigin: true
  },
  "/aam/login/auth": {
    target: process.env.API_PROXY_TARGET || "http://localhost:8080",
    changeOrigin: true
  },
  "/aamlogout": {
    target: process.env.API_PROXY_TARGET || "http://localhost:8080",
    changeOrigin: true
  }
}
```

不要 rewrite 路径。

本地 Mock 模式不会真正访问 AAM，因此可以继续通过原 `LoginPage.vue` 登录。

## 15. 部署路径

如果行内部署地址带上下文，例如：

```text
/icbc/aiops/langfuse
```

需要统一配置：

```text
Webpack publicPath
Vue Router base
后端 context-path
AAM client site_url
Nginx history fallback
API 反向代理路径
```

AAM 回调地址必须最终打开：

```text
/icbc/aiops/langfuse/aamlogin
```

后端跳转入口则是：

```text
/icbc/aiops/langfuse/api/aam/login
```

不要在不同文件里分别拼接上下文路径。

## 16. 安全要求

1. `SSIAuth/SSISign` 不写入 localStorage。
2. 不写入 sessionStorage。
3. 不输出到 console。
4. 不放进错误提示。
5. 提取后立即清理浏览器 URL。
6. returnUrl 只允许站内 `/` 开头路径。
7. 禁止 `//host/path`，防止开放重定向。
8. AAM 回调接口使用 `credentials: "include"`。
9. 业务写接口继续携带 CSRF Token。
10. 前端 role 只用于隐藏按钮，后端仍必须强制鉴权。

## 17. 测试场景

必须覆盖：

- 未登录访问 Tracing 自动跳转 AAM。
- 原始 URL 的 path、query、筛选条件登录后恢复。
- `/aamlogin` 正确读取 `SSIAuth/SSISign`。
- 参数缺失展示错误。
- 后端返回 `code != "0"` 展示错误。
- 回调后地址栏不再包含 AAM 参数。
- 回调期间不发生重复 AAM 跳转。
- `/api/v1/auth/me` 正确恢复用户和角色。
- VIEW 用户不显示管理操作。
- ADMIN 用户显示管理操作。
- Session 过期后自动重新认证。
- Logout 清理 AAM 与本地状态。
- 浏览器前进/后退不会重新提交旧 AAM 凭证。
- 刷新 `/aamlogin` 且没有参数时不会死循环。
- 本地 Mock profile 仍可开发。
- `/aam/login/auth` 和 `/aamlogout` 在开发代理下可访问。

## 18. 实施顺序

1. 增加 `/aamlogin` 路由和 `AamCallbackPage.vue`。
2. 增加 `src/auth/aam.js`。
3. 改造 API Client。
4. 改造 Vuex 登录状态机。
5. 改造 App.vue 登录分支。
6. 改造全局 401 处理。
7. 改造退出登录。
8. 补充 Webpack 代理与部署 base。
9. 保留本地 Mock 登录。
10. 与后端 Hermes AAM 接口联调。

## 19. 完成标准

- AAM 模式不再显示账号和 ticket 输入框。
- 未登录用户自动进入行内统一认证。
- 回调调用 `/aam/login/auth`。
- 请求字段为 `SSIAuth/SSISign`。
- 回调凭证不会持久化或输出日志。
- 登录后通过 `/api/v1/auth/me` 恢复身份。
- Trace 筛选 URL 登录后能够恢复。
- Session 过期不会停留在空白页面。
- Logout 同时退出 AAM 和业务 Session。
- 本地 Mock 登录仍能独立使用。

需要特别注意：[frontend0914 的 App.vue](/Users/mac/Desktop/icbc/Aiops/langfuse-web/frontend0914/src/App.vue:6) 当前在未登录时直接截断了 `<router-view>`，因此仅增加 `/aamlogin` 路由还不够；必须同时调整 App 顶层渲染顺序，否则 AAM 回调组件不会真正显示和执行。