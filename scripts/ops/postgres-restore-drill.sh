#!/usr/bin/env bash
set -euo pipefail

: "${PGHOST:?PGHOST is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
: "${PGDATABASE:?PGDATABASE is required}"

PGPORT="${PGPORT:-5432}"
RESTORE_DB="${RESTORE_DB:-helvoca_restore_drill_$(date -u +%Y%m%d%H%M%S)}"
BACKUP_FILE="${BACKUP_FILE:-$(mktemp -t helvoca-restore-drill-XXXXXX.dump)}"
KEEP_BACKUP="${KEEP_BACKUP:-false}"

case "$RESTORE_DB" in
  helvoca_restore_drill_[0-9]*) ;;
  *)
    echo "RESTORE_DB must start with helvoca_restore_drill_ followed by digits." >&2
    exit 2
    ;;
esac

cleanup() {
  set +e
  dropdb     --if-exists     --host="$PGHOST"     --port="$PGPORT"     --username="$PGUSER"     "$RESTORE_DB" >/dev/null 2>&1

  if [ "$KEEP_BACKUP" != "true" ]; then
    rm -f "$BACKUP_FILE"
  fi
}
trap cleanup EXIT INT TERM

echo "[1/5] Creating logical backup from $PGDATABASE"
pg_dump   --host="$PGHOST"   --port="$PGPORT"   --username="$PGUSER"   --dbname="$PGDATABASE"   --format=custom   --no-owner   --no-acl   --file="$BACKUP_FILE"

echo "[2/5] Creating isolated scratch database $RESTORE_DB"
createdb   --host="$PGHOST"   --port="$PGPORT"   --username="$PGUSER"   "$RESTORE_DB"

echo "[3/5] Restoring backup into scratch database"
pg_restore   --host="$PGHOST"   --port="$PGPORT"   --username="$PGUSER"   --dbname="$RESTORE_DB"   --no-owner   --no-acl   --exit-on-error   "$BACKUP_FILE"

echo "[4/5] Verifying schema and core tables"
flyway_rows="$(
  psql     --host="$PGHOST"     --port="$PGPORT"     --username="$PGUSER"     --dbname="$RESTORE_DB"     --tuples-only     --no-align     --command="SELECT COUNT(*) FROM flyway_schema_history;"
)"
flyway_rows="${flyway_rows//[[:space:]]/}"
if [ -z "$flyway_rows" ] || [ "$flyway_rows" -lt 1 ]; then
  echo "Restore drill failed: flyway_schema_history is empty." >&2
  exit 1
fi

core_tables="$(
  psql     --host="$PGHOST"     --port="$PGPORT"     --username="$PGUSER"     --dbname="$RESTORE_DB"     --tuples-only     --no-align     --command="SELECT COUNT(*) FROM (VALUES ('business'), ('app_user'), ('booking'), ('business_operation'), ('business_payment')) AS required(name) WHERE to_regclass('public.' || name) IS NOT NULL;"
)"
core_tables="${core_tables//[[:space:]]/}"
if [ "$core_tables" -ne 5 ]; then
  echo "Restore drill failed: expected 5 core tables, found $core_tables." >&2
  exit 1
fi

echo "[5/5] Restore drill PASSED"
echo "Flyway migrations present: $flyway_rows"
echo "Core tables present: $core_tables/5"
echo "Scratch database will be removed automatically."
