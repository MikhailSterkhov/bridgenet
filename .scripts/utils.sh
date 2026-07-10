#!/usr/bin/env bash
PREF="BridgeNet ::"

BUILD_DIR=.build
SCRIPTS_DIR=.scripts
ENDPOINTS_MODULE_PATH=services/endpoint

# Lombok val/var transformation is broken on JDK 25+, older code targets 1.8.
JAVA_MIN_VERSION=8
JAVA_MAX_VERSION=24

APP=$(basename "$0")

# Allow the 'alias mvn=...' workaround from README to work in non-interactive shells.
# Define the alias here (before run_mvn) if 'mvn' is not in your PATH.
shopt -s expand_aliases 2>/dev/null || true

# Neutral project settings keep builds reproducible on machines whose global
# ~/.m2/settings.xml contains mirrors/proxies that hijack third-party repos.
MAVEN_SETTINGS_FILE=${BRIDGENET_MAVEN_SETTINGS:-$SCRIPTS_DIR/etc/maven-settings.xml}

function run_mvn() {
    mvn -s "$MAVEN_SETTINGS_FILE" "$@"
}

function except_code() {
    # shellcheck disable=SC2181
    if [ $? -eq 0 ]; then
      echo "$PREF [Success completed]"
    else
      echo "$PREF [Process aborted: failed]"
      exit 1
    fi
}

function require_maven() {
    if ! command -v mvn >/dev/null 2>&1; then
      echo "$PREF Apache Maven ('mvn') was not found in PATH."
      echo "$PREF Install Maven (https://maven.apache.org) or add its 'bin' directory to PATH."
      echo "$PREF Windows (Git Bash) fallback: add 'alias mvn=\"path/to/maven/bin/mvn\"' to .scripts/utils.sh (see README)."
      exit 1
    fi
}

# Prints the major version ("8", "17", "21") for a raw java version string.
function normalize_java_major() {
    local version=$1
    version=${version#1.}
    version=${version%%[!0-9]*}
    echo "$version"
}

# Prints the major version of the JDK installed at "$1" (empty when undetectable).
function jdk_home_major() {
    local version=""
    if [ -f "$1/release" ]; then
      version=$(sed -n 's/^JAVA_VERSION="\(.*\)"$/\1/p' "$1/release" | head -1)
    fi
    if [ -z "$version" ] && [ -x "$1/bin/java" ]; then
      version=$("$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -1)
    fi
    if [ -n "$version" ]; then
      normalize_java_major "$version"
    fi
}

# Ensures mvn will run on a JDK within [JAVA_MIN_VERSION..JAVA_MAX_VERSION]:
# keeps the current JDK when it is already supported, otherwise searches the
# standard install locations of macOS, Linux and Windows (Git Bash) and exports
# JAVA_HOME. Set BRIDGENET_JAVA_HOME to force a specific JDK.
function resolve_java() {
    if [ -n "$BRIDGENET_JAVA_HOME" ]; then
      export JAVA_HOME="$BRIDGENET_JAVA_HOME"
      export PATH="$JAVA_HOME/bin:$PATH"
      echo "$PREF Using JDK $(jdk_home_major "$JAVA_HOME") from BRIDGENET_JAVA_HOME: $JAVA_HOME"
      return 0
    fi

    local major=""
    if [ -n "$JAVA_HOME" ]; then
      major=$(jdk_home_major "$JAVA_HOME")
    elif command -v java >/dev/null 2>&1; then
      major=$(normalize_java_major "$(java -version 2>&1 | sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -1)")
    fi

    if [ -n "$major" ] && [ "$major" -ge "$JAVA_MIN_VERSION" ] && [ "$major" -le "$JAVA_MAX_VERSION" ]; then
      return 0
    fi

    if [ -n "$major" ]; then
      echo "$PREF Default JDK $major is outside the supported range [$JAVA_MIN_VERSION..$JAVA_MAX_VERSION] (Lombok limitation), searching for a supported JDK..."
    else
      echo "$PREF No JDK detected via JAVA_HOME/PATH, searching for an installed JDK..."
    fi

    local program_files=""
    if command -v cygpath >/dev/null 2>&1; then
      program_files=$(cygpath -u "${PROGRAMFILES:-C:\\Program Files}")
    fi

    local best_home="" best_major=0 candidate cand_major
    for candidate in \
        "$HOME/Library/Java/JavaVirtualMachines"/*/Contents/Home \
        /Library/Java/JavaVirtualMachines/*/Contents/Home \
        "$HOME/.jdks"/* \
        /usr/lib/jvm/* \
        /usr/java/* \
        /opt/java/* \
        ${program_files:+"$program_files/Java"/*} \
        ${program_files:+"$program_files/Eclipse Adoptium"/*} \
        ${program_files:+"$program_files/Amazon Corretto"/*} \
        ${program_files:+"$program_files/Microsoft"/jdk*} \
        ${program_files:+"$program_files/Zulu"/*}
    do
      [ -x "$candidate/bin/java" ] || continue
      cand_major=$(jdk_home_major "$candidate")
      [ -n "$cand_major" ] || continue
      if [ "$cand_major" -ge "$JAVA_MIN_VERSION" ] && [ "$cand_major" -le "$JAVA_MAX_VERSION" ] && [ "$cand_major" -gt "$best_major" ]; then
        best_major=$cand_major
        best_home=$candidate
      fi
    done

    if [ -z "$best_home" ]; then
      echo "$PREF No JDK in the supported range [$JAVA_MIN_VERSION..$JAVA_MAX_VERSION] was found on this machine."
      echo "$PREF Install one (e.g. JDK 21) or point JAVA_HOME / BRIDGENET_JAVA_HOME to it."
      exit 1
    fi

    export JAVA_HOME="$best_home"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "$PREF Using JDK $best_major: $JAVA_HOME"
}
