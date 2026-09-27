# Commercial Release Candidate Gate

## Objetivo

Esta rama no agrega funcionalidades de producto. Define la puerta única para convertir el estado actual de RecepVoz / Helvoca en un candidato comercial verificable.

Base auditada de `main`: `67ca6bb78d1664dee139aeced17b779ebfcc6284`.

Hasta que esta puerta esté en PASS:

- no agregar funcionalidades nuevas;
- no desplegar;
- no fusionar estas ramas a `main`;
- no activar proveedores reales por conveniencia;
- no ejecutar llamadas, WhatsApp ni cobros reales fuera de una certificación humana explícitamente autorizada;
- solo aceptar fixes P0/P1, correcciones de certificación, seguridad, CI o reconciliación entre ramas.

## Workstreams comerciales

| PR | Rama | Responsabilidad | Estado observado |
| --- | --- | --- | --- |
| #548 | `perf/latency-certification-readonly-v1` | certificación de latencia read-only | CI verde |
| #549 | `chore/commercial-release-hardening` | release/CI/protección | CI verde |
| #550 | `fix/voice-commercial-certification` | voz y ciclo de llamada | CI verde; validación humana pendiente |
| #551 | `test/golden-journey-commercial-v1` | Golden Journey E2E | CI verde |
| #552 | `test/saas-billing-commercial-readiness` | billing SaaS | CI en ejecución |
| #553 | `docs/first-commercial-pilot` | operación primer cliente | CI verde |
| #554 | `feat/owner-commercial-dashboard-v1` | dashboard del dueño | corrección CI aplicada; revalidación en ejecución |
| #555 | `feat/commercial-demo-onboarding` | tenant demo/onboarding | corrección de cobertura aplicada; revalidación en ejecución |
| #556 | `feat/commercial-observability-v1` | trazabilidad/soporte | migración renumerada a V78; revalidación requerida |

Los estados son una fotografía del 27-09-2026. La decisión final se toma contra los HEAD exactos, no contra esta tabla histórica.

## Colisiones detectadas y resolución

### Migraciones

#552 y #556 fueron creadas en paralelo con una migración `V77` distinta. Eso impedía integrarlas con seguridad.

Orden obligatorio:

1. #552 conserva `V77__saas_billing_webhook_idempotency.sql`;
2. #556 usa `V78__commercial_observability_runtime_metadata.sql`;
3. #556 no se integra antes de #552.

### Formulario de onboarding

#553 y #555 modifican `docs/FIRST_CUSTOMER_ONBOARDING_FORM.md`.

No resolver con "ours/theirs" automático. La versión integrada debe conservar:

- campos de perfil público/modalidad introducidos por onboarding;
- distinción demo ficticio vs cliente real;
- limitaciones reales de políticas transaccionales;
- checklist y reglas operativas del primer cliente;
- precios/alcance comercial consistentes con backend.

## Orden de integración propuesto

No ejecutar este orden hasta que todos los gates obligatorios estén verdes y exista autorización explícita para merge.

1. #549 release hardening.
2. #548 certificación de latencia read-only.
3. #552 billing SaaS, incluyendo V77.
4. #556 observabilidad, incluyendo V78 y reconciliación con #548.
5. #550 voz/ciclo de llamada.
6. #551 Golden Journey.
7. #555 demo/onboarding.
8. #553 operación comercial, reconciliando el formulario compartido con #555.
9. #554 dashboard del dueño.
10. crear un HEAD integrado de release candidate y ejecutar toda la matriz otra vez desde cero.

El orden puede ajustarse si Git demuestra una dependencia distinta, pero nunca se salta la recertificación del HEAD integrado.

## Gate A: repositorio

Debe cumplirse todo:

- [ ] `main` requiere Pull Request para cambios.
- [ ] `fast-gate` obligatorio.
- [ ] Full Gate/`test` obligatorio.
- [ ] push directo normal a `main` bloqueado.
- [ ] force push bloqueado.
- [ ] candidato nace de un SHA conocido.
- [ ] no existen migraciones Flyway duplicadas.
- [ ] no existen conflictos semánticos sin resolver entre PRs.

Hallazgo actual: `main` sigue reportado por GitHub como no protegido. #549 documenta la configuración requerida.

## Gate B: producto automatizado

