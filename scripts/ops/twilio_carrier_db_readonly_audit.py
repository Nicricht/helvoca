#!/usr/bin/env python3
"""Stage D: bounded authenticated GET-only Twilio vs read-only Railway PostgreSQL.

Operator runs locally under Railway CLI 'railway run' to receive the existing
backend environment variables in process memory. Never logs/records secrets,
account/phone SIDs, tenant IDs or the telephone number. Database raw values
are held only in process memory. No calls, purchases, configuration writes,
database mutations, Flyway, deployments, or GitHub uploads.
"""
from __future__ import annotations

import argparse
import base64
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener
from uuid import UUID

PROJECT = "41988ee6-f9f6-48eb-b726-abb973bb5740"
ENVIRONMENT = "8bbc755a-58b5-46e8-8227-eefa4adbbce4"
PG_SERVICE = "107bb612-f25d-4c57-98a4-f494ea6b9cb4"
API_SERVICE = "edbfc4d2-3487-4cfb-ab7c-52fdb4060c4a"
PG_MARKER = "HELVOCA_D_CARRIER_DB:"
E164 = re.compile(r"^\+[1-9][0-9]{7,14}$")
ACCOUNT = re.compile(r"^AC[0-9a-fA-F]{32}$")
PN = re.compile(r"^PN[0-9a-fA-F]{32}$")
ROOT = "https://api.twilio.com"
VOICE_PATH = "/webhooks/v1/twilio/voice"
STATUS_PATH = "/webhooks/v1/twilio/status"


class AuditError(RuntimeError):
    pass


def expect(condition: bool, code: str):
    if not condition:
        raise AuditError(code)


def phone_sql(phone: str) -> str:
    """E.164 strict ASCII whitelist makes static inline SQL literal safe."""
    expect(E164.fullmatch(phone) is not None, "INVALID_E164")
    return (
        "BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;\n"
        "SELECT '" + PG_MARKER + "' || json_build_object("
        "'read_only', current_setting('transaction_read_only') = 'on',"
        "'major', current_setting('server_version_num')::integer / 10000,"
        "'rows', (SELECT COALESCE(json_agg(json_build_object("
        "'business_id', p.business_id, 'provider', p.provider,"
        "'active', p.active, 'external_id', p.external_id,"
        "'twilio_account_sid', to_jsonb(p)->>'twilio_account_sid',"
        "'twilio_binding_source', to_jsonb(p)->>'twilio_binding_source',"
        "'voice_verified_at', to_jsonb(p)->>'voice_verified_at'"
        ")), '[]'::json) FROM public.phone_number p"
        " WHERE p.phone_number = '" + phone + "'))::text;\n"
        "COMMIT;"
    )


