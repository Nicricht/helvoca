# Business Configuration Architecture

## Purpose

This document is the implementation baseline for Helvoca's "Mi negocio" work.

The goal is not to build a different product for each industry. Helvoca remains a universal multi-tenant conversational operations platform. A restaurant, store, clinic, salon, workshop or professional office is a tenant configuration over shared capabilities.

The governing rule is:

> The AI converses. Helvoca owns business truth.

Prices, stock, availability, policies that change transactional behavior, customer identity, permissions and operation state must come from backend-owned data and services. They must never be invented or trusted from model memory.

## Audit snapshot

Audit performed against `main` at `61e7a4e7ac96f97f2c7af0906ff4b7abf3eba870`.

### Existing reusable foundation

| Area | Current implementation | Decision |
| --- | --- | --- |
| Tenant root | `Business` with name, timezone, language, human transfer phone and status | Keep as tenant root |
| Roles | `PLATFORM_ADMIN`, `BUSINESS_ADMIN`, `OPERATOR` | Keep; refine permissions later |
| Tenant isolation | `TenantProvider` plus forced PostgreSQL RLS from V40 | Mandatory foundation |
| Business hours | Weekly intervals via `BusinessHour` | Keep |
| Schedule exceptions | `BusinessScheduleException` supports closed/special dates | Keep; expose in admin UX/API |
| Services | `ServiceItem` with name, description, duration, price and active flag | Keep as current bookable-service authority |
| Universal catalog | `CatalogItem` supports PRODUCT/SERVICE, description, price, currency, duration, metadata and active flag | Keep and extend for sellable products |
| Service/catalog sync | Database trigger projects legacy services into `catalog_item` | Preserve during migration |
| Knowledge | `KnowledgeItem` with title, category, content and active flag | Keep for FAQs and explanatory policies |
| Bookings | `Booking`, availability, overlap prevention, reschedule/cancel, audit | Keep and extend |
| Orders/quotes/leads/requests | Universal operation engine and typed projections | Keep |
| Delivery | Zones, fees, minimum orders and coverage terms | Keep |
| Payments | Provider-neutral payment workflow | Keep |
| AI configuration | `AiAgent` plus per-tenant `AiCapability` | Keep as runtime tool authorization |
| AI tools | Backend-authoritative catalog, booking, order, delivery, quote, lead and payment tools | Keep and extend |
| Confirmation/policy | Backend policy engine, confirmation tokens and idempotency for commercial operations | Keep |
| Audit | Tenant-scoped append-only `audit_log`, human actor snapshots, CSV/XLSX export | Keep and broaden coverage |
| Customers | Tenant-scoped customer registry and CSV/XLSX export | Keep |
| Onboarding | Business + services + weekly hours + knowledge; AI can propose data from a public source | Reuse, expand |
| Frontend settings | Business, AI agent, services, hours, FAQ and phone configuration | Rework into "Mi negocio" |
| Readiness | Onboarding and commercial readiness checks | Reuse, make capability-aware |
| Voice/WhatsApp | Shared business-operation architecture with channel adapters | Preserve |

## Gaps confirmed by the audit

### Business profile

The tenant root does not currently contain the public information a receptionist commonly needs:

- business type/preset;
- public description;
- public phone/email;
- website;
- street/address;
- commune/city/region/country;
- default currency.

Do not overload the core `business` row with every future field. Use a 1:1 business profile extension for public/business configuration while `business` remains the tenant identity root.

### Product catalog

Products exist in the universal catalog, but there is no first-class support for:

- SKU;
- category hierarchy;
- product variants/options;
- inventory tracking flag;
- inventory quantity;
- stock reservations/holds;
- stock movement history;
- reorder/minimum-stock threshold.

There is currently no runtime inventory module. Searches for stock, SKU and inventory show no production implementation.

### Booking policy

Current booking safety already enforces:

- active service;
- future time;
- business hours;
- no overlapping booking for the same service;
- tenant/customer ownership.

Missing configurable operational rules include:

- minimum booking notice;
- maximum advance window;
- cancellation notice;
- reschedule notice;
- buffer before/after;
- simultaneous capacity;
- staff/room/table/equipment resources.

The runtime currently behaves effectively as one reservable capacity per service. A generic resource model exists only in old design material, not production code.

### Schedule administration

Schedule exceptions exist in the backend model and are consumed by availability, but the current business-hours admin API exposes only weekly hours. "Mi negocio" must expose closed dates and special opening hours without bypassing the schedule service.

