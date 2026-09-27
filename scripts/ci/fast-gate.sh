#!/usr/bin/env bash
set -euo pipefail

BASE_SHA="${1:-}"
ZERO_SHA="0000000000000000000000000000000000000000"

if [[ -z "$BASE_SHA" || "$BASE_SHA" == "null" || "$BASE_SHA" == "$ZERO_SHA" ]]; then
  BASE_SHA="$(git rev-parse HEAD^ 2>/dev/null || git rev-parse HEAD)"
fi

if ! git cat-file -e "$BASE_SHA^{commit}" 2>/dev/null; then
  git fetch --no-tags --depth=1 origin "$BASE_SHA"
fi

mapfile -t CHANGED < <(git diff --name-only "$BASE_SHA"...HEAD)

echo "Fast Gate base: $BASE_SHA"
echo "Changed files: ${#CHANGED[@]}"
printf ' - %s\n' "${CHANGED[@]:-}"

if [[ ${#CHANGED[@]} -eq 0 ]]; then
  echo "No changed files. Fast Gate passed."
  exit 0
fi

declare -A TESTS=()
JAVA_CHANGED=false
POM_CHANGED=false

add_tests_from_dir() {
  local dir="$1"
  [[ -d "$dir" ]] || return 0
  while IFS= read -r path; do
    local name
    name="$(basename "$path" .java)"
    TESTS["$name"]=1
  done < <(find "$dir" -maxdepth 1 -type f \( -name '*Test.java' -o -name '*Tests.java' \) | sort)
}

add_test_file() {
  local path="$1"
  local name
  name="$(basename "$path" .java)"
  TESTS["$name"]=1
}

for file in "${CHANGED[@]}"; do
  case "$file" in
    pom.xml)
      POM_CHANGED=true
      ;;
    src/main/java/*.java|src/main/java/**/*.java)
      JAVA_CHANGED=true
      test_dir="${file/src\/main\/java/src\/test\/java}"
      add_tests_from_dir "$(dirname "$test_dir")"

      case "$file" in
        src/main/java/cl/helvoca/ai/realtime/*)
          add_tests_from_dir "src/test/java/cl/helvoca/ai/realtime"
          add_tests_from_dir "src/test/java/cl/helvoca/booking"
          add_tests_from_dir "src/test/java/cl/helvoca/call"
          ;;
        src/main/java/cl/helvoca/booking/*)
          add_tests_from_dir "src/test/java/cl/helvoca/booking"
          add_tests_from_dir "src/test/java/cl/helvoca/ai/realtime"
          add_tests_from_dir "src/test/java/cl/helvoca/call"
          ;;
        src/main/java/cl/helvoca/call/*)
          add_tests_from_dir "src/test/java/cl/helvoca/call"
          add_tests_from_dir "src/test/java/cl/helvoca/ai/realtime"
          ;;
        src/main/java/cl/helvoca/operations/*)
          add_tests_from_dir "src/test/java/cl/helvoca/operations"
          add_tests_from_dir "src/test/java/cl/helvoca/booking"
          ;;
      esac
      ;;
    src/test/java/*.java|src/test/java/**/*.java)
      JAVA_CHANGED=true
      add_test_file "$file"
      ;;
    src/main/resources/static/*.js|src/main/resources/static/**/*.js|e2e/*.js|e2e/**/*.js)
      echo "Syntax check: $file"
      node --check "$file"
      ;;
  esac
done

if [[ "$JAVA_CHANGED" == true || "$POM_CHANGED" == true ]]; then
  echo "Compiling Java test sources..."
  mvn --batch-mode --no-transfer-progress -DskipTests test-compile
fi

if [[ ${#TESTS[@]} -gt 0 ]]; then
  mapfile -t SORTED_TESTS < <(printf '%s\n' "${!TESTS[@]}" | sort)
  TEST_CSV="$(IFS=,; echo "${SORTED_TESTS[*]}")"
  echo "Running targeted tests: $TEST_CSV"
  mvn --batch-mode --no-transfer-progress \
    -Dtest="$TEST_CSV" \
    -Dsurefire.failIfNoSpecifiedTests=false \
    test
elif [[ "$JAVA_CHANGED" == true ]]; then
  echo "No direct package tests found. Compilation gate completed; Full Gate will run the complete suite."
fi

echo "Fast Gate passed."
