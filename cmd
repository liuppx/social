#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_DIR="${ROOT_DIR}/target/local"
LOG_DIR="${LOCAL_DIR}/logs"
PID_DIR="${LOCAL_DIR}/pids"
LOCAL_CONFIG_DIR="${ROOT_DIR}/config/local"
CONFIG_TEMPLATE_DIR="${ROOT_DIR}/config/templates"

DEFAULT_LOCAL_SERVICES=(platform server rtc)
STOP_ORDER=(rtc server platform)

usage() {
  cat <<'EOF'
Usage:
  ./cmd local-start [service...]
  ./cmd local-stop [service...]
  ./cmd local-status [service...]

Local services:
  platform  Spring Boot platform API, default port 8888
  server    IM WebSocket server, default port 8878
  rtc       Spring Boot RTC API, default port 8890

With no service arguments, local-start starts platform/server/rtc and
local-stop stops them in reverse order.

Local configuration is read from config/local/<service>/application.yml when
present, then each service's packaged application.yml and application-*.yml
files. Missing local config files are bootstrapped from config/templates.
This command writes logs/pids under target/local and local config under
config/local. It does not use production bin/, logs/, pids/,
config/<service>/, or scripts/starter.sh.

Frontend is intentionally not managed here. Start it manually from web/ when
needed.
EOF
}

timestamp() {
  date +"%Y-%m-%d %H:%M:%S"
}

log() {
  echo "[$(timestamp)] $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "ERROR: command not found: $1" >&2
    exit 1
  }
}

ensure_local_dirs() {
  mkdir -p "${LOG_DIR}" "${PID_DIR}" "${LOCAL_CONFIG_DIR}"
}

ensure_local_config() {
  local service="$1"
  local config_dir="${LOCAL_CONFIG_DIR}/${service}"
  local config_file="${config_dir}/application.yml"
  local template_file="${CONFIG_TEMPLATE_DIR}/${service}/application.yml"
  mkdir -p "${config_dir}"
  if [[ ! -f "${config_file}" && -f "${template_file}" ]]; then
    cp "${template_file}" "${config_file}"
    log "created local ${service} config from template: ${config_file}"
  fi
}

config_arg() {
  local service="$1"
  local config_dir="${LOCAL_CONFIG_DIR}/${service}"
  if [[ -d "${config_dir}" ]]; then
    printf '%s' "--spring.config.additional-location=file:${config_dir}/"
  fi
}

pid_file() {
  echo "${PID_DIR}/$1.pid"
}

assert_service() {
  case "$1" in
    platform|server|rtc) ;;
    *)
      echo "ERROR: unknown local service: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
}

services_or_default() {
  if [[ "$#" -eq 0 ]]; then
    printf '%s\n' "${DEFAULT_LOCAL_SERVICES[@]}"
  else
    printf '%s\n' "$@"
  fi
}

stop_services_or_default() {
  if [[ "$#" -eq 0 ]]; then
    printf '%s\n' "${STOP_ORDER[@]}"
  else
    printf '%s\n' "$@"
  fi
}

is_running() {
  local service="$1" file pid
  file="$(pid_file "${service}")"
  [[ -f "${file}" ]] || return 1
  pid="$(cat "${file}" 2>/dev/null || true)"
  [[ "${pid}" =~ ^[0-9]+$ ]] || return 1
  kill -0 "${pid}" 2>/dev/null
}

write_run_script() {
  local service="$1"
  local run_file="${LOCAL_DIR}/run-${service}.sh"
  local spring_config_arg
  spring_config_arg="$(config_arg "${service}")"
  cat > "${run_file}" <<EOF
#!/usr/bin/env bash
set -euo pipefail
cd "${ROOT_DIR}"
rm -rf "${ROOT_DIR}/${service}/target/classes/db/migration"
rm -f "${ROOT_DIR}/${service}/target/${service}.jar"
mvn -pl "${service}" -am -DskipTests package
exec java \${JAVA_OPTS:-} -Dspring.profiles.active="\${SPRING_PROFILES_ACTIVE:-dev}" -jar "${ROOT_DIR}/${service}/target/${service}.jar" ${spring_config_arg}
EOF
  chmod +x "${run_file}"
  echo "${run_file}"
}

