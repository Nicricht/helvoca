# V39 Observability

V39 adds operational visibility without weakening Helvoca's tenant isolation.

## HTTP correlation

Every HTTP request receives `X-Correlation-Id`.

- a bounded safe client value may be reused
- missing or unsafe values are replaced with a generated UUID
- the value is echoed in the response
- the value lives in SLF4J MDC only for the request lifetime
- raw tenant, customer, phone and payload values are not added to the MDC pattern

## Durable worker correlation

Persistent job execution adds only `jobId` and the bounded enum `jobType` to MDC while the handler runs. The values are removed in `finally`, including failure paths.

## Prometheus metrics

The existing Micrometer/Prometheus stack now publishes Helvoca domain metrics:

- `helvoca.jobs.enqueue.requests`
- `helvoca.jobs.executions`
- `helvoca.jobs.execution.duration`
- `helvoca.jobs.current`

Metric tags are intentionally low-cardinality. Tenant ids, operation ids, job ids, phone numbers, provider error strings and payload fields are never Prometheus labels.

`/actuator/prometheus` remains authenticated by the existing security configuration. `/actuator/health` remains the only public actuator endpoint used by Railway.

## Tenant operational summary

Authenticated `BUSINESS_ADMIN` and `OPERATOR` roles may call:

`GET /api/v1/observability/summary`

The tenant is derived from the authenticated JWT via `TenantProvider`; the caller cannot choose another `businessId` in the request.

The response contains counts only:

- universal operations by status
- persistent jobs by status
- outbound messages by status
- calendar events by status
- timestamp of the oldest active durable job

No job payload, message content, customer data or provider secret is returned.

## Production safety

V39 does not enable any provider, worker, message, call, payment or calendar integration. It only observes existing behavior.
