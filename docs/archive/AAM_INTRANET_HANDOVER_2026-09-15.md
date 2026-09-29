# 内网接续：让 AAM 接入用真实 jar 跑通

> 这份文档面向**拿到行内环境之后的接续者**（人或模型）。
> 在公网/本地环境无法完成的部分，到这里必须做完。
>
> 项目：`langfuse-web`　后端：Spring Boot 2.7.18 + **Java 8** + MyBatis，端口 8080

---

## 0. 任务书 —— 先读完这一节再动手

### 0.1 背景

`langfuse-web` 的 AAM 接入**策略层已全部完成并通过 132 个测试**，但 **Hermes 握手本身从未编译过**：
开发机上拿不到行内 Maven 仓库，那段代码被隔离在 `src/main/aam/java`，只在 `-Paam` 下参与编译。
**你现在在能拿到仓库的环境里，任务就是把这段未验证的代码跑通。**

### 0.2 ⚠️ 最重要的一条：不要照抄本文档 §3 的签名表

**§3 里那张调用签名表是假设，不是事实。** 它是从参考工程 `dsf-self-analysis` 反推出来的
（`LoginController` 的 422–486 行），**没有一条对着真实 jar 验证过**。

- **用 `javap` 读真实签名，以 jar 为准。**
- **签名对不上是预期内的，不是错误。** 修正它，并把偏差记下来。
- 不要把本文档当成规范 —— 它是一份**待验证的假设清单**。照抄会把错误固化。

### 0.3 先读

| 文件 | 看什么 |
|---|---|
| `PROJECT_PROGRESS.md` **§31 / §32** | AAM 的设计决策、已验/未验边界、验收整改 |
| `PROJECT_PROGRESS.md` **§9** | 接续开发检查清单（每次开工先过一遍） |
| `PROJECT_PROGRESS.md` **§34.1** | **本机可用 Chrome + CDP 做浏览器自动化**（零依赖） |
| `backend/src/main/aam/java/` | **唯一未经实测的代码**（两个文件） |

### 0.4 第一步：确认环境（不要跳过）

```bash
cd backend
mvn -o test                     # 先确认基线：期望 132/132
mvn -Paam dependency:resolve    # 能解析 = 环境 OK
mvn -Paam dependency:tree | grep -E "hermes|sirius|ssic"
```

细节见 §2。仓库地址配 `settings.xml`，**不要写进 pom**。

### 0.5 核心工作

```bash
mvn -Paam clean compile         # 大概率报错 —— 报错信息就是你要的东西
```

逐个报错 → `javap` 读真实签名 → 修正适配器 → **记录偏差**。完整流程见 §3。

### 0.6 🛑 什么时候必须停下来问人

这两个地方**不要自作主张**：

1. **如果发现必须改 `src/main/java/` 里的代码才能编译** —— 停下来说明情况。
   那说明端口边界设计需要调整（例如 `AamConfig.getServerSideAuth()` 返回的不是 ssic 类型），
   属于**设计变更**，不是改签名。
2. **不要为了让代码编译过，把 hermes 依赖泄漏进默认构建。** 见 §1.3。

### 0.7 本文档导航

| 节 | 内容 |
|---|---|
| §1 | 硬约束（破坏了就是事故） |
| §2 / §2.1 | 依赖解析；**可选**去掉 `-Paam` |
| **§3** | **核心：用 javap 逐个核对真实签名** |
| §4 | 填 `application.yml`（无环境变量、无 profile） |
| §5 | 门户登录/退出地址；**退出必须是"两半"** |
| §6 | 前端 `/aamlogin` 回调页（**此前完全未做**） |
| §7 | 端到端验收清单 |
| §8 / §9 | 数据库；单点登录控制（未实现，待确认是否强制） |
| §10 | **完成后要写回哪些内容** |
| 附录 | 本地已验部分（不必重做）+ 常见坑 |

---

## 1. 硬约束（破坏了就是事故）

