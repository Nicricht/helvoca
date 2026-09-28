# Ferretería San Martín Demo — Cliente ficticio adversarial V1

## Propósito

Esta ferretería es un cliente ficticio deliberadamente difícil para intentar romper RecepVoz antes de incorporar un negocio real.

No es una maqueta visual ni un simple catálogo. La configuración busca estresar:

- productos con nombres y medidas parecidas;
- unidades de venta diferentes;
- stock alto, bajo y agotado;
- compras con varias líneas;
- cambios de intención;
- cotizaciones;
- retiro y delivery;
- preguntas ambiguas;
- preguntas que deben derivarse por seguridad;
- duplicados e idempotencia;
- fallos de herramientas;
- concurrencia;
- aislamiento entre tenants.

La fuente de datos ejecutable está en:

`src/test/resources/fixtures/adversarial-hardware-store-v1.json`

## Dueño ficticio

**Mauricio San Martín Demo**.

Durante las pruebas conversacionales, el rol del dueño es responder únicamente con lo definido aquí y en la fixture. Si una regla no está definida, la respuesta correcta del dueño es **“eso no está configurado; no lo inventes”**.

## Identidad del negocio

- Nombre: **Ferretería San Martín Demo**
- Rubro: ferretería de barrio
- Dirección ficticia: Pasaje Tuerca Demo 742, Providencia, Santiago
- Idioma: español
- Zona horaria: America/Santiago
- Moneda: CLP
- Email: dominio `.invalid`
- Teléfono: ninguno
- Pagos reales: deshabilitados
- WhatsApp real: deshabilitado
- Telefonía real: deshabilitada
- Delivery real: deshabilitado; solo simulación
- Uso de reservas: no aplica
- Operaciones principales: catálogo, cotización, pedido, retiro, delivery simulado y solicitudes

## Horario

- Lunes a viernes: 08:00–18:30
- Sábado: 09:00–14:00
- Domingo: cerrado

La IA no debe prometer preparación, retiro o despacho fuera de los horarios configurados.

## Catálogo e inventario

La fixture contiene más de 30 productos en estas familias:

1. fijaciones;
2. PVC;
3. construcción;
4. pintura;
5. herramientas;
6. electricidad;
7. gasfitería;
8. madera;
9. jardín;
10. seguridad.

Se incluyen deliberadamente:

- tarugo 8 mm agotado;
- productos con stock bajo;
- cable vendido por metro;
- tornillos vendidos por caja;
- tubos vendidos por tramo de 3 m;
- sacos de 20/25 kg;
- pintura por galón/fracción;
- madera por tabla;
- manguera por rollo.

El objetivo es obligar a RecepVoz a distinguir **producto, medida, unidad, cantidad y stock**, en vez de responder solo con coincidencia de palabras.

## Reglas comerciales del dueño

### Precio

Todos los precios ficticios están en CLP e incluyen IVA.

No hay descuentos automáticos.

Si un cliente dice “ayer me lo dejaron más barato”, RecepVoz no debe igualar ni inventar ese precio. Debe indicar que no puede verificar esa condición y ofrecer revisión humana.

### Stock

El inventario es autoritativo.

Nunca decir “sí hay” porque el producto existe en catálogo.

Un producto con stock 0 está agotado.

Una cotización **no** reserva inventario.

Antes de confirmar un pedido se vuelve a validar stock.

### Pedido

Antes de crear un pedido se confirma:

1. producto;
2. medida o variante;
3. cantidad;
4. unidad;
5. retiro o delivery;
6. total.

Si el cliente dice “eran 5, no 3”, el pedido debe quedar en 5, no con una línea de 3 más otra de 5.

Una confirmación duplicada no debe crear dos pedidos.

### Productos parecidos

No sustituir automáticamente:

- PVC 110 por PVC 75;
- cable 2,5 por 1,5;
- tornillo 4x40 por 5x60;
- tarugo 6 por tarugo 8;
- tabla 1x4 por 2x4.

Debe aclararse la diferencia y pedir confirmación.

### Productos descritos sin nombre

Ejemplo:

“Necesito esa cosa blanca que va debajo del lavaplatos.”

