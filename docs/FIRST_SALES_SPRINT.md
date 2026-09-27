# Helvoca — First Sales Sprint

> Operación diaria: `docs/FIRST_CUSTOMER_OPERATION.md`.

## Objetivo

Conseguir los primeros clientes pagadores mediante venta y onboarding asistidos. Durante este sprint, la prioridad de producto es adquisición, activación, calidad del servicio, retención y corrección de bloqueos que impidan cerrar o mantener clientes.

## Estado de salida

Helvoca / RecepVoz se vende como piloto asistido. V42 Plans / Entitlements / Billing respalda planes, límites y consumo.

Las capacidades externas se ofrecen solo cuando el tenant concreto tiene el proveedor/canal configurado y probado. Que el core soporte una operación no equivale a que una cuenta de Twilio, WhatsApp o calendario esté activa para todos los clientes.

**Merchant payment LIVE no está disponible para venta:** el adaptador actual se mantiene en SANDBOX y el modo LIVE está intencionalmente deshabilitado.

## Oferta de lanzamiento

- Emprende: $24.990 CLP/mes, 100 minutos de voz incluidos, excedente $149/min.
- Negocio: $39.990 CLP/mes, 250 minutos incluidos, excedente $129/min. Plan recomendado.
- Pro: $69.990 CLP/mes, 500 minutos incluidos, excedente $109/min.
- Enterprise: desde $119.990 CLP/mes, cotización personalizada.

La configuración inicial asistida se incluye durante el lanzamiento. WhatsApp y otras integraciones se habilitan según alcance acordado y certificación del tenant; no se venden como uso ilimitado por defecto.

## Propuesta comercial

RecepVoz ayuda a evitar que una llamada o mensaje sin responder se convierta en una oportunidad perdida. Usa la información real del negocio y, según las capacidades habilitadas, puede responder consultas y avanzar a una reserva, pedido, cotización, lead, solicitud, delivery/pickup o handoff humano.

No vender la tecnología. Vender el resultado verificable: atención, menos interrupciones y acciones concretas realizadas con reglas del negocio.

## Mercado inicial

Priorizar negocios donde se combinen:

- consultas repetitivas;
- llamadas o mensajes mientras el personal está ocupado;
- una acción posterior clara y valiosa;
- pérdida visible de oportunidades confirmada por el negocio;
- disposición a probar un piloto pequeño y medible.

Ejemplos: odontología, veterinarias, estética, peluquerías, talleres, restaurantes, inmobiliarias, academias, gimnasios, servicios técnicos y servicios profesionales.

## Meta diaria

Trabajar primero el tracker existente. Prioridad:

1. `follow_up_date` vencida o de hoy;
2. `CONTACTED`;
3. `NEW`.

Después:

- realizar contactos reales;
- conseguir conversaciones reales;
- intentar agendar demos;
- registrar resultado y siguiente acción de cada contacto.

No inventar prospectos, reuniones ni estados para cumplir una meta de actividad.

## Pipeline

1. **NEW** — prospecto identificado; todavía no hubo contacto real.
2. **CONTACTED** — primer contacto realmente realizado.
3. **QUALIFIED** — confirmó problema real, encaje y siguiente paso.
4. **DEMO** — aceptó ver una demo o ya la realizó.
5. **PILOT** — aceptó configurar/prueba con alcance y criterio de éxito definidos.
6. **CUSTOMER** — pago realmente recibido por un medio autorizado y servicio acordado activado.
7. **CLOSED** — no continúa por ahora; registrar motivo.

Nunca dejar un prospecto abierto sin `next_action` y `follow_up_date`. Nunca usar `follow_up_date` para fingir una reunión o seguimiento que el prospecto no aceptó.

Después de cada reunión, completar también `highest_interest_capability`, `main_objection`, `plan_or_pilot_discussed` y `requested_changes`.

## Mensaje inicial por WhatsApp o DM

Hola. Estoy incorporando los primeros negocios a RecepVoz, una recepcionista digital con IA que usa la información real del negocio para responder consultas y avanzar a acciones como reservas, solicitudes, cotizaciones o pedidos. La configuración inicial es asistida. ¿Te puedo mostrar una demo corta aplicada a tu negocio?

## Mensaje inicial por email

Asunto: Una forma de atender consultas sin interrumpir a tu equipo

Hola,

