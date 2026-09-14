# Universal Business Operations

## Objetivo

RecepVoz/Helvoca no debe asumir que todo negocio agenda horas. La plataforma separa ahora el cerebro conversacional de las operaciones comerciales que cada tenant puede ejecutar.

El mismo agente puede atender una peluquería, restaurante, taller, clínica, tienda o inmobiliaria sin agregar condicionales por industria en producción.

## Modelo

### Capacidades explícitas por tenant

`BusinessOperationCapability` funciona como una capa de configuración/preset de negocio:

- `CATALOG`: catálogo universal de productos y servicios.
- `ORDER`: cotizar, crear, consultar y cancelar pedidos.
- `DELIVERY`: cobertura, validación de dirección, costos y mínimos de despacho.
- `QUOTE`: cotizaciones estructuradas.
- `LEAD`: captura estructurada de potenciales clientes.

La autorización efectiva de herramientas tiene una sola fuente de verdad: `AiAgent` + `AiCapability`, persistida en `ai_agent_capability`. Los presets anteriores se traducen a capacidades concretas como `LIST_CATALOG`, `QUOTE_ORDER`, `CREATE_ORDER`, `GET_ORDER_STATUS`, `CANCEL_ORDER`, `LIST_DELIVERY_ZONES`, `VALIDATE_DELIVERY_ADDRESS`, `CREATE_QUOTE` y `CREATE_LEAD`.

Las capacidades comerciales son opt-in. Los defaults legacy excluyen todas las capacidades comerciales, por lo que agregar una nueva constante al enum no puede habilitar una transacción automáticamente para tenants existentes.

V22 migra grants existentes desde `business_operation_capability` hacia `ai_agent_capability`. La tabla antigua queda únicamente como artefacto de compatibilidad/migración y no participa en la autorización runtime.

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

`catalog_item` elimina la suposición de que cada ítem comercial es necesariamente una reserva.

Tipos:

- `SERVICE`
- `PRODUCT`

Cada ítem puede contener nombre, descripción, precio, moneda, duración opcional y metadata adicional.

Los registros existentes de `service` se copian automáticamente al catálogo y un trigger mantiene sincronizados los cambios futuros. Las reservas existentes continúan usando `service`, por lo que V20 no rompe el contrato actual de booking.

Los `SERVICE` vinculados a booking se administran únicamente mediante `/api/v1/services`; el CRUD del catálogo universal permite administrar directamente `PRODUCT`. Esto evita que un servicio reservable tenga un nombre, precio o estado distinto entre booking y catálogo.

API:

- `GET /api/v1/catalog`
- `POST /api/v1/catalog`
- `PUT /api/v1/catalog/{id}`
- `DELETE /api/v1/catalog/{id}` (desactiva)

## Delivery

`delivery_zone` almacena una zona comercial con:

- nombre;
- `coverageTerms` configurables, separados por coma, punto y coma, barra vertical o salto de línea;
- costo de despacho;
- compra mínima opcional;
- estado activo/inactivo.

Ejemplo de cobertura:

```text
Huechuraba; Pedro Fontova; Ciudad Empresarial
```

La dirección entregada por el cliente se normaliza en backend y se compara con las reglas activas del tenant. Se eliminan diferencias de mayúsculas y acentos para el matching. Si ninguna zona coincide, el despacho se rechaza. Si dos zonas empatan con la misma especificidad, el backend falla cerrado y exige revisar la cobertura antes de confirmar.

API:

- `GET /api/v1/delivery-zones`
- `POST /api/v1/delivery-zones`
- `PUT /api/v1/delivery-zones/{id}`
- `DELETE /api/v1/delivery-zones/{id}` (desactiva)

Esta versión usa reglas textuales de cobertura. Geocodificación y polígonos podrán implementarse posteriormente como adapters sin mover la decisión de cobertura al LLM.

## Herramientas comerciales

Cuando la capacidad correspondiente está habilitada, voz y WhatsApp pueden recibir:

- `list_catalog`
- `list_delivery_zones`
- `validate_delivery_address`
- `quote_order`
- `create_order`
- `get_order_status`
- `cancel_order`
- `create_quote`
- `create_lead`

Los proveedores reciben únicamente las herramientas autorizadas por el `AiAgent` del tenant actual. Voz y WhatsApp comparten las mismas definiciones y el mismo servicio de operaciones comerciales.

### Regla de pedido

`quote_order` siempre recalcula precios usando el catálogo actual.

`create_order` exige `expectedTotal`, que debe coincidir exactamente con el total recalculado por backend. Esto evita que el modelo cree un pedido con un precio inventado, manipulado o desactualizado.

Para `DELIVERY`, el backend exige:

- capability `DELIVERY` activa;
- dirección no vacía;
- dirección cubierta por exactamente una zona válida;
- compra mínima de la zona satisfecha;
- costo obtenido exclusivamente desde configuración backend.

`deliveryZoneId` puede enviarse como referencia después de `validate_delivery_address`, pero el backend vuelve a resolver la dirección y rechaza cualquier ID que no corresponda con la cobertura calculada.

El pedido confirmado persiste snapshots de nombre, cantidad, precio unitario y total de línea.

## Entidades transaccionales

### `business_order`

Pedido real con fulfillment `PICKUP` o `DELIVERY`, estado, subtotal, despacho, total, moneda y origen (`VOICE`, `WHATSAPP`, `MANUAL`, `API`).

Estados:

`CONFIRMED -> PREPARING -> READY`

Desde `READY`:

- `PICKUP -> COMPLETED`
- `DELIVERY -> DISPATCHED -> COMPLETED`

`CONFIRMED` y `PREPARING` admiten cancelación. `COMPLETED` y `CANCELLED` son terminales. El backend rechaza saltos arbitrarios de estado.

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

Las consultas operativas requieren `BUSINESS_ADMIN` u `OPERATOR`. Las mutaciones de capacidades, catálogo, zonas y estado administrativo requieren `BUSINESS_ADMIN`.

## Ejemplo restaurante

1. Cliente pide dos hamburguesas y despacho.
2. El agente ejecuta `list_catalog`.
3. Recoge modificadores del cliente.
4. Pide la dirección de entrega.
5. Ejecuta `validate_delivery_address`.
6. Ejecuta `quote_order`; backend vuelve a comprobar dirección, precios, mínimo y despacho.
7. Comunica el total real devuelto por backend.
8. Cliente confirma.
9. Ejecuta `create_order` con el mismo total como `expectedTotal`.
10. El backend vuelve a recalcular todo.
11. Solo si `success=true` comunica que el pedido quedó confirmado.

No se crea una reserva y no se usa `create_request` como sustituto de un pedido.

## Ejemplos por tipo de negocio

- Restaurante: `CATALOG + ORDER + DELIVERY`.
- Tienda con retiro: `CATALOG + ORDER`.
- Peluquería: booking existente, sin necesidad de `ORDER`.
- Taller: booking para hora de revisión y `QUOTE` para trabajos que deben presupuestarse.
- Inmobiliaria: `LEAD` para captar interés y booking/request para coordinar visita según configuración.
- Clínica o veterinaria: booking/request; las capacidades comerciales se activan solo si el negocio realmente las necesita.

Los ejemplos son presets conceptuales, no ramas de código por industria.

## Principio de arquitectura

Los verticales deben ser presets de configuración, no ramas de código.

El backend define hechos, reglas y transacciones. El LLM interpreta lenguaje y reúne datos, pero no calcula precios, no decide cobertura, no inventa disponibilidad y no confirma operaciones antes de que el backend devuelva éxito.
