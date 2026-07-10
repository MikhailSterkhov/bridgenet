#!/usr/bin/env bash
ENDPOINTS_TARGET_PATH=${BUILD_DIR:?}/services
ENDPOINT_TARGET=$1

echo "ENDPOINT_TARGET = $ENDPOINT_TARGET"

run_mvn clean install -Dmaven.test.skip --file services/model/pom.xml
except_code

function compile_endpoint() {
  local name=$1
  local target_path="$ENDPOINTS_TARGET_PATH/$name"
  local endpoint_path="$ENDPOINTS_MODULE_PATH/$name"

  rm -rf "${target_path:?}" || { echo "$PREF Failed to clean $target_path"; exit 1; }
  mkdir -p "$target_path" || exit 1

  # The endpoint pom writes its jar into '<module>/endpoint/' (maven-jar-plugin outputDirectory).
  run_mvn clean install --file "$endpoint_path/pom.xml"
  except_code

  cp -R "$endpoint_path/endpoint/." "$target_path"
  except_code
  rm -rf "$endpoint_path/endpoint"
}

function build_endpoint() {
  local module_path=$1
  local name
  name=$(basename "$module_path")

  # Skip plain files (pom.xml etc.) and the parent's own 'target' directory.
  if [ ! -d "$module_path" ] || [ "$name" = "target" ]; then
    return 0
  fi

  echo "$PREF Endpoint detected: '$name' from $module_path"
  echo "  - Processing installation of '$name'..."

  compile_endpoint "$name"

  echo "  - Endpoint '$name' was success installed"
  echo ""
}

if [ -z "$ENDPOINT_TARGET" ]; then
  rm -rf "${ENDPOINTS_TARGET_PATH:?}" || { echo "$PREF Failed to clean $ENDPOINTS_TARGET_PATH"; exit 1; }
  mkdir -p "$ENDPOINTS_TARGET_PATH" || exit 1

  for endpoint in "$ENDPOINTS_MODULE_PATH"/*
  do
    build_endpoint "$endpoint"
  done
else
  if [ ! -d "$ENDPOINTS_MODULE_PATH/$ENDPOINT_TARGET" ]; then
    echo "$PREF Endpoint '$ENDPOINT_TARGET' is not found in $ENDPOINTS_MODULE_PATH"
    exit 1
  fi

  mkdir -p "$ENDPOINTS_TARGET_PATH" || exit 1
  build_endpoint "$ENDPOINTS_MODULE_PATH/$ENDPOINT_TARGET"
fi
