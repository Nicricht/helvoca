# First Fictional Customer Week

## Goal

Certify **Barbería Norte Demo** as the first fictional operational customer by exercising a simulated seven-day operating week through existing RecepVoz harnesses, fixtures and product contracts.

This is a certification layer, not a new runtime subsystem.

## Safety boundary

The gate is simulation/sandbox only:

- no real calls;
- no real WhatsApp delivery;
- no real payments;
- no live provider credentials;
- no destructive production mutation;
- no manual production deploy.

## Seven-day simulation

| Day | Operational focus | Evidence |
| --- | --- | --- |
| 1 | onboarding, business profile, services/prices, hours, FAQ and receptionist | `DevDataInitializerTest`, `OnboardingServiceTest`, first-user UX E2E |
| 2 | price/FAQ questions, availability, booking, reschedule and cancellation | simulator tests, `RealtimeToolServiceTest`, Golden Journey |
| 3 | duplicate protection, repeated conversation, closed hours and nonexistent/invalid service behavior | booking/conversation packs, realtime tool contracts |
| 4 | customer changes their mind, interruption/barge-in and human handoff | booking state-machine pack, Gemini/Twilio voice tests, human-transfer tool test |
| 5 | tool/backend error with bounded retry and safe fallback | `SafeOperationRetryChaosCertificationTest` |
| 6 | tenant isolation, metrics, commercial timeline, entitlements and owner dashboard | PostgreSQL RLS, metrics/timeline/entitlement tests, operational home E2E |
| 7 | replay, trace and observability review | Journey Trace and Conversation Replay suites |

## Command

```bash
bash scripts/ci/first-fictional-customer-week.sh
```

Success is declared only when the command reaches:

```text
FIRST FICTIONAL CUSTOMER WEEK: PASS
```

Any failing underlying scenario stops the gate before that marker and leaves the exact failing test in CI evidence.

## External limits

This gate does not certify physical provider delivery. In particular it does not prove real telephone delivery, real WhatsApp delivery, real payment charging, or Mercado Pago provider-to-production HTTP webhook delivery.