1. **默认构建不得依赖任何 `com.icbc` 行内构件。** 行内依赖只允许出现在 `-Paam` profile 里。
   验收：`mvn -o test` 必须仍是 **132/132 通过**。
2. **运行期只有一个开关：`application.yml` 的 `app.auth.mode`（`mock`/`aam`）。**
   没有 Spring profile，没有环境变量。**不要**引入 `application-aam.yml` 或 `@Profile("aam")` ——
   原因见 `PROJECT_PROGRESS.md` §31.4：mock 认证器是 `matchIfMissing=true`，用 profile 会让 AAM
   部署在漏设开关时**静默退回 Mock 登录**，这是明令禁止的降级。
3. **Maven 的 `aam` profile 必须保留。** 它管的是「没有行内 jar 时能不能编译」，不是运行期开关。
   去掉它会让默认源码树 import 到 `com.icbc.hermes.*`，本地连编译都过不去，
   mock 模式、全部测试、前端联调一起停摆。
4. **表名一律带 `langfuse_` 前缀**（§29）。改 mapper 必须同步改 `backend/sql/polardbx-schema.sql`。
5. **日志里绝不能出现 `SSIAuth`、`SSISign`、私钥或票据。**
6. **PolarDB-X 不支持 `UPDATE ... SET col = (SELECT ...)`**（§29.3，报 `PXC-4518`）。
   与本任务无关，但会让 Dashboard 更新接口 500，**别误判成本次改动引入的**。

---

## 2. 让依赖可解析

把行内构件装进本地仓库，或配置 `settings.xml` 指向行内私服：

- `com.icbc.hermes:hermes-aam:6.69.5500050.0`（已排除 `com.icbc.hsm:hsm-hardware-encryption`）
- `com.icbc.sirius:sirius-aam-ldap:1.0.21`

```bash
mvn -Paam dependency:resolve
mvn -Paam dependency:tree | grep -E "hermes|sirius|ssic"
```

**版本号如果和私服不一致，只改 `pom.xml` 里 `<properties>` 的 `hermes.version` /
`sirius.version` 两行即可**，不用动依赖块。仓库地址配在 `settings.xml` 的
`<mirrors>`/`<servers>`，**不要写进 pom**。

> ⚠️ **不要把 `sirius-aam-ldap` 删掉。**
> `com.icbc.sirius.aam.ldap.UserAuthenticate` 和 `AamUser` 都来自这个构件，
> `IcbcAamConfiguration` 里有 `new UserAuthenticate()`、适配器里 import 了这两个类型。
> 删了会直接编译不过。只有当依赖树显示 hermes-aam 已传递引入**同一个** sirius 构件时才考虑精简。

`build-helper-maven-plugin:3.6.1` 也需要在私服存在；若没有，换成私服有的版本。

---

## 2.1 可选：去掉 `-Paam`，把 AAM 并入默认构建

上面所有命令都带 `-Paam`。如果不想每次记这个标志（且内网仓库始终可达），可以把它彻底去掉。
**三步，纯机械改动；改完代码里一行都不用动。**

### 代价（先读）

去掉 profile 后，**任何拿不到内网仓库的机器都无法构建**：不是 AAM 用不了，而是
`mvn clean test` 直接失败，mock 模式、132 个测试、前端联调全部停摆。
**只有当这个工程以后只在内网构建时才建议做。**

### 第 1 步：`backend/pom.xml`

**(a)** 两个版本号从 profile 的 `<properties>` 提到顶层 `<properties>`：

```xml
<properties>
    <java.version>1.8</java.version>
    <!-- …已有的其它属性不动… -->
    <hermes.version>6.69.5500050.0</hermes.version>
    <sirius.version>1.0.21</sirius.version>
</properties>
```

**(b)** 把 `hermes-aam` 和 `sirius-aam-ldap` 两个 `<dependency>` 整块从 profile 移到顶层
`<dependencies>`（内容照抄，`<exclusions>` 保留）。

