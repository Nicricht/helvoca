# Helvoca / RecepVoz — First Customer Launch Board

**Date:** 2026-10-07  
**Technical V1 baseline:** `fd8b718d2861e44e200a6401103673e533bda3e9`  
**Objective:** obtain the first real **PILOT** using the already-certified V1 without reopening product development.

## Operating rule

The next milestone is **not another feature**.

The next milestone is:

```text
CONTACTED -> QUALIFIED -> DEMO -> PILOT
```

Do not mark a prospect as qualified, demo, pilot or customer until the corresponding real-world event happened.

Do not activate a real provider merely to make the pipeline look further along.

## Default sellable pilot

For the fastest first customer, lead with the smallest scope that is already fully inside the certified core:

- web/simulator-assisted demo;
- business information / FAQ;
- catalog when applicable;
- BOOKING as the default mutating capability for appointment businesses;
- reprogram/correction behavior;
- human handoff when required;
- activity/result visibility.

Keep these outside the first commitment unless the customer explicitly needs them and the tenant-specific external gate is completed:

- real voice;
- real Meta WhatsApp delivery;
- outbound messaging;
- external calendar provider;
- automated Mercado Pago SaaS subscription collection;
- merchant payment LIVE.

This lets Helvoca close a pilot without waiting for provider certification that the customer may not even need on day one.

## Wave 1 — follow up existing CONTACTED prospects first

These four rows already have real contact recorded and therefore outrank every NEW lead.

| Priority | Prospect | Current state | Demo angle | Next factual transition |
| ---: | --- | --- | --- | --- |
| 1 | Hechas a Mano | CONTACTED | BOOKING + FAQ for service/cita questions | response -> qualify problem -> offer 5-minute web demo |
| 2 | Saint Antonie | CONTACTED | BOOKING + FAQ while staff are serving customers | response -> qualify -> demo |
| 3 | Albert Moreno Style | CONTACTED | BOOKING + FAQ around schedule coordination | response -> qualify -> demo |
| 4 | Eterna Juventud | CONTACTED | treatment FAQ + appointment flow | response -> qualify -> demo |

All four follow-up dates in the tracker are already past. Do **not** change their status merely because the follow-up is overdue.

## Wave 2 — easiest NEW fits if Wave 1 does not answer

Prioritize simple appointment businesses whose proposed scope maps directly to the certified BOOKING + FAQ core:

1. Guapisima Spa
2. Clínica Dental Los Leones Providencia
3. Clínica Dental Carmona y Asociados
4. GO Providencia
5. Clinica Dent Smile
6. Peluqueria Ricciarelli
7. Barbería Eleven

The purpose of Wave 2 is contact + qualification, not mass outreach.

Avoid leading with WhatsApp, telephony or integrations. Lead with the business outcome and show the web/simulator flow first.

## Demo contract

Use the existing safe demo pattern:

1. public Sales surface;
2. configured demo tenant;
3. one real-looking FAQ/service question;
4. availability lookup when relevant;
5. create one booking or other agreed core action in the demo environment;
6. reprogram/correct it to prove no duplicate action;
7. ask an unknown question and demonstrate fail-closed/no invention behavior;
8. show the persisted result/activity;
9. show public pricing;
10. ask only: **“¿Quieres que lo configuremos con tus datos y definamos un piloto?”**

No real call, WhatsApp send or payment-provider request belongs in this demo.

## Qualification gate

Move a prospect from CONTACTED to QUALIFIED only after they confirm:

- a current problem RecepVoz can address;
- the desired primary action;
- willingness to see/configure a next step.

Record the actual wording/problem in the tracker. Do not preserve “Por validar” after qualification.

## Pilot gate

Move to PILOT only when all of these are explicit:

- written scope;
- included channel(s);
- primary action;
- exclusions;
- plan/price or documented pilot condition;
- business owner/contact;
- RecepVoz owner;
- business data source;
- success metric;
- review date.

Then complete `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md`.

## Recommended first-pilot scope

For the first appointment-oriented customer, prefer:

```text
FAQ + BOOKING + REPROGRAM + HUMAN HANDOFF
channel: web/simulator during setup
external voice/WhatsApp: OFF until separately certified
merchant payment LIVE: OUT
```

This scope exercises the commercial core without making the first sale dependent on Twilio, Meta or Mercado Pago.

## Payment rule

Do not mark CUSTOMER before payment is actually received through an authorized method.

If the customer is ready before automated Mercado Pago SaaS billing is provider-certified, use the documented assisted/manual commercial bridge. Do not represent it as automated subscription billing and do not perform ad-hoc database state edits.

## Launch Cage rule

The tenant gets GO only after:

- business data is confirmed;
- scope is complete;
- core action tests pass for that tenant;
- every included external channel is certified or explicitly excluded;
- no P0 blocks the sold scope;
- ownership and rollback responsibility are clear.

## Immediate next action

The critical path is now human/commercial:

1. follow up the four CONTACTED prospects;
2. record only real replies;
3. run one safe web demo for the first qualified prospect;
4. complete onboarding when one accepts;
5. certify only the channels included in that exact pilot;
6. obtain Launch Cage GO;
7. start measuring the agreed success metric.

**Do not reopen V1 feature development unless a real qualified/pilot customer exposes a P0/P1 gap in the sold scope.**
