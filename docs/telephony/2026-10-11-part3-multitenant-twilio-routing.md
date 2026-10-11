# Helvoca | Parte 3/8: enrutamiento de llamadas multiempresa

Fecha: 2026-10-11. Repositorio: `Nicricht/helvoca`. PR: [#784](https://github.com/Nicricht/helvoca/pull/784).

## Dependencias y regla de publicación

- La Parte 2 está en [PR #783](https://github.com/Nicricht/helvoca/pull/783), commit certificado `2cae6392e5ba8192dd068101368314c7c2c41dba`.
- La Parte 3 deriva de ese commit: la procedencia `MANAGED_PURCHASE`, la cuenta propietaria y `voice_verified_at` dependen de la migración V100.
- `main` se verificó en `39b45f4fe1aa9a825c257b226110c046fce9e218`. El CI del PR #784 compara contra main; **no fusionar #784 antes que #783**. Tras fusionar #783, recalcular el diff y ejecutar la CI completa de la base actual.
- Sin compras de números, llamadas de prueba reales, cambios de webhooks o despliegues en esta fase.

## Contrato de confianza

1. `TwilioSignatureValidationFilter` ya verificaba el HMAC del webhook HTTP con el token de la cuenta respectiva.
2. Los endpoints HTTP de voz, prueba saliente, certificación y callbacks ahora requieren el `AccountSid` recibido como parámetro de Twilio. Un callback sin cuenta o de una cuenta ajena no puede activar un negocio.
3. `TwilioCallBindingGuard` consulta datos solo bajo `TenantDatabaseContext.callAsSystem()` y restaura el scope previo. Exige simultáneamente número activo, proveedor TWILIO, procedencia `MANAGED_PURCHASE`, `voice_verified_at`, PN SID/AC SID válidos y coincidencia de cuenta.
4. Un `CallSid` previamente guardado solo se reutiliza si coinciden empresa, teléfono de la empresa, llamante y destino. Bloquea el reciclado de SID entre empresas.
5. Los callbacks de estado deben asociarse a un `CallSession` existente y certificado. Para el callback de Stream se exige además igualdad exacta del `StreamSid` guardado.
6. El WebSocket conserva su firma temporal HMAC de `TwilioMediaRouteSigner`, pero además exige un `CallSession` ya admitido, no terminal, cuyo `AccountSid` coincida con el número certificado. No basta un HMAC reutilizado para generar una sesión de IA fuera de ese contexto.
7. Las llamadas salientes de prueba quedan registradas antes de emitir el TwiML con Media Stream, evitando que el socket genere una sesión nueva sin admisión previa.
8. RLS protege la lectura normal por tenant; SYSTEM solo se usa en la pequeña frontera de validación carrier. El resto de las herramientas y persistencia utilizan el `businessId` obtenido de la sesión persistida, no de un parámetro confiado al navegador.

## Casos simulados exigidos

- Número inexistente, desactivado, legado o sin procedencia.
- PN incorrecto, AC diferente, verificación ausente y proveedor diferente de TWILIO.
- `CallSid` de la empresa A presentado como llamada de la empresa B, con destino o remitente falsificado.
- Webhooks de estado, stream y media sin asociación a la sesión.
- HMAC de media válido con `AccountSid` perteneciente a otra empresa.
- PostgreSQL real con Testcontainers (dos tenants, migración V100 y verificación RLS).
- Pruebas existentes de firmas correctas/incorrectas de Twilio, expiración del token y ausencia de efectos externos.
- Verificación de pruebas comerciales, Playwright y cobertura diferencial.

## Evidencia y NO-GO

No declarar parte certificada hasta disponer de:
- **CI SUCCESS en el HEAD exacto** (Fast Gate, Full Gate, Postgres, Frontend, E2E).
- **100 % diferencial aplicable** de líneas, ramas y métodos; jamás reducir umbrales.
- Pruebas de concurrencia e idempotencia exitosas, incluyendo los tests de capacidad por tenant.
- Revisión de cambios y del modelo de callbacks con un número y subcuenta Twilio reales en una fase autorizada.
- Ninguna operación real de telefonía ejecutada durante Parte 3.

La verificación del proveedor y las pruebas Mock/Testcontainers **no sustituyen** llamadas reales del piloto. El negocio legado con Phone Number SID vacío seguirá rechazado. El GO comercial integral queda reservado a Parte 8.
