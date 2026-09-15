# V33 Omnichannel Core

## Goal

V33 changes Helvoca from channel-local operational context to a conservative tenant-scoped omnichannel core.

A customer may start through VOICE, continue through WHATSAPP, and return through another linked channel without creating a second operational state. The backend remains authoritative and no industry-specific branching is introduced.

## Identity contract

Automatic identity linking is fail-closed.

- Identity is always scoped by `business_id`.
- Phone values are normalized only when they already contain an international prefix (`+` or `00`). Helvoca never guesses a country code.
- `UNVERIFIED` and `PROVIDER_ASSERTED` identities do not authorize automatic customer linking.
- Only `CUSTOMER_VERIFIED` and `MANUAL_VERIFIED` identities may be used for automatic linking.
- If more than one customer in the same tenant has the same verified identity, resolution is ambiguous and returns no customer.
- A phone number that happens to look similar is never sufficient to merge two customers.
- Existing `customer.phone` values are migrated only as `UNVERIFIED` observations.

BUSINESS_ADMIN can explicitly verify a phone identity through the tenant-scoped customer identity API. Verification is audited.

## Session model

`omnichannel_session` represents the durable tenant/customer conversation context.

`omnichannel_channel_session` links concrete channel sources such as a call or WhatsApp conversation to that omnichannel session.

Rules:

- At most one ACTIVE omnichannel session exists for a tenant/customer pair.
- A concrete channel source can belong to only one omnichannel session.
- A previously linked source cannot silently switch to another customer.
- Tenant A cannot resolve, link, or adopt a source belonging to tenant B.
- Anonymous/unidentified sources remain channel-local.

## Shared operation state

`conversation_operation_state` remains compatible with the legacy `(business, channel, sourceReferenceId)` key for anonymous/local conversations.

For identified omnichannel sessions, it additionally references `omnichannel_session_id`.

PostgreSQL enforces exactly one shared conversation state per `(business_id, omnichannel_session_id)`. The session resolver also takes a transaction-scoped PostgreSQL advisory lock on the resolved omnichannel session before shared state is read or mutated. This prevents VOICE and WHATSAPP requests arriving concurrently from creating competing shared state rows.

The shared state carries the active operation and conversation state JSON. Therefore a confirmed or updated operation on WhatsApp is visible when the same customer continues by voice, and vice versa.

## Channel behavior

Inbound VOICE and WHATSAPP may automatically attach a customer only through a unique verified identity. Provider caller/sender assertions are stored as observations but do not become trusted merely because a carrier supplied them.

Explicit customer registration may record a declared phone identity as unverified. Verification is a separate operation.

## Safety invariants

- strict multi-tenant isolation
- fail closed on missing, malformed, ambiguous, or conflicting identity
- no fuzzy customer matching
- no country-code guessing
- no industry hardcoding
- no certification flag changes
- no real call or real payment execution introduced by V33
- backend remains authority for operations and state

## Verification required before release closure

V33 is not complete until all of the following are true:

1. PR CI is green on the exact head SHA.
2. The PR is merged using that expected head SHA.
3. `main` CI is green on the exact merge SHA.
4. Railway deploys that exact `main` SHA.
5. Flyway validates and migrates schema V32 to V33.
6. PostgreSQL and Hibernate/JPA initialize successfully.
7. Tomcat and `HelvocaApplication` start successfully.
8. `/actuator/health` succeeds.
