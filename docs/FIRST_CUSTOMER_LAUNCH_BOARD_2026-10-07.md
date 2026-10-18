# Helvoca / RecepVoz — First Customer Launch Board

**Date:** 2026-10-07  
**Technical V1 baseline:** `fd8b718d2861e44e200a6401103673e533bda3e9`  
**Objective:** obtain the first real **PILOT** using the already-certified V1 without reopening product development.

## Current commercial checkpoint (2026-10-18; no external messages sent here)

The existing tracker has **26 prospects: 4 CONTACTED, 22 NEW, 0 QUALIFIED/DEMO/PILOT/CUSTOMER**. All four Wave 1 contacts already received an initial email (2026-09-28) and follow-up (2026-10-01), and each has one additional **unsent DRAFT** in the original thread; no incoming reply was observed in the read original threads ([commercial evidence](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6246532054)). **Do not repeat an already sent contact or create/send duplicate follow-ups.** In Wave 2, existing drafts for GO Providencia and CVI Los Leones were updated in place, with corrected /app/sales CTA; ConsigueTuCasa has an older draft. All remain unsent ([CTA evidence](https://github.com/Nicricht/helvoca/issues/823#issuecomment-6246614478)). A draft does not make a prospect CONTACTED; sending needs specific authorization.

**Narrow sellable offer:** web/simulator-assisted FAQ/BOOKING pilot with voice, WhatsApp, LIVE merchant payment and automated SaaS collection explicitly excluded until approved for the tenant. Presenting this existing offer is **not** proof of a qualified lead, live customer, signed pilot or receipt of money; consult the master Issue #823 and PR #846 for subsequent evidence.

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

## Wave 1 — initial contact and follow-up already sent; no automatic repeat

These four rows already received initial contact and follow-up; prioritize authentic replies, not automatic additional outreach, before considering new leads.

| Priority | Prospect | Current state | Demo angle | Next factual transition |
| ---: | --- | --- | --- | --- |
| 1 | Hechas a Mano | CONTACTED | BOOKING + FAQ for service/cita questions | response -> qualify problem -> offer 5-minute web demo |
| 2 | Saint Antonie | CONTACTED | BOOKING + FAQ while staff are serving customers | response -> qualify -> demo |
| 3 | Albert Moreno Style | CONTACTED | BOOKING + FAQ around schedule coordination | response -> qualify -> demo |
| 4 | Eterna Juventud | CONTACTED | treatment FAQ + appointment flow | response -> qualify -> demo |

**Already completed:** initial and follow-up emails for these four prospects. Third drafts remain unsent. Do **not** send them, recreate the previous follow-up or change CONTACTED merely because the tracker date is overdue. The next transition requires a real response or a separately authorized close.

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

1. reuse prior Wave 1 threads and existing Wave 2 drafts, requesting specific approval before **any** further outreach;
2. record only genuine replies and progress the tracker only after demonstrated qualification;
3. show the existing safe web demo once a prospect agrees;
4. complete onboarding only after a real acceptance;
5. certify only the channels included in the exact pilot;
6. obtain authorized Launch Cage GO for that tenant;
7. start measuring its agreed success metric.

**Do not reopen V1 feature development unless a real qualified/pilot customer exposes a P0/P1 gap in the sold scope.**
