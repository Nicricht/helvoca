#!/usr/bin/env python3
"""Helvoca Part 8: OFFLINE Gemini voice-model, pilot cost and consent preflight.

Runs with synthetic metadata only. Does not read tokens, query Google/Twilio,
connect to Railway/DB, send audio, reserve provider quota or authorize calls.
A passing synthetic case is a rehearsal, NEVER production entitlement or GO.
"""
from __future__ import annotations

import argparse
from decimal import Decimal, InvalidOperation, ROUND_CEILING
import json
from pathlib import Path

EXPECTED_MODEL = "gemini-3.8-live"
OFFICIAL_WS = (
    "wss://generativelanguage.googleapis.com/ws/"
    "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
)
MAX_CALL_SECONDS = 180
MAX_CALLS = 1
MAX_CAP_USD = Decimal("5.00")
RESERVE_FACTOR = Decimal("2.00")
MAX_DECIMAL_DIGITS = 18


def item(doc: dict, key: str) -> dict:
    value = doc.get(key)
    return value if isinstance(value, dict) else {}


def yes(value: object) -> bool:
    return type(value) is bool and value is True


def no(value: object) -> bool:
    return type(value) is bool and value is False


def money(value: object) -> Decimal | None:
    """Bounded, finite, positive decimal. Reject float precision / booleans."""
    if not isinstance(value, str) or len(value) > MAX_DECIMAL_DIGITS:
        return None
    if not value or any(c not in "0123456789." for c in value):
        return None
    if value.count(".") > 1:
        return None
    try:
        amount = Decimal(value)
    except InvalidOperation:
        return None
    return amount if amount.is_finite() and amount > 0 else None


def assess(input_doc: object) -> dict:
    blockers: list[str] = []

    def require(ok: bool, code: str) -> None:
        if not ok:
            blockers.append(code)

    if not isinstance(input_doc, dict):
        blockers.append("INVALID_INPUT")
        input_doc = {}
    model = item(input_doc, "model")
    carrier = item(input_doc, "carrier")
    budget = item(input_doc, "budget")
    consent = item(input_doc, "consent")
    fences = item(input_doc, "fences")
    evidence = item(input_doc, "evidence")

    require(input_doc.get("source") == "synthetic"
            and type(input_doc.get("source")) is str,
            "EXTERNAL_ATTESTATION_NOT_VERIFIED")
    # Only this exact string is accepted for a synthetic test. The allowlist is
    # LOCAL source code and does not prove a corresponding Google API model exists.
    require(model.get("id") == EXPECTED_MODEL
            and model.get("tier") == "STABLE",
            "MODEL_NOT_EXACT_LOCAL_ALLOWLIST")
    require(model.get("endpoint") == OFFICIAL_WS,
            "NON_OFFICIAL_WEBSOCKET_ENDPOINT")
    require(yes(model.get("local_configuration_valid"))
            and yes(model.get("api_key_configured"))
            and model.get("selected_voice_provider") == "GEMINI",
            "GEMINI_CONFIG_OR_ROUTER_NOT_PROVEN")
    # These are untrusted declarations, NOT proof of live provider access.
    require(yes(evidence.get("provider_model_entitlement_attested"))
            and yes(evidence.get("api_key_project_attested"))
            and yes(evidence.get("carrier_ownership_attested"))
            and yes(evidence.get("tenant_and_phone_binding_attested")),
            "REAL_CARRIER_OR_MODEL_ENTITLEMENT_NOT_ATTESTED")
    require(yes(evidence.get("current_backup_and_restore_attested"))
            and yes(evidence.get("independent_encrypted_copy_attested"))
            and yes(evidence.get("flyway_rls_attested")),
            "RECOVERY_OR_TENANT_EVIDENCE_MISSING")
    require(yes(consent.get("operator_consent"))
            and yes(consent.get("participant_consent"))
            and yes(consent.get("recording_policy_reviewed")),
            "CONSENT_NOT_ATTESTED")
    approvers = consent.get("two_approvers")
    require(isinstance(approvers, list) and len(approvers) == 2
            and all(isinstance(x, str) and x.startswith("reviewer-")
                    and 9 <= len(x) <= 40 and x.isascii() for x in approvers)
            and len(set(approvers)) == 2,
            "TWO_INDEPENDENT_APPROVERS_MISSING")
    require(yes(fences.get("pilot_paused"))
            and yes(fences.get("tenant_isolation"))
            and no(fences.get("real_calls_enabled"))
            and no(fences.get("outbound_enabled"))
            and no(fences.get("gemini_probe_enabled"))
            and no(fences.get("twilio_mutations_enabled")),
            "LIVE_EFFECT_FENCE_NOT_CLOSED")

    seconds = budget.get("max_call_seconds")
    call_count = budget.get("max_calls")
    valid_duration = type(seconds) is int and 1 <= seconds <= MAX_CALL_SECONDS
    valid_count = type(call_count) is int and call_count == MAX_CALLS
    require(valid_duration and valid_count, "CALL_DURATION_OR_COUNT_UNBOUNDED")
    twilio = money(budget.get("twilio_usd_per_minute"))
    gemini = money(budget.get("gemini_usd_per_minute"))
    cap = money(budget.get("total_usd_cap"))
    require(twilio is not None and gemini is not None and cap is not None
            and cap <= MAX_CAP_USD, "UNKNOWN_OR_UNBOUNDED_PROVIDER_PRICES")
    require(yes(budget.get("provider_hard_cap_attested"))
            and yes(budget.get("pilot_hard_cap_attested")),
            "BILLABLE_HARD_CAP_NOT_ATTESTED")
    reserved = None
    if valid_duration and valid_count and twilio is not None and gemini is not None:
        rounded_minutes = (Decimal(seconds) / Decimal(60)).to_integral_value(
            rounding=ROUND_CEILING)
        reserved = (twilio + gemini) * rounded_minutes * MAX_CALLS * RESERVE_FACTOR
        require(cap is not None and reserved <= cap,
                "RESERVED_BUDGET_EXCEEDS_CEILING")
    # Never print raw config, identifiers, payload, credentials or arbitrary model.
    return {
        "part": "8/10",
        "state": "STOP" if blockers else "SYNTHETIC_REVIEW_ONLY",
        "blockers": sorted(set(blockers)),
        "model_policy": "LOCAL_ONLY_NOT_PROVIDER_ATTESTATION",
        "billing_estimate": "CONSERVATIVE_NOT_REAL_INVOICE",
        "reserve_factor": "2x",
        "estimated_reserved_usd": str(reserved) if reserved is not None else None,
        "source_trusted": False,
        "provider_handshake_performed": False,
        "carrier_request_performed": False,
        "charged_calls_performed": False,
        "may_merge": False,
        "may_deploy": False,
        "may_migrate": False,
        "may_call": False,
        "live_call_certified": False,
        "commercial_go": False,
    }