El HEAD integrado debe aprobar:

- [ ] Fast Gate.
- [ ] suite backend completa.
- [ ] diferencial de líneas.
- [ ] diferencial de ramas.
- [ ] navegador/E2E.
- [ ] Golden Journey comercial.
- [ ] pack de voz comercial.
- [ ] pack de billing SaaS.
- [ ] onboarding demo repetible e idempotente.
- [ ] observabilidad/Journey Trace.
- [ ] dashboard del dueño en desktop y móvil.
- [ ] no duplicación de booking/tool effects.
- [ ] aislamiento multi-tenant.
- [ ] cierre sin continuación post-farewell.

Un PASS de una PR aislada no sustituye este gate sobre el HEAD integrado.

## Gate C: demo comercial

Desde un entorno limpio debe ser posible, sin SQL manual:

1. crear/cargar tenant demo;
2. mostrar perfil, horarios, servicios, FAQ y políticas;
3. consultar disponibilidad;
4. crear una reserva;
5. modificar la elección sin conservar estado obsoleto;
6. ver la operación en dashboard;
7. reconstruir la interacción desde observabilidad.

El dataset debe seguir siendo ficticio y no activar teléfono, WhatsApp ni pagos reales.

## Gate D: billing

Antes de cobrar a un cliente:

- [ ] plan público coincide con catálogo/backend;
- [ ] checkout sandbox/test crea referencia correcta;
- [ ] webhook firmado enruta al tenant correcto;
- [ ] activación de suscripción actualiza entitlements;
- [ ] renovación es idempotente;
- [ ] rechazo/cancelación deja estado coherente;
- [ ] webhook duplicado no duplica efectos;
- [ ] merchant payments de los clientes del negocio permanecen separados del cobro SaaS;
- [ ] credenciales LIVE siguen fuera de la certificación automatizada.

No realizar cobro real como atajo de prueba.

## Gate E: voz humana obligatoria

Los tests pueden certificar estado, tools, transporte y cierre, pero no juzgar sonido real.

Antes de vender voz se requiere una llamada humana controlada que confirme:

- [ ] Sulafat mantiene identidad femenina consistente desde saludo hasta despedida;
- [ ] español natural para clientes chilenos, sin tono IVR;
- [ ] velocidad natural y respuestas breves;
- [ ] interrupción real corta playback inmediatamente;
- [ ] dos interrupciones rápidas no reanudan audio viejo;
- [ ] una consulta con tool real tiene latencia percibida aceptable;
- [ ] booking no repregunta datos conocidos;
- [ ] una corrección reemplaza la elección anterior;
- [ ] "no gracias", "eso sería todo" y "chao" producen una sola despedida completa;
- [ ] carrier corta después de la última palabra;
- [ ] no existe audio residual después del hangup.

Esta llamada no se ejecuta desde las ramas de certificación que prohíben llamadas reales.

## Gate F: primer cliente

Antes de aceptar dinero:

- [ ] negocio calificado para el alcance realmente soportado;
- [ ] datos y políticas aprobados por el responsable;
- [ ] tenant configurado sin edición manual de DB;
- [ ] canal incluido certificado;
- [ ] billing/medio de cobro a RecepVoz autorizado;
- [ ] soporte sabe reconstruir una llamada problemática;
- [ ] no existe P0 conocido;
- [ ] SHA desplegable identificado;
- [ ] rollback definido.

## Definición de "vendible"

RecepVoz se declara listo para un piloto comercial únicamente cuando el mismo release candidate puede repetir:

`NEGOCIO NUEVO -> CONFIGURACIÓN -> CONVERSACIÓN -> ACCIÓN/RESERVA -> RESULTADO VISIBLE -> TRAZA -> SUSCRIPCIÓN/ENTITLEMENTS`

sin SQL manual, sin efectos duplicados, sin romper aislamiento tenant y sin depender de una corrección humana invisible.

## Lo que no es requisito para el primer piloto

No bloquear el lanzamiento por:

- CRM nuevo;
- rediseño general;
- nuevas integraciones no incluidas en el piloto;
- automatización completa de la operación comercial;
- métricas que el backend aún no puede respaldar;
- funcionalidades "por si acaso".

Primero se vende el recorrido certificado. Después, el roadmap vuelve a abrirse con evidencia de clientes reales.
