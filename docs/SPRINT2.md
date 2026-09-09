# Helvoca - Sprint 2

## Objective

Add the core business capabilities required before telephony and AI voice integration:

- Customers
- Service catalog
- Booking lifecycle
- Availability checks
- Knowledge Base
- Tenant-scoped access
- Business-role authorization
- Unit tests

## Implemented modules

### Customer

- Create customer
- List customers for current tenant
- Get customer by id
- Search by phone
- Update customer
- Audit create/update operations

### Service catalog

- Create service
- List/get services
- Update service
- Soft deactivate service
- Service duration and price
- Unique service name per business
- Audit create/update/deactivate operations

### Booking

- Create booking
- List/get bookings
- Check availability for a concrete start time
- Calculate end time from service duration
- Reject overlapping confirmed bookings
- Reschedule booking
- Cancel booking
- Reject scheduling in the past
- Serializable transaction isolation for create/reschedule
- Audit create/reschedule/cancel operations

### Knowledge Base

- Create/list/get/update knowledge entries
- List only active entries with `activeOnly=true`
- Soft deactivate entries
- Audit write operations

## Security

- Tenant resolved only from JWT `business_id`
- Customer and booking operations: `BUSINESS_ADMIN`, `OPERATOR`
- Service catalog reads: `BUSINESS_ADMIN`, `OPERATOR`
- Service catalog writes: `BUSINESS_ADMIN`
- Knowledge reads: `BUSINESS_ADMIN`, `OPERATOR`
- Knowledge writes: `BUSINESS_ADMIN`

## Database

Migration `V3__sprint2_core.sql` adds:

- `customer`
- `service`
- `booking`
- `knowledge_item`
- tenant indexes
- booking period indexes
- foreign keys
- booking period integrity check

## Current booking model limitation

For the MVP, one service represents one reservable capacity. This intentionally avoids pretending that a generic service automatically models multiple employees, rooms, chairs, tables, or equipment.

A later resource-scheduling module should introduce concepts such as:

- Resource
- Staff member
- Room
- Table
- Capacity
- Resource-specific availability

## Tests added

- Customer list uses authenticated tenant id
- Overlapping bookings are rejected

## Next sprint

Sprint 3 should introduce the telephony domain:

1. Phone numbers
2. Calls
3. Call states
4. Telephony webhook security
5. Twilio integration boundary
6. WebSocket/media-session foundation
7. Call transcript persistence