**(c)** **删掉整个 `<profile><id>aam</id>…</profile>` 块**，包括里面的
`build-helper-maven-plugin`。`enforce-java8-api-on-newer-jdks` 那个 profile **保留**。

### 第 2 步：移动源码到标准位置

```bash
cd backend
mv src/main/aam/java/com/icbc/aiops/langfuse/security/HermesAamTicketAuthenticator.java \
   src/main/java/com/icbc/aiops/langfuse/security/
mv src/main/aam/java/com/icbc/aiops/langfuse/config/IcbcAamConfiguration.java \
   src/main/java/com/icbc/aiops/langfuse/config/
rm -rf src/main/aam
```

包名本来就一致（`com.icbc.aiops.langfuse.security` / `.config`），**文件内容不需要改**。

### 第 3 步：清理过时注释

改完这些地方提到的 `-Paam` / "aam Maven source root" 就过时了：

| 位置 | 要改什么 |
|---|---|
| `AamTicketAuthenticator.java` | 类 Javadoc 里 "lives in the `aam` Maven source root" 那段 |
| `IcbcAamConfiguration.java` | 类注释里 "Compiled only under the `aam` Maven profile" |
| `AAM_INTRANET_HANDOVER.md` | §3 标题、§4 构建命令、附录 |
| `PROJECT_PROGRESS.md` | §31.1 表格、§31.4 整节、§31.7 构建命令 |

### 改完后的验证

```bash
mvn clean test          # 期望 132/132
mvn clean package -DskipTests
```

**这个验证必须在能解析依赖的机器上做** —— 正是它区别于"看起来对了"的唯一依据。

---

## 3. 让 `-Paam` 编译通过 —— 逐个核对真实签名

**这一步是本次任务的核心，也是唯一需要动代码的地方。**

不要凭记忆或凭本文档猜测 API。**用 `javap` 直接读真实 jar 的签名**：

```bash
HERMES=~/.m2/repository/com/icbc/hermes/hermes-aam/6.69.5500050.0/hermes-aam-6.69.5500050.0.jar
javap -classpath "$HERMES" com.icbc.hermes.aam.wrapper.AamWrapper com.icbc.hermes.aam.AamConfig

# ssic 类可能在 hermes-aam 内，也可能在独立 jar，先定位：
for j in $(find ~/.m2/repository/com/icbc -name '*.jar'); do
  unzip -l "$j" 2>/dev/null | grep -q 'ssic/base/Credentials.class' && echo "FOUND: $j"
done
javap -classpath <找到的jar> com.icbc.ssic.base.Credentials \
      com.icbc.ssic.base.SSICUser com.icbc.ssic.base.ServerSideAuthenticator
```

### 需要逐一核对的调用

本地是**照参考工程** `dsf-self-analysis/src/main/java/com/icbc/dsf/self/analysis/ctl/LoginController.java`
的 422–486 行（AAM 登录/登出）写出来的，可能有偏差：

| 位置 | 本地代码的假设 | 核对方式 |
|---|---|---|
| `IcbcAamConfiguration` | `@EnableAam` 在 `com.icbc.hermes.aam.EnableAam` | `unzip -l` 找 `EnableAam.class` |
| `IcbcAamConfiguration` | 构造器 `new AamWrapper("SM2")` 存在 | 参考工程 `ShiroConfig.java:192` 同款 |
| `HermesAamTicketAuthenticator` | `aamConfig.getServerSideAuth()` → `com.icbc.ssic.base.ServerSideAuthenticator` | `javap AamConfig` |
| 同上 | `aamWrapper.auth(request, response, ssiAuth, ssiSign, authenticator)` → `boolean` | `javap AamWrapper` |
| 同上 | `request.getAttribute("ssiCredentials")` 可强转为 `com.icbc.ssic.base.Credentials` | 属性名以参考实现为准 |
| 同上 | `((Credentials) v).getSSICUser()` → `SSICUser`；`ssicUser.getUserName()` | `javap SSICUser` |
| 同上 | `aamWrapper.logout(request, response, "", "", authenticator)` | 参考实现 483 行同款 |
| 同上 | `com.icbc.sirius.aam.ldap.UserAuthenticate#searchAamUser(String)` → `AamUser`；`AamUser.getUserName()/getDepartmentId()/getDepartmentName()`；抛 `NamingException` | `javap UserAuthenticate AamUser` |
| 同上 | `new UserAuthenticate()` 有无参构造器（参考工程 `conf/AAMConfig` 用的就是它） | `javap UserAuthenticate` |

