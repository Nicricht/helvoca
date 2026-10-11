# RecepVoz: piloto gratuito y aislado de Gemini

**Estado: preparado, sin llamadas a Gemini ni claves copiadas.** La prueba es exclusivamente con archivos sintéticos y en un proyecto personal propio.

## En Google AI Studio

1. Ir a https://aistudio.google.com/projects y seleccionar una cuenta personal autorizada. La captura previa mostraba una cuenta educativa con `Default Gemini Project` y cuatro claves existentes: **no reutilizarlas ni usarla para el piloto**.
2. Crear un proyecto nuevo llamado `RecepVoz-Gemini-Pilot` y comprobar su ID único. Si AI Studio exige importar un proyecto de Google Cloud, crearlo desde la cuenta personal y luego importarlo.
3. Comprobar explícitamente que el proyecto aparezca en **Free Tier**, sin cuenta de facturación vinculada, y que el modelo `gemini-3.5-flash-lite` tenga cuota gratuita disponible. Los modelos y cuotas pueden cambiar. No activar facturación.
4. Crear clave propia del proyecto, limitar el uso a la Gemini API si se ofrece esa opción y guardarla **solo localmente**. No subir la clave a GitHub, ChatGPT, capturas, Railway ni a proyectos de voz.
5. Usar un menú de restaurante y una lista de servicios completamente inventados, JPG/PNG/WEBP o PDF corto. En el nivel gratuito Google puede utilizar contenido para mejorar productos. No enviar información real de clientes.

## Prueba sin conexión ni claves

```bash
python3 scripts/pilot/gemini_import_probe.py ~/pilot/menu.png ~/pilot/servicios.jpg
python3 -m unittest discover -s scripts/pilot -p 'test_*.py' -v
```

Por defecto no envía ninguna solicitud. Admite 1 a 3 archivos diferentes, imágenes de hasta 512 KB y PDF de hasta 256 KB con 1 o 2 páginas. Para PDF instalar `pypdf` localmente.

## Requisito para la primera solicitud gratuita

Configurar estas variables **únicamente después de comprobar personalmente cada punto**, nunca por adelantado:

```text
GEMINI_PILOT_APPROVED=yes
GEMINI_PILOT_PERSONAL_PROJECT=yes
GEMINI_PILOT_FREE_TIER_CONFIRMED=yes
GEMINI_PILOT_NO_BILLING=yes
GEMINI_PILOT_SYNTHETIC_ONLY=yes
GEMINI_PILOT_PROJECT_NAME=RecepVoz-Gemini-Pilot
GEMINI_PILOT_PROJECT_ID=<ID del proyecto nuevo>
GEMINI_PILOT_API_KEY=<clave del proyecto nuevo>
```

**IMPORTANTE:** las banderas son comprobaciones humanas; el script no puede verificar la facturación del proveedor de forma remota. Si existe alguna duda, detenerse. Este flujo no tiene permiso para consumir el presupuesto de USD 2 reservado al piloto de OpenAI.

```bash
python3 scripts/pilot/gemini_import_probe.py --send \
  --audit ~/private-recepvoz-pilot/gemini-audit.jsonl \
  --report ~/private-recepvoz-pilot/gemini-review.jsonl \
  ~/pilot/menu.png ~/pilot/servicios.jpg
```

Las carpetas de auditoría y revisión deben estar fuera del repositorio. Nunca repetir automáticamente solicitudes inciertas. El ejecutor registra fases STARTED/RESPONSE/UNCERTAIN, modelo, tokens y SHA-256, y separa propuestas del registro financiero. No escribe en la base de datos ni activa IA comercial.

## Criterios de comparación contra OpenAI

Comparar ambos modelos con **las mismas imágenes sintéticas** y una tabla manual de nombres, precios, stock y duraciones explícitos. Aceptación: 0 datos inventados; si un campo es ilegible debe quedar ausente o con advertencia. Comparar latencia y tokens; comprobar en AI Studio Usage que no exista gasto de Gemini. Las pruebas unitarias solo verifican seguridad del ejecutor, no precisión real.

## Fuentes oficiales

- https://ai.google.dev/gemini-api/docs/pricing
- https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite
- https://ai.google.dev/gemini-api/docs/api-key
- https://ai.google.dev/gemini-api/docs/billing

**Producción sigue apagada** para importación pagada de imágenes/PDF. No habilitar `HELVOCA_BUSINESS_IMPORT_AI_ENABLED` ni modificar sus cuotas para este experimento.

## Benchmark sin evaluación manual repetitiva

