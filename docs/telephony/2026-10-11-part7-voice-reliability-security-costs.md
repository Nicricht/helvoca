# Helvoca | Parte 7/8: seguridad, observabilidad, costos y estabilidad

## Alcance y dependencias
Rama derivada del HEAD certificado de la Parte 6: `a676264add82a1be8b2b427736a235c538aae274` (PR #788), a su vez dependiente de Partes 2–5. **No implica merge ni despliegue**. La Parte 8, certificación telefónica real y prueba multiempresa, se mantiene fuera de alcance.

## Hallazgo corregido: fuga de errores de proveedores
El circuito de salud retenía mensajes sin depurar de Gemini/OpenAI; su `snapshot().detail()` podía exponer contenido de respuesta, posibles identificadores de clientes, tokens y datos internos. El `VoiceCallRouter.readiness()` también entregaba `Exception.getMessage()` cuando fallaba `mediaProviders.require()`.

Ahora:
- Los errores en circuitos mantienen solo la categoría enumerable (`AUTH`, `NO_CREDITS`, `RATE_LIMIT`, `SESSION`, `UPSTREAM`, `UNKNOWN`), sin mensajes originales en diagnósticos.
- `VoiceCallRouter.readiness()` muestra un mensaje fijo al fallar el proveedor.
- Los logs de fallos de selección de proveedor registran solo el tipo de excepción, no el mensaje sin confianza.
- Los circuit breakers existentes conservan su política de enfriamiento y el rechazo fail-closed.
- El diagnóstico sigue siendo **observación**, no certificación ni prueba con llamadas reales.

## Métricas nuevas, sin cardinalidad alta
En `VoiceProviderHealthRegistry` se registran contadores Micrometer:
- `helvoca.voice.provider.circuit.opened`: etiqueta `kind` limitada al enum `FailureKind`.
- `helvoca.voice.provider.admission.rejected`: etiqueta `reason` limitada a `unconfigured` y `circuit_open`.
- `helvoca.voice.provider.circuit.recovered`: etiqueta `reason=success`, incrementada cuando se cierra un circuito previamente abierto.

**No se agregan** identificadores de tenant, teléfonos, CallSid, PhoneNumberSid, AccountSid, mensajes, errores externos ni tokens como etiquetas. Se aprovecha el registro Micrometer existente; no se activa ningún servicio externo.

## Costos, límites y estabilidad existentes, auditados
La versión base ya dispone de:
- `CallLifecycleService`: control de capacidad concurrente por suscripción y tenant, bloqueos advisory de PostgreSQL por llamada/empresa, idempotencia del inicio/callback y registro de duración y **costo estimado**, no costo facturado.
- `CallCommercialProperties`: tarifas configurables por proveedor/modelo; el valor cero por defecto **no demuestra tarifa real cero**.
- `PerformanceCostService`: análisis por tenant con estimados separados del costo real, cobertura de muestras y señales de información faltante.
- `V41__usage_cost_metering.sql`: ledger append-only, RLS y unicidad de idempotencia. Sus tests previos y los gates integrados siguen aplicando.
- `ApiRateLimitFilter` y `DistributedRateLimiter`: control de tasa existente que debe conservarse, sin afirmar que sustituye a controles específicos de Twilio Media WebSocket.

Las métricas nuevas complementan los contadores existentes de admisión y duración. **No** hay autorización para compras, aumentos de cuotas, comprobaciones facturadas ni cambios de pricing o secretos. La configuración de tarifas y el costo final del proveedor deben verificarse antes de fijar precios comerciales.

## Pruebas nuevas y regresión obligatoria
- Pruebas de fuga de cadenas con secretos/caller en el circuito y en readiness.
- Verificación de categorías, métricas con etiquetas acotadas y recuperación idempotente.
- Reutilización de pruebas de concurrencia, PostgreSQL real/Testcontainers, RLS, seguridad, provisión y proveedor de voz existentes.
- GitHub Actions del **HEAD exacto**: los siete jobs aplicables completos en SUCCESS.
- Cobertura diferencial JaCoCo exigida: **100% de líneas, ramas y métodos aplicables**, sin modificar gates ni mínimos; frontend Inicio/Inventario y Playwright completos.

## Bloqueos comerciales y operación
**NO-GO comercial** aunque CI apruebe, hasta prueba autorizada de número E.164, Twilio PhoneNumberSid, cuenta/subcuenta, tenant, webhooks, audio Gemini Live, respuesta audible y terminación correcta, incluida separación entre al menos dos negocios. La Parte 7 no realiza llamadas reales, compras, operaciones facturadas, cambios en Railway, escrituras en producción ni fusiones a `main`.

## Certificación
Pendiente de ejecución en el HEAD final de la rama de Parte 7. Registrar commit, workflow, conteos de Java/Playwright, cobertura diferencial exacta y resultado 7/7 antes de declarar terminada la parte.
