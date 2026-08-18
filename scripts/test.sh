#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SUITE="${TEST_SUITE:-unit}"
TIMEOUT="${TEST_TIMEOUT:-120}"
FORMAT="${TEST_FORMAT:-text}"
BASE_URL="${TEST_BASE_URL:-http://127.0.0.1:8888}"

usage() {
  cat <<'EOF'
Usage: ./scripts/test.sh [options]

Options:
  --suite <unit|smoke>       Test suite (default: unit)
  --timeout <seconds>        Overall smoke check timeout (default: 120)
  --base-url <url>           Platform base URL for smoke checks
  --format <text|json>       Output format (default: text)
  --help                     Show this help text
EOF
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --suite|--timeout|--base-url|--format)
      [[ "$#" -ge 2 ]] || { echo "ERROR: $1 requires a value" >&2; exit 2; }
      case "$1" in --suite) SUITE="$2" ;; --timeout) TIMEOUT="$2" ;; --base-url) BASE_URL="$2" ;; --format) FORMAT="$2" ;; esac
      shift 2 ;;
    --help) usage; exit 0 ;;
    *) echo "ERROR: unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ "${SUITE}" =~ ^(unit|smoke)$ ]] || { echo "ERROR: unsupported suite: ${SUITE}" >&2; exit 2; }
[[ "${TIMEOUT}" =~ ^[1-9][0-9]*$ && "${FORMAT}" =~ ^(text|json)$ ]] || { echo "ERROR: invalid timeout or format" >&2; exit 2; }

if [[ "${SUITE}" == "unit" ]]; then
  [[ -f "${ROOT_DIR}/pom.xml" ]] || { echo "ERROR: unit tests require the source checkout and Maven." >&2; exit 3; }
  command -v mvn >/dev/null 2>&1 || { echo "ERROR: Maven is required for unit tests." >&2; exit 3; }
  exec mvn -f "${ROOT_DIR}/pom.xml" test
fi

START="$(date +%s)"
if "${SCRIPT_DIR}/health-check.sh" --level readiness --timeout "${TIMEOUT}" --format text >&2; then
  if [[ "${FORMAT}" == "json" ]]; then
    printf '{"schema_version":"1.0","type":"automated_test","project":"social","suite":"smoke","status":"pass","summary":{"total":1,"passed":1,"failed":0,"skipped":0}}\n'
  else
    printf '[PASS] SMOKE-READY-001: service processes and HTTP listeners are ready\nRESULT status=pass suite=smoke passed=1 failed=0 skipped=0 duration_ms=%s\n' "$(( ($(date +%s) - START) * 1000 ))"
  fi
else
  echo '[FAIL] SMOKE-READY-001: readiness check failed' >&2
  exit 1
fi