def fixture(optimistic: bool = False) -> dict:
    return {
        "source": "synthetic",
        "model": {
            "id": EXPECTED_MODEL, "tier": "STABLE", "endpoint": OFFICIAL_WS,
            "api_key_configured": optimistic, "local_configuration_valid": optimistic,
            "selected_voice_provider": "GEMINI" if optimistic else "NONE",
        },
        "carrier": {},
        "budget": {
            "max_call_seconds": 60, "max_calls": 1,
            "twilio_usd_per_minute": "0.20",
            "gemini_usd_per_minute": "0.50",
            "total_usd_cap": "2.00",
            "provider_hard_cap_attested": optimistic,
            "pilot_hard_cap_attested": optimistic,
        },
        "consent": {
            "operator_consent": optimistic, "participant_consent": optimistic,
            "recording_policy_reviewed": optimistic,
            "two_approvers": ["reviewer-a", "reviewer-b"] if optimistic else [],
        },
        "fences": {
            "pilot_paused": True, "tenant_isolation": optimistic,
            "real_calls_enabled": False, "outbound_enabled": False,
            "gemini_probe_enabled": False, "twilio_mutations_enabled": False,
        },
        "evidence": {
            "provider_model_entitlement_attested": optimistic,
            "api_key_project_attested": optimistic,
            "carrier_ownership_attested": optimistic,
            "tenant_and_phone_binding_attested": optimistic,
            "current_backup_and_restore_attested": optimistic,
            "independent_encrypted_copy_attested": optimistic,
            "flyway_rls_attested": optimistic,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--simulate-blocked", action="store_true")
    mode.add_argument("--simulate-review", action="store_true")
    mode.add_argument("--evidence", type=Path)
    args = parser.parse_args()
    if args.evidence is not None:
        try:
            doc = json.loads(args.evidence.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, ValueError):
            doc = {}
    else:
        doc = fixture(optimistic=args.simulate_review)
    result = assess(doc)
    print(json.dumps(result, sort_keys=True))
    if args.simulate_blocked:
        return 0 if result["state"] == "STOP" else 2
    if args.simulate_review:
        return 0 if result["state"] == "SYNTHETIC_REVIEW_ONLY" else 2
    return 2 if result["state"] == "STOP" else 3


if __name__ == "__main__":
    raise SystemExit(main())
