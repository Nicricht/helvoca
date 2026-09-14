# Helvoca — lanzamiento comercial controlado

## Objetivo inmediato

Conseguir los primeros 3–5 clientes pagadores usando onboarding asistido, medir conversaciones reales y corregir problemas antes de escalar adquisición.

Helvoca se vende como **recepción digital con IA para teléfono y WhatsApp**, no como un chatbot genérico. El valor comercial es atender consultas, usar información real del negocio y convertir conversaciones en reservas, solicitudes o derivaciones.

## Qué podemos vender hoy

### Voz

- Atención telefónica con IA.
- Identidad y configuración por negocio.
- Catálogo de servicios y precios.
- Horarios y disponibilidad.
- Registro e identificación de clientes.
- Crear, consultar, reprogramar y cancelar reservas.
- Base de conocimiento del negocio.
- Registro de preguntas no resueltas.
- Solicitudes y transferencia humana cuando corresponda.
- Cierre remoto de llamadas por la IA cuando existe intención clara de terminar.
- Resúmenes, trazabilidad y panel de operaciones.

### WhatsApp — piloto controlado

- Webhook Twilio validado.
- Idempotencia por MessageSid para evitar repetir acciones por reintentos del proveedor.
- Cliente compartido con voz mediante número telefónico dentro del tenant.
- Catálogo, conocimiento, horarios y reservas compartidos con voz.
- Crear, consultar, reprogramar y cancelar reservas.
- Perfil AiAgent compartido: identidad, idioma e instrucciones del negocio.
- Herramientas publicadas al modelo filtradas por capacidades del agente del tenant.

## Qué NO debemos prometer todavía

- WhatsApp ilimitado incluido en cualquier plan.
- Campañas masivas o marketing outbound por WhatsApp.
- Provisioning 100% autoservicio de WhatsApp por cliente.
- SLA empresarial formal.
- Cero errores de IA.
- Superioridad frente a competidores sin evidencia.

## Oferta para primeros clientes

Vender como **piloto comercial pagado con configuración asistida**.

Catálogo público de lanzamiento:

| Plan | Precio mensual | Voz incluida | Excedente voz | Enfoque |
| --- | ---: | ---: | ---: | --- |
| Emprende | $24.990 CLP | 100 min | $149/min | Independientes y negocios pequeños |
| Negocio | $39.990 CLP | 250 min | $129/min | Plan recomendado para la mayoría de pymes |
| Pro | $69.990 CLP | 500 min | $109/min | Mayor volumen y concurrencia |
| Enterprise | Desde $119.990 CLP | Según cotización | Según cotización | Volumen, sedes o integraciones especiales |

Los valores vigentes que ve el producto se obtienen desde `/api/v1/public/pricing`. No ofrecer precios distintos sin modificar primero el catálogo oficial o dejar por escrito que se trata de un acuerdo comercial especial.

La configuración asistida se incluye durante la etapa inicial para reducir fricción comercial. Para WhatsApp durante el piloto, acordar límites de uso y alcance de forma explícita hasta que exista metering de mensajes y coste por conversación.

## Perfil de cliente inicial recomendado

Helvoca es horizontal y puede configurarse para cualquier negocio donde existan consultas, reservas, citas, solicitudes o atención repetitiva. Esto incluye, entre otros, odontología, centros médicos y clínicas cuando el alcance sea administrativo, además de estética, talleres, restaurantes, inmobiliarias, servicios profesionales, veterinarias, academias y comercios.

Priorizar prospectos que:

1. reciben llamadas o mensajes mientras el equipo está ocupado;
2. tienen catálogo o información relativamente clara;
3. trabajan con citas, reservas o solicitudes;
4. repiten muchas respuestas durante el día;
5. pueden medir el valor de una reserva o lead recuperado.

## Demo comercial de 5 minutos

1. Mostrar `/sales.html` y explicar el problema que resuelve Helvoca.
2. Abrir una empresa demo o una configuración preparada para el prospecto.
3. Mostrar servicios, horarios, conocimiento y perfil del agente.
4. Simular una consulta por precio/servicio.
5. Consultar disponibilidad.
6. Crear una reserva.
7. Cambiar la hora y demostrar que se reprograma, no se duplica.
8. Mostrar una pregunta que el sistema no conoce y cómo la registra sin inventar.
9. Mostrar que teléfono y WhatsApp comparten cliente y reservas.
10. Mostrar operaciones/resumen y explicar el plan mensual.

## Datos que pedir al cerrar un piloto

- Nombre legal/comercial y nombre que debe usar la recepcionista.
- Persona responsable del piloto.
- Servicios/productos activos.
- Precio y duración cuando corresponda.
- Horarios normales y excepciones conocidas.
- Preguntas frecuentes.
- Políticas de reserva, cancelación y atraso.
- Promociones vigentes que la IA sí puede mencionar.
- Afirmaciones que la IA nunca debe realizar.
- Teléfono de transferencia humana.
- Número/canal de telefonía y WhatsApp a conectar.
- Tono deseado, idioma y saludo.
- Herramientas/capacidades que se habilitarán.

## Checklist antes de activar un cliente real

- [ ] Tenant creado y acceso del administrador confirmado.
- [ ] Servicios revisados por el cliente.
- [ ] Precios revisados por el cliente.
- [ ] Horarios revisados por el cliente.
- [ ] Knowledge/FAQ revisado por el cliente.
- [ ] Saludo e instrucciones del agente aprobados.
- [ ] Capacidades del agente revisadas.
- [ ] Número telefónico asociado al tenant correcto.
- [ ] Transferencia humana configurada o decisión explícita de operar sin ella.
- [ ] Simulador probado con al menos: consulta, venta, disponibilidad, reserva, reagendamiento, cancelación, desconocido y solicitud humana.
- [ ] Llamada real certificada si se habilita voz.
- [ ] Flujo real de WhatsApp certificado si se habilita WhatsApp.
- [ ] Plan/suscripción habilitado.
- [ ] El cliente conoce el alcance del piloto y sus límites.

## Bloqueadores antes de escalar más allá de pilotos

### P0 comercial

- Metering de WhatsApp por tenant (mensajes/conversaciones y coste estimado).
- Estado de provisioning de WhatsApp por número/tenant, no solo flag global.
- Certificación E2E real de WhatsApp: consulta → reserva → reagendamiento → cancelación → humano.
- Política y flujo operativo de takeover humano en WhatsApp.

### P0 legal/privacidad

- Identificar la entidad que comercializa Helvoca y datos de contacto legales.
- Términos de servicio revisados.
- Política de privacidad revisada.
- Definir retención/eliminación de conversaciones y grabaciones.
- Definir tratamiento de datos entre Helvoca y cada negocio.

### P1 escala

- Pruebas de carga y concurrencia.
- Límites por plan para mensajes y llamadas concurrentes.
- Alertas de coste y consumo.
- Failover y degradación controlada cuando un proveedor de IA/telefonía no responde.

## Métricas de los primeros clientes

No medir solo cantidad de conversaciones. Registrar como mínimo:

- llamadas y chats atendidos;
- conversaciones resueltas;
- reservas creadas;
- reservas reprogramadas;
- solicitudes/leads generados;
- derivaciones humanas;
- preguntas sin respuesta;
- tasa de error de herramientas;
- duración/coste de voz;
- mensajes/coste estimado de WhatsApp cuando el metering esté disponible;
- ingreso o valor estimado atribuido a acciones recuperadas.

## Regla de producto durante el lanzamiento

Priorizar solamente trabajo que mejore adquisición, activación, calidad de atención, conversión, retención, seguridad o coste operativo de los primeros clientes.
