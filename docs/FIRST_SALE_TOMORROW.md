# Helvoca — primera venta hoy

## Objetivo

Helvoca ya puede salir a venta asistida. El objetivo de cada reunión es terminar con una acción concreta, en este orden:

1. piloto pagado;
2. piloto aceptado con fecha de configuración;
3. segunda demo agendada usando datos reales del negocio.

No considerar suficiente un cierre ambiguo como “mándame información y después vemos”.

## Estado comercial actual

La producción estable incluye el core multi-tenant, catálogo, conocimiento, reservas, operaciones universales, Policy Engine, Safe Retry, handoff humano durable, omnicanalidad, mensajería saliente, confirmaciones, jobs persistentes, calendario provider-neutral, observabilidad, RLS PostgreSQL y metering de uso.

V42 Plans / Entitlements / Billing sigue en desarrollo y no debe bloquear la primera venta. Hasta cerrar V42, los primeros clientes se incorporan mediante onboarding y configuración asistidos.

Regla: que una capacidad exista en el core no significa que un proveedor externo esté activo para todos los tenants. Voz, WhatsApp, outbound, calendario y pagos solo se ofrecen como activos cuando la integración concreta del cliente está configurada y probada.

## Qué vender

No vender “un chatbot” ni explicar arquitectura salvo que el prospecto lo pida.

Mensaje central:

> Helvoca ayuda a que una llamada o un WhatsApp sin responder no se convierta en un cliente perdido. Atiende usando la información real del negocio y, según la configuración, puede transformar la conversación en una reserva, pedido, cotización, solicitud o siguiente paso concreto.

La promesa comercial inicial es un piloto asistido con alcance definido y medible.

## Pitch de 30 segundos

> Helvoca es una recepcionista digital para empresas. Atiende llamadas y canales habilitados usando la información real del negocio y puede hacer cosas concretas como reservar, cotizar, registrar solicitudes o tomar pedidos. Nosotros configuramos el primer piloto con tus datos y lo probamos contigo antes de activarlo.

## Preguntas de descubrimiento

1. ¿Qué ocurre cuando entra una llamada o WhatsApp y el equipo está ocupado?
2. ¿Qué preguntan los clientes una y otra vez?
3. ¿Qué acción hacen normalmente después de responder: reservar, cotizar, tomar un pedido, registrar datos o derivar a alguien?
4. ¿Cuánto vale aproximadamente una reserva, pedido o cliente promedio?
5. ¿Qué parte de esa atención les quita más tiempo actualmente?

No convertir la reunión en un interrogatorio. Identificar el dolor principal y adaptar la demo.

## Demo principal de 5–10 minutos

1. Mostrar `/sales.html`.
2. Mostrar el tenant demo ya configurado.
3. Hacer una consulta real de servicio, producto, precio o información.
4. Pedir disponibilidad o la acción equivalente del negocio.
5. Ejecutar una acción permitida por el backend.
6. Corregir un dato para demostrar que la versión nueva reemplaza a la anterior cuando corresponde.
7. Mostrar el resultado persistido en el sistema.
8. Mostrar `/pricing.html`.
9. Cerrar con una acción concreta.

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

Capacidades: CATALOG + BOOKING + REQUEST. PAYMENT solo cuando el proveedor del tenant esté configurado.

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
- explicar pickup/delivery si está habilitado;
- mostrar PAYMENT solo con integración merchant activa.

Capacidades: CATALOG + ORDER + DELIVERY/PICKUP + PAYMENT cuando corresponda.

### Inmobiliarias y negocios orientados a leads

- consultar información disponible;
- recopilar necesidad;
- registrar lead;
- solicitar visita o seguimiento;
- derivar a una persona cuando corresponda.

Capacidades: CATALOG + LEAD + BOOKING + REQUEST.

## Qué sí existe pero requiere activación concreta

