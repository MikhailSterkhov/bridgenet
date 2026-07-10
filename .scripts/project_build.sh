#!/usr/bin/env bash
run_mvn clean install -N
except_code

rm -rf "${BUILD_DIR:?}" || { echo "$PREF Failed to clean $BUILD_DIR (is a built server still running?)"; exit 1; }
mkdir -p "$BUILD_DIR" || exit 1

function install_module() {
  run_mvn clean install -Dmaven.test.skip --file "$1/pom.xml"
  except_code
}

function assembly_resources() {
  mkdir -p "$BUILD_DIR/etc"
  cp -R assembly/etc/. "$BUILD_DIR/etc"
  except_code
  cp -R bootstrap/target/bridgenet-server.jar "$BUILD_DIR"
  except_code
}

declare -a modules_queue=("assembly" "profiler" "api" "mtp" "jdbc" "rest" "services" "bootstrap" "client" "testing")

for module in "${modules_queue[@]}"
do
  install_module "$module"
done

# The services reactor leaves per-endpoint 'endpoint/' jar directories in the
# source tree (maven-jar-plugin outputDirectory) — keep the working copy clean.
rm -rf "${ENDPOINTS_MODULE_PATH:?}"/*/endpoint

assembly_resources
