# Helvoca — primera venta mañana

## Objetivo

Salir de la reunión con una acción concreta, en este orden de preferencia:

1. cliente pagador;
2. piloto aceptado con fecha de activación;
3. segunda demo acordada usando datos reales del negocio.

No considerar suficiente un cierre ambiguo como “mándame información y después vemos”.

## Regla de alcance

Durante la preparación comercial se congela temporalmente el desarrollo de nuevas capacidades grandes. No se elimina ni desactiva nada existente. V32 Omnichannel Core sigue siendo el siguiente gran bloque técnico, pero no se implementa antes de la primera reunión si no mejora directamente la probabilidad de cerrar.

No tocar ni degradar:

- Voice;
- WhatsApp existente;
- ORDER;
- BOOKING;
- QUOTE;
- LEAD;
- REQUEST;
- DELIVERY;
- PAYMENT;
- Policy Engine;
- Event Log;
- Safe Retry Engine.

## Qué vender

No vender “un chatbot” ni explicar arquitectura.

Mensaje central:

> Helvoca ayuda a que una llamada o un WhatsApp sin responder no se convierta en un cliente perdido. Atiende usando la información real del negocio y, según la configuración, puede transformar la conversación en una reserva, pedido, cotización, solicitud o siguiente paso concreto.

## Preguntas de descubrimiento antes de mostrar la demo

1. ¿Qué ocurre cuando entra una llamada o WhatsApp y el equipo está ocupado?
2. ¿Qué preguntan los clientes una y otra vez?
3. ¿Qué acciones hacen normalmente después de responder: reservar, cotizar, pedir datos, tomar un pedido o derivar a alguien?
4. ¿Cuánto vale aproximadamente una reserva, pedido o cliente promedio?
5. ¿Qué parte de esa atención les quita más tiempo actualmente?

No interrogar durante demasiado tiempo. El objetivo es descubrir el dolor principal y adaptar la demo a él.

## Demo principal de 5–10 minutos

### Apertura

Decir en una frase qué resuelve Helvoca y conectar con el problema que el prospecto acaba de describir.

### Secuencia

1. Mostrar la landing comercial.
2. Mostrar el negocio demo configurado.
3. Hacer una consulta real de servicio/producto/precio.
4. Pedir disponibilidad o una acción equivalente del rubro.
5. Ejecutar una acción real permitida por el backend.
6. Hacer una corrección del cliente para demostrar que la información nueva reemplaza la anterior cuando corresponde.
7. Mostrar el resultado en el sistema.
8. Mostrar planes.
9. Cerrar con una acción concreta.

## Escenarios por tipo de negocio

### Negocios con agenda

Ejemplos: clínica administrativa, odontología, veterinaria, peluquería, estética, academia, gimnasio.

Demo:

- preguntar por un servicio;
- preguntar precio/duración si están configurados;
- consultar disponibilidad;
- crear reserva;
- cambiar hora;
- demostrar que se modifica la reserva y no se duplica;
- cancelar si conviene demostrarlo.

Capacidades principales: CATALOG + BOOKING + REQUEST + PAYMENT cuando esté configurado.

### Talleres y servicios profesionales

Demo:

- consultar un servicio;
- explicar información oficial;
- registrar necesidad del cliente;
- generar cotización o solicitud según alcance configurado;
- agendar revisión/visita si corresponde.

Capacidades principales: CATALOG + QUOTE + BOOKING + REQUEST.

### Restaurantes y comercios

Demo:

- consultar catálogo;
- elegir productos;
- crear pedido;
- corregir cantidad o selección;
- mostrar total calculado por backend;
- explicar delivery/pickup si la capacidad está habilitada;
- mostrar PAYMENT solo si el proveedor del tenant está configurado.

Capacidades principales: CATALOG + ORDER + DELIVERY/PICKUP + PAYMENT.

### Inmobiliarias y negocios orientados a leads

Demo:

- consultar propiedad/servicio disponible;
- recopilar necesidad real del cliente;
- registrar lead;
- solicitar visita o seguimiento;
- derivar si el caso requiere persona.

Capacidades principales: CATALOG + LEAD + VISIT/BOOKING + REQUEST.

## Qué NO prometer mañana

- continuidad perfecta Voice → WhatsApp como una única sesión antes de V32;
- envío outbound automático de links por WhatsApp desde una llamada hasta implementar el Outbound Messaging Engine;
- videollamada automática Google Meet/Zoom/Teams hasta integrar MeetingProvider;
- WhatsApp ilimitado;
- campañas masivas;
- cero errores de IA;
- SLA enterprise;
- funciones específicas no configuradas para ese tenant;
- pago real si el tenant no tiene proveedor merchant configurado.

