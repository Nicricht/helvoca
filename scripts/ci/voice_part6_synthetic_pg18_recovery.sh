#!/usr/bin/env bash
# Part 6: CI-only synthetic PostgreSQL restore + encryption exercise.
# NEVER supply production dumps or credentials. Both Docker containers have no network.
set -euo pipefail
umask 077

for executable in docker openssl sha256sum python3; do
  command -v "$executable" >/dev/null
done
if env | cut -d= -f1 | grep -Eq '^(RAILWAY_TOKEN|RAILWAY_ENVIRONMENT|DATABASE_URL|DB_URL|PGHOST|PGPASSWORD|PGSERVICE|PGSERVICEFILE|PGPASSFILE)$'; then
  echo "PART6_BLOCKED: production-capable database environment detected" >&2
  exit 2
fi

work="$(mktemp -d)"
source_name="helvoca-part6-source-$RANDOM"
restore_name="helvoca-part6-restore-$RANDOM"
cleanup() {
  docker rm -f "$source_name" "$restore_name" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT
fixture_password="$(openssl rand -hex 24)"

docker run --detach --name "$source_name" --network none \
  --env POSTGRES_PASSWORD="$fixture_password" postgres:18-alpine >/dev/null
docker run --detach --name "$restore_name" --network none \
  --env POSTGRES_PASSWORD="$fixture_password" postgres:18-alpine >/dev/null
for instance in "$source_name" "$restore_name"; do
  ready=0
  for attempt in $(seq 1 45); do
    if docker exec -u postgres "$instance" pg_isready -U postgres -d postgres >/dev/null 2>&1; then
      ready=1
      break
    fi
    sleep 2
  done
  if [ "$ready" -ne 1 ]; then
    echo "PART6_BLOCKED: disposable PostgreSQL unavailable" >&2
    exit 3
  fi
  if [ "$(docker inspect -f '{{.HostConfig.NetworkMode}}' "$instance")" != "none" ]; then
    echo "PART6_BLOCKED: fixture container has network access" >&2
    exit 3
  fi
done

docker exec -i -u postgres "$source_name" psql -v ON_ERROR_STOP=1 -U postgres -d postgres <<'SQL'
CREATE TABLE public.part6_tenant (
  tenant_id integer PRIMARY KEY, tenant_name text NOT NULL UNIQUE
);
CREATE TABLE public.part6_booking (
  booking_id integer PRIMARY KEY,
  tenant_id integer NOT NULL REFERENCES public.part6_tenant(tenant_id),
  status text NOT NULL CHECK (status IN ('NEW', 'DONE'))
);
INSERT INTO public.part6_tenant VALUES (1, 'synthetic-alpha'), (2, 'synthetic-beta');
INSERT INTO public.part6_booking VALUES (10, 1, 'NEW'), (20, 2, 'DONE');
SQL

fingerprint_query="SELECT string_agg(tenant_id::text || ':' || tenant_name, ',' ORDER BY tenant_id) FROM public.part6_tenant;"
booking_query="SELECT string_agg(booking_id::text || ':' || tenant_id::text || ':' || status, ',' ORDER BY booking_id) FROM public.part6_booking;"
source_tenants="$(docker exec -u postgres "$source_name" psql -XAtq -U postgres -d postgres -c "$fingerprint_query")"
source_bookings="$(docker exec -u postgres "$source_name" psql -XAtq -U postgres -d postgres -c "$booking_query")"
docker exec -u postgres "$source_name" pg_dump -Fc -U postgres -d postgres > "$work/fixture.dump"
test -s "$work/fixture.dump"
initial_hash="$(sha256sum "$work/fixture.dump" | awk '{print $1}')"

mkdir -m 700 "$work/a" "$work/b"
openssl rand -hex 32 > "$work/ephemeral-key"
for destination in a b; do
  openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt \
    -in "$work/fixture.dump" -out "$work/$destination/copy.enc" \
    -pass file:"$work/ephemeral-key"
  openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
    -in "$work/$destination/copy.enc" -out "$work/$destination/decoded.dump" \
    -pass file:"$work/ephemeral-key"
  cmp "$work/fixture.dump" "$work/$destination/decoded.dump"
  test "$initial_hash" = "$(sha256sum "$work/$destination/decoded.dump" | awk '{print $1}')"
done
if cmp -s "$work/a/copy.enc" "$work/b/copy.enc"; then
  echo "PART6_BLOCKED: encryption salting check failed" >&2
  exit 3
fi
echo "PART6_SYNTHETIC_ENCRYPTION=PASS (two ephemeral local copies, NOT offsite)"

docker cp "$work/fixture.dump" "$restore_name:/tmp/fixture.dump"
docker exec "$restore_name" chmod 0644 /tmp/fixture.dump
start_seconds="$(date +%s)"
docker exec -u postgres "$restore_name" pg_restore \
  --exit-on-error --no-owner --no-privileges -U postgres -d postgres /tmp/fixture.dump
elapsed_seconds="$(( $(date +%s) - start_seconds ))"

restored_tenants="$(docker exec -u postgres "$restore_name" psql -XAtq -U postgres -d postgres -c "$fingerprint_query")"
restored_bookings="$(docker exec -u postgres "$restore_name" psql -XAtq -U postgres -d postgres -c "$booking_query")"
[ "$source_tenants" = "$restored_tenants" ]
[ "$source_bookings" = "$restored_bookings" ]
foreign_keys="$(docker exec -u postgres "$restore_name" psql -XAtq -U postgres -d postgres -c "SELECT count(*) FROM pg_constraint WHERE conrelid = 'public.part6_booking'::regclass AND contype = 'f';")"
[ "$foreign_keys" = "1" ]
echo "PART6_SYNTHETIC_PG18_RESTORE=PASS"
echo "PART6_SYNTHETIC_RESTORE_SECONDS=$elapsed_seconds (NOT production RTO)"
echo "PART6_PRODUCTION_BACKUP=UNVERIFIED"
echo "PROD_DEPLOY=NO-GO"
echo "LIVE_CALL_CERTIFIED=false"
echo "COMMERCIAL_GO=NO-GO"
