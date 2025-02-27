#!/bin/bash
PREF="BridgeNet ::"

BUILD_DIR=.build
SCRIPTS_DIR=.scripts
ENDPOINTS_MODULE_PATH=services/endpoint

APP=$(basename $0)

function except_code() {
    # shellcheck disable=SC2181
    if [ $? -eq 0 ]; then
      echo "$PREF [Success completed]"
    else
      echo "$PREF [Process aborted: failed]"
      exit 1
    fi
}