### 改动范围

**严格限制在 `src/main/aam/java/` 下的两个文件内。**

如果发现必须改 `src/main/java` 里的代码才能编译，**先停下来**——那说明端口边界设计需要调整
（例如 `AamConfig.getServerSideAuth()` 返回的不是 ssic 类型），不是简单改签名。请先说明情况。

### 编译通过后立刻回归

```bash
mvn -o test          # 期望 132/132
mvn -Paam compile    # 期望 BUILD SUCCESS
```

---

## 4. 填真实的 AAM 配置（全部在 `application.yml` 里）

编辑 `backend/src/main/resources/application.yml`：

1. 把 `app.auth.mode` 从 `mock` 改成 `aam`。
2. 填文件**底部的 `aam:` 块**：

   | 键 | 说明 |
   |---|---|
   | `aam.ssic.server.ip` | AAM SSIC 服务端地址 |
   | `aam.ssic.server.pub_key_path` | 服务端 SM2 公钥路径 |
   | `aam.ssic.client.site_url` | **前端**回调页，形如 `https://<域名>/<上下文>/aamlogin`。**必须指向前端页面，不是后端接口**——门户把 `SSIAuth/SSISign` 回给该页面，页面再 POST 到 `/aam/login/auth` |
   | `aam.ssic.client.key_name` | 本应用的客户端标识 |
   | `aam.ssic.client.pri_key_path` | 本应用 SM2 私钥路径 |
   | `aam.ssic.client.pri_key_passwd` | 私钥口令 |

**不需要设任何环境变量，不需要激活任何 Spring profile，不需要改启动脚本。**

这些值必须向行内 AAM 团队申请**本应用自己的**，不要从别的工程复制。

```bash
mvn -Paam -DskipTests package
java -jar target/langfuse-web-service-0.1.0-SNAPSHOT.jar
```

如果 `mode=aam` 而某些值还是空的，启动会**直接失败**并逐条列出缺哪些键——这是刻意的
（`AamConfigurationValidator`，在实例化任何 bean 之前触发），
避免 AAM 部署在没配好凭据的情况下跑起来。清空 `aam:` 块时 mock 模式仍可正常启动。

> ⚠️ **`pri_key_passwd` 是明文口令。填好后不要把 `application.yml` 提交回仓库。**
> 若部署平台能注入单个变量，可把这一个值改回 `${AAM_CLIENT_PRIVATE_KEY_PASSWORD}`。

---

## 5. 确认门户入口与退出地址

`/api/aam/login` 与 `/api/aam/logout` 都由 Hermes 的 `@EnableAam` 注册，**本地未自建**。启动后确认：

- 实际路径是否就是 `/api/aam/login` 与 `/api/aam/logout`？
- `SecurityConfig` 已放行 `/api/aam/login`；若实际路径不同，需同步补上。
- `GET /api/v1/auth/config` 返回的 `loginUrl` / `logoutUrl` 应指向真实路径；
  路径不同就改 `application.yml` 的 `app.auth.portal.login-url` / `logout-url`，**不必改代码**。

### 退出必须是"两半"（容易漏）

```
1. 前端 XHR:  POST /api/v1/auth/logout     -> 后端清 AAM 侧 + 本地 session
2. 前端整页:  window.location.href = <config.logoutUrl>   -> 门户清自己的 SSO Cookie
```

