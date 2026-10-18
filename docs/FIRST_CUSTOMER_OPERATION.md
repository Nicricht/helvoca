# OPERACIÓN PRIMER CLIENTE RECEPVOZ

> Documento operativo de una página. Objetivo: abrirlo, seguirlo de arriba abajo y saber qué decir, qué mostrar, qué registrar y cuándo avanzar.

## Regla maestra

Embudo único:

```text
PROSPECTO / NEW -> CONTACTADO -> CALIFICADO -> DEMO -> PILOTO -> CLIENTE
                                               \-> CLOSED
```

No avanzar estados por intención. Avanzar solo por hechos reales y registrarlos en `docs/FIRST_PROSPECTS_TRACKER.csv`.

- **NEW:** prospecto identificado, todavía sin contacto real.
- **CONTACTED:** hubo contacto real.
- **QUALIFIED:** confirmó un problema que RecepVoz puede resolver y existe un siguiente paso concreto.
- **DEMO:** aceptó ver una demo o ya la realizó.
- **PILOT:** aceptó configurar un piloto con alcance, responsables y criterio de éxito definidos.
- **CUSTOMER:** pago efectivamente recibido por un medio autorizado y servicio acordado activado.
- **CLOSED:** no continúa por ahora; registrar motivo.

## 1. Pitch de 20–30 segundos

> RecepVoz es una recepcionista digital con IA para negocios. Atiende usando la información real de la empresa y puede convertir una conversación en una acción concreta, por ejemplo una reserva, solicitud, cotización, pedido o derivación. El primer cliente entra con un piloto asistido: configuramos sus datos, probamos el alcance con él y solo activamos los canales que estén certificados para ese negocio.

## 2. Mensaje inicial

> Hola. Estoy incorporando los primeros negocios a RecepVoz, una recepcionista digital con IA que usa la información real del negocio para responder consultas y avanzar a acciones como reservas, solicitudes, cotizaciones o pedidos. La configuración inicial es asistida. ¿Te puedo mostrar una demo corta aplicada a tu negocio?

CTA único: conseguir permiso para una demo. No vender por chat, no mandar una pared de texto y no afirmar que el prospecto pierde clientes si todavía no lo confirmó.

## 3. Demo segura de 5–10 minutos

Demo por defecto: **web/simulador**, sin proveedores reales.

1. Mostrar `/sales.html`.
2. Abrir un tenant demo ya configurado.
3. Hacer una consulta de servicio/producto, precio u horario.
4. Consultar disponibilidad cuando corresponda.
5. Ejecutar la acción principal del rubro: reserva, pedido, cotización, lead o solicitud.
6. Corregir/reprogramar y demostrar que el sistema actualiza en vez de duplicar.
7. Hacer una pregunta desconocida y mostrar que no inventa una respuesta.
8. Mostrar el resultado persistido / actividad.
9. Mostrar `/pricing.html`.
10. Cerrar: “¿Quieres que lo configuremos con tus datos y definamos un piloto?”

**Voz o WhatsApp real solo se muestran si ese tenant/canal está previamente configurado y certificado. Merchant payments LIVE no se demuestran ni se venden: el producto mantiene ese modo intencionalmente deshabilitado y solo dispone del flujo sandbox.**

## 4. Objeciones frecuentes

**“Ya tengo agenda online.”**  
RecepVoz no sustituye necesariamente la agenda. Puede atender la conversación previa, responder preguntas y ejecutar la reserva cuando la capacidad esté habilitada.

**“Ya uso WhatsApp.”**  
WhatsApp no se ofrece como ilimitado. Si se incluye en el piloto, se define un alcance concreto y se activa solo después de certificar el canal del tenant.

**“No confío en una IA atendiendo clientes.”**  
Primero se configura con datos aprobados por el negocio y se prueba antes de activar. Precios, disponibilidad y acciones operativas dependen del backend y de reglas configuradas.

**“Es caro.”**  
Los planes parten en $24.990 CLP/mes. La comparación útil es contra el valor de oportunidades reales que el negocio confirme durante el piloto. No prometer ROI.

**“¿Funciona siempre / 24/7?”**  
No ofrecer SLA ni disponibilidad garantizada. Explicar el alcance contratado y que los canales externos dependen también de sus proveedores.

**“¿Puede cobrar a mis clientes?”**  
No como capacidad comercial LIVE actualmente. El merchant payment disponible es sandbox y no debe presentarse como cobro real.

## 5. Precios

El catálogo público/backend vigente es:

| Plan | Mensual | Voz incluida | Excedente |
| --- | ---: | ---: | ---: |
| Emprende | $24.990 CLP | 100 min | $149/min |
| Negocio | $39.990 CLP | 250 min | $129/min |
| Pro | $69.990 CLP | 500 min | $109/min |
| Enterprise | Desde $119.990 CLP | Según cotización | Según cotización |

Fuente operativa: `/api/v1/public/pricing`. Si cambia el backend, manda el backend. No inventar descuentos, minutos o condiciones.

Explicación corta:

> Partimos desde $24.990 al mes. El plan define minutos y límites de voz; la configuración inicial está incluida durante esta etapa. WhatsApp e integraciones externas se cotizan o acuerdan por alcance cuando estén disponibles y certificadas para tu tenant.

## 5 bis. Puerta C6-A: unit economics y coste operativo verificable

**Objetivo:** impedir que un plan de precio fijo se venda con márgenes, cargos o límites inventados. Esta es una **plantilla de auditoría**, no un cálculo certificado de rentabilidad. No sustituye facturas ni tarifas reales. Los precios/minutos del cuadro anterior son valores documentados y deben contrastarse nuevamente con `/api/v1/public/pricing` al contratar; Enterprise requiere cotización individual.

Para **cada plan y tenant**, preparar un registro privado sin credenciales ni datos personales con estas entradas:

| Entrada necesaria | Fuente exigida antes de aprobar un piloto real |
| --- | --- |
| Precio mensual efectivamente cobrable en CLP e impuestos aplicables | Catálogo API vigente + condiciones de facturación aprobadas; distinguir precio publicado de ingreso neto |
| Minutos incluidos, concurrencia máxima y excedente por minuto | Catálogo/entitlements del backend y contrato aceptado |
| Consumo estimado y consumo medido de telefonía, por tipo de llamada | Tarifas oficiales vigentes de Twilio para número/ruta real + medición del piloto; separar número fijo, minutos y traspasos |
| Consumo de Gemini Live, entrada/salida, modelo y cuotas de la cuenta | Tarifa oficial vigente del **modelo realmente configurado**, prueba de consumo/coste de cuenta, fecha y moneda; nunca asumir que el modelo puede usarse por aparecer en documentación |
| Infraestructura por tenant, backups/observabilidad, soporte e incidentes | Coste facturado o asignación justificada de Railway/almacenamiento y horas de operación |
| Comisiones de cobro, conversión USD/CLP, impuestos y reembolsos | Contrato y liquidaciones verificables de medios de pago, tipo de cambio con fecha y criterio tributario revisado |
| Riesgo de excedentes, abuso, reintentos y fallos de proveedor | Escenarios de uso máximo, límites técnicos medidos, alertas y responsable de STOP |

**Cálculo sin números ficticios:** `ingreso_neto_CLP = cobro_real_CLP - tributos_no_reconocidos_como_ingreso - descuentos/reembolsos aplicables`; `coste_total_CLP = telefonía + IA + infraestructura_asignada + comisiones + soporte + reserva_operativa`; `margen_estimado_CLP = ingreso_neto_CLP - coste_total_CLP`. Evaluar **uso promedio y peor caso permitido** por plan, incluidos excedentes y concurrencia. Guardar fecha, origen y criterio de cada tarifa; no mezclar USD y CLP sin tipo de cambio fechado.

**Puerta NO-GO:** si faltan tarifas aplicables, límites verificables de consumo/coste, canal de soporte, mecanismo de suspensión o aceptación comercial/tributaria, no declarar margen positivo, SLA ni capacidad comercial lista. Con un piloto web aislado sin voz, marcar telefonía/IA real **fuera de alcance**, no cero coste supuesto de una prestación ofrecida. El titular valida costes/condiciones y un responsable operativo documenta alertas, presupuesto y acciones de PAUSE; ningún cálculo autoriza gastos, llamadas o cobros reales.

## 6. Oferta de piloto

> Configuramos un piloto con tus servicios, horarios, preguntas frecuentes y reglas. Definimos por escrito qué canal y qué acción principal vamos a probar, qué queda fuera y cómo mediremos el resultado. Lo probamos contigo antes de activar. El piloto utiliza uno de los planes públicos vigentes o una condición especial documentada.

Nunca ofrecer como parte automática del piloto: WhatsApp ilimitado, SLA enterprise, cero errores, merchant payment LIVE, campañas masivas, integraciones externas no certificadas o funcionalidades futuras.

## 7. Checklist antes de aceptar dinero

