#!/usr/bin/env python3
"""C2-B one-call Twilio Call REST readback, GET-only, never a release approval.

Requires two deliberate opt-ins, and the carrier/tenant SIDs supplied through
private environment variables, not shell arguments. Does not call Twilio unless
explicitly invoked. Cannot certify pause, callback, DB, or local AI closure.
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
import sys
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, Request, build_opener
from uuid import UUID

ACCOUNT = re.compile(r"AC[0-9a-fA-F]{32}\Z")
CALL = re.compile(r"CA[0-9a-fA-F]{32}\Z")
BASE = "https://api.twilio.com"
TERMINAL = frozenset(("canceled", "completed", "failed", "busy", "no-answer"))
NONTERMINAL = frozenset(("queued", "ringing", "in-progress"))


class Blocked(Exception):
    pass


def require(condition: bool, code: str) -> None:
    if not condition:
        raise Blocked(code)


def get_call(account: str, call: str, auth_account: str, token: str) -> dict:
    """Single bounded GET to precisely one call. Reject redirects and raw bodies."""
    require(bool(ACCOUNT.fullmatch(account)) and bool(CALL.fullmatch(call)),
            "C2B_INVALID_CALL_IDENTITY")
    require(bool(ACCOUNT.fullmatch(auth_account)) and bool(token),
            "C2B_CREDENTIALS_MISSING")
    path = f"/2010-04-01/Accounts/{account}/Calls/{call}.json"
    url = BASE + path
    basic = base64.b64encode((auth_account + ":" + token).encode("utf-8")).decode("ascii")
    request = Request(url, method="GET", headers={
        "Authorization": "Basic " + basic, "Accept": "application/json"
    })

    class NoRedirect(HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            raise Blocked("C2B_PROVIDER_REDIRECT_BLOCKED")

    try:
        with build_opener(NoRedirect()).open(request, timeout=12) as response:
            require(response.geturl() == url, "C2B_PROVIDER_REDIRECT_BLOCKED")
            require(response.status == 200, "C2B_PROVIDER_READ_UNAVAILABLE")
            raw = response.read(65537)
    except Blocked:
        raise
    except (HTTPError, URLError, TimeoutError, OSError, ValueError) as exc:
        raise Blocked("C2B_PROVIDER_READ_UNAVAILABLE") from exc
    require(len(raw) <= 65536, "C2B_PROVIDER_RESPONSE_TOO_LARGE")
    try:
        value = json.loads(raw.decode("utf-8", "strict"))
    except (UnicodeError, ValueError) as exc:
        raise Blocked("C2B_PROVIDER_RESPONSE_INVALID") from exc
    require(isinstance(value, dict), "C2B_PROVIDER_RESPONSE_INVALID")
    return value


def classify(value: dict, account: str, call: str) -> str:
    require(value.get("account_sid") == account and value.get("sid") == call,
            "C2B_PROVIDER_IDENTITY_MISMATCH")
    status = value.get("status")
    require(isinstance(status, str) and status in TERMINAL | NONTERMINAL,
            "C2B_PROVIDER_STATUS_UNKNOWN")
    return status


def write_private_report(status: str, root: str) -> Path:
    require(bool(root), "C2B_PRIVATE_STORAGE_MISSING")
    folder = (Path(root) / "Helvoca" / "private-evidence").absolute()
    require(not any(part.is_symlink() or (part / ".git").exists()
                    for part in (folder, *folder.parents)),
            "C2B_PRIVATE_STORAGE_UNSAFE")
    try:
        folder.mkdir(mode=0o700, parents=True, exist_ok=True)
        target = folder / ("helvoca-c2b-twilio-call-get-" +
                           datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") +
                           "-" + secrets.token_hex(4) + ".private.json")
        document = {
            "observed_utc": datetime.now(timezone.utc).isoformat(),
            "scope": "single Twilio Call GET, read-only",
            "identity_match": True,
            "carrier_call_status": status,
            "carrier_terminal_get_observed": status in TERMINAL,
            "pilot_paused_committed": "UNVERIFIED",
            "authenticated_callback": "UNVERIFIED",
            "db_and_local_ai_stop": "UNVERIFIED",
            "commercial_go": False,
        }
        with target.open("x", encoding="utf-8") as handle:
            os.chmod(target, 0o600)
            json.dump(document, handle, indent=2, sort_keys=True)
        return target
    except OSError as exc:
        raise Blocked("C2B_PRIVATE_STORAGE_WRITE_FAILED") from exc


def main(argv: list[str] | None = None, environ: dict | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--probe-read-only", action="store_true")
    args = parser.parse_args(argv)
    env = os.environ if environ is None else environ
    try:
        require(args.probe_read_only and env.get("C2B_CALL_READBACK_APPROVED") == "yes",
                "C2B_EXPLICIT_OPERATOR_OPT_IN_REQUIRED")
        account = env.get("C2B_EXPECTED_ACCOUNT_SID", "")
        call = env.get("C2B_EXPECTED_CALL_SID", "")
        auth_account = env.get("TWILIO_ACCOUNT_SID", "")
        require(bool(ACCOUNT.fullmatch(account)) and bool(CALL.fullmatch(call)),
                "C2B_INVALID_CALL_IDENTITY")
        require(account == auth_account or
                (account == env.get("HELVOCA_TWILIO_SUBACCOUNT_SID") and
                 bool(ACCOUNT.fullmatch(auth_account))),
                "C2B_ACCOUNT_SCOPE_NOT_CERTIFIED")
        tenant = env.get("C2B_PILOT_TENANT_ID", "")
        require(bool(tenant) and str(UUID(tenant)) == tenant,
                "C2B_PILOT_TENANT_NOT_SPECIFIED")
        value = get_call(account, call, auth_account, env.get("TWILIO_AUTH_TOKEN", ""))
        status = classify(value, account, call)
        saved = write_private_report(status, env.get("LOCALAPPDATA", ""))
        print("C2B_TWILIO_CALL_GET_READ_ONLY_CAPTURED")
        print("C2B_PROVIDER_TERMINAL_GET=" + ("OBSERVED" if status in TERMINAL else "NOT_OBSERVED"))
        print("C2B_PRIVATE_EVIDENCE: " + str(saved))
        print("C2B_NO_RELEASE_APPROVAL; PAUSE, callback, DB and AI STOP unverified")
        return 0 if status in TERMINAL else 2
    except (Blocked, ValueError, TypeError) as exc:
        code = str(exc) if isinstance(exc, Blocked) else "C2B_PILOT_TENANT_NOT_SPECIFIED"
        print("C2B_BLOCKED: " + code)
        print("C2B_NO_RELEASE_APPROVAL; no mutation or provider call confirmed")
        return 2


if __name__ == "__main__":
    sys.exit(main())