**第 2 步不能省，也不能用 XHR 代替** —— 只有整页导航会带上门户 Cookie。缺了它，用户"退出"后
下次打开页面会被 AAM 直接重新登入。参考前端（`Navbar/index.vue`）在**成功和失败两条分支里都跳转**，
本项目前端也已按同样语义实现（`store/index.js` 的 `logout`）。

> ⚠️ 该前端改动**未在本机构建验证**（`frontend/node_modules` 缺失，且目录技术栈待确认，
> 见 `PROJECT_PROGRESS.md` §32.3）。内网请先跑一次前端构建。

---

## 6. 前端回调页（本轮完全未做）

需要新增 `/aamlogin` 页面：接收 URL 上的 `SSIAuth`/`SSISign`，POST 到 `/aam/login/auth`，
成功后带返回的 `csrfToken` 进入 Observability；退出时清 `LOGIN`、`loginType` 和本地用户缓存。

- 前端：Vue 2.7 + Element UI + Vite，端口 5173
- 入口：`frontend/src/main.js`；API 封装：`frontend/src/api/client.ts`
- 参考：`hmpnodejs/dsf-self-analysis-ui/src/modules/login/aam.vue`
- 后端已提供 `GET /api/v1/auth/config`（匿名）供前端判断当前该走哪条登录流程

---

## 7. 端到端验收（全部要真实跑通）

1. 未登录访问页面 → 跳转统一认证 → 回调 → 进入 Observability。
2. 后端日志确认走的是 `AamWrapper.auth()`，身份来自 `ssiCredentials`。
3. `GET /api/v1/auth/me` 在页面刷新后能恢复身份。
4. 数据库给某用户配 `ADMIN`（`langfuse_user_roles`），该用户能写；未配置用户只能读，
   写操作返回 **403**。
5. 用**伪造的 `SSIAuth`** 登录 → 返回 `{"code":"1"}`，HTTP 仍 200。
6. 请求体里带 `"role":"ADMIN"` → **无效**，权限不变。
7. 其他写接口不带 `X-XSRF-TOKEN` → **403**；`/aam/login/auth` 不带 → 正常通过。
8. 登录前后 session id 已更换。
9. `/aamlogout` 与 `/api/v1/auth/logout` 都能同时失效 AAM 与本地会话。
   **并确认前端在退出后整页跳到了 `logoutUrl`**；门户不可达时本地会话仍应失效。
10. `app.auth.mode=mock` → `/api/v1/auth/login` 可用、`/aam/login/auth` 返回 **404**；
    切到 `aam` → 反之。清空 `aam:` 块并以 `aam` 启动 → **启动失败**并列出缺失键。
11. 全量日志搜索 `SSIAuth`/`SSISign`/私钥 → **0 命中**。

---

## 8. 数据库

新表 `langfuse_user_roles`（`aam_user_no` 主键 + `role_code`，只允许 `VIEW`/`ADMIN`）。

> **注意**：`role_code` 白名单外取值、用户无行、**以及数据库不可达**，一律回落 **VIEW**（拒绝提升）。
> 若行内希望库挂掉时直接拒绝登录而不是降为只读，需要显式改这一策略。

DDL 与 mapper SQL 已在真实 PolarDB-X 5.4.19 验证过，内网直接执行
`backend/sql/polardbx-schema.sql`（全部 `IF NOT EXISTS`）。

PolarDB-X 会拒绝无 `WHERE` 的 `DELETE FROM t`（`PXC-4620`），需带条件或加
`/*TDDL:FORBID_EXECUTE_DML_ALL=FALSE*/`。

---

## 9. 单点登录控制（方案 §14，未实现）

参考工程登录后写 JNOS Redis `sso-portal-{userId}` = `sessionId:remoteAddress`，TTL 30 分钟。

