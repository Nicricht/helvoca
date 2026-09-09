# Helvoca API - Sprint 5

## Authentication and business

| Method | Path | Role |
|---|---|---|
| POST | `/api/v1/auth/login` | Public |
| GET | `/api/v1/auth/me` | Authenticated |
| GET | `/api/v1/business` | BUSINESS_ADMIN / OPERATOR |
| PATCH | `/api/v1/business` | BUSINESS_ADMIN |
| GET | `/api/v1/admin/users` | BUSINESS_ADMIN |
| POST | `/api/v1/admin/users` | BUSINESS_ADMIN |

## AI agent configuration

| Method | Path | Role |
|---|---|---|
| GET | `/api/v1/ai-agent` | BUSINESS_ADMIN / OPERATOR |
| PUT | `/api/v1/ai-agent` | BUSINESS_ADMIN |

Example:

```json
{
  "name": "Luna",
  "language": "es",
  "voice": "marin",
  "greeting": "Hola, gracias por llamar a Restaurante Norte. ¿En qué puedo ayudarte?",
  "instructions": "Responde de forma cálida y concisa.",
  "active": true,
  "capabilities": [
    "GET_BUSINESS_INFORMATION",
    "LIST_SERVICES",
    "SEARCH_KNOWLEDGE",
    "FIND_CALLER",
    "REGISTER_CALLER",
    "CHECK_BOOKING_AVAILABILITY",
    "CREATE_BOOKING"
  ]
}
```

The effective tenant always comes from JWT. The request cannot select another business.

## Business hours

| Method | Path | Role |
|---|---|---|
| GET | `/api/v1/business/hours` | BUSINESS_ADMIN / OPERATOR |
| PUT | `/api/v1/business/hours` | BUSINESS_ADMIN |

`dayOfWeek` uses ISO numbering: Monday=1 through Sunday=7. Multiple periods for the same day are allowed when they do not overlap.

```json
{
  "periods": [
    {"dayOfWeek": 1, "openTime": "09:00", "closeTime": "13:00"},
    {"dayOfWeek": 1, "openTime": "15:00", "closeTime": "19:00"}
  ]
}
```

An empty `periods` list clears the regular schedule. Until a tenant configures a schedule, legacy behavior remains unrestricted. Once periods exist, a day without periods is closed.

## Schedule exceptions and holidays

| Method | Path | Role |
|---|---|---|
| GET | `/api/v1/business/schedule-exceptions` | BUSINESS_ADMIN / OPERATOR |
| PUT | `/api/v1/business/schedule-exceptions` | BUSINESS_ADMIN |
| DELETE | `/api/v1/business/schedule-exceptions/{id}` | BUSINESS_ADMIN |

Closed day:

```json
{
  "date": "2026-12-25",
  "closed": true,
  "reason": "Feriado"
}
```

Special opening hours:

```json
{
  "date": "2026-12-24",
  "closed": false,
  "openTime": "09:00",
  "closeTime": "14:00",
  "reason": "Horario especial"
}
```

A date exception overrides the weekly schedule.

## Customers

| Method | Path | Role |
|---|---|---|
| GET | `/api/v1/customers` | BUSINESS_ADMIN / OPERATOR |
| GET | `/api/v1/customers/{id}` | BUSINESS_ADMIN / OPERATOR |
| GET | `/api/v1/customers/search?phone=...` | BUSINESS_ADMIN / OPERATOR |
| POST | `/api/v1/customers` | BUSINESS_ADMIN / OPERATOR |
| PATCH | `/api/v1/customers/{id}` | BUSINESS_ADMIN / OPERATOR |

## Services

| Method | Path | Role |
|---|---|---|
| GET | `/api/v1/services` | BUSINESS_ADMIN / OPERATOR |
| GET | `/api/v1/services/{id}` | BUSINESS_ADMIN / OPERATOR |
| POST | `/api/v1/services` | BUSINESS_ADMIN |
| PATCH | `/api/v1/services/{id}` | BUSINESS_ADMIN |
| DELETE | `/api/v1/services/{id}` | BUSINESS_ADMIN |

