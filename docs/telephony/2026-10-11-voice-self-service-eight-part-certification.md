# Helvoca: telefonía e IA de voz autogestionables (8 partes)
Fecha de auditoría: 2026-10-11. Base de código examinada: `main@60869bb6a6763cf6dc3de8863d7c8d77fe4447de` (PR #779 fusionado).

## Objetivo innegociable
Un negocio nuevo debe poder registrarse, configurar su recepcionista y sus reglas, adquirir **o verificar la propiedad y vincular** un número, activar voz y recibir llamadas con IA y operaciones autorizadas **sin editar código, variables Railway, filas PostgreSQL ni webhooks a mano para cada tenant**. Mantener límites de consumo, consentimientos, auditoría, privacidad y capacidad de recuperación.

No confundir **código con tests aprobados** con **servicio de producción desplegado** ni con **número de Twilio verificado**. El aprovisionamiento puede generar cargos; requiere consentimiento previo explícito. Las llamadas reales solo se harán bajo autorización de prueba con límites y destino verificado.

## Estado comprobado de la base existente

| Área | Evidencia concreta | Estado |
| --- | --- | --- |
| Autorización API de telefonía | `PhoneNumberController` ofrece GET/listado, POST/manual, GET/provisioning/status, GET/provisioning/available, POST/provisioning, PATCH/active, DELETE. Escrituras protegidas con `BUSINESS_ADMIN` | Reutilizable; revisar reglas por tenant |
| Compra | `PhoneProvisioningService` exige `confirmed=true`, persiste E.164+PN SID+businessId y procura revertir la compra ante fallo SQL | Parcial; transacciones externas e idempotencia pendientes |
| Cliente Twilio | `TwilioPhoneProvisioningClient` busca números, realiza compra con VoiceUrl/StatusCallback, utiliza credenciales `TwilioProperties.accountSid/authToken` globales | Parcial; no implementa un flujo per-tenant de subcuenta/propiedad |
| UI | `ChannelsSettingsPanel.tsx` ya busca números, exige confirmación de compra, conecta existentes, activa/desactiva y desvincula | Parcial; falta wizard de verificación y estado del carrier |
| Ruteo entrante | `TwilioVoiceController` busca `To` y `CallLifecycleService` resuelve la llamada; `TwilioMediaStreamHandler` usa SYSTEM después de firma válida del route token (PR #779) | Capa existente, falta prueba comercial multi-tenant |
| Bootstrap legado | `TwilioSubaccountVoiceBootstrapRunner` usa un teléfono ancla de WhatsApp desde variables y actualiza voz al ARRANQUE si se habilita | Útil para piloto; no es onboarding comercial autogestionable |
| Readiness | `OnboardingService.buildStatus` considera `phoneConfigured` cuando existe cualquier fila `phone_number.active=true` | **Riesgo crítico de falso READY** |
| Registro manual | `PhoneNumberService.create` acepta E.164 y `externalId` del cliente y deja el número `active=true` por defecto sin prueba de titularidad de Twilio | **Riesgo crítico de apropiación/enrutamiento incorrecto** |
| Relación comercial | `PlatformBusinessProvisioningService` crea negocio e invitación; configuración de telefonía ocurre por otra ruta | Integración de onboarding pendiente |
| Validación | PR #779, HEAD previo `c297a776`, CI run `38114113358` SUCCESS, 1933 tests backend y 431 Playwright; diferencial líneas 34/34, ramas 14/14, métodos 26/26 | Solo cubre cambios del PR #779 |
| Producción | Railway sigue rama `main`; al inicio de esta auditoría el despliegue nuevo `b13fe85c-a0b1-49e4-ba1d-c0da11154337` estaba `WAITING`, anterior `0092ffa7...` SUCCESS. Revisar estado **en cada parte** | **No asumir último commit LIVE** |
| Cuenta real | Informe de comprobación piloto advierte que E.164, SID PN, subcuenta y callbacks aún no han sido cotejados de extremo a extremo | **NO-GO para una llamada de certificación** |

El SID PN de un teléfono distinto jamás debe copiarse a otra fila. El número ancla de WhatsApp y el de voz pueden ser distintos.

## Arquitectura objetivo

- **Identidad:** `tenant/business_id`, E.164, `carrier=TWILIO`, cuenta propietaria `AC...`, SID `PN...`, propósito `VOICE/WHATSAPP/BOTH`, evidencia de titularidad y estado de provisión son conceptos independientes.
- **Titularidad:** verificar desde la API autenticada del carrier que `account_sid`, `phone_number` y `sid` representan el mismo recurso y la cuenta autorizada. Nadie activa un número escribiendo arbitrariamente un E.164 o PN SID. Adoptar estrategia explícita de cuentas administradas/subcuentas por tenant según modelo de facturación, no suponer que una variable global implica la cuenta correcta.
- **Credenciales:** secretos a nivel plataforma/proveedor en almacén seguro; nunca exponer tokens en frontend, logs ni documentación. Nunca depender de variables de entorno **por cliente**.
- **Ciclo de vida:** `DRAFT -> PENDING_CONSENT -> PROVISIONING/VERIFYING -> ACTIVE`; anomalías `BLOCKED/RETRYABLE/RECONCILIATION_REQUIRED`; salida `DEACTIVATING/DETACHED/RELEASE_REQUESTED`. No confundir desvincular en la DB con cancelar o liberar un número en el carrier.
- **Operación externa fiable:** idempotency key por solicitud, deduplicación por tenant/número, bloqueo de concurrencia, estados persistidos y reconciliación con Twilio. No mantener transacciones SQL abiertas durante HTTP ni liberar un número cuyo origen/propiedad esté ambiguo.
- **Webhooks:** URLs HTTPS/WSS plataforma, validación real de firma Twilio sobre el URL público correcto, control de duplicados/reintentos, verificaciones posteriores a configuración, route token corto y validado para media.
- **Enrutamiento:** llamadas -> número verificado y activado -> exactamente un tenant activo -> suscripciones, capacidades y límites -> configuración del agente y herramientas del **mismo** tenant. Sin fallback transversal a otro negocio.
- **Agente:** voz en tiempo real, interrupciones, límites de latencia, modelo seleccionado verificablemente, manejo de caída del proveedor, identidad de negocio, consentimiento de grabaciones y transferencia humana cuando exista.
- **Herramientas:** acceso a disponibilidad y agenda con filtros tenant + control de negocio, confirmación explícita para mutaciones, idempotencia por llamada, prevención de citas dobles; registrar resultado para panel y facturación.
- **Readiness real:** `readyForCalls=true` solo con provisión y propiedad verificadas, webhooks certificados, número de VOZ activo, agente listo, negocio activo, plan/cuota vigentes y salud mínima del canal. Describir *por qué* aún no está listo.

## Fases y criterio de salida

### Parte 1/8: inventario y contrato
- Inventariar backend/frontend/DB/Twilio/Railway; registrar riesgo, dependencia y criterios de salida sin tocar producción.
- Entregable: este documento versionado en PR independiente.
- Gate: evidencia rastreable al código y estado del último deploy mostrado con incertidumbre explícita.

### Parte 2/8: propiedad Twilio y aprovisionamiento robusto
- Separar compra gestionada y verificación de número existente, titularidad AC/PN/E164, capacidad voz, webhook y consentimiento de cargo.
- Modelar datos/migración e idempotencia, conciliación, estados persistentes, concurrencia y compensaciones seguras.
- Gate: mocks contractuales Twilio (sin compra real), RLS y 100% cobertura diferencial; bloquear E164/PN falsos o cruzados.

### Parte 3/8: aislamiento y ruteo
- Exigir identidad autenticada del caller Twilio, firma, número verificado, selección unívoca de tenant, callback y WebSocket con SYSTEM acotado.
- Gate: PostgreSQL real/Testcontainers, transacciones, carrera, dos tenants, firmas inválidas, número inexistente/inactivo, cero acciones externas.

### Parte 4/8: canal voz Gemini
- Controlar WebSocket/MediaStream en tiempo real, buffer/turn-taking, VAD/interrupciones, latencia, reconexión, selección real del modelo, voz y fallback.
- Gate: audio/eventos simulados y verificables, costos/tiempo bajo límite, modelo declarado=usado, sin llamadas reales.

### Parte 5/8: acciones empresariales
- Integrar reserva, disponibilidad, modificación/cancelación y consultas con reglas y consentimiento; capacidad por sector sin hardcode de restaurantes.
- Gate: agenda/PostgreSQL real, no doble reserva, herramientas y transcripciones aisladas, auditoría y errores controlados.

### Parte 6/8: activación autogestionada
- Wizard integrado en Configuración/Canales y onboarding, estados claros, compras con costos/confirmación, reintento seguro y pruebas de accesibilidad/responsive.
- Gate: tenant A/B se activan desde UI sin Railway ni SQL manual por negocio; pruebas Playwright completas.

### Parte 7/8: producción y gastos
- Health checks que reflejen voz real, alertas por fallo, SLA y recuperación, cuotas/uso/facturación, límites y runbooks no destructivos.
- Gate: fault injection, load/concurrency y 100% diferencial, CI exact-HEAD verde, condiciones de rollback.

### Parte 8/8: certificación final con llamadas reales
- Solo cuando se confirme con Twilio `AC+PN+E164`, webhooks, tenant, disponibilidad, gastos máximos y destinatario autorizado.
- Probar llamada real cliente -> recepcionista -> respuesta IA -> acción de agenda -> persistencia/auditoría, y otro tenant para demostrar aislamiento.
- Incorporar un tercer negocio exclusivamente desde UI, sin tocar código, DB ni Railway manualmente; comprobar rutas, costos, registros y rollbacks.
- Gate: evidencia de llamadas, sin filtración entre tenants, UI autoactivable, documentación operativa, estado GO por componente.

## Reglas de trabajo transversales

1. Pruebas **100% diferencial** en líneas, ramas y métodos para todo código aplicable modificado; no confundir con 100% de cobertura global.
2. Cada parte usa feature branch y PR, CI comprobada contra el SHA exacto, sin asumir verde ni desplegar sin compuerta.
3. No hacer compras Twilio, liberaciones de números, llamadas reales, reconfiguración de webhooks ni modificaciones de producción para certificar fases 1-7. La fase 8 exige alcance/destinatario/límite de gasto aprobados.
4. Prohibido asignar un PN SID sin evidencia del carrier. Datos privados y tokens no deben quedar en commits, tests o prompts públicos.
5. Registro por tenant de estado, costo y fallos. Aplicar mínimo privilegio y observar aislamiento RLS.
6. Cualquier estado de producción (incluido deploy y variables) se verifica de nuevo antes de tomar decisiones; valor de variables no disponible cuando el conector Railway las redacta.

## Decisiones abiertas que no deben resolverse por suposición

- Política comercial: cuenta Twilio administrada compartida vs subcuenta por cliente y modelo de portabilidad/BYON.
- Alcance geográfico y requisitos regulatorios/identidad para compra y transferencia por país.
- Verificación externa (Twilio) del número de voz exacto del piloto, subcuenta propietaria y webhooks; no hay acceso de API Twilio conectado en la auditoría actual.
- Políticas de consentimiento de grabación/transcripción, retención, gastos máximos, monedas e impuestos aplicables.
- Determinar un flujo de provisión para clientes que poseen números fuera de cuentas Twilio gestionadas (portabilidad o desvío verificado), sin prometer capacidades no implementadas.