**这不是验票的必要条件**，但上线前需向行内 AAM/安全团队确认是否强制。若强制再实现。

---

## 10. 完成后

1. 往 `PROJECT_PROGRESS.md` **末尾追加新的带日期章节**（编号顺延，当前到 §31），
   如实区分「已验证 / 未验证」。**不要用「全部通过」掩盖失败项。**
2. 同步更新 `PROJECT_PROGRESS.md` §9 检查清单里与之相关的条目（测试基线数字、新的硬约束）。
3. **如果任何 API 签名与本文档的假设不符，逐条记录偏差**——那是下一个人最需要的信息。
4. 本文档可以删除或标注「已完成」。

---

## 附：本地已经验证过的部分（不需要你在内网重做）

这些都有真实测试，内网只需保证仍然通过：

| 部分 | 测试类 |
|---|---|
| 10 位用户号去首位标准化 | `AamUserIdNormalizerTest`（5 项） |
| 权限解析（白名单 / 表读 / 未知 code / 库不可达） | `AamRoleResolverTest`（7 项） |
| AAM 配置缺失时启动失败且不泄漏口令 | `AamConfigurationValidatorTest`（4 项） |
| aam 模式全链路（登录、CSRF、会话轮换、退出、路由隔离、伪造 role 无效、departmentId/Name、logoutUrl、门户不可达仍能退出） | `AamSsoIntegrationTest` |
| mock 模式（登录、CSRF、权限、退出、AAM 路由不可达、无 logoutUrl） | `AamSecurityIntegrationTest` |

`mvn -o clean test` 合计 **132/132**，默认构建**不引入任何行内依赖**。

> **务必用 `clean`**：Maven 增量编译在"测试源码没变、只变了依赖类"时不会重编测试，
> 会报误导性的运行期 `NoSuchMethodError` 而不是编译错误（`PROJECT_PROGRESS.md` §32.5）。

### 尚未验证的部分

- **Hermes 适配器**（§3）——本机无 jar，编译都过不去。**这是你的主要工作。**
- **前端退出的门户跳转**（§5 那"第二半"）——代码已写，但**未点过**。
  `departmentName` 显示已在无头浏览器中确认生效（`PROJECT_PROGRESS.md` §34.5）。
- **前端回调页 `/aamlogin`**（§6）——完全未做。
- `frontend/` 与 `frontend_back/` 目录技术栈冲突未解决，且 `scripts/frontend-runtime.sh`
  仍按 Vite 工程写（`PROJECT_PROGRESS.md` §34.3）。

---

## 附二：容易踩的坑

| 坑 | 说明 |
|---|---|
| **数 DOM 用 `grep`** | 会把内联 CSS 里的类名一起数进去，得到假阳性。**必须用 `querySelectorAll`**（§34.6 踩过） |
| **UI 打不开就先查前端** | **先 `curl /actuator/health` + `lsof -i:8080`**。只在日志里 grep `ERROR` 会漏掉"Maven `BUILD FAILURE` 但日志前半段干净"的情况（§34.4 因此白查半小时） |
| **`mvn test` 不 clean** | 改了依赖类但没改测试源码时，增量编译不重编测试，会报**误导性的运行期 `NoSuchMethodError`**。**用 `clean`**（§32.5） |
| PolarDB-X 限制 | 不支持 `UPDATE ... SET col = (SELECT ...)`（`PXC-4518`）；拒绝无 `WHERE` 的 `DELETE`（`PXC-4620`） |
| 表名前缀 | 一律带 `langfuse_`；改 mapper 必须同步改 `backend/sql/polardbx-schema.sql` |
| Element UI 的 `el-menu-item` | `index` 是内部 prop，**不渲染成 DOM 属性**，`getAttribute('index')` 恒为 `null`，不是 bug（§34.6） |
| 浏览器缓存 | dev server 修好并重启后，**旧标签页可能仍持有坏的 chunk**。清站点数据 + 关标签页重开（§34.4） |
