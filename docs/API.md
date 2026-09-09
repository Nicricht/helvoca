# Helvoca API - Sprint 4

## Authentication and business

| Method | Path | Role |
|---|---|---|
| POST | `/api/v1/auth/login` | Public |
| GET | `/api/v1/auth/me` | Authenticated |
| GET | `/api/v1/business` | BUSINESS_ADMIN / OPERATOR |
| PATCH | `/api/v1/business` | BUSINESS_ADMIN |
| GET | `/api/v1/admin/users` | BUSINESS_ADMIN |
| POST | `/api/v1/admin/users` | BUSINESS_ADMIN |

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

Availability is calculated from the service duration. Confirmed bookings for the same service cannot overlap. Cancelled bookings do not block availability.

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

`GET /api/v1/calls/{id}` returns call metadata, ordered transcript and the automatic summary when available.

## Realtime AI tools

These are not public HTTP endpoints. They are internal function tools exposed only inside an authenticated Realtime call session.

| Tool | Purpose |
|---|---|
| `get_business_information` | Read trusted business metadata |
| `list_services` | List active tenant services |
| `search_knowledge` | Search active Knowledge Base |
| `find_caller` | Resolve caller from trusted call context |
| `register_caller` | Register/update caller using trusted phone number |
| `check_booking_availability` | Query real backend availability |
| `create_booking` | Persist an `AI_CALL` booking |

The model cannot choose `businessId`, `callId`, caller phone or `streamSid`; these values come from the server-side `RealtimeCallContext`.

A tool result follows the contract:

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
