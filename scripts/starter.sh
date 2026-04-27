#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BIN_DIR="${ROOT_DIR}/bin"
LOG_DIR="${ROOT_DIR}/logs"
PID_DIR="${ROOT_DIR}/pids"

JAVA_BIN="${JAVA_BIN:-java}"
JAVA_OPTS="${JAVA_OPTS:-}"

ACTION="${1:-start}"

SERVICES=(
  "platform:${BIN_DIR}/platform.jar"
  "server:${BIN_DIR}/server.jar"
  "rtc:${BIN_DIR}/rtc.jar"
  "web3-identity:${BIN_DIR}/web3-identity.jar"
)

mkdir -p "${LOG_DIR}" "${PID_DIR}"

timestamp() {
  date +"%Y-%m-%d %H:%M:%S"
}

log() {
  echo "[$(timestamp)] $*"
}

pid_file() {
  local name="$1"
  echo "${PID_DIR}/${name}.pid"
}

is_running() {
  local name="$1"
  local file
  file="$(pid_file "${name}")"
  if [[ ! -f "${file}" ]]; then
    return 1
  fi
  local pid
  pid="$(cat "${file}" 2>/dev/null || true)"
  if [[ -z "${pid}" ]]; then
    return 1
  fi
  kill -0 "${pid}" 2>/dev/null
}

start_service() {
  local name="$1"
  local jar="$2"

  if [[ ! -f "${jar}" ]]; then
    log "skip ${name}: jar not found at ${jar}"
    return 0
  fi

  if is_running "${name}"; then
    log "${name} already running (pid=$(cat "$(pid_file "${name}")"))"
    return 0
  fi

  local log_file="${LOG_DIR}/${name}.log"
  log "starting ${name} ..."
  nohup "${JAVA_BIN}" ${JAVA_OPTS} -jar "${jar}" >"${log_file}" 2>&1 &
  local pid=$!
  echo "${pid}" >"$(pid_file "${name}")"
  sleep 1
  if kill -0 "${pid}" 2>/dev/null; then
    log "${name} started (pid=${pid})"
  else
    log "failed to start ${name}, check ${log_file}"
    return 1
  fi
}

stop_service() {
  local name="$1"
  local file
  file="$(pid_file "${name}")"
  if [[ ! -f "${file}" ]]; then
    log "${name} not running (no pid file)"
    return 0
  fi

  local pid
  pid="$(cat "${file}" 2>/dev/null || true)"
  if [[ -z "${pid}" ]]; then
    rm -f "${file}"
    log "${name} pid file invalid, removed"
    return 0
  fi

  if ! kill -0 "${pid}" 2>/dev/null; then
    rm -f "${file}"
    log "${name} already stopped (stale pid ${pid})"
    return 0
  fi

  log "stopping ${name} (pid=${pid}) ..."
  kill "${pid}" 2>/dev/null || true
  for _ in {1..20}; do
    if ! kill -0 "${pid}" 2>/dev/null; then
      break
    fi
    sleep 1
  done
  if kill -0 "${pid}" 2>/dev/null; then
    log "force killing ${name} (pid=${pid})"
    kill -9 "${pid}" 2>/dev/null || true
  fi
  rm -f "${file}"
  log "${name} stopped"
}

start_all() {
  local started=0
  for entry in "${SERVICES[@]}"; do
    local name="${entry%%:*}"
    local jar="${entry#*:}"
    start_service "${name}" "${jar}"
    if [[ -f "${jar}" ]]; then
      started=1
    fi
  done
  if [[ "${started}" -eq 0 ]]; then
    log "no service jars found in ${BIN_DIR}"
    return 1
  fi
}

stop_all() {
  for (( idx=${#SERVICES[@]}-1 ; idx>=0 ; idx-- )); do
    local entry="${SERVICES[idx]}"
    local name="${entry%%:*}"
    stop_service "${name}"
  done
}

case "${ACTION}" in
  start)
    start_all
    ;;
  stop)
    stop_all
    ;;
  restart)
    stop_all
    start_all
    ;;
  *)
    echo "Usage: $0 [start|stop|restart]"
    exit 1
    ;;
esac