El ejecutor Gemini devuelve un esquema amplio de negocio, promociones, extras, horarios y FAQ. Solo analiza el archivo incluido en la solicitud, sin historial de menú anterior. Para evaluar automáticamente una imagen con ground truth revisado:

```bash
python3 scripts/pilot/benchmark_import.py \
  scripts/pilot/fixtures/mercado_del_patio_angled_truth.json \
  ~/private-recepvoz-pilot/actual.json \
  --output ~/private-recepvoz-pilot/score.json
```

El argumento `--truth` permite que el propio piloto calcule la evaluación **después** de una llamada API autorizada:

```bash
python3 scripts/pilot/gemini_import_probe.py --send \
  --audit ~/private-recepvoz-pilot/audit.jsonl \
  --report ~/private-recepvoz-pilot/review.jsonl \
  --truth scripts/pilot/fixtures/mercado_del_patio_angled_truth.json \
  ~/pilot/mercado-del-patio.png
```

El puntaje compara todos los campos explícitos y se detiene después del primer resultado inseguro. No existe acción que publique productos o promociones. En CI se verifican regresiones usando respuestas simuladas: **nunca se dispara la API externa**. El ground truth se creó revisando la tercera imagen ficticia; los límites y credenciales humanas del piloto siguen vigentes.

## Pruebas automáticas de principio a fin (sin subir menús a mano)

Genera tres archivos JPEG **ficticios** reproducibles (limpio, inclinado, con brillo), sus respuestas esperadas y un manifiesto. Las imágenes son carteles con texto sintetizado, no fotografías de platos. El tercer menú manual sigue siendo un caso independiente.

```bash
python3 -m pip install Pillow==11.3.0
python3 scripts/pilot/fixture_factory.py --out /tmp/recepvoz-gemini-menu-tests
python3 scripts/pilot/benchmark_batch.py /tmp/recepvoz-gemini-menu-tests/manifest.json --out /tmp/recepvoz-gemini-offline
```

**Importante:** estas órdenes son OFFLINE. Revisan archivos y autorización sin solicitar IA real. Para hacer una única corrida real de hasta 3 solicitudes, el propietario debe verificar personalmente proyecto gratuito, facturación ausente y la clave aislada, configurar las variables `GEMINI_PILOT_*` conforme a la guía anterior y utilizar un directorio **nuevo** fuera del repositorio:

```bash
python3 scripts/pilot/benchmark_batch.py /tmp/recepvoz-gemini-menu-tests/manifest.json --out /tmp/recepvoz-gemini-live-first --send
```

El resultado de cada llamada se valida automáticamente contra los datos visibles revisados: se detiene en el primer error; no hay reintentos ni escrituras comerciales. Nunca colocar la clave en este repositorio, un issue, un chat o la UI web. La existencia de una clave Gemini en Railway para **voz/mensajería** no autoriza importaciones con esa clave ni un proyecto diferente. La ejecución CI no realiza peticiones de IA y no puede probar calidad visual del modelo en vivo.

## Windows: un único asistente privado

El archivo `scripts/pilot/Run-GeminiPilot.ps1` automatiza la preparación y evaluación. No requiere volver al Playground para cada prueba. Usa el identificador del proyecto personal ficticio `gen-lang-client-0812582341` registrado por el propietario. **No usa `GEMINI_API_KEY` de producción ni modifica Railway.**

Desde PowerShell, **en la carpeta raíz de este repositorio**:

```powershell
# Primera vez: introducir la clave exclusivamente en la ventana privada de PowerShell
.\scripts\pilot\Run-GeminiPilot.ps1 -Configure

# Evaluación sin Gemini y sin costo (default)
.\scripts\pilot\Run-GeminiPilot.ps1

# Evaluación con el endpoint real, solo tras volver a confirmar Free Tier sin facturación
.\scripts\pilot\Run-GeminiPilot.ps1 -Send
```

La clave queda cifrada con Windows DPAPI bajo `%LOCALAPPDATA%\RecepVoz\GeminiImportPilot`, vinculada al usuario Windows, fuera del repositorio. El script no envía secretos en argumentos, URL, stdout ni archivos Git, los carga como variable del proceso solo para la corrida real y los elimina al terminar. Los recibos/revisiones privados permanecen en esa carpeta. **No ejecutar el modo `-Send` si Google solicita habilitar facturación** o si el propietario no ha verificado el nivel gratuito, la cuota o la clave. Es una confirmación humana, no una comprobación técnica infalible del estado de facturación.

La primera corrida real sigue bloqueada mientras no exista una clave aislada aportada privadamente por el propietario. La ruta de API está implementada pero no certificada contra el servidor del proveedor hasta entonces; las pruebas de GitHub usan respuestas simuladas y nunca llaman al proveedor.
