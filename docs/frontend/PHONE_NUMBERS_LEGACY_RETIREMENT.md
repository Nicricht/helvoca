# Legacy phone numbers surface retirement

## Decision

`phone-numbers.html` has no unique product capability left. Its only behavior is listing the current tenant phone numbers and detaching one from the tenant.

Canonical owner: React Settings → Channels.

Canonical destination:
- `/app/settings?section=channels`

The legacy document becomes compatibility-only.

FRAME CHANGE: NO.

## Capability parity already present in React

React Channels already provides:
- list connected phone numbers;
- active/inactive toggle;
- WhatsApp enable/disable toggle;
- detach from current business;
- connect an existing E.164 number;
- search available provider numbers;
- explicit-confirmation provisioning;
- Meta WhatsApp onboarding and staged activation.

Therefore a separate phone-number application would duplicate the canonical channel configuration surface.

## Safety contract

Phone detachment is high-impact tenant configuration but must preserve provider truth.

Detaching a number:
- removes only the number-to-business association in Helvoca;
- does not purchase, release, cancel or delete the provider number;
- requires an explicit user confirmation;
- is restricted to BUSINESS_ADMIN by the backend;
- must refresh canonical channel state after success;
- must not run merely by visiting either the legacy URL or Settings.

Provisioning remains separate and retains its explicit cost confirmation.

## Existing legacy defect

`phone-numbers.html` is not currently part of Spring Security's public console compatibility allowlist. A document navigation cannot attach the bearer token stored in sessionStorage, so the standalone page is structurally unreliable in production even though its JavaScript later sends Authorization headers.

A compatibility redirect should be public like the other retired app shells, while authentication remains enforced by the React AuthBoundary and protected APIs.

## Migration plan

1. Add RED contract proving the old document is still an application and that React detach copy lacks the provider-safety clarification.
2. Replace `phone-numbers.html` with a minimal redirect to `/app/settings?section=channels`.
3. Add the compatibility path to the public console allowlist.
4. Strengthen React detach confirmation/success language so users know the Twilio/provider number is untouched.
5. Keep all channel mutations in existing typed React APIs.
6. Certify exact-head CI before merge.
7. Certify exact-main CI and Railway on the same SHA after merge.

## Acceptance

- legacy URL redirects to canonical Settings Channels;
- legacy document contains no direct phone API calls and no detach implementation;
- visiting legacy/canonical route performs zero write requests;
- Channels renders the existing connected number;
- detach requires confirmation;
- detach sends exactly DELETE `/api/v1/phone-numbers/{id}`;
- UI states that detaching does not release/delete the provider number;
- no new primary navigation entry is introduced;
- backend phone authorization remains unchanged.