**NO aceptar dinero hasta que todos los puntos aplicables estén en verde:**

- [ ] prospecto está al menos en **PILOT**, no solo interesado;
- [ ] alcance del piloto escrito: canal, capacidades, límites, exclusiones y fecha;
- [ ] plan, precio, minutos/excedentes y cualquier condición especial aceptados;
- [ ] responsable del negocio y responsable de RecepVoz identificados;
- [ ] datos oficiales del negocio disponibles para configurar;
- [ ] canal externo incluido tiene readiness/certificación del tenant, o queda explícitamente fuera hasta certificar;
- [ ] merchant payment LIVE no se ofreció como función del producto;
- [ ] medio autorizado para que el cliente pague a RecepVoz y datos de facturación/cobro están definidos;
- [ ] términos y privacidad vigentes fueron puestos a disposición del cliente **y revisados/aceptados por los responsables autorizados**, sin dar por aprobadas las políticas solo porque existen enlaces;
- [ ] ficha `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md`, sección **12 bis C6-A**, completada en expediente privado para roles de tratamiento, información sobre IA, base jurídica, derechos de datos, contactos de incidentes y aprobación humana/jurídica;
- [ ] se acordaron por escrito cancelación del servicio, condiciones de cobro, impuestos, excedentes, soporte y reclamaciones aplicables;
- [ ] criterio de éxito y fecha de revisión quedaron escritos;
- [ ] no existe un bloqueo técnico P0 conocido que impida el alcance vendido.

Si uno falla, convertir el cierre en **piloto aceptado pendiente de activación/cobro**, no en cliente pagador.

## 8. Onboarding después del pago

1. Completar `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md`.
2. Crear/configurar el tenant sin editar producción manualmente cuando exista flujo soportado.
3. Cargar solo datos confirmados: servicios, precios, horarios, FAQ y políticas.
4. Configurar identidad/saludo y capacidades mínimas del piloto.
5. Probar consulta, acción principal, corrección, caso desconocido y handoff si aplica.
6. Certificar por separado cada canal externo incluido.
7. Obtener aprobación del cliente sobre datos y comportamiento.
8. Activar únicamente el alcance aprobado.
9. Registrar fecha de inicio y medición inicial.

## 9. Seguimiento

- **Después de cada contacto:** actualizar estado, objeción, `next_action` y `follow_up_date`.
- **Después de la demo:** registrar capacidad de mayor interés, objeción principal, cambios pedidos y decisión.
- **Durante el piloto:** revisar actividad y fallos; no esperar al final para descubrir un bloqueo.
- **Cierre del piloto:** comparar resultados contra el criterio definido antes de activar y acordar continuar, ajustar alcance o cerrar.

Prioridad diaria del tracker: primero filas con `follow_up_date` vencida o para hoy; después `CONTACTED`; después `NEW`. No inventar prospectos para llenar actividad.

## 10. Criterio para declarar piloto exitoso

Definir los valores concretos antes de activar. El piloto se declara exitoso solo si:

- el canal y la acción principal acordados funcionaron de punta a punta en el alcance certificado;
- existe volumen real suficiente para evaluar el piloto, según el mínimo acordado con el cliente;
- los resultados principales quedan registrados y pueden ser verificados;
- no hubo acciones críticas no autorizadas o duplicadas sin resolver;
- los errores/bloqueos observados están dentro del umbral acordado;
- la métrica de negocio elegida antes de empezar alcanzó el objetivo acordado;
- el cliente confirma que desea continuar con un plan/alcance definido.

Ejemplos de métricas a elegir, no de promesas: llamadas atendidas, conversaciones resueltas, reservas creadas/reprogramadas, pedidos/cotizaciones/leads, solicitudes, derivaciones humanas, preguntas sin respuesta, errores de herramientas, minutos usados y valor estimado generado.

---

### Lo que se vende hoy

Core comercial: información/FAQ, catálogo, reservas/disponibilidad, pedidos, cotizaciones, leads, solicitudes, delivery/pickup, handoff humano, trazabilidad, metering y planes/límites.

### Lo que exige certificación por tenant

Voz real, WhatsApp, outbound messaging y calendario externo.

### Lo que NO está disponible como merchant LIVE

Cobros a clientes finales mediante merchant payment del producto. El modo LIVE está intencionalmente deshabilitado; sandbox no es una promesa comercial.

### Siguiente acción

Abrir `docs/FIRST_PROSPECTS_TRACKER.csv`, trabajar los prospectos existentes y mover cada fila únicamente cuando ocurra el hecho correspondiente.
