# Aurora Sur · WhatsApp Business App coexistence checkpoint (2026-11-04)

## Objetivo y consentimiento

- Tenant DEMO: `b59d49a6-a96d-4b00-bf73-d3e78439beb3`.
- Número seleccionado: **+56 9 5073 0447**; el titular informó que funciona en WhatsApp Business App desde hace varios días.
- El titular NO autorizó migración clásica, eliminación de cuenta, cierre de sesión, pérdida de historial ni compras.
- No usar números de voz …6780 / ajeno …2512 ni el contacto distinto …2585.
- No confundir antigüedad informada con elegibilidad aprobada por Meta. Meta puede requerir actividad reciente y rechazar el número.

## Requisitos Meta actuales (verificados 2026-11-04)

- Embedded Signup v2/v3 terminó su fecha de retiro el **15-oct-2026**. La configuración de producción para nuevas conexiones debe ser Embedded Signup **v4** (Facebook Login for Business / WhatsApp Embedded Signup), no reutilizar un config_id antiguo.
- El selector de coexistencia para Business App sigue siendo `featureType: whatsapp_business_app_onboarding`. El flujo v4 puede omitir `phone_number_id` en el evento final; la UI conserva el WABA de la sesión y luego debe encontrar el número en el descubrimiento de ese WABA y verificarlo contra Graph y allowlist.
- La configuración v4 se crea en Meta App Dashboard: Facebook Login for Business → Configurations → WhatsApp Embedded Signup; productos WhatsApp Cloud API, asset WhatsApp accounts, permisos `whatsapp_business_management` y `whatsapp_business_messaging`; usar un system-user token en el lado servidor. La configuración v4 entrega un nuevo config_id. El código nunca debe recibir app secret/system-user token en el navegador.
- La app debe estar aprobada para los permisos y en modo Live, y la empresa debe estar autorizada como Tech Provider/Solution Partner según el onboarding que se habilite.

## Diseño implementado en el PR de preparación

- La pantalla `ChannelsSettingsPanel` ofrece coexistencia como caso distinto del registro tradicional.
- `FB.login` usa `featureType: whatsapp_business_app_onboarding` y captura solo el evento `FINISH_WHATSAPP_BUSINESS_APP_ONBOARDING` desde origen Meta, nunca un `FINISH` genérico. El teléfono del evento es opcional; cuando falta, el WABA se enlaza a la sesión y el número se selecciona desde su lista, con comprobación posterior contra Meta y allowlist.
- El endpoint `POST /api/v1/channels/whatsapp/meta/embedded-signup/waba/phone-number/coexistence/stage` requiere sesión `BUSINESS_ADMIN`, tenant allowlisted y teléfono exacto allowlisted.
- El backend consulta a Meta **sin mutación** `GET /{phone_number_id}?fields=display_phone_number,is_on_biz_app,platform_type`. Solo acepta `is_on_biz_app=true` y `platform_type=CLOUD_API`, comprueba el número contra allowlist y pertenencia a WABA antes de crear/vincular un registro Helvoca, siempre disabled.
- La ruta coexistencia **nunca** llama a `/{phone_number_id}/register` ni pide PIN de registro.
- La ruta de registro ordinario está bloqueada por defecto vía `META_WHATSAPP_STANDARD_REGISTRATION_ENABLED=false`.
- El parser de mensajes IA ignora `smb_message_echoes` y cualquier otro campo de webhook no `messages`, evitando responder automáticamente a los mensajes de la recepcionista en la app.
- Controles por defecto cerrados: `META_WHATSAPP_COEXISTENCE_ENABLED=false`, `META_WHATSAPP_ENABLED=false`, outbound delivery OFF, jobs OFF.

## Configuración pendiente solo para demo-isolated

**No configurar todavía sin acceso de Meta y consentimiento.** Todas las variables deben agregarse por canales seguros a Railway `helvoca-aurora-demo-20261018` / `demo-isolated` / `helvoca-aurora-api`.

