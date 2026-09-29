#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
source "${SCRIPT_DIR}/backend-runtime.sh"

select_java8
stop_existing_backend

cd "${PROJECT_ROOT}/backend"
exec mvn spring-boot:run
