# HELVOCA · PARTE 6/6 · CERTIFICACIÓN TELEFÓNICA REAL · ACTA DE EVIDENCIA Y NO-GO

**Fecha de corte:** 2026-10-12, Chile. **Proyecto:** `Nicricht/helvoca`. **Rama de trabajo:** `test/voice-real-pilot-6of6-live-evidence-20261012`. **Base:** PR #803, HEAD `738d0f9f6055b74db1894d7c323e1bc18de623ae`. **No mezclar CI especializado, CI completo, puesta en producción ni llamada humana.**

## Decisión actual

**`LIVE_CALL_CERTIFIED=PENDING` · `COMMERCIAL_GO=NO-GO` · `PRODUCTION_DEPLOY=NO-GO`.**

La certificación de **una llamada real nueva** a través de Twilio y Gemini NO se ejecutó. No existió a través de estas herramientas una sesión Gemini Live facturable nueva, un CallSid nuevo verificado ni un testimonio auditado de audio audible. No se deben crear registros sintéticos en producción para fingirlos.

## Evidencia verificada

| Dimensión | Evidencia observada | Alcance real |
| --- | --- | --- |
| Número y tenant | El usuario mostró consulta SQL productiva con `+14355652512`, `provider=TWILIO`, `active=true`, `external_id=PN62601c6420599e74bc940f42de2dd1e4`, tenant `044a5d73-2df7-4e77-849f-8639d05ac30b` | Coincidencia de fila local, NO titularidad actual de carrier por API |
| Twilio Console | Captura previa del usuario: cuenta principal Twilio y webhooks POST hacia Helvoca | Configuración observada históricamente, NO firma o audio de llamada real actual |
| Voice bootstrap | Logs Railway previos `voiceReady=true selectedVoiceProvider=gemini` | Configuración interna al arrancar, NO conexión nueva al proveedor |
| Tráfico entrante | Consulta Railway últimos 168h: `POST /webhooks/v1/twilio/voice` = **0 solicitudes**, `POST /webhooks/v1/twilio/status` = **4 HTTP 2xx** | Sin evidencia de una llamada entrante de prueba en ese intervalo, sin atribuir callback a una llamada certificada |
| Código en producción | Railway `helvoca-api`: último deployment `SUCCESS` desde `main` SHA `ca58693e167752bd6eab8ff4e9be770391fe4293`, sin staged changes | No contiene las mejoras apiladas PR #796–#803 |
| PostgreSQL | PostgreSQL de Railway: Postgres-SSL 18, servicio único, volumen 500 MB; estado `SUCCESS` | No hay consulta directa actual a `flyway_schema_history` ni demostración de backup restaurable |
| CI anterior | PR #799–#802: 7/7 jobs `completed/success` sobre sus respectivos HEAD. PR #803: CI especializado 51/51 (7 Python y 44 Java), CI integral al corte pendiente del job Playwright `test` | Certificación técnica offline, no consentimiento ni prueba humana de llamada |
| Prueba humana histórica | `docs/COMMERCIAL_EXTERNAL_GATES_V1.md` declara una prueba humana controlada el 2026-09-27 | Voz seleccionada y calidad percibida históricas, NO prueba verificable del número/tenant actual y del nuevo código |

## Parte 6: validador independiente de evidencia

Se añade `scripts/ci/voice_pilot_phase6_live_evidence.py` (ningún acceso de red ni tokens), con el JSON de evidencia de solo lectura `docs/telephony/evidence/2026-10-12-phase6-no-live-call.json`.

### Comprobaciones

1. `candidate_sha=main_sha=railway_deployed_sha=full_ci_sha`, CI completo positivo del commit realmente desplegado, restauración PostgreSQL aislada, pruebas de esquema Flyway y titularidad carrier actual.
2. Pilot tenant explícito `RUNNING`, habilitación controlada de efectos globales con otros tenants protegidos, y preflight local + de propiedad Twilio positivos. Nunca habilitar switch porque la checklist está verde.
3. Participante realmente informado/consintiente, número llamante + destinatario autorizado, ventana de ensayo y referencias verificables. Una autorización general del propietario del repo **no** reemplaza consentimiento del participante receptor.
4. Tarifas de Twilio/Gemini verificadas, límite monetario y máximo 300 segundos por llamada, con mecanismo de colgar verificable. Sin tarifas, destino, límites y corte, **no marcar llamada autorizada**.
5. `CallSid` válido, `StreamSid` válido, inicio y fin con zona horaria, carrier `COMPLETED`, media stream iniciado/detenido, sesión Gemini conectada y audio transferido. No inferir audible a partir de `media_started`.
6. Testimonio humano de que escuchó y respondió a Gemini, barge-in funcional, sin audio después de colgar, con referencia auditada sin transcripción completa.
7. Reserva **sintética** creada y cancelada con **mismo UUID**, sin residuos ni tocar reservas de clientes.
8. Costos reales de ambos proveedores conciliados con recibos y `twilio_cost + gemini_cost <= max_total_cost`.

