# Helvoca — ficha para cerrar y configurar al primer cliente

Usar esta ficha únicamente si el prospecto acepta avanzar a piloto o quiere una configuración aplicada a su negocio.

La guía operativa principal es `docs/FIRST_CUSTOMER_OPERATION.md`.

## 1. Negocio

- Nombre comercial:
- Rubro:
- Ciudad/comuna:
- Sitio web o redes públicas:
- Persona responsable del piloto:
- Canal preferido de contacto:

## 2. Problema que quiere resolver

- ¿Qué ocurre hoy cuando no alcanzan a responder?
- ¿Qué tipo de consultas reciben con mayor frecuencia?
- ¿Qué acción debería lograr RecepVoz después de atender?
- Valor aproximado de una reserva/pedido/lead:
- Volumen aproximado de llamadas diarias:
- Volumen aproximado de WhatsApp diarios:

## 3. Capacidades que necesita

Marcar solo lo necesario para el piloto inicial.

- [ ] Información / FAQ
- [ ] CATALOG
- [ ] BOOKING
- [ ] ORDER
- [ ] QUOTE
- [ ] LEAD
- [ ] REQUEST
- [ ] DELIVERY
- [ ] PICKUP
- [ ] Human handoff
- [ ] Outbound messaging
- [ ] Calendar / meeting sync
- [ ] PAYMENT — **requisito futuro; NO disponible como merchant LIVE en el piloto comercial actual**

Una capacidad disponible en el core no implica que el proveedor externo correspondiente esté activo. Cada canal o integración debe quedar configurado y probado para este tenant antes de prometerlo como parte del piloto.

## 4. Servicios o productos

Por cada servicio/producto relevante:

- Nombre:
- Descripción breve:
- Precio:
- Duración, si corresponde:
- Disponibilidad o restricciones:
- Activo/inactivo:

No cargar precios aproximados. Deben ser confirmados por el negocio.

## 5. Horarios

- Lunes:
- Martes:
- Miércoles:
- Jueves:
- Viernes:
- Sábado:
- Domingo:
- Feriados/excepciones conocidas:

## 6. Preguntas frecuentes

Registrar las preguntas que más se repiten y la respuesta aprobada por el negocio.

1.
2.
3.
4.
5.

## 7. Políticas del negocio

- Reserva:
- Reprogramación:
- Cancelación:
- Atrasos:
- Delivery/pickup si corresponde:
- Cotizaciones si corresponde:
- Pago futuro si corresponde:
- Información que la IA nunca debe afirmar:
- Casos que siempre deben ir a una persona:

## 8. Identidad del agente

- Nombre que usará la recepcionista:
- Idioma:
- Saludo preferido:
- Tono:
- Frases o términos preferidos:
- Frases o afirmaciones prohibidas:

## 9. Canales

### Voz

- Número actual del negocio:
- ¿Se conectará un número existente o se provisionará otro?
- Proveedor previsto:
- Estado de credenciales/configuración:
- Número/persona de transferencia humana:
- Readiness probado: sí / no
- Llamada real de prueba completada: sí / no

No activar voz comercial si el provider Live no está READY para el tenant.

### WhatsApp

- Número de WhatsApp Business:
- Proveedor/canal actual:
- Estado actual del canal:
- Alcance acordado para el piloto:
- Inbound probado: sí / no
- Outbound requerido: sí / no
- Outbound probado: sí / no

La entrega externa debe permanecer deshabilitada hasta que proveedor, credenciales y alcance del tenant estén configurados y certificados. No prometer WhatsApp ilimitado ni campañas masivas como parte automática del piloto.

## 10. Integraciones existentes

- Agenda/calendario actual:
- CRM:
- POS/ERP:
- Ecommerce:
- Proveedor de pagos actual del negocio:
- Otras herramientas importantes:

### Calendar / meeting

