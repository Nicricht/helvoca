# Helvoca — primera venta hoy

> Para ejecutar la venta sin improvisar, usar primero `docs/FIRST_CUSTOMER_OPERATION.md`.

## Objetivo

Helvoca / RecepVoz puede salir a venta asistida. El objetivo de cada reunión es terminar con una acción concreta, en este orden:

1. piloto aceptado con alcance y criterio de éxito;
2. fecha de configuración acordada;
3. segunda demo agendada usando datos reales del negocio.

Un **cliente pagador** solo existe cuando el pago fue realmente recibido por un medio autorizado. No adelantar ese estado en el tracker.

No considerar suficiente un cierre ambiguo como “mándame información y después vemos”.

## Estado comercial actual

El core incluye multi-tenancy, catálogo, conocimiento, reservas, operaciones universales, Policy Engine, Safe Retry, handoff humano durable, mensajería, confirmaciones, jobs persistentes, calendario provider-neutral, observabilidad, RLS PostgreSQL, metering de uso y V42 Plans / Entitlements / Billing.

Regla: que una capacidad exista en el core no significa que un proveedor externo esté activo para todos los tenants. Voz, WhatsApp, outbound y calendario solo se ofrecen como activos cuando la integración concreta del cliente está configurada y probada.

**Merchant payments LIVE no forman parte de la oferta comercial actual.** El adaptador disponible opera en SANDBOX y el modo LIVE está intencionalmente deshabilitado.

## Qué vender

No vender “un chatbot” ni explicar arquitectura salvo que el prospecto lo pida.

Mensaje central:

> RecepVoz ayuda a que una llamada o un mensaje sin responder no se convierta en una oportunidad perdida. Atiende usando la información real del negocio y, según la configuración, puede transformar la conversación en una reserva, pedido, cotización, solicitud o siguiente paso concreto.

La promesa comercial inicial es un piloto asistido con alcance definido y medible.

## Pitch de 20–30 segundos

> RecepVoz es una recepcionista digital con IA para empresas. Atiende usando la información real del negocio y puede hacer cosas concretas como reservar, cotizar, registrar solicitudes o tomar pedidos. Configuramos el primer piloto con tus datos, lo probamos contigo y solo activamos los canales que estén certificados para tu negocio.

## Preguntas de descubrimiento

1. ¿Qué ocurre cuando entra una llamada o mensaje y el equipo está ocupado?
2. ¿Qué preguntan los clientes una y otra vez?
3. ¿Qué acción hacen normalmente después de responder: reservar, cotizar, tomar un pedido, registrar datos o derivar a alguien?
4. ¿Cuánto vale aproximadamente una reserva, pedido o cliente promedio?
5. ¿Qué parte de esa atención les quita más tiempo actualmente?

No convertir la reunión en un interrogatorio. Identificar el dolor principal y adaptar la demo.

## Demo principal de 5–10 minutos

La demo por defecto es web/simulador. No improvisar proveedores externos.

1. Mostrar `/sales.html`.
2. Mostrar el tenant demo ya configurado.
3. Hacer una consulta real de servicio, producto, precio o información.
4. Pedir disponibilidad o la acción equivalente del negocio.
5. Ejecutar una acción permitida por el backend.
6. Corregir un dato o reprogramar para demostrar que se actualiza y no se duplica.
7. Mostrar el resultado persistido.
8. Hacer una pregunta desconocida y demostrar que no inventa.
9. Mostrar `/pricing.html`.
10. Cerrar con una acción concreta.

## Escenarios de demo

### Negocios con agenda

Ejemplos: odontología, veterinaria, peluquería, estética, academia, gimnasio y atención administrativa.

- consultar servicio;
- consultar precio/duración;
- consultar disponibilidad;
- crear reserva;
- reagendar;
- comprobar que no se duplica;
- cancelar si sirve para la demo.

Capacidades: CATALOG + BOOKING + REQUEST.

### Talleres y servicios profesionales

- consultar servicios;
- responder información oficial;
- registrar necesidad;
- crear cotización o solicitud;
- reservar revisión o visita si corresponde.

Capacidades: CATALOG + QUOTE + BOOKING + REQUEST.

### Restaurantes y comercios

- consultar catálogo;
- elegir productos;
- crear/corregir un pedido;
- mostrar total calculado por backend;
- explicar pickup/delivery si está habilitado.

Capacidades: CATALOG + ORDER + DELIVERY/PICKUP.

**No demostrar merchant payment como cobro real. El flujo disponible es SANDBOX.**

### Inmobiliarias y negocios orientados a leads