`DELETE` performs a soft deactivation so historical bookings keep their service reference.

## Bookings

| Method | Path | Role |
|---|---|---|
| GET | `/api/v1/bookings` | BUSINESS_ADMIN / OPERATOR |
| GET | `/api/v1/bookings/{id}` | BUSINESS_ADMIN / OPERATOR |
| GET | `/api/v1/bookings/availability?serviceId=UUID&startAt=ISO_INSTANT` | BUSINESS_ADMIN / OPERATOR |
| POST | `/api/v1/bookings` | BUSINESS_ADMIN / OPERATOR |
| PATCH | `/api/v1/bookings/{id}` | BUSINESS_ADMIN / OPERATOR |
| DELETE | `/api/v1/bookings/{id}` | BUSINESS_ADMIN / OPERATOR |

Availability now evaluates both service overlap and the tenant business schedule. `BUSINESS_CLOSED` is returned as a conflict when a booking or reschedule falls outside configured hours.

## Knowledge Base

| Method | Path | Role |
|---|---|---|
| GET | `/api/v1/knowledge?activeOnly=true` | BUSINESS_ADMIN / OPERATOR |
| GET | `/api/v1/knowledge/{id}` | BUSINESS_ADMIN / OPERATOR |
| POST | `/api/v1/knowledge` | BUSINESS_ADMIN |
| PATCH | `/api/v1/knowledge/{id}` | BUSINESS_ADMIN |
| DELETE | `/api/v1/knowledge/{id}` | BUSINESS_ADMIN |

## Phone numbers and calls

| Method | Path | Authentication |
|---|---|---|
| GET | `/api/v1/phone-numbers` | JWT / tenant |
| POST | `/api/v1/phone-numbers` | JWT / tenant |
| PATCH | `/api/v1/phone-numbers/{id}/active` | JWT / tenant |
| GET | `/api/v1/calls` | JWT / tenant |
| GET | `/api/v1/calls/{id}` | JWT / tenant |
| POST | `/webhooks/v1/twilio/voice` | `X-Twilio-Signature` |
| POST | `/webhooks/v1/twilio/status` | `X-Twilio-Signature` |
| WSS | `/ws/twilio` | Twilio signed handshake |

`GET /api/v1/calls/{id}` returns call metadata, ordered transcript and automatic summary when available.

## Realtime AI tools

These are internal function tools, not public HTTP endpoints. The enabled set comes from `ai_agent_capability` for the current tenant.

| Tool | Capability | Purpose |
|---|---|---|
| `get_business_information` | `GET_BUSINESS_INFORMATION` | Read business metadata and schedules |
| `list_services` | `LIST_SERVICES` | List active tenant services |
| `search_knowledge` | `SEARCH_KNOWLEDGE` | Search active Knowledge Base |
| `find_caller` | `FIND_CALLER` | Resolve caller from trusted call context |
| `register_caller` | `REGISTER_CALLER` | Register/update caller using trusted phone number |
| `check_booking_availability` | `CHECK_BOOKING_AVAILABILITY` | Query backend availability and schedule |
| `create_booking` | `CREATE_BOOKING` | Persist an `AI_CALL` booking |

A disabled capability is omitted from the OpenAI `session.update` tool list and is also rejected server-side with `TOOL_DISABLED` if invoked unexpectedly.

The model cannot choose `businessId`, `callId`, caller phone or `streamSid`; these values come from the server-side `RealtimeCallContext`.

A tool result follows:

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

or:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "BOOKING_SLOT_UNAVAILABLE",
    "message": "El horario solicitado ya no está disponible."
  }
}
```

The AI must never report an operation as successful unless the tool returned `success=true`.

## Infrastructure

| Method | Path | Role |
|---|---|---|
| GET | `/actuator/health` | Public |
| GET | `/swagger-ui.html` | Public in development |

## Multi-tenant rule

Business endpoints never accept `businessId` as the authority for tenant selection. Administrative requests resolve the tenant from JWT claim `business_id`; voice tools resolve it from a call context created only after Twilio validation.
