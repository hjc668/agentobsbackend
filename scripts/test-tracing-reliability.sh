#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:8080}"
REQUESTS="${TRACING_LOAD_REQUESTS:-8}"
CONCURRENCY="${TRACING_LOAD_CONCURRENCY:-2}"
MAX_P95_SECONDS="${TRACING_MAX_P95_SECONDS:-20}"
SEARCH_FILTER="${TRACING_LOAD_SEARCH:-traceId:=4172ee06996c2e3f86d01d869c9949fe}"
RESULT_DIR="$(mktemp -d)"
COOKIE_JAR="${RESULT_DIR}/cookies.txt"

cleanup() {
  find "$RESULT_DIR" -type f -delete
  rmdir "$RESULT_DIR"
}
trap cleanup EXIT

curl -sS -c "$COOKIE_JAR" -b "$COOKIE_JAR" "$BASE_URL/api/v1/auth/csrf" >/dev/null
CSRF_TOKEN="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$COOKIE_JAR")"
curl -sS -c "$COOKIE_JAR" -b "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  --data '{"aamId":"38971135","ticket":"mock-ticket"}' >/dev/null

seq 1 "$REQUESTS" | xargs -P "$CONCURRENCY" -I '{}' sh -c '
  curl -sS --max-time 45 -o /dev/null -w "%{http_code} %{time_total}\n" \
    -b "$1" --get "$0/api/v1/observability/observations" \
    --data "page=0" --data "size=25" --data-urlencode "search=$3" \
    > "$2/{}.result"
' "$BASE_URL" "$COOKIE_JAR" "$RESULT_DIR" "$SEARCH_FILTER"

STATUS_FAILURES="$(awk '$1 != 200 { count++ } END { print count + 0 }' "$RESULT_DIR"/*.result)"
P95_INDEX="$(( (REQUESTS * 95 + 99) / 100 ))"
P95_SECONDS="$(awk '{ print $2 }' "$RESULT_DIR"/*.result | sort -n | awk -v idx="$P95_INDEX" 'NR == idx { print; exit }')"

INVALID_STATUS="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 \
  -b "$COOKIE_JAR" \
  "$BASE_URL/api/v1/observability/observations?page=0&size=201")"

awk -v p95="$P95_SECONDS" -v max="$MAX_P95_SECONDS" 'BEGIN { if (p95 > max) exit 1 }'
test "$STATUS_FAILURES" -eq 0
test "$INVALID_STATUS" = "400"

echo "Tracing reliability passed: requests=$REQUESTS concurrency=$CONCURRENCY p95=${P95_SECONDS}s invalid=$INVALID_STATUS"