Sí se puede explicar que estas piezas forman parte del roadmap cuando corresponda, diferenciando claramente “ya disponible” de “próximo”.

## Oferta de lanzamiento

Usar el catálogo oficial vigente:

- Emprende: $24.990 CLP/mes, 100 minutos incluidos, excedente $149/min.
- Negocio: $39.990 CLP/mes, 250 minutos incluidos, excedente $129/min.
- Pro: $69.990 CLP/mes, 500 minutos incluidos, excedente $109/min.
- Enterprise: desde $119.990 CLP/mes, sujeto a cotización.

Durante el lanzamiento, la configuración inicial asistida está incluida.

No inventar descuentos durante la reunión. Si se desea una condición especial para el primer cliente, dejarla por escrito y actualizar la oferta oficial antes de publicarla como precio general.

## Forma de explicar el precio

No defender el precio hablando de servidores, modelos o minutos.

Preguntar cuánto vale un cliente promedio y comparar contra oportunidades recuperadas.

Ejemplo:

> Si una sola reserva que hoy se pierde vale más que el plan mensual, recuperar una de esas oportunidades ya puede justificar el piloto.

No prometer un retorno que todavía no esté medido.

## Cierre recomendado

Cierre principal:

> Puedo configurarlo con sus servicios, horarios y forma de atender para que lo prueben directamente con su negocio. La configuración inicial está incluida y el plan parte desde $24.990 al mes. ¿Avanzamos con un piloto?

Si responde “mándame información”:

> Claro. Antes de irme, ¿qué información necesitarías ver para decidir si hacemos una prueba?

Si responde “lo voy a pensar”:

> Perfecto. ¿Qué parte necesitas evaluar antes de tomar la decisión: precio, confianza en la atención, integración o utilidad para el negocio?

El objetivo no es presionar. Es descubrir la objeción real y acordar una siguiente acción concreta.

## Objeciones frecuentes

### “Ya usamos WhatsApp”

Helvoca no busca reemplazar WhatsApp. Automatiza parte de la atención y comparte la misma información del negocio con otros canales habilitados.

### “Ya tenemos agenda online”

Helvoca no es solo una agenda. Puede conversar antes de la reserva, responder preguntas y ejecutar la acción cuando corresponde.

### “Es caro”

La entrada parte desde $24.990 al mes. Comparar contra el valor de una oportunidad que hoy se pierde cuando nadie responde.

### “No confío en una IA atendiendo”

Se configura y prueba antes de activar. Los datos y acciones críticas dependen de herramientas y validaciones del backend, no de que el modelo invente resultados.

### “Quiero seguir hablando con mis clientes”

Helvoca puede encargarse de consultas repetitivas y dejar intervención humana para los casos donde aporta más valor.

## Checklist técnico antes de salir a la reunión

- [ ] Production healthcheck verde.
- [ ] CI de main verde.
- [ ] Landing `/sales.html` abre correctamente.
- [ ] `/pricing.html` abre y carga los precios oficiales.
- [ ] Tenant demo activo.
- [ ] Servicios/productos demo revisados.
- [ ] Precios demo revisados.
- [ ] Horarios demo revisados.
- [ ] Knowledge/FAQ demo revisado.
- [ ] Agente activo y saludo revisado.
- [ ] Capacidades demo habilitadas correctamente.
- [ ] Cinco conversaciones de demo probadas.
- [ ] Al menos un flujo con corrección del usuario probado.
- [ ] Flujo de error/desconocido probado sin inventar información.
- [ ] Si se muestra voz real, llamada certificada.
- [ ] Si se muestra WhatsApp real, flujo certificado para el alcance demostrado.

## Cinco conversaciones que deben estar ensayadas

1. consulta simple de información;
2. consulta de precio/servicio;
3. acción principal del rubro;
4. cambio de opinión/corrección;
5. pregunta que Helvoca no sabe y debe manejar sin inventar.

## Después de la reunión

Registrar inmediatamente:

- problema principal detectado;
- capacidad que más valoró;
- objeción principal;
- plan sugerido;
- siguiente acción;
- fecha acordada;
- cambios de producto solicitados.

La primera venta debe alimentar el roadmap. Una necesidad expresada por un cliente que paga pesa más que una feature imaginada sin evidencia.
