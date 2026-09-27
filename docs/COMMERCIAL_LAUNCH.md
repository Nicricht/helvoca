# Helvoca — lanzamiento comercial controlado

> Runbook operativo: `docs/FIRST_CUSTOMER_OPERATION.md`.

## Objetivo inmediato

Conseguir los primeros clientes pagadores usando onboarding asistido, medir conversaciones reales y corregir problemas antes de escalar adquisición.

RecepVoz / Helvoca se vende como **recepción digital con IA**. La promesa no es “IA mágica”: es atender con información real del negocio y avanzar a una acción permitida y verificable.

## Qué podemos vender hoy

### Core comercial

- información del negocio y FAQ;
- catálogo de servicios/productos;
- horarios y disponibilidad;
- reservas: crear, consultar, reprogramar y cancelar;
- pedidos;
- cotizaciones;
- leads;
- solicitudes;
- delivery/pickup cuando corresponda;
- registro/identificación de clientes;
- preguntas no resueltas;
- handoff humano;
- resúmenes, trazabilidad y panel operativo;
- planes, límites y metering de voz.

### Voz

La capacidad existe, pero una demo o piloto con telefonía real exige readiness y proveedor Live probado para ese tenant. No improvisar llamadas comerciales ni presentar voz como activa sin esa verificación.

### WhatsApp — alcance controlado

El core de mensajería/continuidad existe. La entrega real depende de proveedor, credenciales e identidad del tenant.

No ofrecer WhatsApp ilimitado ni campañas masivas como parte automática de un plan.

### Merchant payment

**No vender como capacidad LIVE.** El adaptador actual opera en SANDBOX y el modo LIVE está intencionalmente deshabilitado. Puede registrarse como requisito futuro, no como función incluida en el piloto actual.

## Qué NO debemos prometer

- WhatsApp ilimitado incluido en cualquier plan;
- campañas masivas o marketing outbound por WhatsApp;
- provisioning 100% autoservicio de WhatsApp por cliente;
- SLA empresarial formal no contratado;
- disponibilidad 24/7 garantizada;
- cero errores de IA;
- merchant payment LIVE;
- integraciones externas no certificadas;
- ROI garantizado;
- superioridad frente a competidores sin evidencia.

## Oferta para primeros clientes

Vender como **piloto comercial pagado con configuración asistida y alcance escrito**.

Catálogo público de lanzamiento:

| Plan | Precio mensual | Voz incluida | Excedente voz | Enfoque |
| --- | ---: | ---: | ---: | --- |
| Emprende | $24.990 CLP | 100 min | $149/min | Independientes y negocios pequeños |
| Negocio | $39.990 CLP | 250 min | $129/min | Plan recomendado para la mayoría de pymes |
| Pro | $69.990 CLP | 500 min | $109/min | Mayor volumen y concurrencia |
| Enterprise | Desde $119.990 CLP | Según cotización | Según cotización | Volumen, sedes o integraciones especiales |

Los valores vigentes que ve el producto se obtienen desde `/api/v1/public/pricing`. No ofrecer precios distintos sin modificar primero el catálogo oficial o documentar expresamente un acuerdo comercial especial.

La configuración asistida se incluye durante la etapa inicial. Para WhatsApp, acordar límites y alcance de forma explícita hasta que el metering comercial correspondiente esté disponible.

## Perfil de cliente inicial recomendado

Priorizar negocios que:

1. reciben llamadas o mensajes mientras el equipo está ocupado;
2. tienen catálogo o información relativamente clara;
3. trabajan con citas, reservas, pedidos, cotizaciones, leads o solicitudes;
4. repiten muchas respuestas durante el día;
5. pueden medir el valor de una acción recuperada;
6. aceptan empezar con un alcance pequeño.

Ejemplos: odontología, estética, veterinarias, peluquerías, talleres, restaurantes, inmobiliarias, academias, comercios y servicios profesionales.

## Pitch

> RecepVoz es una recepcionista digital con IA para negocios. Atiende usando la información real de la empresa y puede convertir una conversación en una acción concreta, por ejemplo una reserva, solicitud, cotización, pedido o derivación. El primer cliente entra con un piloto asistido: configuramos sus datos, lo probamos con él y solo activamos los canales certificados para ese negocio.

## Demo comercial de 5–10 minutos

Demo por defecto: web/simulador.

1. Mostrar `/sales.html`.
2. Abrir una empresa demo ya configurada.
3. Mostrar servicios, horarios, conocimiento y perfil del agente.
4. Simular una consulta por precio/servicio.
5. Consultar disponibilidad cuando corresponda.
6. Crear la acción principal del caso.
7. Cambiar/corregir un dato y demostrar que no se duplica.
8. Mostrar una pregunta desconocida y cómo la registra/maneja sin inventar.
9. Mostrar operaciones/resumen.
10. Mostrar `/pricing.html`.
11. Cerrar definiendo un piloto.

