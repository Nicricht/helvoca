# Helvoca - Contrato de integración IA

## Tools permitidas

### `get_business_information`
Entrada:
```json
{"topic":"hours|address|policies|general"}
```

### `search_knowledge`
Entrada:
```json
{"query":"¿Aceptan mascotas?"}
```

### `find_customer_by_phone`
Entrada:
```json
{"phone":"+569..."}
```

### `check_booking_availability`
Entrada:
```json
{
  "serviceId":"uuid",
  "date":"2026-09-10",
  "resourceId":"uuid-opcional",
  "partySize":1
}
```

### `create_booking`
Entrada:
```json
{
  "customerId":"uuid",
  "serviceId":"uuid",
  "resourceId":"uuid",
  "startAt":"2026-09-10T18:00:00-03:00",
  "endAt":"2026-09-10T18:30:00-03:00",
  "idempotencyKey":"call-id:tool-call-id"
}
```

Respuesta exitosa:
```json
{
  "success":true,
  "data":{
    "bookingId":"uuid",
    "status":"CONFIRMED",
    "startAt":"2026-09-10T18:00:00-03:00"
  },
  "error":null
}
```

Respuesta de conflicto:
```json
{
  "success":false,
  "data":null,
  "error":{
    "code":"BOOKING_SLOT_UNAVAILABLE",
    "message":"La franja solicitada ya no está disponible."
  }
}
```

### `cancel_booking`
Entrada:
```json
{"bookingId":"uuid"}
```

### `reschedule_booking`
Entrada:
```json
{
  "bookingId":"uuid",
  "newStartAt":"2026-09-10T19:00:00-03:00",
  "newEndAt":"2026-09-10T19:30:00-03:00"
}
```

### `create_order`
Entrada:
```json
{
  "customerId":"uuid",
  "items":[
    {"name":"Producto A","quantity":2,"unitPrice":5990}
  ],
  "idempotencyKey":"call-id:tool-call-id"
}
```

### `get_order_status`
Entrada:
```json
{"orderId":"uuid"}
```

### `request_human_transfer`
Entrada:
```json
{
  "reason":"CUSTOMER_REQUEST",
  "context":"Cliente solicita hablar con una persona."
}
```

### `send_notification`
Entrada:
```json
{
  "channel":"WHATSAPP",
  "type":"BOOKING_CONFIRMATION",
  "recipient":"+569...",
  "message":"..."
}
```

## Regla de oro
Una respuesta verbal de éxito solo se genera cuando la tool devuelve `success=true`.
La IA no transforma un error del backend en un resultado exitoso.

## Idempotencia
Las operaciones que crean efectos persistentes reciben una `idempotencyKey`.
Una repetición de la misma tool call no debe crear dos reservas, pedidos o notificaciones.
