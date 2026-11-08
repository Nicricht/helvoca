# Aurora Sur · estado del piloto aislado · 2026-11-08

## Código y procedencia

- Producción `main` permanece en `7bf4ea363903ff750eae984159995fefaa8b5a9d`; incluye el aislamiento Gemini Live del PR #866 y la validación de día local de PR #863.
- El PR #868 porta a una rama limpia basada en ese main los **48 archivos exclusivos** del piloto telefónico Aurora que estaban en el PR #853. Los cinco archivos que se solapaban con cambios posteriores de main se heredan desde main; `.env.example` también se conserva desde main.
- Candidato inicial del PR #868: `e51c08a0fa50c1ccfe1dc719837ee646937aecae`. Este documento se añade en el commit posterior para registrar el handoff; no afirma que el CI del candidato esté aprobado.
- El PR #853 antiguo permanece como histórico DRAFT hasta que el rebase nuevo complete CI y se confirme el handoff. Ningún cambio se fusiona a main en esta fase.

## Runtime Aurora leído antes del rebase

- Proyecto: `helvoca-aurora-demo-20261018`, ambiente `demo-isolated`, servicio `helvoca-aurora-api`.
- Último deployment observado: `921e11c5-c1b5-431a-af88-56b3b3393cb4`, SUCCESS, runtime SHA `383e2582020f6d66e9ef8bbd16099ddb4d27de05`.
- La configuración del servicio reporta branch `fix/aurora-existing-twilio-stage-20261030` y un pin `cd282aab65cb39aad4e3c36511742c68e75b2db3`. Esta diferencia frente al SHA del último deployment se debe reconciliar antes de cambiar el source.
- Startup readiness observado: Twilio credentials, public base URL y media stream URL presentes; `voiceReady=false`, `selectedVoiceProvider=null`; Gemini `enabled=false`, API key configurada, endpoint aprobado, configuración local no lista; WhatsApp `whatsappEnabled=false`, `whatsappReady=false`.
- Los flags externos permanecen apagados. Este PR no los modifica.

## Incidencias funcionales históricas

- La llamada real del 4-nov completó 478 segundos con Gemini y persistencia en PostgreSQL.
- La certificación de ese recorrido no aprobó la creación/cancelación de reserva: `booking_cancelled_not_successful,created_booking_not_cancelled`. La limpieza automática canceló una reserva de certificación; no se repite ni se crea otra para maquillar el resultado.
- La misma conversación evidenció discrepancia de día de semana. El guard de `requestedWeekday` ya está en main mediante #863 y queda heredado por esta rama. Esto corrige la validación de fecha, pero **no prueba** que la cancelación esté corregida.
- Evidencia histórica: [resultado de llamada y fallos](https://github.com/Nicricht/helvoca/issues/848#issuecomment-6628182363).

## Gates de salida

1. Obtener CI exact-HEAD SUCCESS en PR #868: Fast Gate, Golden Journey, test Java, navegador, PG18/RLS y PG16/18/Flyway. La certificación histórica de PR #853/#863 no sustituye este run.
2. Tras CI, comprobar la configuración del servicio Aurora y reconciliar el pin/source antes de desplegar únicamente en `demo-isolated`. No cambiar producción.
3. Tras el despliegue DEMO, verificar API, PostgreSQL, Flyway y readiness sin activar voz/WhatsApp ni generar tráfico.
4. La aceptación de voz y de reserva/cancelación requiere una prueba autorizada y evidencia completa; no se declara Golden Journey aprobado por el solo hecho de que la llamada conteste.
5. El respaldo externo cifrado sigue pendiente de custodia de clave privada fuera del job y OAuth institucional renovable. El respaldo nativo Railway continúa como protección separada.
6. La suscripción SaaS LIVE requiere resolver con Mercado Pago cómo entregar el evento firmado `subscription_authorized_payment` para preapproval sin plan. El SDK Java 3.7.0 y el OpenAPI de suscripciones consultados no exponen `notification_url` en la solicitud `PreapprovalCreateRequest`; no se añadirá un campo especulativo ni se repetirán checkouts TEST.
7. Meta App Review sigue en curso. WhatsApp LIVE es una integración posterior y no bloquea la V1 telefónica según el plan comercial vigente.

## Límites de esta entrega

Sin migraciones aplicadas, cambios de flags o credenciales, cambios de números/rutas, llamadas, mensajes, reservas, pagos, contacto a proveedores, despliegues ni modificaciones de producción.