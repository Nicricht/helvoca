#!/usr/bin/env python3
"""Helvoca release Part 2/10: OFFLINE immutable V100-V103 migration contract.

No Railway, Twilio, HTTP, PostgreSQL connection, calls, deployments, SQL
execution or migration application. This is source-integrity certification,
NOT production readiness. Selected GitHub JUnit/Testcontainers jobs cover
actual synthetic Flyway incremental migration and PostgreSQL compatibility.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
FROZEN_MIGRATIONS = {
    100: ("V100__twilio_managed_voice_binding_provenance.sql",
          "a3e300ff2eb7891e8dc6069244318608d8aa76ed"),
    101: ("V101__twilio_verified_existing_binding_provenance.sql",
          "54eeb2d87bac7df26fe29cc56885fd2494312613"),
    102: ("V102__twilio_existing_enrollment_claim.sql",
          "2c06b6170899c5dedcb851d7a4b6fc7cec58ad5b"),
    103: ("V103__twilio_existing_enrollment_admin_approval.sql",
          "8289dc3d97a4240585fcbb46dee2396f6a8b6431"),
}
FORBIDDEN_RELEASE_CLAIMS = ("PROD_DEPLOY=GO", "COMMERCIAL_GO=true",
                            "LIVE_CALL_CERTIFIED=true")


def git_blob_sha(data: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(data)).encode("ascii")
                        + b"\\0" + data).hexdigest()


def assess(repo_root: Path = ROOT) -> dict:
    issues = []
    migration_dir = repo_root / "src/main/resources/db/migration"
    sources = {}
    for version, (filename, expected_sha) in FROZEN_MIGRATIONS.items():
        matches = list(migration_dir.glob(f"V{version}__*.sql"))
        if len(matches) != 1 or matches[0].name != filename:
            issues.append(f"MIGRATION_V{version}_FILENAME_OR_COUNT")
            continue
        try:
            source = matches[0].read_bytes()
            sql = source.decode("utf-8")
        except (OSError, UnicodeError):
            issues.append(f"MIGRATION_V{version}_UNREADABLE")
            continue
        sources[version] = sql
        if git_blob_sha(source) != expected_sha:
            issues.append(f"MIGRATION_V{version}_CHANGED")
        if re.search(r"(?im)^\\s*(?:UPDATE\\s+(?:public\\.)?phone_number\\s+SET|"
                     r"INSERT\\s+INTO\\s+(?:public\\.)?phone_number|"
                     r"DELETE\\s+FROM\\s+(?:public\\.)?phone_number)\\b", sql):
            issues.append(f"MIGRATION_V{version}_LEGACY_PHONE_DML")
        if any(claim in sql for claim in FORBIDDEN_RELEASE_CLAIMS):
            issues.append(f"MIGRATION_V{version}_UNAUTHORIZED_GO")
    for version, required in {
        100: ("ADD COLUMN IF NOT EXISTS twilio_binding_source",
              "ADD COLUMN IF NOT EXISTS twilio_account_sid",
              "ADD COLUMN IF NOT EXISTS voice_verified_at"),
        101: ("VERIFIED_EXISTING", "helvoca_verified_existing_write_guard"),
        102: ("CREATE TABLE public.twilio_existing_enrollment_claim",
              "FORCE ROW LEVEL SECURITY", "helvoca_system"),
        103: ("CREATE TABLE public.twilio_existing_enrollment_request",
              "CREATE TABLE public.twilio_existing_enrollment_audit",
              "FORCE ROW LEVEL SECURITY", "helvoca_system"),
    }.items():
        if version in sources and not all(x in sources[version] for x in required):
            issues.append(f"MIGRATION_V{version}_CONTRACT_DRIFT")
    return {
        "part": "2/10",
        "offline_source_contract": "PASS" if not issues else "BLOCKED",
        "issues": sorted(set(issues)),
        "migration_versions": list(FROZEN_MIGRATIONS),
        "production_sql_applied": False,
        "flyway_real_restored_dump_upgrade": "NOT_RUN",
        "production_flyway_version": "V99_OPERATOR_REPORTED",
        "prod_deploy": "NO-GO",
        "may_merge": False,
        "live_call_certified": False,
        "commercial_go": False,
    }


if __name__ == "__main__":
    result = assess()
    print(json.dumps(result, sort_keys=True))
    raise SystemExit(0 if result["offline_source_contract"] == "PASS" else 2)
