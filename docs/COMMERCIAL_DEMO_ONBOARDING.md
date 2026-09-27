# Commercial demo onboarding

## Purpose

This is the repeatable path for taking a **new local/demo environment** from no tenant to a safe commercial demo tenant without editing PostgreSQL by hand.

It deliberately reuses the product's existing onboarding and configuration model. It does **not** provision or activate real telephony, WhatsApp, payment providers, human-transfer numbers, or production customer data.

The reference tenant is a fictitious Chilean barbershop named **Barbería Norte Demo**.

## What already exists in the product

The audit for this flow found that Helvoca already has the pieces needed for real customer onboarding:

| Area | Existing product path | Demo decision |
| --- | --- | --- |
| Tenant/admin creation | `POST /api/v1/auth/register` and the dev seed | Reuse |
| Business basics, services, weekly hours and FAQ | `PUT /api/v1/onboarding/setup` | Reuse |
| Public business profile and location | `PUT /api/v1/business/profile` | Reuse |
| Business/profile/services/hours/knowledge readiness | `OnboardingService` / `SelfServiceReadinessService` | Reuse |
| Pilot activation confirmations | `PilotActivationChecklistService` | Reuse for real pilots |
| Customers | `/api/v1/customers` | Reuse |
| Bookings and availability | `/api/v1/bookings` | Reuse |
| Demo bootstrap | `DevDataInitializer` behind `SEED_ENABLED` | Extend, do not replace |

No second onboarding framework is introduced by the demo flow.

## What still requires real customer/provider configuration

The following are intentionally **not** made ready by the demo fixture:

- a real telephone number or telephony provider;
- a real WhatsApp sender/provider/webhook;
- a real payment merchant account or real checkout;
- a real human-transfer destination;
- provider credentials and provider-specific production certification;
- human approval of a real customer's prices, FAQ, policies, agent identity and pilot scope.

Those items belong to real activation. A no-provider demo can therefore be ready for the simulator and commercial walkthrough while the general operational readiness endpoint still reports blockers such as `PHONE_MISSING`. That is expected and prevents a demo fixture from pretending to be production-ready.

## Demo dataset

With `SEED_ENABLED=true`, `DevDataInitializer` idempotently prepares:

- business: **Barbería Norte Demo**;
- timezone/language: `America/Santiago`, Spanish;
- public profile: explicitly fictitious description and address;
- location: **Pasaje Demo 123, Providencia, Santiago**;
- six active services with duration and CLP price;
- two fictitious catalog products;
- opening hours Monday–Friday 09:00–19:00 and Saturday 10:00–15:00;
- six knowledge/policy entries, including cancellation/rebooking and late-arrival demo policies;
- an active `RecepVoz Demo` agent with information, service, knowledge, availability and booking capabilities;
- three fictitious customers using `.invalid` email domains and no phone numbers;
- three future fictitious reservations for the next weekday.

Legacy generic demo services from the older fixture (`Consulta inicial`, `Servicio completo`, `Control de seguimiento`) are deactivated for this seed tenant so rerunning or upgrading an old demo remains deterministic.

## Create tenant demo -> load configuration -> validate readiness

Use a local/dev database intended for demo data.

```bash
export SEED_ENABLED=true
export SEED_ADMIN_EMAIL=demo@helvoca.local
export SEED_ADMIN_PASSWORD='choose-a-local-demo-password'
```

Start the application through the normal local development path. The existing bootstrap performs all of the following inside the application:

1. creates the demo tenant and BUSINESS_ADMIN when the configured seed email does not exist;
2. starts the existing Basic trial when the subscription service is available;
3. loads the business profile, services, products, hours, knowledge and AI-agent configuration;
4. loads fictitious customers and future reservations;
5. validates the demo fixture before startup completes.

No SQL insert/update is part of the operator procedure.

Rerunning with the same seed email is designed to be idempotent. Existing fixture rows are reused by stable business/admin identity, service name, knowledge title, customer email and booking fixture marker.

## Demo readiness contract

The seed's internal validation requires all of these before it reports the demo tenant as ready:

- public description and fictitious address exist;
- reservations are enabled in the business profile;
- at least five active services exist;
- at least six weekly opening intervals exist;
- at least five active FAQ/policy entries exist;
- the AI agent is active;
- the agent can retrieve business information, list services, search knowledge, check booking availability and create a booking;
- at least three fictitious customers exist;
- at least three fictitious reservations exist.

The validation intentionally does not require a phone number, WhatsApp provider, payment provider or human-transfer number.

## Safety rules for demos

- Never replace the fictitious location with a real customer's address in a shared demo environment.
- Never add real customer phone numbers to the demo fixture.
- Never enable Twilio/Meta/other external messaging delivery only to make the demo look complete.
- Never connect a real merchant account for this fixture.
- Use the simulator or other non-delivery product surfaces for the commercial walkthrough.
- Treat `.invalid` emails and `DEMO_FIXTURE:` booking markers as fixture data, not leads or production records.
- Do not use this seed against a production database.

## Real customer onboarding

For a real accepted pilot, use `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md` to collect approved facts, then use the existing product configuration paths:

1. create/register the tenant and administrator;
2. configure public business profile through `/api/v1/business/profile`;
3. configure business basics, services, weekly hours and knowledge through `/api/v1/onboarding/setup`;
4. configure any additional supported settings in the normal business settings UI/API;
5. review the pilot activation checklist;
6. connect and certify only the real channels included in the agreed pilot.

The demo seed is not a shortcut for production onboarding.

## Verification

Focused test:

```bash
mvn --batch-mode --no-transfer-progress -Dtest=DevDataInitializerTest test
```

Pull requests also pass through the repository's normal fast gate, backend tests and differential coverage. The existing `demo-tenant-qa` workflow can be triggered by commits containing `[demo-tenant-verify]`.

## Commercial demo checklist

Before showing the demo:

- [ ] login uses a demo-only account;
- [ ] business shows **Barbería Norte Demo**;
- [ ] profile clearly uses fictitious data;
- [ ] six active services show duration and price;
- [ ] hours are visible;
- [ ] FAQ and cancellation policy are available;
- [ ] three fictitious customers exist;
- [ ] three future fictitious reservations exist;
- [ ] availability can be queried against configured hours/reservations;
- [ ] agent configuration is active;
- [ ] no real phone/WhatsApp/payment/handoff provider was activated;
- [ ] simulator/non-delivery demo path is used.
