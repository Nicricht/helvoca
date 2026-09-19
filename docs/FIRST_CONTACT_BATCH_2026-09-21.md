# Helvoca — primer lote de contacto — 2026-09-21

Usar esta hoja únicamente para los cinco prospectos programados el 21 de septiembre de 2026. Todos siguen en `NEW`: enviar o realizar el contacto no debe registrarse hasta que realmente ocurra.

## Regla de contacto

- Personalizar la primera línea.
- No afirmar que el negocio pierde clientes, tiene alto volumen o sufre un problema que todavía no confirmó.
- No prometer voz, WhatsApp, pagos, calendario ni integraciones externas como activas si ese tenant no está configurado y probado.
- CTA único: pedir permiso para mostrar una demo corta aplicada al negocio.
- Si acepta, actualizar `first_contact_date`, `pipeline_status`, `next_action` y `follow_up_date` en `FIRST_PROSPECTS_TRACKER.csv`.
- Si responde con una objeción, registrar `main_objection`.
- Si pide cambios o funciones, registrar `requested_changes`.

## 1. Clínica Dental Los Leones Providencia

**Contacto público verificado:** +56 9 6295 9256  
**Enfoque:** agenda dental + preguntas frecuentes.  
**Mostrar en demo:** consulta de información, disponibilidad, creación de reserva y reprogramación.

**Mensaje inicial**

Hola. Vi que trabajan con atención dental y coordinación de horas en Providencia. Estoy trabajando con Helvoca, una recepcionista digital que usa la información real del negocio para responder consultas y, cuando corresponde, ayudar con reservas. ¿Te puedo mostrar una demo de 5 minutos aplicada a una clínica dental?

**Si responde “sí”**

Perfecto. La demo muestra cómo consultar servicios y horarios, revisar disponibilidad y crear o reprogramar una reserva sin inventar datos del negocio. ¿Qué día y hora te acomoda verla?

## 2. Clínica Dental Carmona y Asociados

**Contacto público verificado:** +56 9 7864 0636  
**Enfoque:** evaluaciones, agenda y FAQ.  
**Mostrar en demo:** preguntas de tratamientos, disponibilidad y reserva.

**Mensaje inicial**

Hola. Vi que Clínica Dental Carmona y Asociados atiende evaluaciones y horas en Providencia. Estoy trabajando con Helvoca, una recepcionista digital que puede responder usando la información real de la clínica y apoyar la coordinación de reservas. ¿Te puedo mostrar una demo corta aplicada a su atención dental?

**Si responde “sí”**

Buenísimo. La idea es mostrarte un flujo simple: una persona pregunta por un servicio, revisamos disponibilidad y Helvoca puede avanzar hasta una reserva con confirmación. ¿Qué momento te acomoda?

## 3. Dr. Ariel Santiago - Odontología Digital

**Contacto público verificado:** +56 9 2745 6075  
**Enfoque:** consultas repetitivas + agenda.  
**Mostrar en demo:** información de servicios, disponibilidad y reserva.  
**Nota:** usar solo el número verificado actual. El número anterior del tracker fue descartado.

**Mensaje inicial**

Hola. Vi la atención de Odontología Digital del Dr. Ariel Santiago en Providencia. Estoy trabajando con Helvoca, una recepcionista digital que puede responder consultas usando datos reales del negocio y ayudar a coordinar horas. ¿Te puedo mostrar una demo breve aplicada a una consulta odontológica?

**Si responde “sí”**

Perfecto. Puedo mostrarte en pocos minutos cómo responde una consulta, valida disponibilidad y crea una reserva dentro de un flujo controlado. ¿Cuándo te acomoda?

## 4. CPH Salud Providencia

**Contacto público verificado:** +56 9 7585 6664  
**Enfoque:** orientación inicial, evaluaciones y agenda.  
**Mostrar en demo:** FAQ, información de servicios y reserva.  
**Límite comercial:** no presentar WhatsApp real como activo para el prospecto; mostrar la capacidad solo como parte del producto configurable.

**Mensaje inicial**

Hola. Vi que CPH Salud coordina evaluaciones y atención en Providencia. Estoy trabajando con Helvoca, una recepcionista digital que puede responder consultas con la información real del centro y ayudar a convertir una conversación en una reserva cuando corresponde. ¿Te puedo mostrar una demo corta aplicada a ese flujo?

**Si responde “sí”**

Claro. La demo puede centrarse en una consulta típica de paciente, revisión de disponibilidad y creación de una reserva. Dura unos minutos. ¿Qué horario te sirve?

## 5. GO Providencia

**Contacto público verificado:** +56 9 6833 9941  
**Enfoque:** consultas de especialidades + primera evaluación.  
**Mostrar en demo:** preguntas sobre servicio/especialidad, disponibilidad y reserva.

**Mensaje inicial**

Hola. Vi que GO Providencia trabaja con distintas atenciones y evaluaciones. Estoy trabajando con Helvoca, una recepcionista digital que responde usando la información configurada del negocio y puede ayudar a coordinar una primera reserva. ¿Te puedo mostrar una demo breve aplicada a GO Providencia?

**Si responde “sí”**

Perfecto. Podemos simular una consulta por una atención, revisar disponibilidad y avanzar a una reserva con confirmación. ¿Qué día y hora te acomoda?

## Después de cada contacto real

Actualizar el tracker inmediatamente. No avanzar un prospecto por intención propia:

- `NEW -> CONTACTED` solo si el contacto se realizó;
- `CONTACTED -> QUALIFIED` solo si el prospecto confirmó un problema real y encaje;
- `QUALIFIED -> DEMO` solo si aceptó una demo;
- `DEMO -> PILOT` solo si aceptó una configuración/prueba;
- `PILOT -> CUSTOMER` solo si se convierte en cliente pagador;
- `CLOSED` si no continúa por ahora, registrando `close_reason`.

La meta de este lote no es “vender a toda costa”. Es conseguir una respuesta real, aprender la objeción y cerrar un siguiente paso concreto sin inventar interés.