El verificador nunca incluye CallSid completo, destinatario real, tokens ni transcripción en la salida. `BLOCKED` es el resultado obligatorio ante cualquier evidencia faltante. Si se completan *declaraciones* válidas devuelve solo `READY_FOR_INDEPENDENT_LIVE_EVIDENCE_REVIEW`, **no** `LIVE_CALL_CERTIFIED`, `COMMERCIAL_GO` ni `allowed_to_dial`: aun así deben verificarse las fuentes oficiales y el audio humano. Un JSON autorrellenado no constituye prueba.

Las pruebas `scripts/ci/test_voice_pilot_phase6_live_evidence.py` usan **datos completamente sintéticos** y prohíben confundirlos con hechos; cubren separación de tenants, falsificación de CallSid/StreamSid, huellas de audio ausentes, reservas ajenas, duración, facturas, tarifas y consentimiento.

## Ausencias que impiden realizar la llamada ahora

- Los PR #796–#803 no se fusionaron a `main`, y el backend productivo no ejecuta el nuevo preflight.
- El validador de Parte 5 informa `BLOCKED`: no se comprobó un último respaldo productivo restaurable, una restauración aislada ni el estado real de Flyway/compatibilidad de cuatro migraciones `V100` a `V103`.
- La conexión Railway existente no expone aquí operaciones de copia/restore/PITR ni SQL directo. No hay conector Twilio autenticado entre las herramientas disponibles para obtener un CallSid nuevo, sus parámetros, tarifas reales o para llamar/cortar. La búsqueda de plugins no identificó una integración Twilio disponible para esta tarea.
- No hay confirmación verificable de consentimiento de una persona que vaya a contestar, destino de prueba, tarifa actual, tope monetario ni corte automático de la prueba.
- No hay evidencia de audio humano, llamada completada, reserva sintética con mismo ID y costos reales del nuevo candidato.

**No se realizaron cambios en Railway, no se fusionó código, no se ejecutaron migraciones en producción ni se compró/transfirió un número o se iniciaron llamadas.**

## Secuencia obligatoria para que una futura llamada sea certificable

1. Terminar CI exacto de Parte 5 y 6, con los siete gates y 100 % JaCoCo diferencial sin excepción.
2. Conservar prueba de snapshot actual en Railway y hacer restauración real a un **servicio PostgreSQL diferente**, sin alterar volumen origen. Consultar `flyway_schema_history` en read-only, probar V100–V103 sobre el clon restaurado y medir tiempos.
3. Revisar la cadena larga de PR, preparar despliegue acotado, identificar responsable/ventana y verificación de reversión compatible con esquema. Solo desplegar con restauración segura demostrada.
4. Autenticar el endpoint de preflight con JWT `BUSINESS_ADMIN`, comprobar el número/tenant y evidencia del AccountSid y webhook en la API Twilio. Resolver correctamente los permisos de inscripción existentes; **nunca inventar `voice_verified_at` o `twilio_binding_source`**.
5. Establecer un presupuesto global y por proveedor basado en tarifas observadas, una sola llamada, límite de duración máximo 300 segundos, stop verificado y participación consentida en una ventana fijada.
6. Ejecutar una sola llamada real controlada, recoger únicamente IDs parciales y estados, confirmar que el participante oyó y fue oído, interrupción/cierre correcto, creación/cancelación de reserva de prueba con el mismo UUID y cero efectos en otros tenants.
7. Conciliar factura de Twilio y consumo de Gemini, limpiar cualquier dato sintético, dejar piloto desactivado salvo GO documentado. Solo entonces emitir acta humana independiente.

## Continuidad y resultado

El cierre de las seis partes es **preparación y certificación técnica offline**, no lanzamiento comercial ni nueva prueba telefónica. Los bloqueos son explícitos, no inferencias del CI. Si se proporciona evidencia verificable mediante conectores apropiados en otra sesión, el proceso puede continuar desde este acta, sin repetir variables o ensayos ya certificados.

**FINAL: `LIVE_CALL_CERTIFIED=PENDING`, `COMMERCIAL_GO=NO-GO`, `PRODUCTION_DEPLOY=NO-GO`.**
