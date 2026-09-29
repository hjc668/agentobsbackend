#!/usr/bin/env bash

# Shared runtime helpers for the Spring Boot launchers. This file is sourced by
# the start scripts and is not intended to be executed directly.

is_java8_home() {
  local candidate="${1:-}"
  [[ -n "${candidate}" && -x "${candidate}/bin/java" && -x "${candidate}/bin/javac" ]] || return 1
  "${candidate}/bin/java" -version 2>&1 | grep -qE 'version "1\.8\.|openjdk version "1\.8\.'
}

is_supported_jdk_home() {
  local candidate="${1:-}"
  local version=""
  local major=""
  [[ -n "${candidate}" && -x "${candidate}/bin/java" && -x "${candidate}/bin/javac" ]] || return 1
  version="$("${candidate}/bin/java" -version 2>&1 | awk -F'"' '/version/ { print $2; exit }')"
  if [[ "${version}" == 1.* ]]; then
    major="${version#1.}"
    major="${major%%.*}"
  else
    major="${version%%.*}"
  fi
  [[ "${major}" =~ ^[0-9]+$ && "${major}" -ge 8 ]]
}

select_java8() {
  local candidate=""
  local java_path=""

  # APP_JAVA8_HOME is the optional explicit override. A JAVA_HOME that points
  # to Java 17/11 is ignored so the script can still discover an installed JDK 8.
  for candidate in "${APP_JAVA8_HOME:-}" "${JAVA_HOME:-}"; do
    if is_java8_home "${candidate}"; then
      export JAVA_HOME="${candidate}"
      export PATH="${JAVA_HOME}/bin:${PATH}"
      echo "Using Java 8 JDK: ${JAVA_HOME}"
      return 0
    fi
  done

  if [[ "$(uname -s)" == "Darwin" && -x /usr/libexec/java_home ]]; then
    candidate="$(/usr/libexec/java_home -v 1.8 2>/dev/null || true)"
    if is_java8_home "${candidate}"; then
      export JAVA_HOME="${candidate}"
      export PATH="${JAVA_HOME}/bin:${PATH}"
      echo "Using Java 8 JDK: ${JAVA_HOME}"
      return 0
    fi
  fi

  # Common macOS, Linux and offline-package installation locations.
  for candidate in \
      /Library/Java/JavaVirtualMachines/*/Contents/Home \
      /usr/lib/jvm/java-8* \
      /usr/lib/jvm/jdk8* \
      /usr/lib/jvm/jdk1.8* \
      /usr/java/jdk1.8* \
      /opt/java/jdk8* \
      /opt/jdk8*; do
    if is_java8_home "${candidate}"; then
      export JAVA_HOME="${candidate}"
      export PATH="${JAVA_HOME}/bin:${PATH}"
      echo "Using Java 8 JDK: ${JAVA_HOME}"
      return 0
    fi
  done

  if command -v update-alternatives >/dev/null 2>&1; then
    while IFS= read -r java_path; do
      candidate="$(cd "$(dirname "${java_path}")/.." 2>/dev/null && pwd -P || true)"
      if is_java8_home "${candidate}"; then
        export JAVA_HOME="${candidate}"
        export PATH="${JAVA_HOME}/bin:${PATH}"
        echo "Using Java 8 JDK: ${JAVA_HOME}"
        return 0
      fi
    done < <(update-alternatives --list java 2>/dev/null || true)
  fi

  java_path="$(command -v java || true)"
  if [[ -n "${java_path}" ]]; then
    candidate="$(cd "$(dirname "${java_path}")/.." 2>/dev/null && pwd -P || true)"
    if is_java8_home "${candidate}"; then
      export JAVA_HOME="${candidate}"
      export PATH="${JAVA_HOME}/bin:${PATH}"
      echo "Using Java 8 JDK: ${JAVA_HOME}"
      return 0
    fi
  fi

  # Spring Boot 2.7 can run on newer JDKs. The Maven build still targets Java 8
  # via pom.xml, so an offline host with only JDK 17 can start directly.
  for candidate in "${JAVA_HOME:-}"; do
    if is_supported_jdk_home "${candidate}"; then
      export JAVA_HOME="${candidate}"
      export PATH="${JAVA_HOME}/bin:${PATH}"
      echo "JDK 8 was not found; using compatible JDK at ${JAVA_HOME} (build target remains Java 8)."
      return 0
    fi
  done

  if [[ -n "${java_path}" ]]; then
    candidate="$(cd "$(dirname "${java_path}")/.." 2>/dev/null && pwd -P || true)"
    if is_supported_jdk_home "${candidate}"; then
      export JAVA_HOME="${candidate}"
      export PATH="${JAVA_HOME}/bin:${PATH}"
      echo "JDK 8 was not found; using compatible JDK at ${JAVA_HOME} (build target remains Java 8)."
      return 0
    fi
  fi

  echo "A complete JDK 8 or newer was not found. Install a JDK (not a JRE), or set JAVA_HOME." >&2
  return 1
}

backend_port_pids() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true
  elif [[ -x /usr/sbin/lsof ]]; then
    /usr/sbin/lsof -nP -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true
  elif [[ "$(uname -s)" == "Linux" ]] && command -v fuser >/dev/null 2>&1; then
    fuser -n tcp "${port}" 2>/dev/null | tr ' ' '\n' | awk 'NF'
  else
    echo "Neither lsof nor fuser is available; cannot safely identify the service on port ${port}." >&2
    return 2
  fi
}

stop_existing_backend() {
  local port="${SERVER_PORT:-8080}"
  local pids=""
  local pid=""
  local command_line=""
  local deadline=0

  pids="$(backend_port_pids "${port}")" || return $?
  [[ -n "${pids}" ]] || return 0

  for pid in ${pids}; do
    command_line="$(ps -p "${pid}" -o command= 2>/dev/null || true)"
    if [[ "${command_line}" != *"${PROJECT_ROOT}"* \
          && "${command_line}" != *"langfuse-web-service"* \
          && "${command_line}" != *"com.icbc.aiops.langfuse.LangfuseQueryApplication"* ]]; then
      echo "Port ${port} is occupied by an unrelated process (PID ${pid}); refusing to stop it: ${command_line}" >&2
      return 1
    fi
  done

  echo "Stopping existing Langfuse Web backend on port ${port}: ${pids//$'\n'/ }"
  kill ${pids}
  deadline=$((SECONDS + 15))
  while [[ ${SECONDS} -lt ${deadline} ]]; do
    [[ -z "$(backend_port_pids "${port}")" ]] && return 0
    sleep 1
  done

  pids="$(backend_port_pids "${port}")"
  if [[ -n "${pids}" ]]; then
    echo "Backend did not stop within 15 seconds; forcing the same verified process to exit."
    kill -KILL ${pids}
  fi
}
