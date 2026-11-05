# RecepVoz · WhatsApp Business coexistence for SaaS tenants

## Product objective

RecepVoz supports two independent customer-facing channels through one tenant-scoped receptionist:

- **Voice:** existing telephone routing and the current voice agent.
- **WhatsApp:** Meta WhatsApp Cloud API inbound messages, AI replies, supported audio transcription, and booking/customer tools.

For an eligible business that already uses the **WhatsApp Business mobile app**, the preferred onboarding option is **Coexistence**. The business can keep using its Business app while the same number is connected to Cloud API for RecepVoz. This is not a migration to a new number and must never be implemented through the ordinary phone-number `/register` endpoint.

Personal WhatsApp accounts are not the target of this flow. Meta decides whether a particular Business App number and business are eligible during Embedded Signup. RecepVoz must not promise universal eligibility.

## Customer experience

1. A Business Admin opens **Settings → Channels → WhatsApp**.
2. The UI explains the difference between:
   - **I already use WhatsApp Business on my phone:** start the Coexistence Embedded Signup flow.
   - **I have a new number for the WhatsApp Business Platform:** use the separate standard onboarding path, only when that path is explicitly enabled and safe for the selected number.
3. The business owner completes Meta authorization and confirms the flow on the phone when Meta requests it.
4. The browser sends the WABA ID reported by Meta's Embedded Signup session alongside the one-time authorization code. The backend exchanges the code, uses the resulting customer-scoped token to read that exact WABA, confirms that the WABA is also shared with the RecepVoz provider portfolio, and persists the WABA-to-tenant binding. It returns only that authorized WABA, never the provider's full shared-WABA list.
5. For Coexistence, the backend requires Meta to confirm `is_on_biz_app=true` and `platform_type=CLOUD_API`, then verifies the displayed E.164 number.
6. RecepVoz stages the sender **disabled**. A separate readiness/certification and activation step is required before any customer-facing message is sent.
7. Once activated, inbound messages are routed to the correct tenant, the AI responds using that business's own profile/services/knowledge, and supported operations are persisted through the existing WhatsApp tools.
8. The owner can continue one-to-one use of the WhatsApp Business mobile app. App-originated echoes must never trigger another AI reply.

## Shared receptionist behavior

The WhatsApp channel reuses the tenant's AI agent identity, language, greeting, business instructions, knowledge, service catalog, hours, customer identity, bookings, and request handling.

The existing backend already contains the main interaction path:

- Meta webhook parsing and tenant resolution.
- Durable inbound text/audio jobs.
- Audio transcription.
- AI response generation with an allowlisted tool set.
- Service and business information lookup.
- Availability checks and booking create/reschedule/cancel operations.
- Conversation and message persistence.
- Durable outbound replies and provider delivery-status handling.

Voice and WhatsApp remain separate transports. They share business data and customer identity where the existing omnichannel model can safely correlate them; one channel must not impersonate or replay another channel's message.

## Tenant isolation and safety requirements

- Every onboarding action requires an authenticated `BUSINESS_ADMIN` and the current `business_id`.
- The WABA ID from the Meta session must be readable with the customer-scoped token produced by that same authorization code.
- The selected WABA must also be present in the provider's shared-WABA inventory; the customer token proof and provider-side share check must both pass.
- The WABA-to-tenant binding is persisted in the tenant's Meta WhatsApp configuration. Every later assignment, subscription, phone discovery, validation, and staging request must match that binding.
- Meta readback must confirm Coexistence before any local sender record is created or linked.
- A phone number already owned by another RecepVoz tenant is rejected. The unique phone-number constraint and tenant ownership check remain authoritative.
- A tenant cannot select an arbitrary WABA from the provider portfolio: authorization is tied to the WABA reported by the current Meta session and proven readable by that customer's exchanged token.
- The coexistence staging path never calls Meta's standard `/{phone_number_id}/register` endpoint and never requests a registration PIN.
- The standard registration path performs a read-only Meta `is_on_biz_app` check immediately before `/register`. If Meta reports `true`, it rejects the operation and requires Coexistence. If the status cannot be read or is incomplete, it fails closed without registering the number.
- The staged sender remains disabled. No inbound/outbound traffic is enabled by staging alone.
- Webhook signatures, tenant resolution, idempotency, durable jobs, delivery callbacks, and existing outbound external-effect guards remain mandatory.
- `smb_message_echoes`, history synchronization events, and other non-`messages` webhook fields must not be treated as new customer messages or answered by the AI.
- A failed or incomplete Meta readback is a hard stop. Never fall back to standard registration.

