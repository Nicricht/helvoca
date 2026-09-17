# Operations and Conversations UX Design

**Date:** 2026-09-17

## Goal

Make the authenticated product feel like one Helvoca product and prioritize business outcomes over provider internals.

## Principles

- Preserve the existing static Spring Boot frontend and all backend APIs.
- Keep the dark Helvoca visual language already used by login and Home.
- Operations answers “what did Helvoca do for my business?”
- Conversations answers “what did customers and Helvoca say?”
- Provider details, readiness and end-to-end certification remain available, but under collapsed technical diagnostics.
- Internal event codes remain unchanged in the backend and logs; only the presentation layer translates them to human Spanish.
- Do not activate calls, WhatsApp sends, payments or providers.

## Operations

The page starts with business metrics, then open requests and unanswered questions, then recent calls. Technical readiness and certification move to a closed `<details>` section at the bottom. Existing element IDs and endpoints are preserved so backend behavior does not change.

## Conversations

The two-column inbox remains. It shares the dark visual language, translates action/resolution codes to human labels and automatically opens the newest available conversation after loading. Voice and WhatsApp continue loading independently.

## Error handling

Partial WhatsApp failure does not remove calls. Operations keeps the existing authenticated API failure behavior. Technical diagnostics stay available for troubleshooting without dominating the normal customer experience.

## Tests

Playwright coverage verifies that diagnostics are collapsed by default, business work is visible first, internal event codes are not shown to the customer, conversations auto-open the newest item, and partial channel failure remains usable.
