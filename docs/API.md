# Helvoca API - Sprint 2

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

`DELETE` deactivates the knowledge item instead of removing historical data.

## Infrastructure

| Method | Path | Role |
|---|---|---|
| GET | `/actuator/health` | Public |
| GET | `/swagger-ui.html` | Public in development |

## Multi-tenant rule

Business endpoints never accept `businessId` as the authority for tenant selection. The backend resolves the tenant from the authenticated JWT claim `business_id` and scopes repository queries with that identifier.
