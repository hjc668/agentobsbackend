export const SEARCH_FIELDS = [
  ["environment", "Environment"],
  ["type", "Type"],
  ["root", "Is Root Observation"],
  ["level", "Status"],
  ["name", "Name"],
  ["traceName", "Trace Name"],
  ["tags", "Trace Tags"],
  ["model", "Provided Model Name"],
  ["prompt", "Prompt Name"],
  ["traceId", "Trace ID"],
  ["session", "Session ID"],
  ["user", "User ID"],
  ["latency", "Latency (s)"],
  ["ttft", "Time To First Token (s)"],
  ["inputTokens", "Input Tokens"],
  ["outputTokens", "Output Tokens"],
  ["tokens", "Total Tokens"],
  ["metadata", "Metadata"],
  ["version", "Version"],
  ["release", "Release"]
];

export function scanTokens(value) {
  const result = [];
  let start = -1,
    quote = false,
    depth = 0;
  for (let i = 0; i <= value.length; i++) {
    const ch = value[i] || " ";
    if (start < 0 && !/\s/.test(ch)) start = i;
    if (start < 0) continue;
    if (ch === '"' && value[i - 1] !== "\\") quote = !quote;
    if (!quote && ch === "(") depth++;
    if (!quote && ch === ")") depth = Math.max(0, depth - 1);
    if ((!quote && depth === 0 && /\s/.test(ch)) || i === value.length) {
      const raw = value.slice(start, i);
      if (raw && !/^(AND|OR|NOT)$/i.test(raw)) result.push({ raw, start, end: i });
      start = -1;
    }
  }
  return result;
}

function safeField(field) {
  return String(field).replace(/[^A-Za-z0-9_-]/g, "");
}

export function removeField(search, field) {
  const wanted = safeField(field).toLowerCase();
  return scanTokens(search)
    .filter(token => {
      const match = token.raw.match(/^-?([^:]+):/);
      return !match || safeField(match[1]).toLowerCase() !== wanted;
    })
    .map(token => token.raw)
    .join(" ");
}

export function fieldValues(search, field) {
  const wanted = safeField(field).toLowerCase(),
    token = scanTokens(search).find(value => {
      const match = value.raw.match(/^-?([^:]+):/);
      return match && safeField(match[1]).toLowerCase() === wanted;
    });
  if (!token) return [];
  let raw = token.raw.slice(token.raw.indexOf(":") + 1);
  if (
    (raw[0] === "(" && raw[raw.length - 1] === ")") ||
    (raw[0] === String.fromCharCode(34) && raw[raw.length - 1] === String.fromCharCode(34))
  )
    raw = raw.slice(1, -1);
  return raw
    .split(/\s+OR\s+/i)
    .map(value => value.replace(/^=/, "").replace(/^"|"$/g, "").trim())
    .filter(Boolean);
}

export function setDslField(search, field, value) {
  const cleaned = removeField(search, field),
    raw = String(value || "").trim();
  if (!raw) return cleaned;
  const encoded = /^(=|>|<|\(|")/.test(raw) || /\s+OR\s+/i.test(raw) ? raw : /\s/.test(raw) ? '"' + raw + '"' : raw;
  return (cleaned + " " + field + ":" + encoded).trim();
}
