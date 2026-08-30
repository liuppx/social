#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_NAME="$(basename "${ROOT_DIR}")"
OUTPUT_DIR="${ROOT_DIR}/output"
TAG_ARG="${1:-}"

if [[ "$#" -gt 1 ]]; then
  echo "Usage: $0 [v<major>.<minor>.<patch>]"
  exit 2
fi

ORIGINAL_REF="$(git -C "${ROOT_DIR}" rev-parse --abbrev-ref HEAD)"
ORIGINAL_COMMIT="$(git -C "${ROOT_DIR}" rev-parse HEAD)"

cleanup() {
  if [[ "${ORIGINAL_REF}" == "HEAD" ]]; then
    git -C "${ROOT_DIR}" checkout --detach "${ORIGINAL_COMMIT}" >/dev/null 2>&1 || true
  else
    git -C "${ROOT_DIR}" checkout "${ORIGINAL_REF}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

log() {
  echo "[$(date +"%Y-%m-%d %H:%M:%S")] $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "ERROR: command not found: $1"
    exit 1
  }
}

require_cmd git
require_cmd tar
require_cmd mvn
if [[ -d "${ROOT_DIR}/web" ]]; then
  require_cmd npm
fi

if ! git -C "${ROOT_DIR}" remote get-url origin >/dev/null 2>&1; then
  echo "ERROR: git remote 'origin' not configured."
  exit 1
fi

if [[ -n "$(git -C "${ROOT_DIR}" status --porcelain)" ]]; then
  echo "ERROR: working tree is dirty. Please commit or stash changes first."
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"

log "fetching latest remote branches and tags ..."
git -C "${ROOT_DIR}" fetch --tags origin

log "syncing main branch ..."
git -C "${ROOT_DIR}" checkout main >/dev/null
git -C "${ROOT_DIR}" pull --rebase origin main

