# Merchant Payments

## Alcance

V28 conecta la operación universal `PAYMENT` con un primer provider comercial concreto: Mercado Pago Checkout Pro mediante Orders API.

La integración se entrega **sandbox-only**. El backend rechaza configuraciones `LIVE`, no habilita PAYMENT automáticamente para ningún tenant y no reutiliza las credenciales `MERCADOPAGO_*` del billing de suscripciones de Helvoca.

## Separación de dominios

Existen dos usos completamente distintos de Mercado Pago:

1. Billing SaaS de Helvoca: cobra planes/suscripciones de Helvoca y continúa usando la configuración `MERCADOPAGO_*` existente.
2. Merchant payments: permite a un negocio cobrar una operación comercial de su propio cliente y usa exclusivamente `HELVOCA_PAYMENT_<CREDENTIAL_REF>_*`.

Nunca deben compartirse access tokens, webhook secrets ni estados entre estos dos dominios.

## Configuración por tenant

V28 agrega `business_payment_provider_config`.

La tabla conserva únicamente configuración no secreta:

- `business_id`
- `provider`
- `mode`
- `enabled`
- `credential_ref`
- `webhook_key`
- timestamps

Los secretos nunca se persisten en PostgreSQL.

API:

- `GET /api/v1/payment-provider` — `BUSINESS_ADMIN` u `OPERATOR`.
- `PUT /api/v1/payment-provider` — solo `BUSINESS_ADMIN`.

Ejemplo conceptual de configuración:

```json
{
  "provider": "mercadopago",
  "mode": "SANDBOX",
  "enabled": true,
  "credentialRef": "ACME_TEST"
}
```

El backend rechaza `LIVE` deliberadamente.

## Secretos de deployment

Para `credentialRef=ACME_TEST`, el deployment debe contener:

```text
HELVOCA_PAYMENT_ACME_TEST_ACCESS_TOKEN=<sandbox access token>
HELVOCA_PAYMENT_ACME_TEST_WEBHOOK_SECRET=<sandbox webhook secret>
HELVOCA_PAYMENT_ACME_TEST_SANDBOX_CONFIRMED=true
```

`SANDBOX_CONFIRMED=true` es obligatorio. No se intenta inferir el ambiente a partir del prefijo del token.

Si falta cualquiera de estos valores, el adapter no soporta al tenant y PAYMENT falla cerrado con provider no disponible.

## Checkout Pro / Orders API

El adapter `MercadoPagoSandboxPaymentProviderAdapter` usa `MercadoPagoOrderClient` y la Orders API.

Para crear una Order:

- el monto proviene del backend universal PAYMENT;
- la moneda actual soportada es `CLP`;
- el monto CLP debe ser entero;
- `external_reference` es el UUID de la operación PAYMENT;
- la idempotency key estable es `payment-operation:<operationId>`;
- el adapter devuelve el `checkout_url` del provider.

Un `checkout_url` no significa que el pago haya sido completado. Solo un estado remoto verificado y traducido a `SUCCEEDED` cuenta como pago exitoso.

## Estados

Mapeo conservador actual:

- `created` / `action_required` → `REQUIRES_ACTION`
- `processing` → `PENDING`
- `processed + accredited` → `SUCCEEDED`
- `processed + refunded` → `REFUNDED`
- `processed + partially_refunded` → `FAILED` para evitar tratar un reembolso parcial como pago íntegro
- `refunded` → `REFUNDED`
- `canceled` / `cancelled` → `CANCELLED`
- `expired` → `EXPIRED`
- `failed` / `charged_back` → `FAILED`
- estado desconocido → `PENDING`

## Webhook

Cada tenant recibe un endpoint opaco propio:

```text
POST /webhooks/v1/payments/mercadopago/{webhookKey}
```

El `webhookPath` se devuelve en la API de configuración y debe registrarse como notificación de tipo `order` en la aplicación sandbox correspondiente de Mercado Pago.

El webhook:

1. resuelve el tenant mediante `webhookKey`;
2. exige configuración habilitada en modo `SANDBOX`;
3. rechaza `live_mode=true`;
4. verifica `x-signature` con el webhook secret del tenant y `x-request-id`;
5. deduplica por `(business_id, provider, event_id)`;
6. no confía en el estado incluido en el body;
7. consulta nuevamente la Order a Mercado Pago usando el access token del tenant;
8. actualiza `business_payment`, la operación universal PAYMENT y Conversation State solo con ese estado revalidado.

Los bodies del webhook no se almacenan. `payment_webhook_event` conserva únicamente un SHA-256 del payload para trazabilidad.

Cuando un webhook válido llega antes de que la transacción local haya terminado de persistir la proyección, un `external_reference` válido de Helvoca provoca respuesta reintentable en lugar de descartar silenciosamente el evento.

## Capabilities

Configurar un provider no habilita las herramientas PAYMENT del `AiAgent`.

La capability `PAYMENT` sigue siendo opt-in y debe activarse por separado. Esto mantiene dos llaves independientes:

- autorización conversacional para publicar/ejecutar herramientas PAYMENT;
- configuración técnica del provider comercial.

Ambas deben estar presentes para que el flujo pueda crear una intención externa.

## Garantías de seguridad de V28

- No se realizan pagos al arrancar ni al desplegar.
- No se almacenan tarjetas, CVV, access tokens ni webhook secrets en la base de datos.
- LIVE está rechazado en backend.
- `live_mode=true` se rechaza en el webhook merchant.
- Los montos siguen siendo backend-authoritative.
- El provider recibe idempotency key estable.
- Los webhooks están firmados, deduplicados y revalidados contra la API del provider.
- Las credenciales de billing SaaS de Helvoca permanecen separadas.

## Limitaciones pendientes

- La garantía de exactamente-una-vez frente a dos `create_payment` verdaderamente simultáneos todavía requiere locking transaccional y/o recuperación explícita de unique violations.
- El adapter V28 está limitado a CLP entero.
- LIVE seguirá bloqueado hasta que exista una fase de certificación separada, autorización explícita y controles operativos adicionales.
- No existe todavía una consola administrativa específica para listar y reconciliar merchant payments; el dominio y los webhooks sí sincronizan sus estados.
