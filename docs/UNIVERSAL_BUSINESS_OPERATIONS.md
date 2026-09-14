# Universal Business Operations

## Objetivo

RecepVoz/Helvoca no debe asumir que todo negocio agenda horas. La plataforma separa ahora el cerebro conversacional de las operaciones comerciales que cada tenant puede ejecutar.

El mismo agente puede atender una peluquería, restaurante, taller, clínica, tienda o inmobiliaria sin agregar condicionales por industria en producción.

## Modelo

### Capacidades explícitas por tenant

`BusinessOperationCapability` incorpora:

- `CATALOG`: catálogo universal de productos y servicios.
- `ORDER`: cotizar, crear, consultar y cancelar pedidos.
- `DELIVERY`: zonas, costos y mínimos de despacho.
- `QUOTE`: cotizaciones estructuradas.
- `LEAD`: captura estructurada de potenciales clientes.

Las capacidades nuevas se almacenan en `business_operation_capability`. No se habilitan por defecto para tenants existentes.

Dependencias normalizadas:

- `ORDER` implica `CATALOG`.
- `QUOTE` implica `CATALOG`.
- `DELIVERY` implica `ORDER + CATALOG`.

API de configuración:

- `GET /api/v1/business-capabilities`
- `PUT /api/v1/business-capabilities`

Ejemplo:

```json
{
  "capabilities": ["ORDER", "DELIVERY", "QUOTE"]
}
```

## Catálogo universal

`catalog_item` reemplaza la suposición de que cada ítem es un servicio reservable.

Tipos:

- `SERVICE`
- `PRODUCT`

Cada ítem puede contener nombre, descripción, precio, moneda, duración opcional y metadata adicional.

Los registros existentes de `service` se copian automáticamente al catálogo y un trigger mantiene sincronizados los cambios futuros. Las reservas existentes continúan usando `service`, por lo que V20 no rompe el contrato actual de booking.

API:

- `GET /api/v1/catalog`
- `POST /api/v1/catalog`
- `PUT /api/v1/catalog/{id}`
- `DELETE /api/v1/catalog/{id}` (desactiva)

## Delivery

`delivery_zone` almacena cobertura comercial simple por zona, costo y compra mínima.

API:

- `GET /api/v1/delivery-zones`
- `POST /api/v1/delivery-zones`
- `PUT /api/v1/delivery-zones/{id}`
- `DELETE /api/v1/delivery-zones/{id}` (desactiva)

Esta primera versión valida zonas configuradas. Geocodificación y polígonos quedan como adapters futuros y no forman parte de la lógica del LLM.

## Herramientas comerciales

Cuando la capacidad correspondiente está habilitada, voz y WhatsApp pueden recibir:

- `list_catalog`
- `list_delivery_zones`
- `quote_order`
- `create_order`
- `get_order_status`
- `cancel_order`
- `create_quote`
- `create_lead`

Los proveedores reciben únicamente las herramientas autorizadas para el tenant actual.

### Regla de pedido

`quote_order` siempre recalcula precios usando el catálogo actual.

`create_order` exige `expectedTotal`, que debe coincidir exactamente con el total recalculado por backend. Esto evita que el modelo cree un pedido con un precio inventado, manipulado o desactualizado.

Para `DELIVERY`, el backend exige:

- capability `DELIVERY` activa;
- `deliveryZoneId` perteneciente al tenant y activo;
- dirección no vacía;
- compra mínima de la zona satisfecha.

El pedido confirmado persiste snapshots de nombre, cantidad, precio unitario y total de línea.

## Entidades transaccionales

### `business_order`

Pedido real con fulfillment `PICKUP` o `DELIVERY`, estado, subtotal, despacho, total, moneda y origen (`VOICE`, `WHATSAPP`, `MANUAL`, `API`).

### `business_order_line`

Líneas estructuradas del pedido. No se persiste el pedido como texto libre.

### `business_quote`

Cotización independiente. Si todos los ítems tienen precio oficial, puede quedar `READY` con monto calculado. Si necesita evaluación humana, queda `REQUESTED` sin inventar monto.

### `business_lead`

Lead estructurado con nombre, teléfono verificado del canal, correo opcional, interés y presupuesto opcional.

## Consola/API operativa

- `GET /api/v1/commercial/orders`
- `PATCH /api/v1/commercial/orders/{id}/status`
- `GET /api/v1/commercial/quotes`
- `GET /api/v1/commercial/leads`

Esto permite que el negocio vea lo que el agente creó y continúe el flujo operacional.

## Ejemplo restaurante

1. Cliente pide dos hamburguesas y despacho.
2. El agente ejecuta `list_catalog`.
3. Recoge modificadores del cliente.
4. Ejecuta `list_delivery_zones`.
5. Ejecuta `quote_order` con ítems, zona y dirección.
6. Comunica el total real devuelto por backend.
7. Cliente confirma.
8. Ejecuta `create_order` con el mismo total como `expectedTotal`.
9. Solo si `success=true` comunica que el pedido quedó confirmado.

No se crea una reserva y no se usa `create_request` como sustituto de un pedido.

## Principio de arquitectura

Los verticales deben ser presets de configuración, no ramas de código.

El backend define hechos, reglas y transacciones. El LLM interpreta lenguaje y reúne datos, pero no calcula precios, no inventa disponibilidad y no confirma operaciones antes de que el backend devuelva éxito.