Solo mostrar voz o WhatsApp real si el canal del tenant ya está configurado y probado. No mostrar merchant payment como cobro LIVE.

## Datos que pedir al cerrar un piloto

- nombre legal/comercial y nombre que debe usar la recepcionista;
- persona responsable del piloto;
- servicios/productos activos;
- precio y duración cuando corresponda;
- horarios normales y excepciones conocidas;
- preguntas frecuentes;
- políticas de reserva, cancelación y atraso;
- promociones vigentes que la IA sí puede mencionar;
- afirmaciones que la IA nunca debe realizar;
- teléfono/persona de transferencia humana;
- canal(es) que realmente se incluirán;
- tono deseado, idioma y saludo;
- herramientas/capacidades que se habilitarán;
- plan/precio;
- medio autorizado para que el cliente pague a RecepVoz;
- criterio de éxito y fecha de revisión.

## Checklist antes de aceptar dinero

No aceptar dinero hasta que:

- [ ] el prospecto haya aceptado un piloto con alcance escrito;
- [ ] plan, precio, límites y condiciones estén confirmados;
- [ ] canal e integración vendidos estén certificados para el tenant o expresamente excluidos;
- [ ] merchant payment LIVE no forme parte de lo prometido;
- [ ] responsables de ambas partes estén definidos;
- [ ] datos mínimos del negocio estén disponibles;
- [ ] medio de pago a RecepVoz y datos de facturación/cobro estén definidos;
- [ ] términos y privacidad estén disponibles para el cliente;
- [ ] criterio de éxito esté escrito;
- [ ] no exista un bloqueo P0 conocido para el alcance.

## Checklist antes de activar un cliente real

- [ ] tenant creado y acceso del administrador confirmado;
- [ ] servicios revisados por el cliente;
- [ ] precios revisados por el cliente;
- [ ] horarios revisados por el cliente;
- [ ] knowledge/FAQ revisado por el cliente;
- [ ] saludo e instrucciones del agente aprobados;
- [ ] capacidades del agente revisadas;
- [ ] transferencia humana configurada o decisión explícita de operar sin ella;
- [ ] simulador probado con consulta, acción principal, corrección y caso desconocido;
- [ ] llamada real certificada si se habilita voz;
- [ ] flujo real de WhatsApp certificado si se habilita WhatsApp;
- [ ] plan/suscripción habilitado;
- [ ] cliente conoce alcance, límites y exclusiones;
- [ ] merchant payment LIVE excluido mientras siga deshabilitado.

## Bloqueadores antes de escalar más allá de pilotos

### P0 comercial

- metering de WhatsApp por tenant;
- provisioning repetible de WhatsApp;
- certificación E2E real del alcance WhatsApp;
- política y flujo operativo de takeover humano;
- runbooks repetibles para activación de canales externos.

### P0 legal/privacidad

Antes de escalar más allá de pilotos:

- definir claramente entidad que comercializa/factura y datos de contacto legales;
- revisar términos de servicio;
- revisar política de privacidad;
- definir retención/eliminación de conversaciones y grabaciones;
- definir tratamiento de datos entre RecepVoz y cada negocio.

### P1 escala

- pruebas de carga y concurrencia;
- límites por plan para mensajes y llamadas concurrentes;
- alertas de coste y consumo;
- failover/degradación controlada ante fallos de proveedores.

## Métricas de los primeros clientes

Registrar como mínimo:

- llamadas/chats atendidos;
- conversaciones resueltas;
- reservas creadas/reprogramadas/canceladas;
- solicitudes/leads/pedidos/cotizaciones;
- derivaciones humanas;
- preguntas sin respuesta;
- errores de herramientas;
- duración/minutos de voz;
- consumo/coste de WhatsApp cuando exista metering;
- valor estimado atribuido a acciones recuperadas.

## Regla para declarar piloto exitoso

Antes de activar, escribir el objetivo y umbral. Al cerrar:

- la acción principal debe funcionar de punta a punta dentro del alcance certificado;
- debe existir volumen real suficiente para evaluar;
- resultados y fallos deben ser verificables;
- no deben quedar acciones críticas no autorizadas o duplicadas sin resolver;
- la métrica principal debe alcanzar el umbral acordado;
- el cliente debe confirmar continuidad con un plan/alcance definido.

## Regla de producto durante el lanzamiento

Priorizar solamente trabajo que mejore adquisición, activación, calidad de atención, conversión, retención, seguridad o coste operativo de los primeros clientes.
