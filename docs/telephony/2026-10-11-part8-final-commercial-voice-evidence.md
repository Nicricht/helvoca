# Helvoca | Parte 8/8: certificación final, evidencia técnica y prueba comercial controlada

## Alcance de esta rama
Se basa exactamente en la Parte 7: `a1aa72e47d527b0b4a56574082afff75ec9b6f28` (PR #789), encadenada a los PR #783–#788 pendientes. No se fusionó ni desplegó nada en producción.

**Regla de seguridad:** no se efectuó ni autorizó una llamada real, compra, búsqueda tarifada, cambio de webhook, despliegue o escritura a PostgreSQL de producción. La Parte 8 **no puede declararse comercialmente completa** solo porque GitHub Actions apruebe.

## Corrección de falso positivo de certificación
Antes, `CallCertificationStatusService.current()` podía informar `PASSED` con 8 checks de acciones/transcripción completos aunque no hubiera terminado el media stream, ni hubiera confirmación `COMPLETED` del carrier, ni CallSid/número asociado. Ahora exige 12 comprobaciones:

1. MEDIA_STARTED
2. AI_READY
3. TRANSCRIPT
4. SERVICES
5. AVAILABILITY
6. BOOKING_CREATED
7. BOOKING_CANCELLED
8. SUMMARY
9. MEDIA_STOPPED
10. CALL_COMPLETED
11. CARRIER_BOUND (transport Twilio, CallSid formato CA, vínculo con número)
12. AI_PROVIDER (proveedor identificado)

El código rechaza respuestas de un tenant equivocado aunque una consulta a repositorio devolviese por error una llamada ajena. `Status.commercialCertified=false` **siempre**, incluso con 12/12. `PASSED` solo indica la comprobación de hitos técnicos persistidos de **esa** llamada, no disponibilidad comercial.

`CallCertificationService.verifyAndCleanup()` también niega `success` si Twilio registró FAILED/BUSY, hay vinculación insuficiente del CallSid o falta proveedor IA. Mantiene limpieza de reservas y fail closed.

**Limitación esencial:** ni el estado `COMPLETED`, ni `AI_READY`, ni una transcripción garantizan que una persona escuchó la voz. Tampoco demuestran que el PhoneNumberSid, AccountSid, la subcuenta y los webhooks siguen iguales hoy. La cuenta/sid presente en BD es evidencia histórica, no validación remota.

## Pruebas sin efectos externos
- Unitarios: cierre correcto, stream aún abierto, falla del carrier, metadatos faltantes/tampered, datos del tenant B, resultado NOT_RUN, transcript/tools/resumen ausentes, disponibilidad alternativa, no-go comercial explícito.
- PostgreSQL 16 real con Testcontainers: dos tenants con números, CallSid y cuentas distintos; RLS forzada y lectura de llamadas de certificación aislada para cada tenant.
- Suite anterior: firma webhook válida/inválida, Media Stream autenticado y bloqueado, tenant ownership, llamadas concurrentes, idempotencia, acciones, frontend.
- Ejecución GitHub Actions exacta en HEAD; 100% diferencial JaCoCo de líneas, ramas, métodos; siete jobs SUCCESS; Playwright, Inicio e Inventario. Registrar resultados verificados al finalizar.

## Matriz de evidencias comerciales, no inferibles de CI

| Comprobación requerida | Evidencia de autorización real necesaria | Estado |
|---|---|---|
| E.164 propiedad de negocio A | Registro actual y verificación directa de Twilio + PostgreSQL | PENDIENTE |
| PhoneNumberSid y AccountSid/subcuenta A | Coincidencia exacta en carrier y tenant A, sin copiar SIDs | PENDIENTE |
| Webhooks y firma Twilio A | URLs HTTPS POST y firma real en recepción y status | PENDIENTE |
| Gemini Live conectado A | Modelo y sesión de una llamada autorizada, no solo tests | PENDIENTE |
| Audio bidireccional | Voz oída y respuesta confirmada por persona autorizada | PENDIENTE |
| Cierre, persistencia y cleanup | CallSid, StreamSid, estado y acciones sin residuos falsos | PENDIENTE |
| Segundo negocio B | Número/AccountSid distinto, una llamada autorizada y cero cruces | PENDIENTE |
| Consumo y límites | Duración, costo Twilio/Gemini verificados contra tarifas vigentes | PENDIENTE |

**Decisión:** **NO-GO** hasta documentar la totalidad de pruebas autorizadas y controlar el presupuesto. El CI verde es aprobación técnica, **nunca** certificación comercial.

## Procedimiento de certificación real futura (solo con autorización explícita)
1. Identificar los dos tenants y números elegibles, con evidencias verificadas de titularidad y subcuenta.
2. Obtener consentimiento expreso de titulares/participantes, ventana de prueba, número destino permitido, presupuesto y duración máxima. No modificar nada de producción en este PR.
3. Confirmar manualmente que flags de arranque y one-shot están deshabilitados antes de cualquier despliegue. Solo activar una prueba controlada después de otra autorización específica.
4. Comprobar proveedor Twilio y rutas; ejecutar una llamada autorizada A, conversación real, consulta de datos y finalización, sin divulgar datos reales de pacientes/clientes.
5. Repetir con B, comprobar RLS, cero mezcla de logs/datos y que los CallSid no se reutilizan.
6. Conciliar costos reales con proveedor y libro de consumo, revisar auditoría y dejar producción en estado seguro.
7. Emitir informe GO/NO-GO con CallSid **redactado**, timestamp, evidencia de audio percibido y responsable que lo corroboró.

Las llamadas y los cambios de producción **no** forman parte de la ejecución automática de este CI.
