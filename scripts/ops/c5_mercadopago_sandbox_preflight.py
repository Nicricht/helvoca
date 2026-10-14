#!/usr/bin/env python3
"""Fail-closed Mercado Pago TEST seller identity preflight.

Only a GET to /users/me may occur here, never creates subscriptions/payments.
Do not log credentials or the raw provider response. No provider access in offline tests.
"""
import json
import os
import re
import sys
from urllib import request

IDENTITY_URL = "https://api.mercadopago.com/users/me"
CONSENT = "I_VERIFIED_TEST_CREDENTIALS"


class SandboxBlocked(Exception):
    pass


def validate_settings(env):
    if env.get("CONFIRM_SANDBOX") != "true":
        raise SandboxBlocked("C5_SANDBOX_EXPLICIT_CONFIRMATION_REQUIRED")
    if env.get("CONFIRM_TEST_CREDENTIAL_SOURCE") != CONSENT:
        raise SandboxBlocked("C5_TEST_CREDENTIAL_SOURCE_UNCONFIRMED")
    if env.get("RAILWAY_ENVIRONMENT", "").lower() == "production":
        raise SandboxBlocked("C5_PRODUCTION_ENVIRONMENT_FORBIDDEN")
    token = env.get("MERCADOPAGO_TEST_ACCESS_TOKEN", "")
    # Both live AND test credentials can be APP_USR. This is NOT a test check.
    if not re.fullmatch(r"APP_USR-[A-Za-z0-9-]{12,}", token):
        raise SandboxBlocked("C5_ACCESS_TOKEN_FORMAT_INVALID")
    email = env.get("MERCADOPAGO_TEST_PAYER_EMAIL", "").strip().lower()
    if not re.fullmatch(r"testuser[0-9]+@testuser\.com", email):
        raise SandboxBlocked("C5_REGISTERED_TEST_BUYER_REQUIRED")
    seller_id = env.get("MERCADOPAGO_EXPECTED_TEST_SELLER_ID", "")
    if not re.fullmatch(r"[1-9][0-9]{2,20}", seller_id):
        raise SandboxBlocked("C5_EXPECTED_TEST_SELLER_ID_REQUIRED")
    return token, email, seller_id


def verify_identity(identity, seller_id):
    if not isinstance(identity, dict):
        raise SandboxBlocked("C5_MP_IDENTITY_UNVERIFIED")
    if str(identity.get("id", "")) != seller_id:
        raise SandboxBlocked("C5_MP_TEST_SELLER_ID_MISMATCH")
    if identity.get("site_id") != "MLC":
        raise SandboxBlocked("C5_MP_SELLER_NOT_CHILE")
    # This property must be present and literally true. A missing property
    # is NOT proof that an APP_USR token is safe to use.
    if identity.get("test_user") is not True:
        raise SandboxBlocked("C5_MP_TEST_ACCOUNT_UNVERIFIED")
    return True


def check_provider_identity(token, expected_seller_id, opener=request.urlopen):
    safe_request = request.Request(
        IDENTITY_URL,
        headers={"Authorization": "Bearer " + token, "Accept": "application/json"},
        method="GET",
    )
    try:
        with opener(safe_request, timeout=8) as response:
            raw = response.read(8193)
        if len(raw) > 8192:
            raise SandboxBlocked("C5_MP_IDENTITY_UNVERIFIED")
        identity = json.loads(raw)
    except SandboxBlocked:
        raise
    except Exception:
        # Do not print HTTP errors, URL, body or exception, which can leak data.
        raise SandboxBlocked("C5_MP_IDENTITY_LOOKUP_FAILED") from None
    return verify_identity(identity, expected_seller_id)


def main():
    try:
        token, _, seller_id = validate_settings(os.environ)
        check_provider_identity(token, seller_id)
    except SandboxBlocked as exc:
        print("C5_SANDBOX_BLOCKED: " + str(exc))
        return 1
    print("C5_TEST_SELLER_PROVENANCE_VERIFIED_READ_ONLY")
    return 0


if __name__ == "__main__":
    sys.exit(main())
