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

## Aurora demo-isolated · port 2026-11-08

- Se portaron los 17 archivos del cambio certificado en PR #863 a la rama aislada de Aurora mediante el commit `e623cb5e9fa78dafa984f55ce209fce081d696c0`, parent `d852bf4e4133ed9ace9703a57a35aa239101bd34`. El delta incluye validación `requestedWeekday` en disponibilidad/creación/reprogramación, tool definitions, regresiones backend/Golden Journey y mejoras de onboarding por rubro/siguiente paso.
- Antes de copiar, los 15 archivos existentes afectados por #863 se verificaron idénticos entre el HEAD de Aurora y la base original de #863 (`84cedae671a8d1ae6e87e1323d0611ea5c7518a5`); los dos documentos restantes son nuevos. No se sobrescribieron diferencias propias de Aurora.
- PR #853 sigue DRAFT, sin merge a `main`. El CI nuevo del commit `e623cb5` comenzó en run #42535586662 y falló inicialmente solo en el pre-change receipt porque faltaba el recibo estructurado anterior al commit; los workflows PostgreSQL 18 y PG16/18 permanecieron en ejecución. Se registró el recibo V2 para el parent exacto antes de este nuevo commit documental. Este checkpoint **no** declara CI verde; comprobar el nuevo run exact-HEAD.
- Railway Aurora: el último deployment leído sigue SUCCESS en SHA runtime `383e2582020f6d66e9ef8bbd16099ddb4d27de05`; la configuración de servicio también devuelve un pin `cd282aab65cb39aad4e3c36511742c68e75b2db3`, por lo que hay que reconciliar el source pin antes de cambiarlo. El nuevo commit `e623cb5` **todavía no está desplegado**.
- La llamada histórica del 4-nov completó audio Gemini, pero su aceptación de reserva/cancelación falló (`booking_cancelled_not_successful,created_booking_not_cancelled`) y se observó la discrepancia de día de semana. El port corrige la validación del día; **no** prueba que la cancelación esté corregida. No repetir llamada ni reserva para ocultar el fallo.
- Sin migraciones, cambios de flags/credenciales/números/rutas/límites, llamadas, mensajes, reservas, pagos ni cambios en producción. La activación del canal de voz y la aceptación real del Golden Journey siguen siendo gates separados.
