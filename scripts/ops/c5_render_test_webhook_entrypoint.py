#!/usr/bin/env python3
"""Render Free ingress for C5 TEST only. NO billing or external API actions.

Receives and authenticates HMAC requests in volatile memory; cannot reconcile
or charge subscriptions. Never use for production delivery or persist payloads.
"""
import os
import re
import sys
try:
    from c5_ephemeral_webhook_ingress import Collector
except ModuleNotFoundError:
    from scripts.ops.c5_ephemeral_webhook_ingress import Collector


def settings(env):
    if env.get("C5_RENDER_TEST_ONLY") != "isolated-no-payment":
        raise ValueError("C5_RENDER_PRODUCTION_MODE_FORBIDDEN")
    if env.get("C5_EPHEMERAL_MODE") != "test-only":
        raise ValueError("C5_RENDER_TEST_INGRESS_DISABLED")
    secret = env.get("C5_EPHEMERAL_HMAC_SECRET", "")
    if len(secret) < 32 or secret.lower().startswith(("app_usr", "test-")):
        raise ValueError("C5_RENDER_SIGNING_SECRET_UNAVAILABLE")
    raw_port = env.get("PORT", "")
    if not re.fullmatch(r"[0-9]{2,5}", raw_port):
        raise ValueError("C5_RENDER_PORT_MISSING_OR_INVALID")
    port = int(raw_port)
    if not 1024 <= port <= 65535:
        raise ValueError("C5_RENDER_PORT_OUT_OF_RANGE")
    return secret, port


def main():
    try:
        secret, port = settings(os.environ)
    except ValueError as exc:
        print(str(exc))
        return 2
    # Render's public ingress cannot reach 127.0.0.1; bind all interfaces
    # ONLY in the specifically gated TEST-only entrypoint.
    server = Collector(("0.0.0.0", port), secret, require_test_mode=True)
    print("C5_RENDER_TEST_INGRESS_LISTENING_NO_PAYMENT_ACTIONS", flush=True)
    try:
        server.serve_forever(poll_interval=0.2)
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
