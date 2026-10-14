#!/usr/bin/env python3
"""Ephemeral TEST-only inbound webhook gate; never processes or charges payments.

This HTTP process is for network + signature ingress certification only.
It does NOT reconcile subscriptions, save PII, or access Mercado Pago.
Production backend remains the authoritative payment state machine.
"""
import hashlib
import hmac
import json
import os
import re
import secrets
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

MAX_BODY = 4096
WINDOW_MS = 300_000
ALLOWED_TOPICS = frozenset(("subscription_preapproval", "subscription_authorized_payment"))
HEX64 = re.compile(r"[a-fA-F0-9]{64}")
NUMBER = re.compile(r"[0-9]{1,40}")
PREAPPROVAL = re.compile(r"[0-9a-fA-F-]{1,80}")

class InvalidWebhook(ValueError):
    pass

def strict_json(blob):
    def unique_pairs(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise InvalidWebhook("duplicate JSON field")
            result[key] = value
        return result
    try:
        obj = json.loads(blob, object_pairs_hook=unique_pairs)
    except (ValueError, UnicodeDecodeError):
        raise InvalidWebhook("invalid JSON") from None
    if not isinstance(obj, dict):
        raise InvalidWebhook("JSON object required")
    return obj

def parse_signature(header):
    if not isinstance(header, str) or len(header) > 180:
        raise InvalidWebhook("signature missing or too long")
    fields = {}
    for part in header.split(","):
        segments = part.strip().split("=", 1)
        if len(segments) != 2 or segments[0] in fields:
            raise InvalidWebhook("invalid signature fields")
        fields[segments[0]] = segments[1]
    if set(fields) != {"ts", "v1"} or not HEX64.fullmatch(fields["v1"]):
        raise InvalidWebhook("unexpected signature shape")
    ts = fields["ts"]
    if not (re.fullmatch(r"[0-9]{10}", ts) or re.fullmatch(r"[0-9]{13}", ts)):
        raise InvalidWebhook("invalid timestamp")
    return ts, fields["v1"].lower()

def verify(body, query, headers, secret, now_ms=None):
    if not secret or len(secret) < 24:
        raise InvalidWebhook("missing test-only secret")
    if not isinstance(body, (bytes, bytearray)) or len(body) > MAX_BODY:
        raise InvalidWebhook("oversized body")
    if headers.get_all("x-signature") is None or len(headers.get_all("x-signature")) != 1:
        raise InvalidWebhook("signature header ambiguity")
    if headers.get_all("x-request-id") is None or len(headers.get_all("x-request-id")) != 1:
        raise InvalidWebhook("request header ambiguity")
    request_id = headers.get("x-request-id")
    if not isinstance(request_id, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,100}", request_id):
        raise InvalidWebhook("request id invalid")
    ts, supplied = parse_signature(headers.get("x-signature"))
    stamp = int(ts) * (1000 if len(ts) == 10 else 1)
    clock = int(time.time() * 1000) if now_ms is None else now_ms
    if abs(clock - stamp) > WINDOW_MS:
        raise InvalidWebhook("stale or future timestamp")
    data = strict_json(body)
    params = parse_qs(query, keep_blank_values=True, strict_parsing=False)
    for key in ("type", "data.id"):
        if len(params.get(key, [])) > 1:
            raise InvalidWebhook("duplicate query")
    topic = params.get("type", [data.get("type")])[0]
    if topic not in ALLOWED_TOPICS or data.get("type") not in (None, topic):
        raise InvalidWebhook("unrecognized or conflicting topic")
    wrapped = data.get("data")
    body_id = wrapped.get("id") if isinstance(wrapped, dict) else None
    query_id = params.get("data.id", [None])[0]
    if isinstance(body_id, int):
        body_id = str(body_id)
    if not isinstance(body_id, str) and query_id is None:
        raise InvalidWebhook("event id required")
    if body_id is not None and query_id is not None and body_id != query_id:
        raise InvalidWebhook("conflicting event id")
    event_id = query_id if query_id is not None else body_id
    if not isinstance(event_id, str):
        raise InvalidWebhook("event id invalid")
    allowed = NUMBER if topic == "subscription_authorized_payment" else PREAPPROVAL
    if not allowed.fullmatch(event_id):
        raise InvalidWebhook("event id shape invalid")
    manifest = f"id:{event_id.lower()};request-id:{request_id};ts:{ts};".encode()
    expected = hmac.new(secret.encode(), manifest, hashlib.sha256).hexdigest()
    if not hmac.compare_digest(expected, supplied):
        raise InvalidWebhook("HMAC mismatch")
    return topic, event_id

def require_sandbox_event(payload):
    # An authentic HMAC is necessary but not sufficient to mark a test event.
    # Production-labeled or unspecified events must not be acknowledged by
    # Render's permanently public test-only receiver.
    data = strict_json(payload)
    if data.get("live_mode") is not False:
        raise InvalidWebhook("non-test provider event")
    return True


class Collector(ThreadingHTTPServer):
    daemon_threads = True
    def __init__(self, address, secret, require_test_mode=False):
        super().__init__(address, Handler)
        self.secret = secret
        self.require_test_mode = require_test_mode
        self.received = 0
        self.unique = set()

class Handler(BaseHTTPRequestHandler):
    server_version = "C5EphemeralTEST/1.0"
    sys_version = ""

    def log_message(self, *_):
        # Never log raw paths/headers/bodies/identifiers.
        pass

    def respond(self, status):
        self.send_response(status)
        self.send_header("Content-Length", "0")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()

    def do_GET(self):
        self.respond(204 if urlsplit(self.path).path == "/health" else 404)

    def do_POST(self):
        parts = urlsplit(self.path)
        if parts.path != "/webhooks/v1/mercadopago":
            return self.respond(404)
        raw_length = self.headers.get_all("Content-Length")
        if raw_length is None or len(raw_length) != 1:
            return self.respond(411)
        try:
            size = int(raw_length[0])
        except ValueError:
            return self.respond(400)
        if size <= 0 or size > MAX_BODY:
            return self.respond(413)
        try:
            content = self.rfile.read(size)
            topic, event_id = verify(content, parts.query, self.headers, self.server.secret)
            if self.server.require_test_mode:
                require_sandbox_event(content)
        except InvalidWebhook:
            return self.respond(401)
        except Exception:
            # Prevent exception content or private request data from reaching clients.
            return self.respond(500)
        self.server.received += 1
        self.server.unique.add((topic, event_id))
        if self.server.require_test_mode:
            # Topic belongs to a fixed allowlist; do not log raw event IDs,
            # headers, signature, request body, payer, buyer or token.
            print("C5_AUTHENTICATED_TEST_RECEIPT_TOPIC=" + topic, flush=True)
        # Mercado Pago documents HTTP 200/201 as successful webhook ACK.
        # 204 was accepted by our generic HTTP smoke, but can trigger provider retries.
        self.respond(200)

def main():
    if os.getenv("C5_EPHEMERAL_MODE") != "test-only":
        print("C5_INGRESS_DISABLED_NOT_TEST_MODE")
        return 2
    secret = os.getenv("C5_EPHEMERAL_HMAC_SECRET", "")
    if len(secret) < 24 or secret.lower().startswith(("app_usr", "test-")):
        print("C5_INGRESS_INVALID_EPHEMERAL_SECRET")
        return 2
    port = int(os.getenv("PORT", "8765"))
    if not 1024 <= port <= 65535:
        print("C5_INGRESS_INVALID_PORT")
        return 2
    server = Collector(("127.0.0.1", port), secret)
    print("C5_INGRESS_TEST_ONLY_LISTENING", flush=True)
    try:
        server.serve_forever(poll_interval=0.2)
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0

if __name__ == "__main__":
    sys.exit(main())
