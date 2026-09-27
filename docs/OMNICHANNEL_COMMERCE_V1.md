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
5. payment-success preparation uses a stable business/purpose/payment-operation/recipient key, so a later operation revision or a second provider event cannot create another success confirmation;
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

## Block 2: complete structured journey certification

The branch now includes `OmnichannelCommerceJourneyIntegrationTest`, a PostgreSQL/Testcontainers certification of the full structured journey:

`Voice conversation -> product/media -> WhatsApp handoff -> selection -> quote -> order confirmation -> sandbox payment -> verified webhook -> WhatsApp payment confirmation -> shared Voice/WhatsApp context`

The test deliberately keeps `app.outbound.delivery-enabled=false`. PRODUCT_SHOWCASE and PAYMENT_CONFIRMATION are persisted as PREPARED only, and the test asserts that no message becomes QUEUED or SENT.

The payment provider used by this certification is an in-process test adapter. It performs no network request, returns a backend-owned HTTPS checkout URL, and transitions to SUCCEEDED only when the verified webhook path queries provider status.

This certification exposed and fixed a real continuity defect: `PaymentWorkflowService.confirm` replaced payment-operation metadata and discarded `commercialJourneyOperationId`. Payment confirmation now merges backend payment metadata into the existing operation metadata, preserving the root journey linkage. A focused unit regression and the full E2E both cover this invariant.

Verified invariants in Block 2:

- Voice and WhatsApp for the same verified customer resolve the same omnichannel operational state;
- the exact root commercial operation survives the channel handoff;
- showcase selection is backend-authoritative;
- quote, order and payment totals remain backend-owned;
- order/payment confirmation tokens remain required;
- payment creation preserves `commercialJourneyOperationId`;
- provider webhook SUCCEEDED moves the root commercial journey to `PAID`;
- the shared conversation state observes the verified payment status;
- exactly one product showcase and one payment-success confirmation are prepared;
- real WhatsApp dispatch remains disabled.

## Inventory V1 integration boundary

PR #465 remains isolated and is not merged into this branch.

Inventory V1 currently integrates at the ORDER/PAYMENT lifecycle boundary:

- order confirmation reserves stock;
- order cancellation releases the reservation;
- payment SUCCEEDED consumes the reservation;
- payment FAILED/CANCELLED/EXPIRED releases it;
- variants persist exact `variantId` through operation items and order lines.

Those are the correct domain integration points for Omnichannel Commerce V1. The omnichannel layer should not independently mutate inventory.

When Inventory V1 is eventually integrated, reconcile only the overlapping domain files (`OrderWorkflowService`, `PaymentWorkflowService`, `PaymentWebhookService`, commercial tool definitions/items/lines) while preserving the Block 1 and Block 2 invariants above. In particular, the payment-operation metadata merge introduced here must survive that reconciliation so inventory settlement and omnichannel journey completion can both execute from the same verified payment webhook.

## Next gap after Block 2

The remaining work is no longer basic journey continuity. The next useful block is a controlled integration/certification plan for Inventory V1 plus Omnichannel Commerce V1, followed by explicit operator/business-owner visibility for the complete commercial timeline. No branch should be merged into `main` until that combined contract is green and merge is explicitly authorized.
