#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 0 ]]; then
  echo "Usage: $0" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
source "${SCRIPT_DIR}/frontend-runtime.sh"

export VITE_API_BASE_URL="${VITE_API_BASE_URL:-http://127.0.0.1:8080}"
export VITE_DATA_SOURCE_LABEL="${VITE_DATA_SOURCE_LABEL:-Live ClickHouse · MyBatis}"
export VITE_PROJECT_NAME="${VITE_PROJECT_NAME:-test}"

select_frontend_package_manager
ensure_frontend_dependencies
stop_existing_frontend
start_frontend
