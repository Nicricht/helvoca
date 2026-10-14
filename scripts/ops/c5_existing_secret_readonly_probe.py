#!/usr/bin/env python3
"""Inspect an EXISTING Actions secret via Mercado Pago GET /users/me only.

No API writes, checkout, subscription creation or token output. The expected
seller identity is supplied as a SHA256 fingerprint of the ID, generated TEST
nickname and country independently shown in the owner's Test Accounts panel.
"""
import hashlib
import hmac
import json
import os
import re
import sys
from urllib import error, request

IDENTITY_URL = "https://api.mercadopago.com/users/me"

class ProbeBlocked(Exception):
    pass

def verify_public_identity(payload, expected_digest, seller_id_digest="", buyer_id_digest=""):
    if not isinstance(payload, dict):
        raise ProbeBlocked("C5_READONLY_RESPONSE_INVALID")
    user_id = str(payload.get("id") or "")
    nickname = payload.get("nickname")
    site = payload.get("site_id")
    if (not user_id.isascii() or not user_id.isdigit()
        or not isinstance(nickname, str) or
        not re.fullmatch(r"TEST[A-Z0-9]{4,40}", nickname.upper()) or
        site != "MLC"):
        raise ProbeBlocked("C5_EXISTING_SECRET_NOT_CONFIRMED_TEST_SELLER")
    if payload.get("test_user", True) is not True:
        raise ProbeBlocked("C5_EXISTING_SECRET_TEST_IDENTITY_CONTRADICTED")
    email = payload.get("email")
    if email is not None and (
        not isinstance(email, str) or
        not re.fullmatch(r"[^@\s]+@testuser\.com", email.strip().lower())
    ):
        raise ProbeBlocked("C5_EXISTING_SECRET_TEST_EMAIL_MISMATCH")
    candidate = hashlib.sha256(
        f"{user_id}|{nickname.upper()}|{site}".encode("utf-8")
    ).hexdigest()
    if not hmac.compare_digest(candidate, expected_digest):
        identity_digest = hashlib.sha256(user_id.encode("utf-8")).hexdigest()
        if seller_id_digest and hmac.compare_digest(identity_digest, seller_id_digest):
            raise ProbeBlocked("C5_EXISTING_SECRET_SELLER_ID_MATCH_BUT_ALIAS_MISMATCH")
        if buyer_id_digest and hmac.compare_digest(identity_digest, buyer_id_digest):
            raise ProbeBlocked("C5_EXISTING_SECRET_IS_SCREENSHOT_BUYER_NOT_SELLER")
        raise ProbeBlocked("C5_EXISTING_SECRET_IS_DIFFERENT_TEST_ACCOUNT")
    return True

def probe(token, expected_digest, opener=request.urlopen, seller_id_digest="", buyer_id_digest=""):
    if not token:
        raise ProbeBlocked("C5_EXISTING_REPOSITORY_SECRET_UNAVAILABLE_TO_ACTIONS")
    if not re.fullmatch(r"[a-f0-9]{64}", expected_digest):
        raise ProbeBlocked("C5_EXPECTED_SELLER_FINGERPRINT_INVALID")
    if not token.startswith("APP_USR-"):
        raise ProbeBlocked("C5_EXISTING_SECRET_TOKEN_FORMAT_UNEXPECTED")
    req = request.Request(
        IDENTITY_URL, method="GET",
        headers={"Authorization": "Bearer " + token, "Accept": "application/json"},
    )
    try:
        with opener(req, timeout=8) as response:
            body = response.read(16385)
    except error.HTTPError as exc:
        if exc.code in (401, 403):
            raise ProbeBlocked("C5_EXISTING_SECRET_REJECTED_BY_PROVIDER") from None
        raise ProbeBlocked("C5_MP_READONLY_LOOKUP_UNAVAILABLE") from None
    except Exception:
        raise ProbeBlocked("C5_MP_READONLY_LOOKUP_UNAVAILABLE") from None
    if len(body) > 16384:
        raise ProbeBlocked("C5_READONLY_RESPONSE_OVERSIZED")
    try:
        payload = json.loads(body)
    except (ValueError, UnicodeDecodeError):
        raise ProbeBlocked("C5_READONLY_RESPONSE_INVALID") from None
    return verify_public_identity(payload, expected_digest, seller_id_digest, buyer_id_digest)

def main():
    try:
        probe(
            os.environ.get("MERCADOPAGO_TEST_ACCESS_TOKEN", ""),
            os.environ.get("C5_EXPECTED_TEST_SELLER_FINGERPRINT", ""),
            seller_id_digest=os.environ.get("C5_SELLER_ID_HASH", ""),
            buyer_id_digest=os.environ.get("C5_BUYER_ID_HASH", ""),
        )
    except ProbeBlocked as exc:
        # Only return safe categorical outcomes. Never log credential or payload.
        print(str(exc))
        return 1
    print("C5_EXISTING_SECRET_VERIFIED_READONLY_MATCHES_TEST_SELLER")
    return 0

if __name__ == "__main__":
    sys.exit(main())
