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
RUN_CHAOS=false
RUN_PILOT=false
RUN_SOFTWARE_FACTORY=false

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
          add_test_file "src/test/java/cl/helvoca/ai/gemini/ClosingConversationCertificationPackTest.java"
          ;;
        src/main/java/cl/helvoca/ai/gemini/*)
          add_tests_from_dir "src/test/java/cl/helvoca/ai/gemini"
          add_test_file "src/test/java/cl/helvoca/ai/realtime/BookingConversationCertificationPackTest.java"
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
          RUN_CHAOS=true
          ;;
        src/main/java/cl/helvoca/payment/*|src/main/java/cl/helvoca/jobs/*|src/main/java/cl/helvoca/messaging/meta/*|src/main/java/cl/helvoca/messaging/audio/*|src/main/java/cl/helvoca/quality/*)
          RUN_CHAOS=true
          ;;
      esac
      ;;
    src/test/java/*.java|src/test/java/**/*.java)
      JAVA_CHANGED=true
      add_test_file "$file"
      case "$file" in
        src/test/java/cl/helvoca/chaos/*|src/test/java/cl/helvoca/payment/*Chaos*|src/test/java/cl/helvoca/operations/*Chaos*)
          RUN_CHAOS=true
          ;;
      esac
      ;;
    scripts/ci/chaos-certification.sh)
      RUN_CHAOS=true
      ;;
    scripts/ci/pilot-e2e-certification.sh|docs/PILOT_END_TO_END_CERTIFICATION_V1.md)
      RUN_PILOT=true
      ;;
    software-factory/*|software-factory/**/*)
      RUN_SOFTWARE_FACTORY=true
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

if [[ "$RUN_CHAOS" == true ]]; then
  echo "Critical reliability surface changed. Running Failure/Chaos Lab V3..."
  bash scripts/ci/chaos-certification.sh
fi

if [[ "$RUN_PILOT" == true ]]; then
  echo "Pilot certification surface changed. Running Pilot End-to-End Certification V1..."
  bash scripts/ci/pilot-e2e-certification.sh
fi

if [[ "$RUN_SOFTWARE_FACTORY" == true ]]; then
  echo "Software Factory surface changed. Running contract tests..."
  python3 -m unittest discover -s software-factory/tests -p 'test_*.py' -v
fi

echo "Checking protected frontend frame contract..."
node scripts/ci/check-frontend-frame-contract.js

echo "Fast Gate passed."
