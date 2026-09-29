/**
 * API Client for Langfuse Observability
 * Converted from TypeScript to JavaScript
 */

const API_BASE_URL = (process.env.VITE_API_BASE_URL || "").replace(/\/$/, "");

export const DATA_SOURCE_LABEL = process.env.VITE_DATA_SOURCE_LABEL || "Mock query service";
export const PROJECT_NAME = process.env.VITE_PROJECT_NAME || "AIOps Observability";

async function request(path, signal) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { Accept: "application/json" },
    credentials: "include",
    signal
  });

  if (!response.ok) {
    const body = await response.text();
    if (response.status === 401) window.dispatchEvent(new Event("aam:unauthenticated"));
    if (response.status === 403) window.dispatchEvent(new Event("aam:forbidden"));
    throw new Error(body || `Request failed: ${response.status}`);
  }

  return await response.json();
}

async function mutate(path, method, body) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      ...csrfHeader()
    },
    credentials: "include",
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  if (!response.ok) {
    const payload = await response.text();
    if (response.status === 401) window.dispatchEvent(new Event("aam:unauthenticated"));
    if (response.status === 403) window.dispatchEvent(new Event("aam:forbidden"));
    throw new Error(payload || `Request failed: ${response.status}`);
  }
  if (response.status === 204) return undefined;
  window.dispatchEvent(new Event("aam:authorized"));
  return await response.json();
}

const observabilityPath = "/api/v1/observability";
const workspacePath = "/api/v1/workspace";
let latestCsrfToken = "";

function csrfHeader() {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  const token = latestCsrfToken || (match ? decodeURIComponent(match[1]) : "");
  return token ? { "X-XSRF-TOKEN": token } : {};
}

function queryString(values) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  return params.toString();
}