- Proveedor requerido:
- Cuenta/conexión disponible:
- Alcance: crear / reagendar / cancelar / enlace de reunión
- Integración probada para el tenant: sí / no

El core de sincronización de calendario existe, pero una integración externa solo forma parte del piloto cuando el proveedor concreto está conectado y probado.

### Merchant payment

- ¿El prospecto lo solicita como requisito futuro?: sí / no
- Proveedor actual del negocio:
- Notas/requisitos:

**Estado comercial actual:** merchant payment LIVE está intencionalmente deshabilitado. El flujo existente es SANDBOX y no debe incluirse en el alcance vendido ni utilizarse para cobrar a clientes finales.

Registrar necesidades futuras aunque todavía no formen parte del piloto. No prometer fechas de integración no comprometidas.

## 11. Plan comercial

- Plan sugerido:
- Precio oficial:
- Minutos incluidos:
- Excedente:
- Alcance WhatsApp acordado:
- Integraciones incluidas:
- Configuración inicial incluida: sí durante lanzamiento
- Fecha objetivo de activación:
- Responsable por parte de Helvoca/RecepVoz:
- Responsable por parte del cliente:
- Medio autorizado para que el cliente pague a RecepVoz:
- Datos de facturación/cobro confirmados: sí / no

Los valores vigentes deben contrastarse con `/api/v1/public/pricing`. Los límites, condiciones, capacidades e integraciones incluidas deben quedar documentados expresamente para cada tenant.

## 12. Checklist antes de aceptar dinero

- [ ] prospecto aceptó un piloto con alcance escrito;
- [ ] plan, precio, minutos/excedentes y condiciones confirmados;
- [ ] responsable del cliente identificado;
- [ ] responsable de RecepVoz identificado;
- [ ] medio autorizado de pago a RecepVoz definido;
- [ ] datos de facturación/cobro definidos;
- [ ] términos y privacidad puestos a disposición;
- [ ] criterio de éxito escrito;
- [ ] canal externo incluido está certificado o queda explícitamente fuera;
- [ ] merchant payment LIVE no fue prometido;
- [ ] no existe un bloqueo P0 conocido para el alcance vendido.

Si un punto aplicable falla, registrar **PILOT pendiente**, no **CUSTOMER**.

## 13. Criterios para activar

No activar hasta cumplir:

- [ ] precios confirmados;
- [ ] servicios/productos confirmados;
- [ ] horarios confirmados;
- [ ] FAQ revisada;
- [ ] capacidades aprobadas;
- [ ] saludo/instrucciones aprobados;
- [ ] límites y alcance del piloto entendidos;
- [ ] pruebas de conversación realizadas;
- [ ] acciones mutantes probadas;
- [ ] handoff humano probado si se ofrece;
- [ ] voz certificada si se habilita;
- [ ] WhatsApp certificado si se habilita;
- [ ] outbound certificado si se habilita;
- [ ] calendario certificado si se habilita;
- [ ] merchant payment LIVE excluido del alcance mientras siga deshabilitado.

## 14. Éxito del piloto

Definir antes de activar qué se medirá y qué umbral se considera suficiente.

Posibles métricas:

- llamadas atendidas;
- conversaciones resueltas;
- reservas creadas;
- reservas reprogramadas;
- pedidos/cotizaciones/leads generados;
- solicitudes;
- oportunidades recuperadas;
- derivaciones humanas;
- preguntas sin respuesta;
- errores de herramientas;
- minutos usados;
- coste de voz/mensajería cuando esté disponible;
- valor estimado generado para el negocio.

No prometer ROI. Medir con datos reales.

Para cerrar el piloto como exitoso, revisar también:

- acción principal funcionando de punta a punta dentro del alcance certificado;
- resultados verificables;
- ausencia de acciones críticas no autorizadas o duplicadas sin resolver;
- fallos dentro del umbral acordado;
- cliente dispuesto a continuar con un plan/alcance definido.