- continuidad omnicanal Voice / WhatsApp: el core existe, pero solo demostrarla con canales e identidad del tenant configurados y probados;
- outbound messaging: el motor durable existe, pero la entrega externa permanece controlada por configuración/proveedor;
- calendario y reuniones: el core de sincronización existe, pero una integración externa solo se promete cuando el proveedor del tenant esté conectado y certificado;
- pagos: la operación y los adapters existen, pero el cobro real depende de la cuenta merchant y configuración del tenant;
- voz: solo ofrecer demo telefónica real cuando readiness y proveedor Live estén operativos.

## Qué NO prometer

- WhatsApp ilimitado;
- campañas masivas sin alcance contratado y proveedor adecuado;
- cero errores de IA;
- SLA enterprise no contratado;
- funciones no habilitadas para ese tenant;
- integraciones externas no conectadas;
- pago real sin merchant configurado;
- onboarding 100% automático mientras V42 y el provisionamiento comercial sigan cerrándose;
- ROI garantizado.

## Oferta de lanzamiento

Catálogo vigente mientras V42 no lo sustituya formalmente:

- Emprende: $24.990 CLP/mes, 100 minutos incluidos, excedente $149/min.
- Negocio: $39.990 CLP/mes, 250 minutos incluidos, excedente $129/min.
- Pro: $69.990 CLP/mes, 500 minutos incluidos, excedente $109/min.
- Enterprise: desde $119.990 CLP/mes, sujeto a cotización.

Durante el lanzamiento, la configuración inicial asistida está incluida.

No inventar descuentos durante una reunión. Cualquier condición especial debe quedar documentada.

## Cómo explicar el precio

No defender el precio hablando de servidores, modelos o tokens.

Preguntar cuánto vale una oportunidad promedio y comparar el plan contra llamadas, reservas o ventas que hoy se pierden. Presentarlo como hipótesis de valor, no como retorno garantizado.

## Cierre

> Puedo configurarlo con sus servicios, horarios y forma de atender para que lo prueben directamente con su negocio. La configuración inicial está incluida y el plan parte desde $24.990 al mes. ¿Avanzamos con un piloto?

Si responde “mándame información”:

> Claro. Antes de irme, ¿qué información necesitarías ver para decidir si hacemos una prueba?

Si responde “lo voy a pensar”:

> Perfecto. ¿Qué parte necesitas evaluar antes de decidir: precio, confianza en la atención, integración o utilidad para el negocio?

El objetivo es descubrir la objeción real y acordar una siguiente acción, no presionar.

## Checklist técnico antes de salir

- [ ] producción Railway en SUCCESS;
- [ ] `main` corresponde al SHA desplegado;
- [ ] landing `/sales.html` disponible;
- [ ] `/pricing.html` disponible y precios revisados;
- [ ] tenant demo activo;
- [ ] servicios/productos demo revisados;
- [ ] precios demo confirmados;
- [ ] horarios demo revisados;
- [ ] Knowledge/FAQ demo revisado;
- [ ] agente activo y saludo revisado;
- [ ] capacidades demo habilitadas;
- [ ] cinco conversaciones ensayadas;
- [ ] corrección de datos ensayada;
- [ ] caso desconocido ensayado sin inventar;
- [ ] si se muestra voz real, readiness y llamada real comprobados;
- [ ] si se muestra WhatsApp real, flujo del alcance acordado comprobado.

## Cinco conversaciones obligatorias para la demo

1. consulta simple de información;
2. consulta de precio/servicio;
3. acción principal del rubro;
4. cambio de opinión/corrección;
5. pregunta que Helvoca no sabe y debe manejar sin inventar.

## Si acepta el piloto

Completar `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md` antes de activar canales reales. Definir alcance, datos oficiales, capacidades, responsables, fecha y criterios de éxito.

## Después de cada reunión

Registrar:

- empresa y contacto;
- problema principal;
- capacidad que más valoró;
- objeción principal;
- plan o piloto discutido;
- siguiente acción;
- fecha acordada;
- cambios solicitados.

La primera venta debe alimentar el roadmap. Una necesidad repetida por prospectos reales pesa más que una feature imaginada sin evidencia.
