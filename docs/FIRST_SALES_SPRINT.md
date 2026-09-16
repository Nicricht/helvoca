# Helvoca — First Sales Sprint

## Objetivo

Conseguir los primeros 5 clientes pagadores mediante venta y onboarding asistidos. Durante este sprint, la prioridad de producto es adquisición, activación, calidad del servicio, retención y corrección de bloqueos que impidan cerrar o mantener clientes.

## Estado de salida

Helvoca puede venderse hoy como piloto asistido. V42 Plans / Entitlements / Billing sigue en desarrollo y no debe bloquear prospección, demos ni los primeros pilotos. Hasta su certificación, los límites y condiciones comerciales de cada cliente se documentan durante el onboarding.

Las capacidades externas se ofrecen solo cuando el tenant concreto tiene el proveedor/canal configurado y probado. Que el core soporte una operación no equivale a que una cuenta de Twilio, WhatsApp, calendario o pagos esté activa para todos los clientes.

## Oferta de lanzamiento

- Emprende: $24.990 CLP/mes, 100 minutos de voz incluidos, excedente $149/min.
- Negocio: $39.990 CLP/mes, 250 minutos incluidos, excedente $129/min. Plan recomendado.
- Pro: $69.990 CLP/mes, 500 minutos incluidos, excedente $109/min.
- Enterprise: desde $119.990 CLP/mes, cotización personalizada.

La configuración inicial asistida se incluye durante el lanzamiento. WhatsApp y otras integraciones se habilitan según el alcance acordado; no se venden como uso ilimitado por defecto.

## Propuesta comercial

Helvoca ayuda a evitar que una llamada o mensaje sin responder se convierta en una oportunidad perdida. Usa la información real del negocio y, según las capacidades habilitadas, puede responder consultas y avanzar a una reserva, pedido, cotización, lead, solicitud, delivery, pago o handoff humano.

No vender la tecnología. Vender el resultado: menos oportunidades perdidas, menos interrupciones y acciones concretas realizadas con reglas del negocio.

## Mercado inicial

Helvoca es horizontal. Priorizar negocios donde se combinen:

- consultas repetitivas;
- llamadas o mensajes mientras el personal está ocupado;
- una acción posterior clara y valiosa;
- pérdida visible de oportunidades por falta de respuesta;
- disposición a probar un piloto pequeño.

Ejemplos: odontología, veterinarias, estética, peluquerías, talleres, restaurantes, inmobiliarias, academias, gimnasios, servicios técnicos y servicios profesionales.

## Meta diaria

- Identificar 20 prospectos nuevos.
- Contactar 20 prospectos.
- Conseguir al menos 3 conversaciones reales.
- Intentar agendar al menos 1 demo.
- Registrar resultado y siguiente acción de cada contacto.

## Pipeline

1. NEW — prospecto identificado.
2. CONTACTED — primer contacto realizado.
3. QUALIFIED — confirmó problema real y encaje inicial.
4. DEMO — aceptó ver una demostración.
5. PILOT — aceptó configuración/prueba.
6. CUSTOMER — cliente pagador.
7. CLOSED — no continúa por ahora.

Nunca dejar un prospecto abierto sin `next_action` y `follow_up_date`.

## Mensaje inicial por WhatsApp o DM

Hola. Estoy trabajando con Helvoca, una recepcionista digital para empresas que reciben llamadas o mensajes mientras el equipo está ocupado. Usa la información real del negocio y puede ayudar con acciones como reservas, solicitudes, cotizaciones o pedidos según la configuración. Estoy incorporando los primeros negocios con configuración asistida. ¿Te puedo mostrar una demo corta aplicada a tu negocio?

## Mensaje inicial por email

Asunto: Una forma de atender consultas sin interrumpir a tu equipo

Hola,

Estoy trabajando con Helvoca, una recepcionista digital con IA que puede atender consultas usando la información real de cada negocio y avanzar a acciones como reservas, solicitudes, cotizaciones o pedidos según la configuración.

Estoy incorporando los primeros negocios mediante pilotos asistidos. Si te interesa, puedo mostrarte una demo breve aplicada a tu negocio.

Saludos.

## Guion de contacto presencial o llamada

1. Preguntar si reciben llamadas o mensajes mientras están atendiendo clientes o trabajando.
2. Preguntar qué ocurre cuando no pueden responder.
3. Preguntar qué acción suele venir después de responder.
4. Explicar Helvoca en una frase.
5. No explicar arquitectura, proveedores de IA ni detalles técnicos salvo que lo pidan.
6. Ofrecer una demo breve aplicada al negocio.
7. Cerrar una siguiente acción concreta: demo, configuración piloto o creación de cuenta.

## Demo de 5–10 minutos

1. Mostrar `/sales.html`.
2. Conectar con el problema que el prospecto acaba de describir.
3. Mostrar un tenant demo configurado.
4. Hacer una consulta real de información/precio.
5. Ejecutar la acción principal del caso de uso.
6. Corregir un dato para demostrar que la nueva versión reemplaza la anterior.
7. Mostrar el resultado persistido.
8. Mostrar `/pricing.html`.
9. Preguntar si quiere configurar un piloto con sus propios datos.

Si se demuestra voz o WhatsApp real, ese canal debe estar previamente configurado y probado. Nunca improvisar una integración externa durante una reunión comercial.

## Respuestas a objeciones

### Ya uso WhatsApp

Helvoca no busca reemplazar WhatsApp. Automatiza parte de la atención y puede compartir la misma información y operaciones del negocio con otros canales habilitados.

### Ya tengo agenda online

Helvoca no es solo una agenda. Puede conversar antes de la reserva, responder preguntas, ayudar a elegir y ejecutar la acción cuando corresponde.

### Es caro

La entrada parte desde $24.990 CLP al mes. La comparación útil es cuánto vale una oportunidad que hoy se pierde porque nadie alcanzó a responder. No prometer ROI; medirlo durante el piloto.

### No confío en una IA atendiendo clientes

La configuración se prueba antes de operar. Las acciones críticas pasan por servicios y validaciones del backend; la IA no decide por sí sola precios, disponibilidad ni estados operativos.

### Quiero hablar yo con los clientes

Helvoca puede encargarse de consultas repetitivas y dejar intervención humana para los casos donde aporta más valor.

## Cierre recomendado

> ¿Quieres que configuremos una prueba con tus servicios, horarios y reglas para que veas cómo atendería Helvoca a un cliente real de tu negocio?

Si acepta, completar `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md`.

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

- prospectos nuevos;
- contactos realizados;
- conversaciones reales;
- tasa de respuesta;
- demos agendadas;
- demos realizadas;
- pilotos iniciados;
- clientes pagadores;
- ingreso mensual recurrente nuevo;
- principal objeción;
- principal bloqueo de producto reportado.

## Regla de desarrollo durante el sprint

Si una tarea no mejora adquisición, activación, conversión, calidad del servicio, retención o control de costes de los primeros clientes, puede esperar.