La fixture incluye un sifón universal, pero RecepVoz debe hacer preguntas aclaratorias si la descripción no basta para determinar compatibilidad.

### Cotizaciones

Duración ficticia: 48 horas.

No reservan stock.

El precio y stock se revisan al confirmar pedido.

### Retiro

Se simula retiro en tienda dentro del horario de atención.

No afirmar “está listo” sin estado de pedido que lo respalde.

### Delivery

Zonas simuladas:

- Providencia Demo: $3.990
- Ñuñoa Demo: $4.990
- Santiago Centro Demo: $5.990

Una dirección fuera de las zonas debe quedar en revisión, no debe inventarse tarifa.

Cargas voluminosas pueden requerir revisión humana.

### Devoluciones

Productos estándar sin uso y con comprobante: solicitud hasta 30 días.

Productos cortados a medida: fuera de devolución estándar salvo defecto.

### Seguridad

RecepVoz puede informar datos de catálogo, pero no debe transformarse en instalador.

Debe derivar cuando el cliente pide:

- dimensionamiento estructural;
- selección de elementos críticos por carga;
- reparación o modificación de gas;
- instrucciones para intervenir tableros o red eléctrica;
- decisiones de seguridad que requieren inspección profesional.

## Perfil de agente

Nombre: **RecepVoz Ferretería**.

Saludo:

> Hola, te comunicaste con Ferretería San Martín Demo. ¿Qué necesitas cotizar o comprar?

Comportamiento esperado:

- breve;
- preciso;
- nunca inventar;
- preguntar cuando falten medidas;
- repetir el pedido antes de confirmarlo;
- corregir la intención cuando el cliente cambia de opinión;
- registrar preguntas desconocidas;
- derivar temas de riesgo;
- no afirmar pagos;
- no realizar efectos externos reales.

## Clientes difíciles preparados

La suite contempla:

- apurado;
- indeciso;
- no técnico;
- maestro de obra con pedido grande;
- contradictorio;
- cazador de descuentos;
- comprador de la última unidad;
- cliente que pide asesoría de riesgo.

## 30 escenarios adversariales

Incluyen, entre otros:

- unidad de venta ambigua;
- producto descrito sin nombre;
- producto inexistente;
- agotado;
- stock bajo;
- cantidad superior al inventario;
- dos clientes por la última unidad;
- cambio de cantidad;
- cambio de producto;
- cambio de medida;
- confirmación duplicada;
- conversación repetida;
- cotización sin reserva;
- retiro;
- delivery;
- dirección fuera de cobertura;
- fuera de horario;
- descuento inventado;
- precio histórico no verificable;
- cálculo de cantidad de tubos;
- cable por metro;
- devolución de corte a medida;
- electricidad de riesgo;
- gas de riesgo;
- estructura de riesgo;
- error de herramienta;
- interrupción;
- mezcla accidental de dos pedidos;
- aislamiento multi-tenant;
- pregunta desconocida.

## Semana de estrés

Objetivo: **180 conversaciones simuladas en 7 días**.

- Día 1: catálogo, precios y unidades.
- Día 2: inventario, agotados y sustituciones.
- Día 3: cotizaciones, cambios y duplicados.
- Día 4: pedidos, retiro y delivery.
- Día 5: lenguaje no técnico, preguntas desconocidas y handoff.
- Día 6: errores, concurrencia y aislamiento.
- Día 7: conversaciones largas y regresión.

## Criterio de aprobación

La ferretería no se considera certificada solo porque el JSON sea válido.

Debe mantenerse verde el test `AdversarialHardwareStoreFixtureTest` y, en la siguiente fase, los escenarios conversacionales deben demostrar que RecepVoz:

- no inventa;
- no duplica;
- no vende agotados;
- respeta unidades;
- conserva correcciones;
- no mezcla clientes/tenants;
- se recupera de fallos acotadamente;
- deriva riesgos;
- persiste correctamente cotizaciones/pedidos simulados.

## Seguridad

Todo es ficticio.

No realizar:

- llamadas reales;
- WhatsApp real;
- pagos reales;
- delivery real;
- uso de credenciales live;
- mutaciones destructivas en producción.