- Meta Business Portfolio y App autorizados como Tech Provider/Solution Partner con permisos adecuados.
- `META_WHATSAPP_EMBEDDED_SIGNUP_APP_ID` y `META_WHATSAPP_EMBEDDED_SIGNUP_CONFIG_ID` para un flujo de coexistencia admitido por Meta (configuración v4, según versión actual).
- `META_WHATSAPP_EMBEDDED_SIGNUP_BUSINESS_ID`, `META_WHATSAPP_EMBEDDED_SIGNUP_SYSTEM_USER_ID`, tokens por canal secreto, `META_WHATSAPP_APP_SECRET`, `META_WHATSAPP_VERIFY_TOKEN`; autorización y suscripción del webhook `messages`, `account_update`, `smb_app_state_sync`, `smb_message_echoes` y `history` si se opta por sincronización.
- Únicamente tras esas comprobaciones: `META_WHATSAPP_COEXISTENCE_ENABLED=true`, `META_WHATSAPP_COEXISTENCE_ALLOWED_BUSINESS_ID=b59d49a6-a96d-4b00-bf73-d3e78439beb3`, `META_WHATSAPP_COEXISTENCE_ALLOWED_PHONE=+56950730447`.
- Mantener `META_WHATSAPP_STANDARD_REGISTRATION_ENABLED=false` SIEMPRE en Aurora.
- Readback tenant, credenciales, WABA, callback: `https://helvoca-aurora-api-demo-isolated.up.railway.app/webhooks/v1/meta/whatsapp`.
- Antes de desplegar: CI exact-SHA y prueba con QA; PR #853 y PR de coexistencia permanecen DRAFT; no confiar en HEAD móvil de la rama de Aurora.

## Consentimiento final necesario

El titular debe realizar el Embedded Signup con su Meta Business Portfolio, confirmar en el celular WhatsApp Business y verificar que Meta muestra coexistencia para **ese** número. No pedir OTP, PIN ni tokens por chat o GitHub. Si aparece “migración” o “eliminar cuenta”, cancelar.

## Prueba real autorizada (inbound del titular, no campaña saliente)

1. El titular escribe desde su celular `Hola, ¿qué horario tienen?` a +56 9 5073 0447 desde otro WhatsApp propio (no desde la misma cuenta).
2. Confirmar `messages` inbound válido y asociado únicamente a Aurora.
3. Confirmar respuesta del asistente con horario real de Aurora: lunes a viernes 08:00–20:00, sábado 08:00–14:00, domingo cerrado.
4. El titular pregunta por un servicio y precio. Verificar precio exacto de Aurora, sin usar valores de otro negocio.
5. Confirmar id de mensaje saliente y callback `delivered` del proveedor. La respuesta visible en el celular es evidencia adicional.
6. Verificar que WhatsApp Business App sigue abierto y usable. Solo entonces marcar `WHATSAPP_READY=TRUE` y documentar IDs y timestamps en #848 y #823.

## Estado en este checkpoint

`CODE_PROPOSED_DRAFT`; `CI=NOT_VERIFIED`; `META_V4_CONFIG=NOT_CREATED_OR_NOT_PROVIDED`; `DEPLOYMENT=NOT_PERFORMED`; `META_BUSINESS_APP_ELIGIBILITY=NOT_VERIFIED`; `META_AUTHENTICATED_SESSION=UNAVAILABLE`; `SENDER_LINKED=NO_EVIDENCE`; `REAL_DELIVERY=NOT_TESTED`.

Referencias para contrato de Meta:
- https://developers.facebook.com/documentation/business-messaging/whatsapp/embedded-signup/onboarding-business-app-users
- https://developers.facebook.com/documentation/business-messaging/whatsapp/embedded-signup/version-4
- https://www.twilio.com/docs/whatsapp/migrate-numbers-and-senders


## SaaS coexistence hardening follow-up · 2026-11-05

The DRAFT branch now also prepares the same coexistence flow for future SaaS tenants, without enabling it:

- `META_WHATSAPP_COEXISTENCE_SELF_SERVICE_ENABLED=false` is a separate operator gate and defaults OFF.
- The WABA ID reported by the Meta Embedded Signup session accompanies the one-time authorization code.
- The backend exchanges the code and proves that the resulting customer-scoped token can read that exact WABA.
- The selected WABA must also be present in RecepVoz's provider-side shared-WABA inventory.
- Only the proven WABA is returned to the browser; the provider's full shared-WABA list is not exposed.
- The WABA ID is persisted in the current tenant's `business_meta_whatsapp_config`. Subsequent system-user assignment, app subscription, phone discovery, validation and staging require an exact match to that tenant binding.
- Existing Aurora tenant/phone allowlisting remains available and takes precedence over SaaS self-service.

This remains a DRAFT code change. No Meta account, credential, Railway variable, webhook, sender, or live message was changed. Do not enable the self-service flag until Meta partner/app approval and exact-HEAD CI are verified.


## QA follow-up · 2026-11-05

The integrated Golden Journey compile identified two stale test call sites from the earlier tenant-scoped Twilio command-store change. The production method now requires an explicit business UUID. The unit test now supplies and asserts the tenant predicate, and the PostgreSQL integration test passes the demo tenant explicitly. These are test-only call-site corrections; no Twilio runtime behavior or external calls were changed.