wait_started() {
  local service="$1" pid="$2" log_file="$3"
  local marker
  case "${service}" in
    platform) marker="Started IMPlatformApp" ;;
    server) marker="Started IMServerApp" ;;
    rtc) marker="Started RtcApp" ;;
  esac
  for _ in {1..90}; do
    if ! kill -0 "${pid}" 2>/dev/null; then
      return 1
    fi
    if grep -q "${marker}" "${log_file}" 2>/dev/null; then
      return 0
    fi
    sleep 1
  done
  return 2
}

local_start() {
  require_cmd mvn
  require_cmd java
  ensure_local_dirs
  local service run_file log_file pid
  while IFS= read -r service; do
    assert_service "${service}"
    if is_running "${service}"; then
      log "${service} already running (pid=$(cat "$(pid_file "${service}")"))"
      continue
    fi
    ensure_local_config "${service}"
    run_file="$(write_run_script "${service}")"
    log_file="${LOG_DIR}/${service}.log"
    : >"${log_file}"
    log "starting ${service}; log=${log_file}"
    nohup "${run_file}" >"${log_file}" 2>&1 </dev/null &
    pid=$!
    echo "${pid}" >"$(pid_file "${service}")"
    if wait_started "${service}" "${pid}" "${log_file}"; then
      log "${service} started (pid=${pid})"
    else
      rm -f "$(pid_file "${service}")"
      log "${service} failed to start; check ${log_file}"
      return 1
    fi
  done < <(services_or_default "$@")
}

child_pids() {
  pgrep -P "$1" 2>/dev/null || true
}

terminate_tree() {
  local pid="$1" child
  for child in $(child_pids "${pid}"); do
    terminate_tree "${child}"
  done
  kill "${pid}" 2>/dev/null || true
}

force_kill_tree() {
  local pid="$1" child
  for child in $(child_pids "${pid}"); do
    force_kill_tree "${child}"
  done
  kill -9 "${pid}" 2>/dev/null || true
}

local_stop() {
  ensure_local_dirs
  local service file pid
  while IFS= read -r service; do
    assert_service "${service}"
    file="$(pid_file "${service}")"
    if [[ ! -f "${file}" ]]; then
      log "${service} not running (no local pid file)"
      continue
    fi
    pid="$(cat "${file}" 2>/dev/null || true)"
    if [[ ! "${pid}" =~ ^[0-9]+$ ]]; then
      rm -f "${file}"
      log "${service} pid file invalid, removed"
      continue
    fi
    if ! kill -0 "${pid}" 2>/dev/null; then
      rm -f "${file}"
      log "${service} already stopped (stale pid ${pid})"
      continue
    fi
    log "stopping ${service} (pid=${pid})"
    terminate_tree "${pid}"
    for _ in {1..20}; do
      if ! kill -0 "${pid}" 2>/dev/null; then
        break
      fi
      sleep 1
    done
    if kill -0 "${pid}" 2>/dev/null; then
      log "force killing ${service} (pid=${pid})"
      force_kill_tree "${pid}"
    fi
    rm -f "${file}"
    log "${service} stopped"
  done < <(stop_services_or_default "$@")
}

local_status() {
  ensure_local_dirs
  local service file pid
  while IFS= read -r service; do
    assert_service "${service}"
    file="$(pid_file "${service}")"
    if [[ ! -f "${file}" ]]; then
      echo "${service}: stopped"
      continue
    fi
    pid="$(cat "${file}" 2>/dev/null || true)"
    if [[ "${pid}" =~ ^[0-9]+$ ]] && kill -0 "${pid}" 2>/dev/null; then
      echo "${service}: running pid=${pid}"
    else
      echo "${service}: stopped stale_pid=${pid:-none}"
    fi
  done < <(services_or_default "$@")
}

ACTION="${1:-}"
if [[ -n "${ACTION}" ]]; then
  shift
fi

case "${ACTION}" in
  local-start) local_start "$@" ;;
  local-stop) local_stop "$@" ;;
  local-status) local_status "$@" ;;
  -h|--help|help|"") usage ;;
  *)
    echo "ERROR: unknown command: ${ACTION}" >&2
    usage >&2
    exit 2
    ;;
esac