Estoy incorporando los primeros negocios a RecepVoz, una recepcionista digital con IA que usa la información real de cada negocio y puede avanzar a acciones como reservas, solicitudes, cotizaciones o pedidos según la configuración.

La configuración inicial es asistida. Si te interesa, puedo mostrarte una demo breve aplicada a tu negocio.

Saludos.

## Guion de contacto presencial o llamada

1. Preguntar si reciben llamadas o mensajes mientras están atendiendo clientes o trabajando.
2. Preguntar qué ocurre cuando no pueden responder.
3. Preguntar qué acción suele venir después de responder.
4. Explicar RecepVoz en una frase.
5. No explicar arquitectura, proveedores de IA ni detalles técnicos salvo que lo pidan.
6. Ofrecer una demo breve aplicada al negocio.
7. Cerrar una siguiente acción concreta: demo o definición de piloto.

## Demo de 5–10 minutos

1. Mostrar `/sales.html`.
2. Conectar con el problema que el prospecto acaba de describir.
3. Mostrar un tenant demo configurado.
4. Hacer una consulta real de información/precio.
5. Ejecutar la acción principal del caso de uso.
6. Corregir un dato para demostrar que la nueva versión reemplaza a la anterior.
7. Mostrar el resultado persistido.
8. Mostrar un caso desconocido manejado sin inventar.
9. Mostrar `/pricing.html`.
10. Preguntar si quiere definir un piloto con sus propios datos.

Si se demuestra voz o WhatsApp real, ese canal debe estar previamente configurado y probado. Nunca improvisar una integración externa durante una reunión comercial. No demostrar merchant payment LIVE.

## Respuestas a objeciones

### Ya uso WhatsApp

RecepVoz no necesita reemplazar WhatsApp. Si ese canal se incluye, se define un alcance concreto y se activa solo cuando esté configurado y certificado para el tenant. No se ofrece como uso ilimitado.

### Ya tengo agenda online

RecepVoz no es solo una agenda. Puede conversar antes de la reserva, responder preguntas, ayudar a elegir y ejecutar la acción cuando corresponde.

### Es caro

La entrada parte desde $24.990 CLP al mes. La comparación útil es cuánto vale una oportunidad que el negocio confirme que hoy se pierde. No prometer ROI; medirlo durante el piloto.

### No confío en una IA atendiendo clientes

La configuración se prueba antes de operar. Las acciones críticas pasan por servicios y validaciones del backend; la IA no decide por sí sola precios, disponibilidad ni estados operativos.

### Quiero hablar yo con los clientes

RecepVoz puede encargarse de consultas repetitivas y dejar intervención humana para los casos donde aporta más valor.

### ¿Funciona 24/7?

No ofrecer SLA ni disponibilidad garantizada. Explicar el alcance contratado y que los canales externos dependen también de sus proveedores.

### ¿Puede cobrar a mis clientes?

Merchant payment LIVE no está disponible actualmente. El flujo existente es SANDBOX y no se vende como cobro real.

## Cierre recomendado

> ¿Quieres que configuremos una prueba con tus servicios, horarios y reglas y dejemos por escrito qué vamos a medir?

Si acepta, completar `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md` y el checklist de `docs/FIRST_CUSTOMER_OPERATION.md` antes de cobrar o activar.

## Registro mínimo de prospectos

Usar `docs/FIRST_PROSPECTS_TRACKER.csv` y mantener como mínimo:

- negocio;
- rubro;
- ciudad/comuna;
- canal de contacto público;
- fecha del primer contacto;
- estado del pipeline;
- problema principal;
- capacidad de mayor interés;
- plan/piloto discutido;
- siguiente acción;
- fecha de seguimiento;
- motivo de cierre si no continúa.

No almacenar información personal innecesaria ni datos sensibles.

## Métricas semanales

- prospectos nuevos reales;
- contactos realizados;
- conversaciones reales;
- tasa de respuesta;
- demos agendadas;
- demos realizadas;
- pilotos aceptados;
- pilotos activados;
- clientes pagadores;
- ingreso mensual recurrente nuevo;
- principal objeción;
- principal bloqueo de producto reportado.

## Regla de desarrollo durante el sprint

Si una tarea no mejora adquisición, activación, conversión, calidad del servicio, retención o control de costes de los primeros clientes, puede esperar.
