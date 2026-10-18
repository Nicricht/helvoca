#!/usr/bin/env bash
# C1: Retired unsafe same-server restore drill.
# Previous script restored/dropped a scratch database on PGHOST, which may
# point at production. Never execute DB clients from this legacy entry point.
set -euo pipefail
printf '%s\n' \
  'C1_LEGACY_SAME_SERVER_RESTORE_BLOCKED' \
  'The old same-server PostgreSQL recovery drill is permanently disabled.' \
  'Reuse scripts/ops/c1_independent_remote_restore.py for the previously approved offline restore.' \
  'Never restore or drop scratch databases on production PostgreSQL.' >&2
exit 2