## Multi-tenant feature gates

All switches default to OFF:

| Variable | Purpose |
| --- | --- |
| `META_WHATSAPP_ENABLED` | Enables the Meta WhatsApp integration runtime. |
| `META_WHATSAPP_EMBEDDED_SIGNUP_ENABLED` | Enables the Embedded Signup UI bootstrap. |
| `META_WHATSAPP_COEXISTENCE_ENABLED` | Enables the coexistence staging capability. |
| `META_WHATSAPP_COEXISTENCE_SELF_SERVICE_ENABLED` | Allows tenant-scoped self-service onboarding for eligible businesses after the provider-level Meta setup is approved. |
| `META_WHATSAPP_COEXISTENCE_ALLOWED_BUSINESS_ID` + `META_WHATSAPP_COEXISTENCE_ALLOWED_PHONE` | Optional paired allowlist for a single controlled pilot. When either is set, both must be set and the exact tenant/phone pair is enforced. |

The paired pilot allowlist takes precedence over self-service. A partially configured allowlist fails closed and cannot fall back to self-service. When both allowlist values are blank, the self-service switch must also be explicitly enabled before any tenant can stage a sender.

The self-service switch is an operator-controlled product gate, not evidence of Meta approval. It must remain OFF until RecepVoz has completed the relevant Meta Tech Provider/partner onboarding, app review/permissions, webhook setup, and operational readiness checks.

## Activation and acceptance gates

A sender is not ready merely because Embedded Signup finished. Before activation, verify:

- Provider credentials and token access are valid and never exposed to the browser or repository.
- The selected WABA and phone belong to the customer authorization.
- Coexistence readback confirms the same phone is on the Business App and Cloud API.
- The sender is staged under exactly one tenant and remains disabled until approved.
- Webhook signature verification and Meta subscription are active.
- An inbound customer text is answered once, by the correct tenant's AI.
- An inbound supported audio message is transcribed and answered once.
- The reply is accepted by Meta and its delivery callback is persisted.
- An app-originated echo does not create a customer turn or AI response.
- The WhatsApp Business mobile app remains usable after onboarding.
- A cross-tenant phone/WABA attempt is rejected without writes or outbound traffic.
- Fast Gate, Golden Journey, system integration, PostgreSQL/RLS, browser E2E, and differential coverage pass on the exact commit.

## External Meta prerequisites

Embedded Signup is Meta's onboarding mechanism for Tech Providers and Solution Partners. Meta's published Embedded Signup material requires app review and the relevant advanced permissions before production release. The exact partner role, app permissions, configuration version, and number eligibility must be confirmed in the live Meta developer/business consoles; code cannot grant these approvals.

References:
- Meta Embedded Signup collection: https://www.postman.com/meta/whatsapp-business-platform/documentation/du6gzjv/embedded-signup
- Meta WhatsApp Business partner ecosystem: https://whatsappbusiness.com/partners/become-a-partner/
- Meta Business App coexistence onboarding: https://developers.facebook.com/documentation/business-messaging/whatsapp/embedded-signup/onboarding-business-app-users
- Meta Embedded Signup v4: https://developers.facebook.com/documentation/business-messaging/whatsapp/embedded-signup/version-4

## Current implementation checkpoint

This branch extends the Aurora coexistence preparation with a tenant-scoped self-service gate and a persisted WABA-to-tenant authorization check. The feature remains OFF by default. The existing Aurora exact tenant/phone allowlist remains supported and takes precedence over self-service. The authorization code is bound to the WABA reported by Meta; the exchanged customer token must read that WABA, and subsequent provider-side operations must match the WABA persisted for the authenticated tenant.

This is code preparation only. It does not enable Meta credentials, change Railway variables, deploy, activate a sender, send a WhatsApp message, or alter `main`. Exact-HEAD CI and provider-console approval are still required.