### Rules and policies

Explanatory policy text can already live in Knowledge, but operational rules must not be free-form AI instructions.

Examples:

- "Returns within 30 days" can be knowledge/policy content.
- "Do not allow a booking with less than 2 hours notice" must be structured backend configuration.
- "Free delivery over X" must be structured pricing/delivery configuration.
- "Hold a product for 90 minutes" must be structured inventory/order configuration.

Natural-language agent instructions may influence tone and conversational behavior only. They are not an authority for prices, stock, availability or transactional rules.

### Onboarding and settings

The current onboarding/setup flow is service-oriented. It handles business basics, services, hours and FAQ, but it does not configure products, inventory, operational policies, delivery or capability-specific readiness.

The current Settings page already has useful foundations, but "Mi negocio" still lacks clear sections for:

- public business profile;
- products;
- inventory;
- special schedule dates;
- reservation rules;
- business policies;
- capability-based readiness.

### Audit coverage

Booking, business, services, knowledge and other important flows already emit audit records, and the audit ledger is append-only. Some newer generic configuration services, such as direct universal catalog management and delivery-zone changes, do not consistently produce before/after human audit snapshots.

Every future business configuration mutation must be auditable.

### Import/export

Customer and audit CSV/XLSX exports already exist. There is no catalog/inventory CSV/XLSX import workflow and no unified export for products, reservations, orders and inventory.

### AI/runtime

The important safety architecture is already correct: tools query backend data and commercial prices are backend-authoritative.

The missing runtime capabilities for the planned experience are principally:

- richer business-profile lookup;
- product search by SKU/category/name;
- stock lookup;
- stock reservation/release;
- structured booking-policy checks;
- structured policy lookup.

Voice and WhatsApp must receive these capabilities through shared domain services, not through duplicated channel-specific business logic.

## Target configuration model

The following is the target domain shape. Names are architectural contracts; migrations are implemented incrementally in later tasks.

### 1. Tenant identity

Keep:

`business`

Responsibilities:

- tenant ID;
- business name;
- timezone;
- language;
- status;
- secure human-transfer destination.

### 2. Public business profile

Add:

`business_profile` 1:1 with `business`

Recommended fields:

- `business_id`;
- `business_type` or `preset_key`;
- `public_description`;
- `public_phone`;
- `public_email`;
- `website_url`;
- `address_line`;
- `commune`;
- `city`;
- `region`;
- `country_code`;
- `default_currency`;
- timestamps.

`preset_key` is an onboarding/UI suggestion only. Runtime business logic must never branch on `if restaurant`, `if clinic`, etc.

### 3. Catalog

Keep and extend `catalog_item`.

Add first-class product structure as needed:

`catalog_category`

- tenant-scoped category;
- parent category optional;
- name;
- active.

`catalog_variant`

- tenant-scoped;
- catalog item;
- SKU;
- option values;
- price override optional;
- active.

A product with no variants can use an implicit/default sellable variant internally so inventory has one consistent key.

Services remain managed through the existing bookable `service` path until a deliberate compatibility migration removes the legacy projection.

### 4. Inventory

Add:

`inventory_stock`

- business;
- sellable item/variant;
- on-hand quantity;
- reserved quantity;
- reorder threshold optional;
- version for optimistic/concurrency control;
- updated timestamp.

Derived:

`available = on_hand - reserved`

Add append-only:

`inventory_movement`

- business;
- item/variant;
- movement type;
- quantity delta;
- reason;
- source operation/reference;
- actor/source;
- timestamp.

Optional transactional hold:

`inventory_reservation`

- business;
- operation/customer;
- item/variant;
- quantity;
- status;
- expires_at;
- timestamps.

Stock changes must be atomic and tenant-scoped. The LLM never supplies the authoritative resulting stock value.

### 5. Services and reservable resources

Keep:

`service`

Extend configuration through generic booking/resource models rather than industry-specific tables.

Target:

`booking_policy`

- business;
- optional service scope;
- minimum notice minutes;
- maximum advance days;
- cancellation notice minutes;
- reschedule notice minutes;
- buffer before minutes;
- buffer after minutes;
- default simultaneous capacity.

For businesses needing real resources:

`reservable_resource`

- business;
- name;
- type: STAFF, ROOM, TABLE, VEHICLE, EQUIPMENT, OTHER;
- capacity;
- active.

`service_resource`

- service;
- resource;
- requirement/capacity metadata.

This model supports a salon chair, clinician, table or vehicle without introducing vertical-specific branches.

