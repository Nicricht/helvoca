# Piloto real y aislado: importación de imágenes/PDF, presupuesto objetivo USD 2

**Estado inicial: SOLO PREPARACIÓN, CERO LLAMADAS PAGADAS.** Issue #773.
El código del importador de producción sigue OFF, y el PR #777 de FAQ/horarios es otro trabajo en revisión.

## Permisos y dependencias antes del primer gasto

1. Crear un **proyecto nuevo, exclusivo** en OpenAI Platform, p. ej. `RecepVoz-Document-Import-Pilot`. No usar el proyecto predeterminado ni compartirlo con el asistente de voz. Elegir la organización correcta, confirmar facturación, permisos y proyecto.
2. En **Project → Limits / Spend limits**, establecer **USD 2.00 por mes con HARD ENFORCEMENT** (no solo alertas); alertas 50%, 80% y 100%, permitir solo modelo `gpt-4.1-mini`. Confirmar que el proyecto empieza sin gastos y sin otro tráfico. Un límite proveedor HARD puede excederse ligeramente por retrasos de medición: **USD 2 es un objetivo, no garantía de máximo absoluto**. No proseguir si no se puede establecer o verificar el límite.
3. Crear una **clave nueva solo para ese proyecto**. Mantenerla fuera de GitHub, ChatGPT y producción. El piloto solo lee `OPENAI_PILOT_API_KEY` local. No usar `OPENAI_API_KEY` ni las credenciales de Railway.
4. Usar hasta **tres archivos consentidos y no sensibles**: una fotografía simple de menú, una fotografía de servicios/tarifas, un PDF de una o dos páginas. Generar datos sintéticos si aún no existe consentimiento. Antes de enviar, revisar que no haya RUT, teléfonos privados, datos de pacientes o clientes.
5. Instalar `pypdf` localmente para validar la cantidad de páginas; `python -m pip install pypdf` (solo para archivos PDF). Imagen ≤512KB, PDF ≤256KB y ≤2 páginas.
6. Guardar la evidencia en un log privado fuera del repositorio. Nunca subir documento, clave, propuestas sensibles ni log de negocio. El script no crea reservas, pagos, clientes ni activa canales.

## Prueba local gratuita (ningún request a OpenAI)

```bash
python3 scripts/pilot/openai_import_probe.py --help
python3 scripts/pilot/openai_import_probe.py ~/pilot/menu.jpg ~/pilot/tarifas.png ~/pilot/folleto.pdf
python3 -m unittest discover -s scripts/pilot -p 'test_*.py' -v
```

## Solicitud pagada tras confirmar el límite en OpenAI

Configurar *localmente* variables de entorno:
```text
OPENAI_PILOT_PROJECT_ID=proj_...(del proyecto aislado)
OPENAI_PILOT_API_KEY=<clave privada del proyecto aislado>
HELVOCA_PILOT_APPROVED=yes
HELVOCA_PILOT_PROJECT_ISOLATED=yes
HELVOCA_PILOT_HARD_CAP_CONFIRMED=yes
HELVOCA_PILOT_SPEND_USD=2.00
```

**Estos flags son una declaración humana de comprobación**. El script no puede leer ni probar remotamente la política de gasto del proveedor. No configurar los flags por adelantado.

Ejecutar con una ruta auditada **fuera del repositorio**, una vez y bajo supervisión:
```bash
python3 scripts/pilot/openai_import_probe.py \
  --send --audit ~/private-recepvoz-pilot/receipts.jsonl \
  --report ~/private-recepvoz-pilot/proposals.jsonl \
  ~/pilot/menu.jpg ~/pilot/tarifas.png ~/pilot/folleto.pdf
```
En Windows usar `py -3`, rutas Windows y configurar las variables en una consola privada sin compartir la clave.

## Qué comprueba realmente

- Una solicitud por archivo, **sin reintentos**, hasta 3, salida máxima 500 tokens, imágenes con detalle bajo. Conserva SHA-256 del archivo, fases STARTED/RESPONSE/UNCERTAIN, ID de respuesta, modelo, input/output tokens y estimación USD. **Nunca guarda en el log de auditoría** la clave, texto del documento ni contenido de respuesta.
- Las propuestas extraídas por IA se guardan **solo localmente** en el archivo privado `--report`, separado de la auditoría y fuera del repositorio, para contrastarlas contra lo visible en los archivos. Destruir este reporte al terminar la revisión según el procedimiento de privacidad.
- Reserva local conservadora `USD 0.50` por intento, `USD 1.50` en tres intentos, para dejar colchón dentro del objetivo USD 2. **No es precio facturado ni cota matemática**: PDFs pueden expandirse en tokens, llamadas fallidas pueden facturarse, la contabilidad del proveedor puede retrasarse. Si falta usage o existe incertidumbre, se detiene; no repetir ciegamente.
- Comparar manualmente la respuesta de cada archivo con los datos realmente visibles, detectando precios o servicios inventados y omisiones. El script NO aplica información en RecepVoz. Esta prueba **no certifica horarios, FAQ, frontend, integración tenant ni el camino SaaS completo**.
- Tras la prueba, contrastar el proyecto en OpenAI **Costs** con los tokens registrados. El endpoint Costs (con acceso autorizado de administrador) es la fuente financiera; ninguna estimación de tokens sustituye la factura. Revocar la clave y archivar el proyecto cuando termine el piloto.

## GO / NO-GO

No ejecutar si no existe proyecto exclusivo, límite HARD verificado, credencial independiente, consentimiento de archivos y log privado. No ejecutar si cualquiera de las tres funciones de protección dice OFF. **Nunca habilitar** `HELVOCA_BUSINESS_IMPORT_AI_ENABLED=true` en producción como parte de este piloto.