export const observabilityApi = {
  getSummary: signal => request(`${observabilityPath}/summary`, signal),

  getMetricTimeSeries: signal => request(`${observabilityPath}/summary/timeseries`, signal),

  getTraces: (query = {}, signal) =>
    request(
      `${observabilityPath}/traces?${queryString({
        search: query.search,
        status: query.status,
        environment: query.environment,
        userId: query.userId,
        sessionId: query.sessionId,
        tag: query.tag,
        fromTimestamp: query.fromTimestamp,
        toTimestamp: query.toTimestamp,
        sortBy: query.sortBy || "TIMESTAMP",
        direction: query.direction || "DESC",
        page: query.page || 0,
        size: query.size || 50
      })}`,
      signal
    ),

  getTrace: (traceId, signal) => request(`${observabilityPath}/traces/${encodeURIComponent(traceId)}`, signal),

  getTraceObservations: (traceId, signal) =>
    request(`${observabilityPath}/traces/${encodeURIComponent(traceId)}/observations`, signal),

  getTraceView: (traceId, signal) => request(`${observabilityPath}/traces/${encodeURIComponent(traceId)}/view`, signal),

  getTraceScores: (traceId, signal) =>
    request(`${observabilityPath}/traces/${encodeURIComponent(traceId)}/scores`, signal),

  getTraceComments: (traceId, signal) =>
    request(`${observabilityPath}/traces/${encodeURIComponent(traceId)}/comments`, signal),

  getObservations: (query = {}, signal) =>
    request(
      `${observabilityPath}/observations?${queryString({
        search: query.search,
        serviceName: query.serviceName,
        environment: query.environment,
        type: query.type,
        level: query.level,
        model: query.model,
        traceId: query.traceId,
        tag: query.tag,
        fromTimestamp: query.fromTimestamp,
        toTimestamp: query.toTimestamp,
        sortBy: query.sortBy || "TIMESTAMP",
        direction: query.direction || "DESC",
        page: query.page || 0,
        size: query.size || 50
      })}`,
      signal
    ),

  getObservationFacets: (field, query = {}, signal) =>
    request(
      `${observabilityPath}/observations/facets?${queryString({
        field,
        limit: 20,
        search: query.search,
        serviceName: query.serviceName,
        environment: query.environment,
        type: query.type,
        level: query.level,
        model: query.model,
        traceId: query.traceId,
        tag: query.tag,
        fromTimestamp: query.fromTimestamp,
        toTimestamp: query.toTimestamp
      })}`,
      signal
    ),

  getObservationPulse: (query = {}, bucket = "DAY", signal) =>
    request(
      `${observabilityPath}/observations/pulse?${queryString({
        bucket,
        search: query.search,
        serviceName: query.serviceName,
        environment: query.environment,
        type: query.type,
        level: query.level,
        model: query.model,
        traceId: query.traceId,
        tag: query.tag,
        fromTimestamp: query.fromTimestamp,
        toTimestamp: query.toTimestamp
      })}`,
      signal
    ),

  getSessions: (search = "", page = 0, signal) =>
    request(`${observabilityPath}/sessions?search=${encodeURIComponent(search)}&page=${page}&size=50`, signal),

  getSession: (sessionId, signal) => request(`${observabilityPath}/sessions/${encodeURIComponent(sessionId)}`, signal),

  getUsers: (search = "", environment = "", page = 0, signal) =>
    request(`${observabilityPath}/users?${queryString({ search, environment, page, size: 50 })}`, signal),

  getUser: (userId, signal) => request(`${observabilityPath}/users/${encodeURIComponent(userId)}`, signal),

  getPrompts: (search = "", page = 0, signal) =>
    request(`${workspacePath}/prompts?search=${encodeURIComponent(search)}&page=${page}&size=50`, signal),

  createPrompt: input => mutate(`${workspacePath}/prompts`, "POST", input),

  setPromptLabels: (id, labels) =>
    mutate(`${workspacePath}/prompts/${encodeURIComponent(id)}/labels`, "PUT", { labels }),

  updatePromptTags: (id, tags) => mutate(`${workspacePath}/prompts/${encodeURIComponent(id)}/tags`, "PUT", { tags }),

  deletePromptVersion: id => mutate(`${workspacePath}/prompts/${encodeURIComponent(id)}`, "DELETE"),

  deletePrompt: name => mutate(`${workspacePath}/prompts?name=${encodeURIComponent(name)}`, "DELETE"),

  getDashboards: (page = 0, signal) => request(`${workspacePath}/dashboards?page=${page}&size=50`, signal),

  createDashboard: input => mutate(`${workspacePath}/dashboards`, "POST", input),

  updateDashboardMetadata: (id, input) =>
    mutate(`${workspacePath}/dashboards/${encodeURIComponent(id)}/metadata`, "PUT", input),

  cloneDashboard: id => mutate(`${workspacePath}/dashboards/${encodeURIComponent(id)}/clone`, "POST"),

  deleteDashboard: id => mutate(`${workspacePath}/dashboards/${encodeURIComponent(id)}`, "DELETE"),

  getDashboardWidgets: (page = 0, signal) => request(`${workspacePath}/dashboard-widgets?page=${page}&size=50`, signal),

  createDashboardWidget: input => mutate(`${workspacePath}/dashboard-widgets`, "POST", input),

  updateDashboardWidget: (id, input) =>
    mutate(`${workspacePath}/dashboard-widgets/${encodeURIComponent(id)}`, "PUT", input),

  cloneDashboardWidget: id => mutate(`${workspacePath}/dashboard-widgets/${encodeURIComponent(id)}/clone`, "POST"),

  getDashboardWidgetMetrics: (id, signal) =>
    request(`${workspacePath}/dashboard-widgets/${encodeURIComponent(id)}/metrics`, signal),

  deleteDashboardWidget: id => mutate(`${workspacePath}/dashboard-widgets/${encodeURIComponent(id)}`, "DELETE")
};

export const authApi = {
  csrf: async () => {
    const value = await request("/api/v1/auth/csrf");
    latestCsrfToken = value.token;
    return value;
  },
  // Anonymous: reports which auth flow is active and where the portal lives.
  config: () => request("/api/v1/auth/config"),
  me: () => request("/api/v1/auth/me"),
  login: async (aamId, ticket) => {
    await authApi.csrf();
    return mutate("/api/v1/auth/login", "POST", { aamId, ticket });
  },
  logout: () => mutate("/api/v1/auth/logout", "POST")
};