- consultar información disponible;
- recopilar necesidad;
- registrar lead;
- solicitar visita o seguimiento;
- derivar a una persona cuando corresponda.

Capacidades: CATALOG + LEAD + BOOKING + REQUEST.

## Qué sí existe pero requiere activación concreta

- continuidad Voice / WhatsApp: demostrarla solo con canales e identidad del tenant configurados y probados;
- outbound messaging: la entrega externa depende de configuración/proveedor;
- calendario y reuniones: una integración externa solo se promete cuando el proveedor del tenant esté conectado y certificado;
- voz: solo ofrecer demo telefónica real cuando readiness y proveedor Live estén operativos.

## Qué NO prometer

- WhatsApp ilimitado;
- campañas masivas sin alcance contratado y proveedor adecuado;
- cero errores de IA;
- SLA enterprise no contratado;
- disponibilidad 24/7 garantizada;
- funciones no habilitadas para ese tenant;
- integraciones externas no conectadas;
- merchant payment LIVE;
- onboarding 100% automático mientras el provisionamiento comercial siga siendo asistido;
- ROI garantizado.

## Oferta de lanzamiento

Catálogo vigente respaldado por V42:

- Emprende: $24.990 CLP/mes, 100 minutos incluidos, excedente $149/min.
- Negocio: $39.990 CLP/mes, 250 minutos incluidos, excedente $129/min.
- Pro: $69.990 CLP/mes, 500 minutos incluidos, excedente $109/min.
- Enterprise: desde $119.990 CLP/mes, sujeto a cotización.

Durante el lanzamiento, la configuración inicial asistida está incluida.

No inventar descuentos durante una reunión. Cualquier condición especial debe quedar documentada.

## Cómo explicar el precio

No defender el precio hablando de servidores, modelos o tokens.

Preguntar cuánto vale una oportunidad promedio y comparar el plan contra llamadas, reservas o ventas que el negocio confirme que hoy se pierden. Presentarlo como hipótesis de valor, no como retorno garantizado.

## Cierre

> Puedo configurarlo con sus servicios, horarios y forma de atender para que lo prueben directamente con su negocio. La configuración inicial está incluida y el plan parte desde $24.990 al mes. ¿Definimos el alcance del piloto?

Si responde “mándame información”:

> Claro. Antes de irme, ¿qué información necesitarías ver para decidir si hacemos una prueba?

Si responde “lo voy a pensar”:

> Perfecto. ¿Qué parte necesitas evaluar antes de decidir: precio, confianza en la atención, integración o utilidad para el negocio?

El objetivo es descubrir la objeción real y acordar una siguiente acción, no presionar.

## Checklist antes de una demo comercial

- [ ] `/sales.html` y `/pricing.html` revisados;
- [ ] precios contrastados con `/api/v1/public/pricing`;
- [ ] tenant demo disponible;
- [ ] servicios/productos demo revisados;
- [ ] precios demo confirmados;
- [ ] horarios demo revisados;
- [ ] Knowledge/FAQ demo revisado;
- [ ] agente activo y saludo revisado;
- [ ] capacidades demo habilitadas;
- [ ] consulta, acción, corrección y caso desconocido ensayados;
- [ ] si se muestra voz real, readiness y llamada real comprobados;
- [ ] si se muestra WhatsApp real, flujo del alcance acordado comprobado.

No marcar producción o un proveedor como READY por documentación histórica. Verificar el estado actual antes de cada piloto real.

## Antes de aceptar dinero

Usar el checklist completo de `docs/FIRST_CUSTOMER_OPERATION.md`. Como mínimo deben estar definidos: alcance, plan/precio, responsables, canal certificado o explícitamente excluido, criterio de éxito, medio autorizado de pago a RecepVoz y ausencia de un bloqueo P0 conocido.

## Cinco conversaciones obligatorias para la demo

1. consulta simple de información;
2. consulta de precio/servicio;
3. acción principal del rubro;
4. cambio de opinión/corrección;
5. pregunta que RecepVoz no sabe y debe manejar sin inventar.

## Si acepta el piloto

Completar `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md` antes de activar canales reales. Definir alcance, datos oficiales, capacidades, responsables, fecha y criterios de éxito.

## Después de cada reunión

Registrar en `docs/FIRST_PROSPECTS_TRACKER.csv`:

- empresa y contacto;
- problema principal confirmado;
- capacidad que más valoró;
- objeción principal;
- plan o piloto discutido;
- siguiente acción;
- fecha acordada;
- cambios solicitados.

No mover una fila por expectativa. Cada estado debe reflejar un hecho real.
