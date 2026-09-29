#!/usr/bin/env bash

# Shared runtime helpers for the frontend launchers.

select_frontend_package_manager() {
  if command -v pnpm >/dev/null 2>&1; then
    FRONTEND_PACKAGE_MANAGER="pnpm"
  elif command -v npm >/dev/null 2>&1; then
    FRONTEND_PACKAGE_MANAGER="npm"
  else
    echo "Neither pnpm nor npm was found. Install Node.js with npm and retry." >&2
    return 1
  fi
  export FRONTEND_PACKAGE_MANAGER
  echo "Using frontend package manager: ${FRONTEND_PACKAGE_MANAGER}"
  configure_frontend_node_options
}

# The webpack 4 toolchain hashes with MD4 through the OpenSSL API. Node 17 moved to
# OpenSSL 3, which drops that algorithm unless the legacy provider is enabled, so the
# dev server dies at startup with "error:0308010C:digital envelope routines::unsupported"
# on any modern Node (this machine runs v24 while package.json still declares 16.16.0).
# Node 16 rejects the flag, so only pass it where it exists.
configure_frontend_node_options() {
  local node_major
  node_major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
  if [[ "${node_major}" -ge 17 ]]; then
    export NODE_OPTIONS="${NODE_OPTIONS:+${NODE_OPTIONS} }--openssl-legacy-provider"
    echo "Node ${node_major} detected; enabling --openssl-legacy-provider for the webpack 4 toolchain."
  fi
}

ensure_frontend_dependencies() {
  # Check the bundler this project actually uses. A Vite-only check would miss the
  # webpack project and re-run npm install on every start.
  if [[ -x "${PROJECT_ROOT}/frontend/node_modules/.bin/vite" ]] \
     || [[ -x "${PROJECT_ROOT}/frontend/node_modules/.bin/webpack-dev-server" ]]; then
    echo "Frontend dependencies already exist; skipping install."
    return 0
  fi

  echo "Installing frontend dependencies with ${FRONTEND_PACKAGE_MANAGER}..."
  (
    cd "${PROJECT_ROOT}/frontend"
    if [[ "${FRONTEND_PACKAGE_MANAGER}" == "pnpm" ]]; then
      pnpm install
    else
      npm install
    fi
  )
}

frontend_port_pids() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true
  elif [[ -x /usr/sbin/lsof ]]; then
    /usr/sbin/lsof -nP -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true
  elif [[ "$(uname -s)" == "Linux" ]] && command -v fuser >/dev/null 2>&1; then
    fuser -n tcp "${port}" 2>/dev/null | tr ' ' '\n' | awk 'NF'
  else
    echo "Neither lsof nor fuser is available; cannot safely identify the frontend on port ${port}." >&2
    return 2
  fi
}

stop_existing_frontend() {
  local port="${FRONTEND_PORT:-5173}"
  local pids=""
  local pid=""
  local command_line=""
  local deadline=0

  pids="$(frontend_port_pids "${port}")" || return $?
  [[ -n "${pids}" ]] || return 0

  for pid in ${pids}; do
    command_line="$(ps -p "${pid}" -o command= 2>/dev/null || true)"
    if [[ "${command_line}" != *"${PROJECT_ROOT}/frontend"* ]]; then
      echo "Port ${port} is occupied by an unrelated process (PID ${pid}); refusing to stop it: ${command_line}" >&2
      return 1
    fi
  done

  echo "Stopping existing Langfuse Web frontend on port ${port}: ${pids//$'\n'/ }"
  kill ${pids}
  deadline=$((SECONDS + 10))
  while [[ ${SECONDS} -lt ${deadline} ]]; do
    [[ -z "$(frontend_port_pids "${port}")" ]] && return 0
    sleep 1
  done

  pids="$(frontend_port_pids "${port}")"
  if [[ -n "${pids}" ]]; then
    echo "Frontend did not stop within 10 seconds; forcing the same verified process to exit."
    kill -KILL ${pids}
  fi
}

start_frontend() {
  local port="${FRONTEND_PORT:-5173}"
  cd "${PROJECT_ROOT}/frontend"
  if [[ "${FRONTEND_PACKAGE_MANAGER}" == "pnpm" ]]; then
    exec pnpm dev -- --port "${port}"
  else
    exec npm run dev -- --port "${port}"
  fi
}
