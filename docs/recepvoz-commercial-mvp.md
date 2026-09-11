# RecepVoz — MVP comercial

## Objetivo

Convertir la base técnica existente de Helvoca en un producto SaaS vendible bajo la marca comercial **RecepVoz**, enfocado en negocios que pierden llamadas, reservas, solicitudes o leads por no poder contestar el teléfono.

RecepVoz no se limitará a restaurantes. Debe servir para clínicas, veterinarias, barberías, talleres, centros estéticos, inmobiliarias, servicios técnicos, comercios y otros negocios configurables.

## Propuesta de valor

**Tus llamadas, siempre atendidas.**

RecepVoz atiende llamadas de clientes, responde preguntas con información oficial del negocio, agenda cuando corresponde, registra solicitudes y puede transferir una llamada a una persona.

El cliente no instala software ni mantiene servidores. Usa un panel web y RecepVoz funciona en la nube.

## Cliente ideal inicial

Priorizar negocios donde una llamada perdida tenga un valor económico claro:

1. Clínicas y centros médicos.
2. Dentistas.
3. Veterinarias.
4. Talleres mecánicos.
5. Centros estéticos.
6. Inmobiliarias.
7. Servicios técnicos.
8. Restaurantes con alto volumen de llamadas.

Evitar al principio negocios con muy pocas llamadas o ticket promedio demasiado bajo.

## Oferta comercial inicial

### Piloto fundador

- Precio: **$29.990 CLP / mes**.
- Activación: **$0**.
- 1 negocio.
- 1 número o conexión de llamadas compatible.
- Hasta 100 minutos incluidos como límite comercial inicial sujeto a validación de costos reales.
- Atención telefónica 24/7 según configuración.
- Información del negocio.
- Reservas/citas cuando el negocio las utilice.
- Solicitudes generales.
- Transferencia humana cuando esté configurada.
- Panel de operaciones.
- Transcripciones y trazabilidad.

Objetivo: conseguir los primeros 3 a 5 negocios y medir uso real antes de fijar precios definitivos.

### Precio objetivo posterior

- Starter: **$49.990 CLP / mes**.
- Pro: **$89.990 CLP / mes**.
- Business: **$149.990 CLP / mes**.

Los límites definitivos de minutos, llamadas y consumo IA deben salir del usage metering real. No vender minutos ilimitados antes de conocer costos reales.

## Qué ya existe técnicamente

La base actual ya incluye:

- multi-tenant
- registro y login
- onboarding
- servicios
- horarios
- conocimiento
- consola de negocio
- operaciones
- Twilio Trial
- flujo comercial Twilio Media Streams
- OpenAI Realtime
- transferencia humana
- reservas
- solicitudes generales
- preguntas no respondidas
- transcripciones
- trazabilidad con call_action
- simulador seguro de recepcionista

No reconstruir estas capacidades desde cero.

## Qué falta para cobrar con seguridad

### P0 — validar llamada comercial real

Hacer una llamada PSTN no-Trial completa:

cliente → Twilio → /webhooks/v1/twilio/voice → /ws/twilio → OpenAI Realtime → herramientas RecepVoz → PostgreSQL

Validar:

- audio natural
- interrupciones
- latencia
- herramientas
- reserva/solicitud
- transferencia humana
- persistencia
- transcripción
- call_action
- detalle en Operaciones

### P0 — usage metering

Medir por tenant:

- número de llamadas
- minutos
- duración total
- duración media
- uso OpenAI
- coste estimado IA
- números activos
- consumo incluido
- consumo adicional

### P0 — límites por plan

Añadir planes configurables y límites verificables. Nunca depender solo del frontend para aplicar límites.

### P1 — billing

Primera versión simple:

- registrar plan
- fecha de inicio
- fecha de renovación
- estado ACTIVE / PAST_DUE / SUSPENDED / CANCELLED
- límite mensual

La primera venta puede cobrarse manualmente mientras se valida el producto. Integrar Stripe u otro proveedor cuando exista demanda real y repetible.

### P1 — onboarding comercial

El dueño debe poder llegar a una experiencia cercana a:

> Dime cuál es tu negocio y RecepVoz se configura para atenderlo.

Reducir configuración manual todo lo posible.

### P1 — provisioning telefónico

Retomar el provisioning self-service de Twilio solo cuando la prueba PSTN comercial esté validada.

No comprar números automáticamente sin confirmación explícita.

## Embudo para conseguir los primeros clientes

1. Elegir un nicho inicial.
2. Buscar negocios con teléfono público y señales de alta demanda.
3. Contactar al dueño/administrador.
4. Ofrecer demostración breve.
5. Mostrar una llamada real a RecepVoz.
6. Explicar el problema económico: llamadas perdidas = clientes perdidos.
7. Ofrecer piloto fundador a $29.990 CLP.
8. Configurar el negocio.
9. Medir llamadas atendidas y resultados.
10. Pedir testimonio/caso de éxito si el piloto funciona.

## Mensaje comercial base

> RecepVoz atiende las llamadas de tu negocio cuando tú no puedes. Responde consultas, agenda clientes, registra solicitudes y deriva llamadas importantes. Tus clientes siguen llamando normalmente y tú puedes revisar lo ocurrido desde un panel web.

## Métricas para saber si el negocio funciona

- MRR
- clientes activos
- churn
- minutos por cliente
- coste por cliente
- margen bruto
- llamadas atendidas
- llamadas con resolución útil
- reservas creadas
- solicitudes creadas
- transferencias humanas
- preguntas sin respuesta
- tiempo ahorrado al negocio
- clientes potencialmente recuperados

## Regla de producto

No vender "inteligencia artificial" como fin principal.

Vender el resultado:

**RecepVoz evita que el negocio pierda clientes por no contestar el teléfono.**

## Próximo hito

1. Completar validación PSTN comercial real con Media Streams.
2. Implementar usage metering.
3. Definir límites comerciales con datos reales.
4. Conseguir primer piloto fundador.
5. Cobrar la primera mensualidad.
