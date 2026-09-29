# Tracing 前端修改方案

> **历史修改建议，非现状说明**（2026-09-22 核对）。本文引用 `client.ts`、`types.ts` 的旧路径；当前正式前端 API 为 `frontend/src/api/client.js`，技术栈为 Vue 2.6 + Webpack 4。实施前先读项目根目录 `PROJECT_PROGRESS.md` 并核对当前 `TracingPage.vue`，不要重复执行已完成步骤。

## 一、修改范围

只修改 Vue 2 + Element UI 前端，不涉及 Spring Boot、MyBatis、ClickHouse SQL。

重点文件：

- `frontend/src/features/observability/TracingPage.vue`
- `frontend/src/api/client.ts`
- `frontend/src/types.ts`
- Tracing 相关样式文件

暂不实现 Scores 和 Comments Tab。

## 二、P0：必须修复

### 1. 修复 Filters 面板空白按钮

当前模板使用：

- `c.select`
- `c.text`
- `c.apply`

但中英文语言对象没有定义这些字段，导致 Select、Text、Apply 按钮显示为空白。

需要在 `TracingPage.vue` 的 `computed.c` 中补充：

```js
// 中文
select: "选择",
text: "文本",
apply: "应用"

// English
select: "Select",
text: "Text",
apply: "Apply"
```

验收标准：

- Environment、Service、Type、Root 分组均能显示“选择/文本”。
- 文本输入模式能显示“应用”按钮。
- 按钮具备可识别的文字或 `aria-label`。
- 点击模式与实际高亮状态一致。

### 2. 完善筛选标签交互

当前 Tag/Chip 基础功能已经存在，需要回归并保证：

- 输入 `type:SPAN` 后形成一个完整标签。
- 标签内容不能被拆成 `type:` 和 `SPAN`。
- 点击标签的 × 只删除当前标签。
- Clear all 清空所有标签、左侧复选框和URL中的 `filter`。
- 从左侧复选框选择条件时，顶部自动生成对应标签。
- 删除顶部标签后，左侧复选框同步取消。
- 选择搜索维度后，之前输入的 `tra` 等检索文字不会残留。
- `traceId:` 后面不自动产生空格。
- 多值条件正确显示，例如：

```text
type:(SPAN OR GENERATION)
```

### 3. 修正快捷筛选

当前 Slow 的“Latency over 10s”只是按延迟排序，并没有过滤数据。

应调整为：

```js
{
  label: "Latency over 10s",
  search: "latency:>10",
  sort: "LATENCY"
}
```

Cost 快捷菜单也要区分：

- “按费用排序”
- “费用大于指定值”

不要把排序操作描述成筛选操作。

Quality、Slow、Cost 点击后均应显示下拉菜单，选择后生成可删除的筛选标签。

### 4. 补齐详情 Graph 视图

详情弹框目前只有：

- Tree
- Timeline
- Data

需要参考原始 Langfuse 增加 Graph：

- 展示 Observation 父子节点。
- 节点区分 SPAN、GENERATION 等类型。
- 当前选中节点高亮。
- 点击节点后右侧 Data 面板同步切换。
- 大链路支持滚动或缩放。
- 移动端可以单独切换到 Graph。

导航建议保持：

```text
Tree | Timeline | Graph | Data
```

## 三、P1：体验优化

### 1. 修复移动端 Tree 层级

桌面端已经按照 `observationDepth()` 设置左侧缩进，移动端没有使用该样式。

移动端节点按钮也需要增加：

```vue
:style="{ paddingLeft: (10 + observationDepth(item) * 18) + 'px' }"
```

确保父节点和子节点层级清楚。

### 2. Observation 切换时同步 URL

点击上下箭头或 Tree/Timeline/Graph 节点后，需要同步更新：

- `peek`
- `observation`
- `traceId`

刷新页面后应恢复当前选中的 Observation。

建议提取统一方法：

```js
selectObservation(id) {
  this.selectedId = id;
  this.updateObservationUrl(id);
}
```

上下箭头也统一调用 `selectObservation()`。

### 3. API 参数补齐 Service Name

`client.ts` 的以下三个请求需要显式传递 `serviceName`：

- `getObservations`
- `getObservationFacets`
- `getObservationPulse`

示例：

```ts
serviceName: query.serviceName,
```

即使当前 Service 条件可以通过 `search` DSL 传递，也应保持类型定义和后端接口一致。

### 4. Log View 校准

Formatted 模式需保证：

- 显示 Observation 名称、类型、Depth、Start、Duration。
- 子节点显示正确的 L0、L1、L2 层级。
- Expand all、Collapse all、单行展开可用。
- 展开后显示 Input、Output、Metadata。
- 搜索可以按名称、类型和 Observation ID 筛选。

JSON 模式需保证：

- JSON 层级可展开/折叠。
- 字段值类型正确。
- Copy 复制完整、合法的 JSON。
- 空值统一显示为 `null`，不要混用空字符串和 `undefined`。

### 5. 详情弹框视觉校准

参考原始 Langfuse 浅色主题校准：

- 弹框宽度和遮罩层。
- Header 高度、字体、关闭按钮。
- Tree/Timeline/Data 面板比例。
- Tab 高亮颜色和下划线。
- Observation 类型图标。
- Latency、Environment、Version、Tokens、Cost 标签。
- 表格边框、圆角和间距。

本期不要显示 Scores 和 Comments Tab。

## 四、前后端联调约定

前端继续使用以下接口：

```text
GET /api/v1/observability/observations
GET /api/v1/observability/observations/facets
GET /api/v1/observability/observations/pulse
GET /api/v1/observability/traces/{traceId}/view
```

前端无需自行过滤后端返回结果。尤其是：

```text
traceName:=configRequirements/read
```

必须原样交给后端，由后端完成 Trace Name 查询。

## 五、验收用例

至少完成以下浏览器测试：

1. `type:SPAN` 标签新增、删除和清空。
2. `traceId:=完整ID` 只显示对应链路。
3. `traceName:=configRequirements/read` 只显示目标 Trace。
4. Service 复选框与顶部标签双向同步。
5. Slow > 10s 产生筛选标签并真正减少数据。
6. Tree、Timeline、Graph 节点数量一致。
7. 点击不同节点时 Data 面板和URL同步变化。
8. Formatted/JSON Log View 展开、搜索和复制正常。
9. 桌面端与移动端均无空白按钮。
10. `npm run build` 构建成功。
