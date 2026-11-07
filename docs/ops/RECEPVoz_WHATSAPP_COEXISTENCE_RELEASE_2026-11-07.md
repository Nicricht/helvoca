# RecepVoz · WhatsApp coexistencia sin activación externa (2026-11-07)

## Propósito

Consolidar los cambios de **WhatsApp Business App coexistence** ya revisados en PR #855 sobre el `main` actual: `a55396c8d22c47db02fce8b83cfd770fa8411e3c`. No incluir los cambios históricos de infraestructura Twilio del PR #853, que pertenece al entorno Aurora y requiere un proceso separado.

## Origen y preservación

- Origen: [PR #855](https://github.com/Nicricht/helvoca/pull/855) HEAD `5b6314a25b84eca9f6edeb8c10ec8be34eb7ad28` (rama apilada).
- CI fuente historico: [#42079483012](https://github.com/Nicricht/helvoca/actions/runs/42079483012), [#42079482904](https://github.com/Nicricht/helvoca/actions/runs/42079482904), [#42079483082](https://github.com/Nicricht/helvoca/actions/runs/42079483082): SUCCESS.
- Se copian exclusivamente archivos de código y pruebas del delta propio de #855. Se excluyen cambios de workflow `ci.yml`, el documento de excepción temporal de cobertura del 80%, y dos tests de Twilio ajenos al alcance de Meta.
- En `.env.example` y `frontend/src/features/settings/api.ts`, aplicar solamente el delta de coexistencia sobre `main` para no revertir variables ni API importadora agregadas posteriormente.
- No añadir nuevas migraciones PostgreSQL, infra ni dominios; no crear nuevos negocios ni un segundo administrador.

## Seguridad y puertas obligatorias

- Todas las funciones externas permanecen **OFF por defecto**: Meta Cloud API, Standard Registration, Coexistence, Self Service, entrega saliente y jobs.
- Preservar WhatsApp Business App actual del cliente: nunca invocar registro clásico ni migración; verificar `is_on_biz_app`, WABA y vínculo tenant antes de staging inactivo.
- No enviar mensajes reales, abrir sesión de WhatsApp, realizar llamadas, consumir pagos ni modificar secretos o cuentas de Meta.
- Ejecutar Fast Gate, Golden Journey, import/home/inventory frontend, E2E integración, browser E2E, Java diferencial 100%, controles de aislamiento multiempresa y workflow protegido `test` sobre HEAD exacto; no rebajar umbrales ni falsificar pruebas.
- Solo fusionar/desplegar **código desactivado** después de todos los checks; no habilitar WhatsApp LIVE.
- La activación real queda bloqueada hasta evidencia actual de Meta Business Verification / Tech Provider Access / App Review, WABA y webhook, sin deducir aprobado del estado `Publicada`.
- No repetir certificaciones del demo Aurora ni ejecutar otra llamada real por defecto.

## Aceptación comercial

Aun después del despliegue técnico, `WHATSAPP_LIVE=NO` hasta la comprobación autentica del proveedor, consentimiento, sender y cuentas; no declarar producto comercial completamente listo. Referencia canónica: [Issue #823](https://github.com/Nicricht/helvoca/issues/823).