### 6. Schedule

Keep:

- `business_hours`;
- `business_schedule_exception`.

Expose both through one admin-facing schedule configuration service.

Later resource-specific schedules may extend the same availability engine rather than replace business hours.

### 7. Knowledge and policies

Keep `knowledge_item` for:

- FAQs;
- location;
- payment methods;
- parking;
- guarantees;
- descriptive return/cancellation information;
- instructions that do not themselves authorize a transaction.

Operational constraints stay in typed domain configuration such as `booking_policy`, delivery zones, inventory settings and operation policy.

Do not create a generic "magic JSON rules" table as the primary authority in this phase. Typed validation is safer and easier to explain to business owners.

### 8. AI business context

Add an application-layer `BusinessContextService`; it is an aggregator, not a new source of truth.

It resolves only the context needed for a conversation:

- public profile;
- enabled capabilities;
- relevant knowledge;
- active services;
- relevant catalog data;
- schedule/policy facts.

Dynamic facts remain tool calls:

- price;
- stock;
- availability;
- customer bookings;
- order/payment/delivery state.

Do not inject an entire catalog or inventory snapshot into the prompt.

### 9. Readiness

Replace the fixed service-centric readiness interpretation with capability-aware readiness.

Examples:

- BOOKING enabled -> require active service + schedule + valid booking policy.
- ORDER enabled -> require active catalog items.
- inventory tracking enabled -> require inventory initialization for tracked products.
- DELIVERY enabled -> require usable delivery configuration.
- Voice enabled -> require phone/provider readiness.
- WhatsApp enabled -> require sender/webhook/provider readiness.

Optional modules must not block a tenant that does not use them.

## Source-of-truth matrix

| Question from customer | Authoritative source |
| --- | --- |
| What is the business called / where is it? | Business + business profile |
| What services do you offer? | Service/catalog |
| What does it cost? | Catalog/service backend price |
| Is this product in stock? | Inventory service |
| Can I book Tuesday at 17:00? | Schedule + booking policy + resources + bookings |
| Can I cancel? | Booking policy + current booking state |
| Do you have parking? | Knowledge |
| What is your return policy? | Knowledge/policy content |
| Can you deliver here? | Delivery coverage service |
| How much is delivery? | Delivery backend |
| What is my order/payment status? | Operation/payment backend |
| Can you give me a discount? | Explicit structured commercial policy only; otherwise no |
| Unknown/unconfigured fact | State that the information is unavailable or hand off; never invent |

## UX contract for "Mi negocio"

The business owner must never need to edit:

- JSON;
- prompts;
- tool schemas;
- API payloads;
- database records.

The UI will present business concepts:

1. Información
2. Horarios
3. Productos
4. Servicios
5. Inventario
6. Reservas
7. Reglas y políticas
8. Preguntas frecuentes
9. Recepcionista IA
10. Canales
11. Estado de preparación

Industry presets may choose sensible defaults and hide irrelevant sections, but they only preconfigure generic capabilities.

## Implementation order after this audit

### Task 2: "Mi negocio" + onboarding

Build the unified information architecture and business-profile configuration first. Reuse the existing Settings/onboarding APIs where possible. Do not build inventory UI before its domain exists.

### Task 3: catalog + services + inventory

Extend the product model, add inventory ledger/holds, import/export and admin UI.

### Task 4: booking + rules + knowledge

Add structured booking policy, schedule-exception administration and resource/capacity model; improve policy/FAQ management.

### Task 5: operational AI brain

Add `BusinessContextService` and missing backend-authoritative tools, then enforce anti-hallucination and confirmation contracts.

### Task 6: voice + WhatsApp + simulator

Make both channels consume the same shared services and expose source-aware simulation.

### Task 7: business administration

Complete roles, audit coverage, exports and integration boundaries.

### Task 8: QA + production

Run cross-industry scenarios, concurrency, tenant isolation, anti-hallucination, channel and production certification.

## Non-negotiable invariants

1. No industry-specific application forks.
2. No price, stock or availability invented by an LLM.
3. No transactional rule stored only in a prompt.
4. Every tenant-owned table is protected by tenant context and PostgreSQL RLS.
5. Every business configuration mutation is auditable.
6. Sensitive mutations use confirmation, idempotency and concurrency protection where applicable.
7. Voice and WhatsApp share domain truth.
8. Provider integrations never become business authority.
9. Missing critical configuration fails closed.
10. The owner-facing UX remains simple even when the backend model is rigorous.
