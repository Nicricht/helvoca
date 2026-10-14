#!/usr/bin/env python3
"""Only fake data, no Mercado Pago token or checkout. Verifies public HTTPS ingress."""
import hashlib
import hmac
import json
import os
import re
import sys
import time
from urllib import error, request, parse

def fail(code):
    print("C5_EPHEMERAL_PUBLIC_HTTPS_" + code)
    sys.exit(1)

def main():
    logfile = os.environ.get("C5_CLOUDFLARED_LOG", "")
    secret = os.environ.get("C5_EPHEMERAL_HMAC_SECRET", "")
    if not logfile or len(secret) < 24:
        fail("CONFIG_MISSING")
    target = None
    for _ in range(60):
        try:
            with open(logfile, encoding="utf-8", errors="replace") as handle:
                data = handle.read()
            match = re.search(r"https://[a-z0-9-]+\.trycloudflare\.com", data)
            if match:
                target = match.group(0)
                break
        except OSError:
            pass
        time.sleep(1)
    if not target:
        fail("PUBLIC_URL_NOT_ASSIGNED")
    url = parse.urlsplit(target)
    if url.scheme != "https" or not url.hostname.endswith(".trycloudflare.com"):
        fail("UNSAFE_PUBLIC_URL")
    try:
        with request.urlopen(target + "/health", timeout=15) as response:
            if response.status != 204:
                fail("HEALTH_NOT_204")
        ident = "1234509876"
        ts = str(int(time.time() * 1000))
        reqid = "c5-fake-public-smoke"
        manifest = f"id:{ident};request-id:{reqid};ts:{ts};".encode()
        mac = hmac.new(secret.encode(), manifest, hashlib.sha256).hexdigest()
        body = json.dumps({"type": "subscription_authorized_payment",
                           "data": {"id": ident}, "live_mode": False},
                          separators=(",", ":")).encode()
        headers = {"Content-Type": "application/json",
                   "x-signature": f"ts={ts},v1={mac}",
                   "x-request-id": reqid, "Cache-Control": "no-store"}
        req = request.Request(target + "/webhooks/v1/mercadopago", data=body,
                              headers=headers, method="POST")
        with request.urlopen(req, timeout=15) as response:
            if response.status != 204:
                fail("SIGNED_POST_NOT_ACCEPTED")
        unsigned = request.Request(target + "/webhooks/v1/mercadopago", data=body,
                                   headers={"Content-Type": "application/json"}, method="POST")
        try:
            with request.urlopen(unsigned, timeout=15):
                fail("UNSIGNED_POST_ACCEPTED")
        except error.HTTPError as exc:
            if exc.code != 401:
                fail("UNSIGNED_NOT_REJECTED")
    except (error.URLError, TimeoutError, OSError):
        fail("NETWORK_OR_GATE_ERROR")
    print("C5_EPHEMERAL_PUBLIC_HTTPS_SIGNED_AND_UNAUTHENTICATED_REJECTION_VERIFIED")
    return 0

if __name__ == "__main__":
    sys.exit(main())
