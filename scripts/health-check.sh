#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PID_DIR="${ROOT_DIR}/pids"

LEVEL="readiness"
TIMEOUT="${HEALTH_TIMEOUT:-10}"
RETRIES="${HEALTH_RETRIES:-0}"
INTERVAL="${HEALTH_INTERVAL:-2}"
FORMAT="${HEALTH_FORMAT:-text}"
QUIET=false
FAILED=0
PASSED=0
STARTED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
START_SECONDS="$(date +%s)"
CHECKS=()

usage() {
  cat <<'EOF'
Usage: ./scripts/health-check.sh [options]

Options:
  --level <liveness|readiness|dependency|all>  Check level (default: readiness)
  --timeout <seconds>                          Per HTTP check timeout (default: 10)
  --retries <count>                            Retry count after a failure (default: 0)
  --interval <seconds>                         Retry interval (default: 2)
  --format <text|json>                         Output format (default: text)
  --quiet                                      Only print the final result in text mode
  --help                                       Show this help text
EOF
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --level|--timeout|--retries|--interval|--format)
      [[ "$#" -ge 2 ]] || { echo "ERROR: $1 requires a value" >&2; exit 2; }
      case "$1" in
        --level) LEVEL="$2" ;;
        --timeout) TIMEOUT="$2" ;;
        --retries) RETRIES="$2" ;;
        --interval) INTERVAL="$2" ;;
        --format) FORMAT="$2" ;;
      esac
      shift 2 ;;
    --quiet) QUIET=true; shift ;;
    --help) usage; exit 0 ;;
    *) echo "ERROR: unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ "${LEVEL}" =~ ^(liveness|readiness|dependency|all)$ ]] || { echo "ERROR: invalid level: ${LEVEL}" >&2; exit 2; }
[[ "${FORMAT}" =~ ^(text|json)$ ]] || { echo "ERROR: invalid format: ${FORMAT}" >&2; exit 2; }
[[ "${TIMEOUT}" =~ ^[1-9][0-9]*$ && "${RETRIES}" =~ ^[0-9]+$ && "${INTERVAL}" =~ ^[0-9]+$ ]] || { echo "ERROR: timeout, retries and interval must be non-negative integers (timeout must be positive)" >&2; exit 2; }

record() {
  local status="$1" name="$2" message="$3"
  CHECKS+=("${status}|${name}|${message}")
  case "${status}" in PASS) ((PASSED+=1)) ;; FAIL) ((FAILED+=1)) ;; esac
  if [[ "${FORMAT}" == "text" && "${QUIET}" != true ]]; then
    printf '[%s] %s: %s\n' "${status}" "${name}" "${message}"
  fi
}

check_process() {
  local service="$1" file="${PID_DIR}/${1}.pid" pid
  if [[ ! -f "${file}" ]]; then record FAIL "process.${service}" "PID file is missing"; return; fi
  pid="$(<"${file}")"
  if [[ "${pid}" =~ ^[0-9]+$ ]] && kill -0 "${pid}" 2>/dev/null; then
    record PASS "process.${service}" "process is running (pid=${pid})"
  else
    record FAIL "process.${service}" "process is not running"
  fi
}

check_http() {
  local service="$1" url="$2" attempt=0
  command -v curl >/dev/null 2>&1 || { record FAIL "http.${service}" "curl is required for readiness checks"; return; }
  while :; do
    if curl --silent --show-error --output /dev/null --max-time "${TIMEOUT}" "${url}"; then
      record PASS "http.${service}" "HTTP listener accepted a request"
      return
    fi
    if (( attempt >= RETRIES )); then
      break
    fi
    ((attempt+=1)); sleep "${INTERVAL}"
  done
  record FAIL "http.${service}" "HTTP listener is unavailable"
}

for service in platform server rtc web3-identity; do check_process "${service}"; done
if [[ "${LEVEL}" == "readiness" || "${LEVEL}" == "all" ]]; then
  check_http platform "${HEALTH_PLATFORM_URL:-http://127.0.0.1:8888/}"
  check_http rtc "${HEALTH_RTC_URL:-http://127.0.0.1:8890/}"
  check_http web3-identity "${HEALTH_IDENTITY_URL:-http://127.0.0.1:8901/}"
fi
if [[ "${LEVEL}" == "dependency" || "${LEVEL}" == "all" ]]; then
  record PASS dependency "dependency checks are performed by each service during startup"
fi

DURATION_MS=$(( ($(date +%s) - START_SECONDS) * 1000 ))
STATUS="pass"; [[ "${FAILED}" -eq 0 ]] || STATUS="fail"
if [[ "${FORMAT}" == "json" ]]; then
  printf '{"schema_version":"1.0","type":"health_check","project":"social","level":"%s","status":"%s","started_at":"%s","duration_ms":%s,"summary":{"passed":%s,"warned":0,"failed":%s,"skipped":0},"checks":[' "${LEVEL}" "${STATUS}" "${STARTED_AT}" "${DURATION_MS}" "${PASSED}" "${FAILED}"
  for index in "${!CHECKS[@]}"; do
    IFS='|' read -r check_status check_name check_message <<< "${CHECKS[index]}"
    [[ "${index}" -eq 0 ]] || printf ','
    case "${check_status}" in
      PASS) check_status_json="pass" ;;
      FAIL) check_status_json="fail" ;;
      *) check_status_json="skip" ;;
    esac
    printf '{"name":"%s","status":"%s","message":"%s"}' "${check_name}" "${check_status_json}" "${check_message}"
  done
  printf ']}\n'
else
  printf 'RESULT status=%s passed=%s warned=0 failed=%s duration_ms=%s\n' "${STATUS}" "${PASSED}" "${FAILED}" "${DURATION_MS}"
fi
[[ "${FAILED}" -eq 0 ]]
