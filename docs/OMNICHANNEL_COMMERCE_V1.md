# Omnichannel Commerce V1

Branch: `feat/omnichannel-commerce-v1`

Base inspected before development: `main@72bab1de73b4ac4ac18765395250f40bdf855bd0`.

## Existing foundation reused

The current main branch already provides:

- tenant-scoped catalog and catalog media;
- durable WhatsApp outbound messages;
- backend-owned product showcase media;
- omnichannel customer/session linkage between Voice and WhatsApp;
- persisted showcase selection;
- selected-product quote/order journey;
- provider-neutral payment workflow and sandbox payment support;
- payment webhook reconciliation;
- shared conversation operation state;
- commercial journey metadata linking root, order and payment operations.

Historical WhatsApp/payment branches were treated only as references. They are not merged wholesale into this branch.

Inventory V1 remains isolated in PR #465. Its reserved migration V72 is intentionally not duplicated here.

## Gap closed in the first block

Before this branch, a successful provider webhook updated payment, commercial journey and conversation state, but there was no first-class backend-rendered WhatsApp payment-success confirmation.

This block adds `PAYMENT_CONFIRMATION`:

1. only a persisted `BusinessPayment.Status.SUCCEEDED` can render it;
2. tenant and customer ownership are revalidated by the outbound engine;
3. amount and currency come from the persisted payment, never from AI text;
4. no checkout URL is included in the success confirmation;
5. outbound preparation remains idempotent through the existing business/purpose/operation/recipient/revision key;
6. a duplicate processed webhook does not trigger the notification hook again;
7. if WhatsApp delivery is disabled, the confirmation is only prepared and no provider/outbox action occurs;
8. missing/ambiguous verified recipients or unavailable WhatsApp do not roll back an already verified payment.

## Intentional limits

- This branch does not enable real WhatsApp delivery.
- It does not send test messages to real customers.
- It does not activate payments outside existing sandbox/configured provider behavior.
- It does not merge Inventory V1.
- It does not change Voice behavior.
- It does not merge to `main`.

## Next gap after this block

Continue certification of the complete structured journey:

`conversation -> product/media -> WhatsApp -> selection -> quote -> order -> payment sandbox -> verified confirmation -> shared context`

The next implementation should harden the cross-channel E2E test around that entire sequence and then reconcile Inventory V1 only at the integration boundary required for stock reservation/consumption.
