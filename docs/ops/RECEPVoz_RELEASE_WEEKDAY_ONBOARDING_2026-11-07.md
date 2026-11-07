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
