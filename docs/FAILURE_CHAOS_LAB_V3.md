# Failure / Chaos Lab V3

This lab certifies failure-handling invariants without injecting faults into production.

## Certified failure families

1. **Operation timeout / transient backend failure**
   - safe automatic retries are bounded by tenant operation policy;
   - retries use deterministic backoff;
   - eventual success is returned once;
   - exhausted retries stop safely instead of looping forever.

2. **Payment webhook provider failure**
   - a provider verification failure leaves the webhook event retryable;
   - retry may later verify the payment successfully;
   - after success the same event is treated as duplicate;
   - a duplicate does not query the provider again.

3. **Duplicate WhatsApp delivery**
   - existing inbound idempotency and persistent-job tests remain part of the chaos certification.

4. **Durable worker crash/retry**
   - persistent job lease, retry and terminal behavior are re-run as part of the lab.

5. **Audio provider failure**
   - Deepgram failure handling, transcription circuit breaker and provider routing are certified together.

6. **Conversation replay invariants**
   - the V2 replay fixture library runs inside the chaos pack so known conversational and duplicate-effect regressions remain locked.

## Running locally

```bash
bash scripts/ci/chaos-certification.sh
```

## Fast Gate integration

`scripts/ci/fast-gate.sh` automatically runs the chaos pack when changes touch critical reliability surfaces such as operations, payment, durable jobs, Meta WhatsApp, audio transcription, quality/replay code or the chaos tests themselves.

## Safety

The lab is test-only. It does not enable outbound delivery, contact real payment providers, place calls, send WhatsApp messages or mutate production inventory. Provider and timeout faults are deterministic test doubles or existing local/Testcontainers integration paths.
