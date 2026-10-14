# Helvoca — ficha para cerrar y configurar al primer cliente

Usar esta ficha únicamente si el prospecto acepta avanzar a piloto o quiere una configuración aplicada a su negocio.

La guía operativa principal es `docs/FIRST_CUSTOMER_OPERATION.md`.

Esta ficha corresponde a **clientes reales**. No reutilizar los datos ficticios de `docs/COMMERCIAL_DEMO_ONBOARDING.md` como configuración de producción. El tenant demo existe solo para demos y pruebas sin proveedores reales.

## 1. Negocio

- Nombre comercial:
- Rubro / preset de referencia:
- Descripción pública aprobada:
- Dirección:
- Comuna:
- Ciudad:
- Región:
- País:
- Teléfono público:
- Email público:
- Sitio web o redes públicas:
- Moneda principal:
- ¿Vende productos?: sí / no
- ¿Vende servicios?: sí / no
- ¿Usa reservas?: sí / no
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

En el producto actual, las políticas explicativas pueden almacenarse en Knowledge/FAQ. No asumir que un plazo escrito en texto queda automáticamente aplicado como regla transaccional. La reserva sí valida en backend servicio activo, fecha futura, horario del negocio y solapamientos. Si el piloto requiere un plazo automático de cancelación/reprogramación u otra regla estructurada que todavía no esté configurada, debe quedar marcada como limitación del alcance en vez de prometerse como automatización.

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

## 12 bis. Puerta C6-A: privacidad, IA y operación comercial (obligatoria antes de un piloto real)

**Estado de esta sección al 2026-10-14: CHECKLIST PREPARATORIA, NO APROBACIÓN LEGAL.** No llenar datos personales, contratos firmados ni pruebas privadas en GitHub. Custodiar el expediente de aceptación fuera del repositorio y registrar aquí solo una referencia segura. La página pública `/privacy.html`, los `/terms.html` y `/data-deletion.html` **existen en el código**; su existencia no prueba aceptación del cliente, conformidad legal, disponibilidad en producción ni procesamiento efectivo de solicitudes.

**Marco a revisar:** [Ley 19.628 vigente hasta el 30-11-2026](https://www.bcn.cl/leychile/Navegar?dt=open&idLey=19628); [Ley 21.719 con vigencia desde el 01-12-2026](https://www.bcn.cl/leychile/Navegar?i=1209272), según Biblioteca del Congreso Nacional. La aplicabilidad de bases de licitud, información, derechos, transferencias y obligaciones especiales debe determinarse con revisión jurídica del servicio y de cada piloto. No asumir que un check técnico reemplaza asesoría o aceptación formal.

### Identidades y acuerdos verificables

- Responsable contractual por RecepVoz (identidad y contacto **verificados, en expediente privado**):
- Responsable contractual por el negocio (identidad/contacto **verificados, en expediente privado**):
- Roles de tratamiento (quién decide finalidades/medios, quién procesa por cuenta ajena): **PENDIENTE DE DEFINICIÓN CONTRACTUAL** hasta evaluación de los flujos concretos.
- Acuerdo de tratamiento de datos, confidencialidad, subencargados/proveedores y transferencias pertinentes: pendiente / revisado / aprobado (referencia no sensible):
- Encargado de incidentes y contacto alternativo verificables (referencia privada):
- Canal de derechos de titulares, verificación de identidad, escalamiento, plazos aplicables y constancia de resolución (referencia privada):
- Decisor autorizado de PAUSE/STOP/rollback y destinatario humano de derivaciones:
- Identidad legal del prestador, datos de facturación e impuestos: verificados / pendientes:

### Pruebas de aceptación que NO se presumen

- [ ] Responsable del negocio revisó y aprobó el alcance de los datos que entregará, base jurídica aplicable y destinatarios; se distingue el uso de datos del negocio del uso por RecepVoz.
- [ ] Aviso al interlocutor de que interactúa con una IA, su finalidad y alternativa de atención humana revisados para el canal y jurisdicción del piloto; texto, momento y evidencia de aceptación o base habilitante autorizados.
- [ ] Si existieran grabaciones o almacenamiento de audio, se documentó separadamente su fundamento legal, información/consentimiento cuando corresponda y política de retención. **No inferir que el producto actual graba llamadas.**
- [ ] Procedimiento para ejercer derechos, corregir, suprimir o restringir datos verificado operacionalmente; sin asumir que la política de retención equivale a una eliminación automática ya desplegada. Véase `docs/DATA_RETENTION_POLICY.md`.
- [ ] Política pública de privacidad, términos y eliminación revisados con responsable jurídico/contractual, accesibles desde el frontend en entorno objetivo y con datos de contacto/identidad válidos.
- [ ] Proveedores, transferencias, controles de acceso, registros sin PII innecesaria y responsabilidades de incidentes evaluados por canal; cualquier proveedor sin certificación permanece fuera del piloto.
- [ ] Plan mensual, impuestos/comisiones, minutos, excedentes, límites de concurrencia, suspensión, cancelación, reembolsos o controversias y vía de soporte definidos, publicados/aceptados según corresponda. El catálogo API manda sobre precios.
- [ ] Contactos y dueños de severidad P0/P1 probados; prueba de PAUSE y rollback en entorno autorizado con evidencias. Véase `docs/FIRST_CUSTOMER_ROLLBACK_SUPPORT.md`.
- [ ] Coste unitario/margen medido con tarifas y factura o consumo representativos, con presupuesto y límite/alarma autorizados; **NO se promete rentabilidad**.
- [ ] Responsable contractual de RecepVoz y, si corresponde, asesor jurídico, autorizaron expresamente el paquete de documentos y el piloto específico.

**Regla de salida:** si cualquiera de las condiciones aplicables está pendiente, el piloto con tráfico o clientes reales sigue `C6=NO-GO`. Es admisible trabajar en demo offline, QA y documentación; **no se habilitan llamadas, mensajes, cobros ni producción** por completar esta ficha.

## 13. Criterios para activar

No activar hasta cumplir:

- [ ] perfil público del negocio confirmado (descripción y ubicación cuando correspondan);
- [ ] modalidad confirmada: productos / servicios / reservas;
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
