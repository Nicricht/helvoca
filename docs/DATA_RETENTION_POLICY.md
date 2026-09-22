# Data Retention and Deletion Policy — V1

**Status:** implementation policy for RecepVoz / Helvoca V1  
**Reviewed against repository state:** 2026-09-22  
**Scope:** application data stored by Helvoca. This document does not define tax, accounting, employment, health-sector, telecom-carrier, or other sector-specific statutory retention duties for a customer's own business.

## 1. Legal baseline used for this technical policy

As of 2026-09-22, the version of Chilean Law 19.628 currently in force remains applicable through 2026-11-30. Its current article 6 requires personal data to be deleted or cancelled when storage no longer has a legal basis or the data has expired.

Law 21.719, published on 2024-12-13, reforms Law 19.628 and enters into force on 2026-12-01. The reformed framework expressly adds purpose limitation and proportionality: personal data may be retained only for the time necessary for the processing purpose and must then be deleted or anonymized, subject to legal exceptions. It also strengthens the data subject's right to suppression.

Official references:

- Current Law 19.628: https://www.bcn.cl/leychile/Navegar?dt=open&idLey=19628
- Law 21.719 and transitional provisions: https://www.bcn.cl/leychile/navegar?i=1209272

The periods below are **Helvoca product defaults**, not claims that Chilean law mandates those exact numbers.

## 2. Core retention rules

1. Retention is purpose-based, not indefinite by default.
2. Tenant isolation remains mandatory for every retention or deletion operation.
3. Deletion must never rely on a client-supplied business_id; the active tenant context or an internal system context must determine scope.
4. A verified legal hold, contractual need, fraud/security investigation, payment dispute, or other documented legal basis pauses deletion only for the affected records and period.
5. Data under a hold must not silently become a permanent exception.
6. When direct deletion would break referential integrity or erase required non-personal history, Helvoca should anonymize personal fields and retain only the minimum operational record.
7. Credentials, tokens, PINs, App Secrets, WABA IDs, Meta phone-number IDs, sensitive SIDs, provider message IDs and credentialRef values must never be copied into audit snapshots during deletion.

## 3. V1 retention matrix

| Data class | Current storage | V1 product default | End-of-retention action | Notes |
|---|---|---:|---|---|
| Call transcript | call_transcript | 90 days after call end | Delete | Highest-content voice data. Cascade deletion through the parent call is acceptable when the entire call expires. |
| Call summary | call_summary | 90 days after call end | Delete | Treated as derived personal conversation content. |
| Call actions | call_action | 90 days after call end | Delete | Retain only while useful for operational traceability of the call. |
| Call session content-linked identifiers | call_session | 180 days after call end | Delete | Includes caller/destination numbers and provider call identifiers. Usage/billing aggregates should survive separately where required. |
| Messaging message body and reply | messaging_message | 90 days after message creation | Delete | Includes inbound/outbound content and delivery metadata stored on the row. |
| Messaging conversation routing envelope | messaging_conversation | 180 days after last_message_at | Delete after child messages | Sender/recipient values are personal contact identifiers and must not remain indefinitely. |
| Customer profile PII | customer and customer identities | While required for active service; target inactivity review at 24 months | Anonymize or delete on verified suppression request when no hold/legal basis applies | Name, phone, email and free-text notes are personal data. A timer must not blindly destroy data needed by active bookings/operations. |
| Non-financial operational records | business_operation and typed projections | 24 months after terminal state | Delete operational row | PostgreSQL already emits a sanitized TYPE_DELETED operation event before deletion. |
| Financially relevant operational records | PAYMENT and records needed to support payment/order disputes | No automated V1 purge until applicable accounting/tax retention is mapped | Hold from automatic purge | Deliberate safety boundary, not an assertion that indefinite retention is lawful. |
| Universal operation history | business_operation_event | Retain sanitized event history | Retain sanitized | Current design is immutable and intentionally excludes arbitrary PII. New PII fields must not be added without privacy review. |
| Administrative audit | audit_log | 24 months by default | Delete after retention unless legal/security hold applies | Before/after snapshots must remain narrowly sanitized. |
| Provider delivery identifiers | fields in messaging_message and provider-specific operational rows | Same lifecycle as parent record unless separately required for an unresolved dispute | Delete with parent | Do not copy them into long-lived audit history. |
| Recordings | No active recording storage found in current Java domain model | Not applicable | Not applicable | A legacy design document mentions recording_uri, but current CallSession does not persist recordings. Any future recording feature requires a separate explicit retention rule before launch. |

## 4. Customer suppression strategy

A customer deletion request must be handled as a controlled privacy operation, not as a raw DELETE customer.

The target flow is:

1. Verify tenant and authorization.
2. Resolve the customer only inside the active tenant.
3. Detect active bookings, unresolved operations, disputes, legal holds, or other valid retention grounds.
4. If no blocking ground exists, remove or anonymize direct customer PII: name, phone, email, notes, customer identity values and other direct contact identifiers.
5. Remove expired call/message content tied to that person according to the policy.
6. Preserve only the minimum sanitized operational/security history required by the system design.
7. Write a HUMAN administrative audit event describing that a privacy deletion/anonymization occurred without copying the removed PII into the audit row.

The deletion audit must use IDs and safe categorical state only.

## 5. Automated purge architecture

The V1 purge implementation should be an internal scheduled service with these controls:

- PostgreSQL-backed execution, not browser-driven deletion.
- Batched deletes to avoid long locks.
- Explicit tenant/system context compatible with RLS.
- Idempotent execution.
- Oldest records first.
- Separate handlers for calls, messaging, customers and operational records.
- Metrics for scanned, deleted, anonymized, skipped-by-hold and failed records.
- No message/call/provider side effects. Purging local persistence must never send WhatsApp messages, place calls, cancel provider resources or trigger payments.
- Fail closed if tenant scope or retention configuration cannot be resolved.
- Unit/integration tests proving cross-tenant records cannot be deleted.

## 6. Legal hold model

Before automated deletion is enabled for data that may participate in disputes or investigations, Helvoca needs a minimal hold mechanism.

A hold must record only tenant, target type, target identifier, safe categorical reason, creation timestamp, release timestamp when applicable, and authorized human/system actor.

The reason must not contain arbitrary customer PII or secrets.

## 7. Backups

Application deletion and backup expiration are separate concerns.

Task 6 must define backup/PITR retention. Once active-data deletion is implemented:

- expired/deleted personal data must not be intentionally reintroduced from a backup;
- disaster recovery procedures must re-run retention/deletion after a restore when necessary;
- Helvoca should rely on backup expiration rather than attempting unsafe surgical edits of historical backup media.

## 8. Implementation order

1. Implement retention configuration/constants and a dry-run inventory.
2. Implement call transcript/summary/action purge.
3. Implement messaging message/conversation purge.
4. Implement verified customer suppression/anonymization.
5. Implement eligible non-financial operation cleanup.
6. Implement audit_log retention.
7. Add legal-hold checks before enabling automated deletion where required.
8. Add integration tests for RLS/tenant isolation and deletion cascades.
9. Add operational metrics and documentation.
10. Run full CI before merging each slice.

## 9. Explicit non-goals for this policy

This document does not activate a purge job, delete production data, configure Railway, define backup retention, activate real Voice/WhatsApp traffic, claim that the V1 product-default periods are statutory Chilean retention periods, or override sector-specific legal, tax, accounting or contractual obligations.