is_valid_tag() {
  [[ "$1" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]
}

increment_patch() {
  local tag="$1"
  local major minor patch
  IFS='.' read -r major minor patch <<< "${tag#v}"
  patch=$((patch + 1))
  echo "v${major}.${minor}.${patch}"
}

CURRENT_TAG=""
if [[ -n "${TAG_ARG}" ]]; then
  if ! is_valid_tag "${TAG_ARG}"; then
    echo "ERROR: invalid TAG format '${TAG_ARG}', expected v<major>.<minor>.<patch>"
    exit 1
  fi
  if ! git -C "${ROOT_DIR}" rev-parse -q --verify "refs/tags/${TAG_ARG}" >/dev/null; then
    echo "ERROR: TAG '${TAG_ARG}' not found."
    exit 1
  fi
  CURRENT_TAG="${TAG_ARG}"
  log "using existing tag ${CURRENT_TAG}"
else
  MAX_TAG="$(git -C "${ROOT_DIR}" tag -l 'v[0-9]*.[0-9]*.[0-9]*' --sort=-v:refname | head -n 1 || true)"
  MAIN_COMMIT="$(git -C "${ROOT_DIR}" rev-parse main)"
  if [[ -z "${MAX_TAG}" ]]; then
    CURRENT_TAG="v0.0.1"
    log "no semantic tags found, creating ${CURRENT_TAG}"
  else
    TAG_COMMIT="$(git -C "${ROOT_DIR}" rev-list -n 1 "${MAX_TAG}")"
    if [[ "${TAG_COMMIT}" == "${MAIN_COMMIT}" ]]; then
      log "main already published at ${MAX_TAG}, skip packaging."
      exit 0
    fi
    CURRENT_TAG="$(increment_patch "${MAX_TAG}")"
    log "creating new tag ${CURRENT_TAG} from main"
  fi
  git -C "${ROOT_DIR}" tag "${CURRENT_TAG}" main
  git -C "${ROOT_DIR}" push origin "${CURRENT_TAG}"
fi

TAG_COMMIT="$(git -C "${ROOT_DIR}" rev-list -n 1 "${CURRENT_TAG}")"
SHORT_HASH="$(git -C "${ROOT_DIR}" rev-parse --short=7 "${TAG_COMMIT}")"
PKG_DIR_NAME="${PROJECT_NAME}-${CURRENT_TAG}-${SHORT_HASH}"
STAGE_DIR="${OUTPUT_DIR}/${PKG_DIR_NAME}"
ARCHIVE_PATH="${OUTPUT_DIR}/${PKG_DIR_NAME}.tar.gz"

log "checking out tag ${CURRENT_TAG} ..."
git -C "${ROOT_DIR}" checkout "tags/${CURRENT_TAG}" >/dev/null

if [[ -d "${ROOT_DIR}/web" ]]; then
  log "installing frontend dependencies ..."
  (
    cd "${ROOT_DIR}/web"
    npm install
    npm run build
  )
fi

log "building backend artifacts ..."
(
  cd "${ROOT_DIR}"
  mvn clean package -DskipTests
)

rm -rf "${STAGE_DIR}" "${ARCHIVE_PATH}"
mkdir -p "${STAGE_DIR}/bin" "${STAGE_DIR}/config" "${STAGE_DIR}/web" "${STAGE_DIR}/scripts" "${STAGE_DIR}/logs" "${STAGE_DIR}/pids"

for module in platform server rtc web3-identity; do
  jar_path="${ROOT_DIR}/${module}/target/${module}.jar"
  if [[ ! -f "${jar_path}" ]]; then
    echo "ERROR: required artifact not found: ${jar_path}"
    exit 1
  fi
  cp "${jar_path}" "${STAGE_DIR}/bin/"
done

for module in platform server rtc web3-identity; do
  cfg_dir="${ROOT_DIR}/${module}/src/main/resources"
  if [[ -d "${cfg_dir}" ]]; then
    mkdir -p "${STAGE_DIR}/config/${module}"
    find "${cfg_dir}" -maxdepth 1 -type f \( -name "application*.yml" -o -name "logback.xml" \) -exec cp {} "${STAGE_DIR}/config/${module}/" \;
  fi
done

if [[ -d "${ROOT_DIR}/web" ]]; then
  if [[ ! -d "${ROOT_DIR}/web/dist" ]]; then
    echo "ERROR: frontend build output not found: ${ROOT_DIR}/web/dist"
    exit 1
  fi
  cp -R "${ROOT_DIR}/web/dist" "${STAGE_DIR}/web/"
fi

for script in starter.sh health-check.sh test.sh; do
  script_path="${ROOT_DIR}/scripts/${script}"
  if [[ ! -f "${script_path}" ]]; then
    echo "ERROR: required package script not found: ${script_path}"
    exit 1
  fi
  cp "${script_path}" "${STAGE_DIR}/scripts/${script}"
  chmod +x "${STAGE_DIR}/scripts/${script}"
done

if [[ ! -d "${ROOT_DIR}/tests/smoke" ]]; then
  echo "ERROR: smoke test resources not found: ${ROOT_DIR}/tests/smoke"
  exit 1
fi
cp -R "${ROOT_DIR}/tests" "${STAGE_DIR}/"

cat > "${STAGE_DIR}/config/runtime.env.template" <<'EOF'
# Copy this file to config/runtime.env before starting services.
# The startup script sources config/runtime.env when it exists.

# Java executable.
# export JAVA_BIN=java

# JVM options shared by platform, server, rtc and web3-identity.
# Keep Spring application arguments out of JAVA_OPTS unless they are JVM system
# properties. The startup script places JAVA_OPTS before "-jar".
# export JAVA_OPTS="-Xms512m -Xmx1024m -Dspring.profiles.active=prod"
EOF

tar -czf "${ARCHIVE_PATH}" -C "${OUTPUT_DIR}" "${PKG_DIR_NAME}"
rm -rf "${STAGE_DIR}"

log "package created: ${ARCHIVE_PATH}"
