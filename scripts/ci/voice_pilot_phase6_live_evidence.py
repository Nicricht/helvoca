#!/usr/bin/env python3
"""Helvoca Part 6: evaluate real-call evidence without performing external effects.

Only reports candidate evidence for independent operator review. Self-attested
JSON is NOT proof of a carrier event or human hearing audio. This program NEVER
places calls, deploys code, alters databases, or emits live certification.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal, InvalidOperation
import json
from pathlib import Path
import re
import sys

SHA = re.compile(r"^[0-9a-f]{40}$")
E164 = re.compile(r"^\+[1-9][0-9]{7,14}$")
CALL_SID = re.compile(r"^CA[0-9a-fA-F]{32}$")
STREAM_SID = re.compile(r"^MZ[0-9a-fA-F]{32}$")
PHONE_SID = re.compile(r"^PN[0-9a-fA-F]{32}$")
TENANT_ID = "044a5d73-2df7-4e77-849f-8639d05ac30b"
DESTINATION = "+14355652512"
EXPECTED_PN = "PN62601c6420599e74bc940f42de2dd1e4"


@dataclass(frozen=True)
class Assessment:
    state: str
    blockers: tuple[str, ...]

    def as_dict(self) -> dict:
        # A self-reported artifact cannot certify independent provider data.
        return {
            "state": self.state,
            "blockers": list(self.blockers),
            "live_call_certified": False,
            "commercial_go": False,
            "allowed_to_dial": False,
            "requires_independent_operator_review": True,
        }


def _section(payload: object, key: str) -> dict:
    value = payload.get(key) if isinstance(payload, dict) else None
    return value if isinstance(value, dict) else {}


def _ref(value: object) -> bool:
    # Evidence references are locators/correlation IDs, not pasted secrets.
    return isinstance(value, str) and 8 <= len(value) <= 250 and not any(
        c.isspace() for c in value
    ) and not value.lower().startswith(("sk-", "ac-", "bearer", "token:"))


def _utc(value: object) -> datetime | None:
    if not isinstance(value, str):
        return None
    try:
        dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return dt if dt.tzinfo is not None and dt.utcoffset() is not None else None
    except ValueError:
        return None


def _amount(value: object) -> Decimal | None:
    if isinstance(value, bool) or not isinstance(value, (str, int, float)):
        return None
    try:
        number = Decimal(str(value))
        return number if number.is_finite() and number >= 0 else None
    except (InvalidOperation, ValueError):
        return None


def assess(payload: object) -> Assessment:
    if not isinstance(payload, dict):
        return Assessment("BLOCKED", ("INVALID_EVIDENCE",))
    release = _section(payload, "deployment")
    auth = _section(payload, "authorization")
    call = _section(payload, "call")
    bill = _section(payload, "cost")
    blockers: list[str] = []

    def req(ok: bool, code: str) -> None:
        if not ok:
            blockers.append(code)

    # Fresh deployment & backup certification are separate from a green CI.
    sha = release.get("candidate_sha")
    req(isinstance(sha, str) and SHA.fullmatch(sha) is not None
        and release.get("railway_deployed_sha") == sha
        and release.get("main_sha") == sha
        and release.get("full_ci_sha") == sha
        and release.get("full_ci_success") is True,
        "CANDIDATE_NOT_DEPLOYED_AND_CERTIFIED")
    req(release.get("isolated_backup_restore_verified") is True
        and _ref(release.get("backup_restore_evidence_ref")),
        "RECOVERABLE_BACKUP_NOT_VERIFIED")
    req(release.get("flyway_migrations_verified") is True
        and _ref(release.get("flyway_evidence_ref")),
        "PRODUCTION_SCHEMA_NOT_CERTIFIED")
    req(release.get("carrier_ownership_verified") is True
        and release.get("local_preflight_passed") is True
        and _ref(release.get("carrier_evidence_ref")),
        "TENANT_CARRIER_BINDING_NOT_CERTIFIED")
    req(release.get("pilot_running_for_target_tenant") is True
        and release.get("controlled_global_voice_enabled") is True
        and release.get("other_tenants_protected") is True,
        "CONTROLLED_PILOT_NOT_VERIFIED")

    req(auth.get("participant_consented") is True
        and _ref(auth.get("consent_evidence_ref"))
        and auth.get("controlled_time_window_confirmed") is True,
        "PARTICIPANT_CONSENT_MISSING")
    # This pilot exercises INBOUND voice: the human caller dials Helvoca's
    # Twilio number. Do not reverse the caller and called numbers.
    req(auth.get("helvoca_called_e164") == DESTINATION
        and isinstance(auth.get("authorized_human_caller_e164"), str)
        and E164.fullmatch(auth["authorized_human_caller_e164"]) is not None
        and auth["authorized_human_caller_e164"] != DESTINATION,
        "TEST_NUMBERS_NOT_CONFIRMED")
    max_seconds = auth.get("max_duration_seconds")
    req(type(max_seconds) is int and 1 <= max_seconds <= 300
        and auth.get("carrier_termination_control_verified") is True,
        "HARD_CALL_DURATION_OR_STOP_NOT_VERIFIED")
    budget = _amount(auth.get("max_total_cost_usd"))
    req(budget is not None and budget > 0
        and auth.get("twilio_tariff_verified") is True
        and auth.get("gemini_tariff_verified") is True,
        "PROVIDER_PRICES_OR_BUDGET_UNKNOWN")

    req(call.get("tenant_id") == TENANT_ID
        and call.get("phone_sid") == EXPECTED_PN
        and call.get("telephony_provider") == "twilio"
        and call.get("ai_provider") == "gemini",
        "CALL_TENANT_OR_PROVIDER_MISMATCH")
    req(isinstance(call.get("call_sid"), str)
        and CALL_SID.fullmatch(call["call_sid"]) is not None
        and isinstance(call.get("stream_sid"), str)
        and STREAM_SID.fullmatch(call["stream_sid"]) is not None
        and _ref(call.get("carrier_call_evidence_ref")),
        "CARRIER_CALL_IDENTIFIERS_NOT_VERIFIED")
    start, end = _utc(call.get("started_at")), _utc(call.get("ended_at"))
    req(start is not None and end is not None and end > start
        and type(max_seconds) is int
        and (end - start).total_seconds() <= max_seconds,
        "CALL_DURATION_NOT_PROVEN_WITHIN_LIMIT")
    req(call.get("carrier_completed") is True
        and call.get("media_started") is True
        and call.get("media_stopped") is True
        and call.get("gemini_connected") is True
        and call.get("audio_from_ai_received") is True
        and call.get("no_audio_after_hangup") is True,
        "BIDIRECTIONAL_MEDIA_NOT_CERTIFIED")
    req(call.get("human_heard_ai") is True
        and call.get("human_spoke_to_ai") is True
        and call.get("barge_in_successful") is True
        and _ref(call.get("human_observation_ref")),
        "HUMAN_AUDIBLE_CONVERSATION_NOT_CERTIFIED")
    created = call.get("created_booking_id")
    cancelled = call.get("cancelled_booking_id")
    req(isinstance(created, str) and re.fullmatch(
        r"[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,28}", created) is not None
        and created == cancelled
        and call.get("synthetic_booking_cleanup_verified") is True
        and _ref(call.get("booking_audit_evidence_ref")),
        "SAME_BOOKING_CREATION_CANCELLATION_NOT_PROVEN")

    twilio = _amount(bill.get("twilio_billed_usd"))
    gemini = _amount(bill.get("gemini_billed_usd"))
    req(twilio is not None and gemini is not None
        and budget is not None and twilio + gemini <= budget
        and _ref(bill.get("twilio_billing_ref"))
        and _ref(bill.get("gemini_billing_ref")),
        "REAL_PROVIDER_BILLING_NOT_RECONCILED")

    return Assessment(
        "BLOCKED" if blockers else "READY_FOR_INDEPENDENT_LIVE_EVIDENCE_REVIEW",
        tuple(blockers),
    )


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--evidence", type=Path, required=True)
    p.add_argument("--expect-blocked", action="store_true")
    args = p.parse_args()
    try:
        evidence = json.loads(args.evidence.read_text(encoding="utf-8"))
        result = assess(evidence)
    except (OSError, ValueError):
        result = Assessment("BLOCKED", ("EVIDENCE_UNREADABLE",))
    print(json.dumps(result.as_dict(), indent=2, sort_keys=True))
    if args.expect_blocked:
        return 0 if result.state == "BLOCKED" else 1
    # Even with all supplied evidence, require independent human review.
    return 2 if result.state == "BLOCKED" else 3


if __name__ == "__main__":
    sys.exit(main())
