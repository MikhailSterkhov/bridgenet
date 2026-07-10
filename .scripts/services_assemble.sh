#!/usr/bin/env bash
ENDPOINT_CONFIG_NAME=endpoint.json
ENDPOINT_ETC_PATH=${SCRIPTS_DIR:?}/etc
ENDPOINTS_TARGET_PATH=${BUILD_DIR:?}/services
ENDPOINT_TARGET=$1

echo "ENDPOINT_TARGET = $ENDPOINT_TARGET"

function apply_endpoint_configuration() {
  local name=$1
  local target_path="$ENDPOINTS_TARGET_PATH/$name"
  local conf_path="$target_path/$ENDPOINT_CONFIG_NAME"

  cp -R "$ENDPOINT_ETC_PATH/.gen/." "$target_path"
  except_code

  # Optional per-endpoint configuration overlay.
  if [ -d "$ENDPOINT_ETC_PATH/$name" ]; then
    cp -R "$ENDPOINT_ETC_PATH/$name/." "$target_path"
    except_code
  fi

  # Attached '.BAK' suffix + rm keeps in-place edit portable across GNU (Linux,
  # Git Bash) and BSD (macOS) sed.
  sed -i'.BAK' "s/%endpoint%/$name/" "$conf_path"
  except_code
  rm -f "$conf_path.BAK"
}

function configure_endpoint() {
  local target_path=$1
  local name
  name=$(basename "$target_path")

  echo "$PREF Endpoint detected: '$name' from $target_path"
  echo "  - Processing assemble of '$name'..."

  apply_endpoint_configuration "$name"

  echo "  - Endpoint '$name' was success assembled"
  echo ""
}

if [ -z "$ENDPOINT_TARGET" ]; then
  configured=0
  for endpoint in "$ENDPOINTS_TARGET_PATH"/*
  do
    [ -d "$endpoint" ] || continue
    configure_endpoint "$endpoint"
    configured=1
  done

  if [ "$configured" -eq 0 ]; then
    echo "$PREF No compiled endpoints found at $ENDPOINTS_TARGET_PATH (run './$APP endpoints' first)"
    exit 1
  fi
else
  if [ ! -d "$ENDPOINTS_TARGET_PATH/$ENDPOINT_TARGET" ]; then
    echo "$PREF Endpoint '$ENDPOINT_TARGET' is not found in $ENDPOINTS_TARGET_PATH (build it first: './$APP endpoints $ENDPOINT_TARGET')"
    exit 1
  fi

  configure_endpoint "$ENDPOINTS_TARGET_PATH/$ENDPOINT_TARGET"
fi
