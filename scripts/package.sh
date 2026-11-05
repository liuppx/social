#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/package-common.sh"
package_common_init social "$@"
for command_name in mvn npm; do command -v "$command_name" >/dev/null 2>&1 || package_fail "$command_name is required"; done
(cd "$PACKAGE_ROOT_DIR/web" && npm install --no-audit --no-fund && npm run build)
(cd "$PACKAGE_ROOT_DIR" && mvn -B -DskipTests clean package)
package_make_root social
mkdir -p "$PACKAGE_DIR/bin" "$PACKAGE_DIR/config" "$PACKAGE_DIR/web"
for module in platform server rtc web3-identity; do
  jar="$PACKAGE_ROOT_DIR/$module/target/$module.jar"
  [[ -f "$jar" ]] || package_fail "required service artifact is missing: $jar"
  cp "$jar" "$PACKAGE_DIR/bin/"
  config_dir="$PACKAGE_ROOT_DIR/$module/src/main/resources"
  if [[ -d "$config_dir" ]]; then
    mkdir -p "$PACKAGE_DIR/config/$module"
    find "$config_dir" -maxdepth 1 -type f \( -name 'application*.yml' -o -name logback.xml \) -exec cp {} "$PACKAGE_DIR/config/$module/" \;
  fi
done
cp -R "$PACKAGE_ROOT_DIR/web/dist" "$PACKAGE_DIR/web/dist"
package_copy_scripts "$PACKAGE_DIR/scripts"
package_strip_build_files "$PACKAGE_DIR"
[[ ! -d "$PACKAGE_ROOT_DIR/tests" ]] || cp -R "$PACKAGE_ROOT_DIR/tests" "$PACKAGE_DIR/tests"
mkdir -p "$PACKAGE_DIR/config"
cat > "$PACKAGE_DIR/config/runtime.env.template" <<'ENV_TEMPLATE'
# Runtime JVM configuration; set only deployment-specific values.
# JAVA_BIN=java
# JAVA_OPTS=-Xms512m\ -Xmx1024m
ENV_TEMPLATE
package_finish_artifact service-with-assets
package_finish
