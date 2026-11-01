# One-shot Twilio Call status readback

This diagnostic retrieves the provider resource for an **already-created** Twilio Call SID. It is not a call launcher and must never be used to retry, update, cancel, or hang up a call.

## Contract

- Default state is disabled: `TWILIO_CERTIFICATION_CALL_STATUS_LOOKUP_ENABLED=false`.
- The target is supplied through `TWILIO_CERTIFICATION_CALL_STATUS_LOOKUP_SID`; it must match the Twilio Call SID format.
- The runner performs exactly one authenticated HTTPS `GET` to the Twilio Call resource under the account configured in `app.twilio`.
- Redirects are disabled. The response must return the exact requested Call SID and configured Account SID.
- It reports status, duration, price and currency, carrier error code, direction, answered-by classification, masked source/destination suffixes, and timestamps.
- It never logs the Auth Token, Authorization header, full phone numbers, or raw provider response.
- It never sends `POST`, creates or updates calls, changes phone-number configuration, or modifies PostgreSQL.

## Isolated execution

Use only in `demo-isolated`, for an existing Call SID whose destination was already authorized. Set the enable flag to true and set the exact existing Call SID, then deploy the reviewed commit. Read the single `TWILIO_CALL_STATUS_READ_ONLY` log record. Do not set the certification command runner, call-on-startup, or ingress flags for this diagnostic.

Immediately after the readback, set the enable flag to false and clear the SID variable, deploy the cleanup, and verify the live variables are disabled/blank. No new voice attempt is part of this procedure.

## Unbound status callbacks

`TwilioVoiceController.status` currently discards a carrier status callback when the Call SID is not yet bound to a persisted `CallSession`, returning HTTP 204. That response only acknowledges webhook receipt; it does **not** confirm that the call rang, answered, or completed. In that case, the Twilio Call resource readback is the source of truth for terminal status, duration, carrier error code, and charge. Do not infer `no-answer` from a missing voice webhook or from callback timing alone.

## Interpretation

The Twilio Call resource is the source of truth for the carrier's terminal status and error code. A status callback reaching the application does not prove that the destination rang or answered. A missing voice webhook is consistent with a call that never reached the answered stage; use the provider's status and error code rather than inferring the cause from callback timing alone.
