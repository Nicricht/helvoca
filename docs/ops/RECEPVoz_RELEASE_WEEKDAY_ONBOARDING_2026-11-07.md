# RecepVoz · Integración segura de reservas y onboarding · 2026-11-07

## Alcance de entrega

Consolidar exclusivamente el código ya aprobado en los PR #857 y #860 sobre el `main` vigente al 7 de noviembre de 2026 (commit `84cedae671a8d1ae6e87e1323d0611ea5c7518a5`), sin arrastrar una versión obsoleta de otros archivos.

- **Reservas:** validación real de `requestedWeekday` respecto de la fecha local del negocio; no alterar la confirmación de dos fases.
- **Onboarding:** recomendaciones por rubro y botón de siguiente paso; mantener revisión/guardado explícitos y aislamiento tenant.
- **Seguridad:** no activar WhatsApp, voz, pagos ni proveedores. El PR #855 y la verificación externa de Meta siguen separados.
- **Código origen:** PR #857 HEAD `9be9aeefc082f5c1649485ebb6efb7cd28f6eff4`, CI #42063433274 SUCCESS; PR #860 HEAD `91ca4f3b1fd6a3d412c9d3b27c1cd29ddf14509a`, CI #42184179908 SUCCESS. Estas evidencias históricas no sustituyen un CI nuevo contra `main`.
- **Certificación requerida:** Fast Gate, Golden Journey, import/frontend/Inventory/Home Coverage, aislamiento, backend, CI `test` protegido, E2E de navegador y cobertura diferencial obligatoria. Sin rebajar umbrales.
- **Salida:** realizar merge/deploy solo cuando el CI exact-HEAD haya aprobado y los servicios sigan con flags externos desactivados. Confirmar Railway SUCCESS en el commit fusionado.
- **Evidencia:** documentar SHAs, CI y resultado final en Issue #823; no repetir pruebas externas reales.

## Evidencia y corrección posterior a la llamada de Aurora · 10-nov-2026 UTC

La llamada real autorizada desde Twilio 6780 hacia 9611 completó `CA40bf04e5ac2766879d5152521910c12f`, **303 s**, costo Twilio **USD 0,44760**, Gemini `gemini-3.8-live` voz Sulafat. El saludo único y la interrupción de despedida funcionaron; las reservas de prueba se cancelaron. **Certificación integral NO-GO:** «próximo jueves» se interpretó inicialmente como 12 nov en vez de 19 nov, y la asistente ofreció verbalmente una hora para «hoy» que ya había pasado.

La validación heredada `requestedWeekday=THURSDAY` solo compara el nombre del día. Tanto 2026-11-12 como 2026-11-19 son jueves. El contrato ahora admite `requestedWeekScope=NEXT_WEEK` para «próximo jueves» y `THIS_WEEK` para «este jueves»; el backend calcula la semana local del negocio a partir del lunes y rechaza `REQUESTED_WEEK_SCOPE_MISMATCH` con la fecha correcta antes de consultar o modificar agenda. Si el cliente no especifica semana se omite el campo; confirmación de dos fases no lo reenvía. La voz tiene reglas explícitas para usar solo horarios futuros devueltos recientemente por el backend. `BusinessScheduleService` ya filtraba `startAt.isAfter(now)`, por lo cual no se ha demostrado que la hora pasada ofrecida fuera retornada por la agenda.

**Limitación y seguridad:** el parámetro es opcional y el modelo debe enviarlo correctamente: la prueba determinista no equivale a certificación conversacional de Gemini. No despliegue sin exact-HEAD CI ni llamadas nuevas sin autorización. `main`, producción, voces, reservas y límites de gasto quedan intactos.
