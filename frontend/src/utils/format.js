/**
 * Format utilities for displaying data
 */

/**
 * Format a date value to locale string
 * @param {string|number|Date} value - Date value to format
 * @returns {string} Formatted date string
 */
export function formatDate(value) {
  if (!value) return "—";
  try {
    const date = new Date(value);
    return date.toLocaleString("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false
    });
  } catch (e) {
    return "—";
  }
}

/**
 * Format duration in milliseconds to human readable string
 * @param {number} ms - Duration in milliseconds
 * @returns {string} Formatted duration
 */
export function formatDuration(ms) {
  if (ms == null || isNaN(ms)) return "—";
  if (ms < 1000) return `${Math.round(ms)}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`;
  const minutes = Math.floor(ms / 60000);
  const seconds = ((ms % 60000) / 1000).toFixed(0);
  return `${minutes}m ${seconds}s`;
}

/**
 * Format number to compact representation (e.g., 10K+)
 * @param {number} value - Number to format
 * @returns {string} Compact number string
 */
export function compactNumber(value) {
  if (value == null || isNaN(value)) return "—";
  if (value >= 1000000) return `${Math.floor(value / 1000000)}M+`;
  if (value >= 1000) return `${Math.floor(value / 1000)}K+`;
  return String(value);
}

/**
 * Safe JSON stringify
 * @param {*} value - Value to stringify
 * @returns {string} JSON string
 */
export function json(value) {
  if (value == null) return "";
  try {
    return JSON.stringify(value, null, 2);
  } catch (e) {
    return String(value);
  }
}

/**
 * Download data as CSV file
 * @param {Array} rows - Data rows
 * @param {string} filename - File name
 */
export function downloadCsv(rows, filename = "export.csv") {
  if (!rows || rows.length === 0) return;

  const headers = Object.keys(rows[0]);
  const csvRows = [
    headers.join(","),
    ...rows.map(row =>
      headers
        .map(h => {
          const val = row[h];
          const str = val == null ? "" : String(val);
          return str.includes(",") || str.includes('"') || str.includes("\n") ? `"${str.replace(/"/g, '""')}"` : str;
        })
        .join(",")
    )
  ];

  const bom = "\uFEFF";
  const blob = new Blob([bom + csvRows.join("\n")], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
