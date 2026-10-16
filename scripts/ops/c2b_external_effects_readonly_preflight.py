#!/usr/bin/env python3
"""C2-B: read-only check of selected effective Railway API startup flags.

Outputs public flag names and OFF/MISSING/NOT_OFF only; never secret values.
A green result DOES NOT authorize deployment, payment, calls or tenant STOP.
"""
from __future__ import annotations
import argparse
import base64
import shutil
import subprocess
import sys

PROJECT = "41988ee6-f9f6-48eb-b726-abb973bb5740"
ENVIRONMENT = "8bbc755a-58b5-46e8-8227-eefa4adbbce4"
SERVICE = "edbfc4d2-3487-4cfb-ab7c-52fdb4060c4a"
FLAGS = (
    "APP_JOBS_ENABLED",
    "SEED_ENABLED",
    "HELVOCA_INVENTORY_RESERVATION_EXPIRY_ENABLED",
    "HELVOCA_MERCHANT_PAYMENT_RECONCILE_ENABLED",
    "TWILIO_CERTIFICATION_CALL_ON_STARTUP",
    "TWILIO_CERTIFICATION_COMMAND_RUNNER_ENABLED",
    "HELVOCA_TWILIO_SUBACCOUNT_VOICE_CONFIGURE_ON_STARTUP",
    "HELVOCA_TWILIO_TEMPLATE_EVENTS_CONFIGURE_ON_STARTUP",
    "HELVOCA_META_WHATSAPP_WABA_SUBSCRIBE_ON_STARTUP",
    "HELVOCA_META_WHATSAPP_TEST_BOOTSTRAP_ON_STARTUP",
    "HELVOCA_META_WHATSAPP_REPLAY_PENDING_REPLY_ON_STARTUP",
    "GEMINI_LIVE_PROBE_ON_STARTUP",
    "MERCADOPAGO_ENABLED",
    "GEMINI_LIVE_ENABLED",
    "META_WHATSAPP_ENABLED",
    "TWILIO_PROVISIONING_ENABLED",
    "TWILIO_CERTIFICATION_INGRESS_ENABLED",
    "HELVOCA_OUTBOUND_DELIVERY_ENABLED",
    "HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED",
)
START = "C2B_EFFECT_FLAGS_BEGIN_V1"
END = "C2B_EFFECT_FLAGS_END_V1"
PREFIX = "C2B_EFFECT_FLAG_V1:"

class Blocked(Exception):
    pass

def command(railway: str) -> list[str]:
    # Exactly fixed project/environment/service; names allowlisted above.
    shell = (
        'set -eu\n'
        '[ "§{RAILWAY_PROJECT_ID:-}" = "' + PROJECT + '" ] || exit 41\n'
        '[ "§{RAILWAY_ENVIRONMENT_ID:-}" = "' + ENVIRONMENT + '" ] || exit 42\n'
        '[ "§{RAILWAY_SERVICE_ID:-}" = "' + SERVICE + '" ] || exit 43\n'
        'printf "' + START + '\\n"\n'
        'for name in ' + ' '.join(FLAGS) + '; do\n'
        '  val="$(printenv "$name" 2>/dev/null || true)"\n'
        '  case "$val" in\n'
        '    false|FALSE|False|0) state=OFF ;;\n'
        '    "") state=MISSING ;;\n'
        '    *) state=NOT_OFF ;;\n'
        '  esac\n'
        '  printf "' + PREFIX + '%s:%s\\n" "$name" "$state"\n'
        'done\n'
        'printf "' + END + '\\n"\n'
    ).replace("§", "$")
    encoded = base64.b64encode(shell.encode("ascii")).decode("ascii")
    remote = "printf %s " + encoded + " | base64 -d | sh"
    return [railway, "ssh", "--project", PROJECT, "--environment", ENVIRONMENT,
            "--service", SERVICE, "--", "sh", "-c", remote]

def parse(data: bytes) -> tuple[tuple[str, str], ...]:
    if len(data) > 8192:
        raise Blocked("C2B_UNEXPECTED_OUTPUT_SIZE")
    try:
        lines = data.decode("utf-8", "strict").splitlines()
    except UnicodeDecodeError:
        raise Blocked("C2B_INVALID_OUTPUT_ENCODING") from None
    if len(lines) != len(FLAGS) + 2 or lines[0] != START or lines[-1] != END:
        raise Blocked("C2B_UNEXPECTED_PROBE_FRAMING")
    observed = []
    for name, line in zip(FLAGS, lines[1:-1]):
        valid = (PREFIX + name + ":OFF", PREFIX + name + ":MISSING",
                 PREFIX + name + ":NOT_OFF")
        if line not in valid:
            raise Blocked("C2B_UNEXPECTED_FLAG_RESPONSE")
        observed.append((name, line.rsplit(":", 1)[-1]))
    return tuple(observed)

def probe(railway: str) -> tuple[tuple[str, str], ...]:
    try:
        result = subprocess.run(command(railway), stdout=subprocess.PIPE,
                                stderr=subprocess.DEVNULL, timeout=120,
                                check=False)
    except (OSError, subprocess.TimeoutExpired):
        raise Blocked("C2B_RAILWAY_SSH_UNAVAILABLE") from None
    if result.returncode != 0:
        raise Blocked("C2B_REMOTE_IDENTITY_OR_SHELL_FAILED")
    return parse(result.stdout)

def main(args_: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--probe-read-only", action="store_true")
    args = parser.parse_args(args_)
    if not args.probe_read_only:
        print("C2B_BLOCKED: EXPLICIT_READ_ONLY_PROBE_REQUIRED")
        return 2
    try:
        railway = shutil.which("railway")
        if not railway:
            raise Blocked("C2B_RAILWAY_CLI_MISSING")
        observed = probe(railway)
        unsafe = [name for name, state in observed if state != "OFF"]
        if unsafe:
            print("C2B_STARTUP_FLAGS_NOT_OFF: " + ", ".join(unsafe))
            print("C2B_NO_RELEASE_APPROVAL; tenant scope, jobs and providers unverified")
            return 2
        print("C2B_STARTUP_FLAGS_OFF_AT_PROBE_ONLY")
        print("C2B_NO_RELEASE_APPROVAL; tenant STOP, backup and authorization unverified")
        return 0
    except Blocked as err:
        print("C2B_BLOCKED: " + str(err))
        print("No changes to production; no provider or release GO")
        return 2

if __name__ == "__main__":
    sys.exit(main())