def db_query(railway: str, phone: str) -> dict:
    encoded = base64.b64encode(phone_sql(phone).encode("utf-8")).decode("ascii")
    remote = (
        'export PGPASSWORD="$POSTGRES_PASSWORD"; '
        'printf %s ' + encoded + ' | base64 -d | '
        'psql -X -q -A -t -v ON_ERROR_STOP=1 '
        '-U "$POSTGRES_USER" -d "$POSTGRES_DB"'
    )
    cmd = [railway, "ssh", "--project", PROJECT, "--environment", ENVIRONMENT,
           "--service", PG_SERVICE, "--", "sh", "-c", remote]
    try:
        reply = subprocess.run(cmd, input=b"", stdout=subprocess.PIPE,
                               stderr=subprocess.DEVNULL, timeout=95, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise AuditError("DB_READONLY_UNAVAILABLE") from exc
    expect(reply.returncode == 0, "DB_READONLY_UNAVAILABLE")
    expect(len(reply.stdout) <= 24576, "DB_UNEXPECTED_RESPONSE")
    try:
        lines = reply.stdout.decode("utf-8").splitlines()
        found = [x.strip()[len(PG_MARKER):] for x in lines
                 if x.strip().startswith(PG_MARKER)]
        expect(len(found) == 1, "DB_UNEXPECTED_RESPONSE")
        data = json.loads(found[0])
    except (ValueError, UnicodeError) as exc:
        raise AuditError("DB_UNEXPECTED_RESPONSE") from exc
    expect(isinstance(data, dict) and data.get("read_only") is True
           and type(data.get("major")) is int and data["major"] == 18
           and isinstance(data.get("rows"), list)
           and len(data["rows"]) <= 3, "DB_UNEXPECTED_RESPONSE")
    return data


def twilio_get(path: str, parent: str, token: str) -> dict:
    """Provider reads restricted to the account/phone resource namespace."""
    allowed = re.fullmatch(
        r"/2010-04-01/Accounts/AC[0-9a-fA-F]{32}(?:"
        r"\\.json|"
        r"/IncomingPhoneNumbers\\.json\\?PhoneNumber=%2B[1-9][0-9]{7,14}&PageSize=20|"
        r"/IncomingPhoneNumbers/PN[0-9a-fA-F]{32}\\.json)",
        path,
    )
    expect(allowed is not None, "INVALID_PROVIDER_PATH")
    expect(ACCOUNT.fullmatch(parent) is not None and bool(token),
           "TWILIO_CREDENTIALS_MISSING")
    raw = (parent + ":" + token).encode("ascii")
    basic = base64.b64encode(raw).decode("ascii")
    url = ROOT + path
    request = Request(url, method="GET", headers={
        "Authorization": "Basic " + basic,
        "Accept": "application/json",
    })
    class DenyRedirect(HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            raise AuditError("TWILIO_REDIRECT_BLOCKED")
    opener = build_opener(DenyRedirect())
    try:
        with opener.open(request, timeout=12) as response:
            expect(response.geturl() == url, "TWILIO_REDIRECT_BLOCKED")
            data = response.read(131073)
    except (HTTPError, URLError, TimeoutError, OSError, ValueError) as exc:
        raise AuditError("TWILIO_READ_UNAVAILABLE") from exc
    expect(len(data) <= 131072, "TWILIO_RESPONSE_TOO_LARGE")
    try:
        value = json.loads(data)
    except (ValueError, UnicodeError) as exc:
        raise AuditError("TWILIO_INVALID_RESPONSE") from exc
    expect(isinstance(value, dict), "TWILIO_INVALID_RESPONSE")
    return value


def safe_public_base(raw: str) -> str:
    parsed = urlsplit(raw.strip())
    expect(parsed.scheme == "https" and bool(parsed.hostname)
           and parsed.username is None and parsed.password is None
           and parsed.query == "" and parsed.fragment == ""
           and parsed.port in (None, 443),
           "TWILIO_PUBLIC_BASE_INVALID")
    expect(not raw.strip().endswith("//"), "TWILIO_PUBLIC_BASE_INVALID")
    return raw.strip().rstrip("/")


def carrier_observe(phone: str, vars: dict, fetch=twilio_get) -> dict:
    parent, token = vars.get("TWILIO_ACCOUNT_SID", ""), vars.get("TWILIO_AUTH_TOKEN", "")
    expect(ACCOUNT.fullmatch(parent or "") is not None and bool(token),
           "TWILIO_CREDENTIALS_MISSING")
    sub = vars.get("HELVOCA_TWILIO_SUBACCOUNT_SID", "")
    expect(not sub or (ACCOUNT.fullmatch(sub) is not None and sub != parent),
           "INVALID_SUBACCOUNT_CONFIG")
    base = safe_public_base(vars.get("TWILIO_PUBLIC_BASE_URL", ""))
    accounts = [parent] + ([sub] if sub else [])
    matches = []
    for sid in accounts:
        page = fetch("/2010-04-01/Accounts/" + sid +
                     "/IncomingPhoneNumbers.json?" +
                     urlencode({"PhoneNumber": phone, "PageSize": 20}), parent, token)
        expect("incoming_phone_numbers" in page and
               isinstance(page["incoming_phone_numbers"], list)
               and page.get("next_page_uri") is None,
               "TWILIO_LIST_AMBIGUOUS_OR_INCOMPLETE")
        expect(len(page["incoming_phone_numbers"]) <= 1,
               "TWILIO_LIST_AMBIGUOUS_OR_INCOMPLETE")
        if page["incoming_phone_numbers"]:
            matches.append((sid, page["incoming_phone_numbers"][0]))
    expect(len(matches) == 1, "TWILIO_NOT_EXACTLY_ONE_OWNED_NUMBER")
    owner, listed = matches[0]
    expect(isinstance(listed, dict) and PN.fullmatch(str(listed.get("sid", ""))),
           "TWILIO_PHONE_SID_MISSING")
    sid = listed["sid"]
    account = fetch("/2010-04-01/Accounts/" + owner + ".json", parent, token)
    expect(account.get("sid") == owner and account.get("status") == "active",
           "TWILIO_ACCOUNT_NOT_ACTIVE")
    if owner != parent:
        expect(account.get("owner_account_sid") == parent,
               "TWILIO_SUBACCOUNT_PARENT_MISMATCH")
    detail = fetch("/2010-04-01/Accounts/" + owner +
                   "/IncomingPhoneNumbers/" + sid + ".json", parent, token)
    reasons = []
    def require(ok: bool, code: str):
        if not ok:
            reasons.append(code)
    for observation in (listed, detail):
        require(observation.get("sid") == sid
                and observation.get("account_sid") == owner
                and observation.get("phone_number") == phone,
                "TWILIO_IDENTITY_DISAGREES")
        require(observation.get("status") == "in-use",
                "TWILIO_NUMBER_NOT_IN_USE")
        caps = observation.get("capabilities")
        require(isinstance(caps, dict) and caps.get("voice") is True,
                "TWILIO_VOICE_UNAVAILABLE")
        receive = observation.get("voice_receive_mode")
        if receive is not None:
            require(receive == "voice", "TWILIO_VOICE_RECEIVE_MODE_MISMATCH")
        require(observation.get("voice_url") == base + VOICE_PATH
                and observation.get("voice_method") == "POST"
                and observation.get("status_callback") == base + STATUS_PATH
                and observation.get("status_callback_method") == "POST",
                "TWILIO_WEBHOOK_MISMATCH")
        require(not observation.get("voice_application_sid")
                and not observation.get("trunk_sid"),
                "TWILIO_VOICE_ROUTING_CONFLICT")
    return {"owner": owner, "phone_sid": sid,
            "owner_kind": "PARENT" if owner == parent else "SUBACCOUNT",
            "reasons": sorted(set(reasons))}


def compare(phone: str, tenant: str, db: dict, carrier: dict, variables: dict) -> list[str]:
    reasons = list(carrier["reasons"])
    rows = db["rows"]
    if len(rows) != 1:
        return sorted(set(reasons + ["DB_NUMBER_NOT_UNIQUE_OR_MISSING"]))
    row = rows[0]
    if not isinstance(row, dict):
        return ["DB_ROW_INVALID"]
    def require(test: bool, label: str):
        if not test:
            reasons.append(label)
    require(str(row.get("business_id")) == tenant, "DB_TENANT_MISMATCH")
    require(row.get("provider") == "TWILIO", "DB_PROVIDER_MISMATCH")
    require(row.get("active") is True, "DB_NUMBER_INACTIVE")
    require(row.get("external_id") == carrier["phone_sid"], "DB_PHONE_SID_MISMATCH")
    require(row.get("twilio_account_sid") == carrier["owner"],
            "DB_ACCOUNT_SID_MISSING_OR_MISMATCH")
    require(row.get("twilio_binding_source") in ("VERIFIED_EXISTING", "MANAGED_PURCHASE"),
            "DB_PROVENANCE_UNVERIFIED")
    require(bool(row.get("voice_verified_at")), "DB_VOICE_TIMESTAMP_MISSING")
    require(str(variables.get("HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED",
                              "")).lower() != "true",
            "LIVE_EFFECTS_SWITCH_ENABLED")
    return sorted(set(reasons))


def private_report(report: dict) -> Path:
    value = os.environ.get("LOCALAPPDATA")
    expect(bool(value), "LOCALAPPDATA_MISSING")
    for folder in ("private-evidence", "private-backups"):
        location = (Path(value) / "Helvoca" / folder).absolute()
        for ancestor in (location, *location.parents):
            expect(not ancestor.is_symlink() and not (ancestor / ".git").exists(),
                   "UNSAFE_PRIVATE_DESTINATION")
        try:
            location.mkdir(parents=True, exist_ok=True)
            name = ("helvoca-stage-d-twilio-carrier-db-" +
                    datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") +
                    "-" + secrets.token_hex(3) + ".private.json")
            target = location / name
            with target.open("x", encoding="utf-8") as output:
                json.dump(report, output, indent=2, sort_keys=True)
            return target
        except OSError:
            continue
    raise AuditError("WINDOWS_PRIVATE_REPORT_DENIED")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Bounded read-only carrier/DB comparison")
    parser.add_argument("--phone", required=True)
    parser.add_argument("--tenant", required=True)
    args = parser.parse_args(argv)
    try:
        phone = args.phone
        expect(E164.fullmatch(phone) is not None, "INVALID_E164")
        tenant = str(UUID(args.tenant))
        expect(args.tenant.lower() == tenant, "INVALID_TENANT_UUID")
        variables = dict(os.environ)
        expect(ACCOUNT.fullmatch(variables.get("TWILIO_ACCOUNT_SID", "")) is not None
               and bool(variables.get("TWILIO_AUTH_TOKEN")),
               "RAILWAY_RUN_BACKEND_VARIABLES_NOT_INJECTED")
        railway = shutil.which("railway")
        expect(bool(railway), "RAILWAY_CLI_MISSING")
        db = db_query(railway, phone)
        carrier = carrier_observe(phone, variables)
        blockers = compare(phone, tenant, db, carrier, variables)
        report = {
            "time_utc": datetime.now(timezone.utc).isoformat(),
            "source": "Twilio authenticated GET + Railway PostgreSQL READ ONLY",
            "twilio_get_only": True, "db_read_only": db["read_only"],
            "observed_accounts_checked": "PARENT_AND_CONFIGURED_SUBACCOUNT",
            "owned_number_kind": carrier["owner_kind"],
            "matching_number_count": len(db["rows"]),
            "status": "CARRIER_DB_READONLY_MATCH" if not blockers else "BLOCKED",
            "blockers": blockers,
            "production_modified": False, "live_call_certified": False,
            "commercial_go": False, "prod_deploy": "NO-GO",
        }
        saved = private_report(report)
        print("STAGE_D_TWILIO_DB_" + ("READONLY_MATCH" if not blockers else "BLOCKED"))
        print("Safe blocker codes: " + (", ".join(blockers) if blockers else "NONE"))
        print("Report saved privately: " + str(saved))
        print("No provider or database changes; commercial release remains NO-GO.")
        return 0 if not blockers else 2
    except (AuditError, ValueError, UnicodeError, OSError) as exc:
        message = str(exc) if isinstance(exc, AuditError) else "INVALID_INPUT_OR_LOCAL_IO"
        print("STAGE_D_TWILIO_DB_BLOCKED: " + message)
        print("No provider or database changes; commercial release remains NO-GO.")
        return 2


if __name__ == "__main__":
    sys.exit(main())
