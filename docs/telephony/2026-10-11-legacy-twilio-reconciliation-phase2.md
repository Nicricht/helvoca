# Helvoca: Fase 2 de 10, evidencia Twilio de solo lectura

Estado: componente backend aislado. No acredita disponibilidad comercial ni autoriza llamadas, cambios en Railway o PostgreSQL, compras, liberaciones, despliegues o merges.

## Contrato oficial verificado (11 octubre 2026)

- https://www.twilio.com/docs/phone-numbers/api/incomingphonenumber-resource
- https://www.twilio.com/docs/iam/api/account
- https://www.twilio.com/docs/iam/api/subaccounts

IncomingPhoneNumbers: sid, phone_number, account_sid, capabilities.voice, status, voice_receive_mode, voice_url, voice_method, status_callback, status_callback_method, voice_application_sid, trunk_sid. Twilio acepta filtro PhoneNumber parcial, por lo que el E.164 de respuesta debe coincidir EXACTAMENTE. Las listas son paginables, así que una respuesta con páginas adicionales nunca certifica unicidad.

Account: sid, owner_account_sid y status. Las credenciales de cuenta principal pueden consultar recursos v2010 de sus subcuentas.

## Implementación

TwilioExistingNumberOwnershipVerifier no tiene controlador, repositorio, persistencia, operaciones de escritura ni métodos de activación.

Entradas que deben proceder de un operador autorizado, NO de un tenant: E.164 exacto, SID de cuenta candidata y SID de número esperado opcional (null permitido para filas legacy). La cuenta candidata debe ser la principal configurada, o exactamente HELVOCA_TWILIO_SUBACCOUNT_SID previamente configurada. No se aceptan otras cuentas aunque su SID tenga formato válido.

Tres GET autenticados con credenciales de la principal:
1. /2010-04-01/Accounts/{accountSid}.json: titular y estado active, owner_account_sid igual a la cuenta principal.
2. /2010-04-01/Accounts/{accountSid}/IncomingPhoneNumbers.json?PhoneNumber={E164}: respuesta completa, sin paginación adicional, un único registro.
3. /2010-04-01/Accounts/{accountSid}/IncomingPhoneNumbers/{PN}.json: segunda validación de identidad y configuración.

Se exige coincidencia E.164/PN/AC exacta en ambas respuestas, estado in-use, voice_receive_mode voice, capabilities.voice boolean true, webhook voz y estado HTTPS esperado, ambos POST; sin voice_application_sid ni trunk_sid que desvíen las rutas.

Los resultados de error usan códigos cerrados. No se devuelve el cuerpo de error de Twilio ni credenciales. Evidencia interna redactada en toString y JSON. Aun con VERIFIED, no existe todavía prueba de entrega real de webhooks ni autorización de uso comercial.

## Regresiones y límites

Pruebas de HTTP simulado: cuenta principal, subcuenta válida, cuenta ajena, formato, PN diferente, número ausente/duplicado, paginación, contradicciones listado/detalle, capacidad de voz, webhooks, override de aplicación/trunk, errores HTTP/red, y no mutación del objeto PhoneNumber.

NO se cambia PhoneProvisioningService ni TwilioCallBindingGuard: MANAGED_PURCHASE continúa con su protección actual. Tampoco se cambia el esquema SQL, RLS ni la procedencia VERIFIED_EXISTING, reservada para una fase posterior. La fila legacy de producción queda intacta.

No hay una verificación REAL del piloto contra Twilio en esta fase: la integración ejecuta únicamente GET simulados. Certificar fase requiere GitHub Actions sobre el HEAD final: los siete jobs esperados en SUCCESS y cobertura diferencial Java aplicable 100 %. Mantener PR #791 en borrador y apilado, sin merge.
